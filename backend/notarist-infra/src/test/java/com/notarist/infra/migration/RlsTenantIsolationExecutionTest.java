package com.notarist.infra.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executes the platform's tenant-isolation invariant against a real PostgreSQL — the security property
 * CLAUDE.md calls "the highest-consequence security invariant," previously only review-verified.
 *
 * <p>It reproduces production faithfully in the one way that matters for RLS: the application connects
 * as a role that is <b>not a superuser, does not hold BYPASSRLS, and OWNS the tables</b> (Flyway creates
 * the tables as the app role in production). A table owner bypasses plain ROW LEVEL SECURITY — which is
 * exactly the hole Flyway V14's {@code FORCE ROW LEVEL SECURITY} closes. So this test is also the
 * executable proof that V14 does its job: were V14 removed, {@link #tenantSeesOnlyItsOwnRows} would go
 * green-to-red because the owning app role would see every tenant's rows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RlsTenantIsolationExecutionTest {

    private EmbeddedPostgres pg;
    private String appJdbcUrl;

    private static final String TENANT_A = UUID.randomUUID().toString();
    private static final String TENANT_B = UUID.randomUUID().toString();

    @BeforeAll
    void setUp() throws Exception {
        pg = EmbeddedPostgres.start();
        DataSource superuser = pg.getPostgresDatabase();

        Flyway.configure().dataSource(superuser)
                .locations("classpath:db/postgres/flyway").load().migrate();

        try (Connection c = superuser.getConnection(); Statement st = c.createStatement()) {
            // A dedicated application role, exactly as production requires: NOT superuser, NO BYPASSRLS.
            st.execute("CREATE ROLE notarist_app LOGIN PASSWORD 'app' NOSUPERUSER NOBYPASSRLS");
            // Make it the OWNER of the table under test — the production configuration in which only
            // FORCE ROW LEVEL SECURITY (V14) keeps RLS in effect.
            st.execute("ALTER TABLE notarist_user OWNER TO notarist_app");
            st.execute("GRANT USAGE ON SCHEMA public TO notarist_app");
            st.execute("GRANT EXECUTE ON FUNCTION notarist_set_identity(text,text,text) TO notarist_app");
            st.execute("GRANT EXECUTE ON FUNCTION notarist_set_system_identity() TO notarist_app");
            st.execute("GRANT EXECUTE ON FUNCTION notarist_tenant_visible(varchar) TO notarist_app");
        }
        appJdbcUrl = pg.getJdbcUrl("notarist_app", "postgres");

        // Seed one user per tenant, each written under its own tenant identity (so WITH CHECK passes).
        insertUserAsTenant(TENANT_A, "user-a");
        insertUserAsTenant(TENANT_B, "user-b");
    }

    @AfterAll
    void tearDown() throws Exception {
        if (pg != null) pg.close();
    }

    private Connection appConnection() throws SQLException {
        return DriverManager.getConnection(appJdbcUrl, "notarist_app", "app");
    }

    /** Runs a unit of work as the app role, in one transaction, under the given tenant identity. */
    private void asTenant(String tenantId, SqlWork work) throws SQLException {
        try (Connection c = appConnection()) {
            c.setAutoCommit(false);   // identity is transaction-local (set_config(..., TRUE))
            setIdentity(c, tenantId);
            work.run(c);
            c.commit();
        }
    }

    private void setIdentity(Connection c, String tenantId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT notarist_set_identity(?, ?, ?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, tenantId);
            ps.setString(3, "STAFF");
            ps.execute();
        }
    }

    private void insertUserAsTenant(String tenantId, String username) throws SQLException {
        asTenant(tenantId, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO notarist_user (user_id, tenant_id, username, password_hash, full_name) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, tenantId);
                ps.setString(3, username);
                ps.setString(4, "x");
                ps.setString(5, username);
                ps.executeUpdate();
            }
        });
    }

    private int countUsers(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM notarist_user")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    @DisplayName("a tenant sees only its own rows, never another tenant's — even as the table owner")
    void tenantSeesOnlyItsOwnRows() throws Exception {
        int[] seenByA = {-1};
        asTenant(TENANT_A, c -> seenByA[0] = countUsers(c));
        assertThat(seenByA[0]).isEqualTo(1);   // user-a only; user-b is invisible across the boundary
    }

    @Test
    @DisplayName("with no identity established the policy is fail-closed — zero rows, never all rows")
    void noIdentityIsFailClosed() throws Exception {
        try (Connection c = appConnection()) {
            c.setAutoCommit(false);
            // Deliberately no notarist_set_identity(...) call.
            assertThat(countUsers(c)).isEqualTo(0);
            c.rollback();
        }
    }

    @Test
    @DisplayName("WITH CHECK forbids writing a row into another tenant")
    void cannotWriteAcrossTenant() throws Exception {
        assertThatThrownBy(() -> asTenant(TENANT_A, c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO notarist_user (user_id, tenant_id, username, password_hash, full_name) "
                            + "VALUES (?, ?, ?, ?, ?)")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, TENANT_B);   // caller is tenant A — this must be rejected
                ps.setString(3, "smuggled-" + UUID.randomUUID());
                ps.setString(4, "x");
                ps.setString(5, "smuggled");
                ps.executeUpdate();
            }
        })).isInstanceOf(SQLException.class)
           .hasMessageContaining("row-level security");
    }

    @Test
    @DisplayName("the explicit system identity is the one path that sees every tenant")
    void systemIdentitySeesAllTenants() throws Exception {
        try (Connection c = appConnection()) {
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SELECT notarist_set_system_identity()");
            }
            assertThat(countUsers(c)).isGreaterThanOrEqualTo(2);   // both tenants' users
            c.rollback();
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection c) throws SQLException;
    }
}
