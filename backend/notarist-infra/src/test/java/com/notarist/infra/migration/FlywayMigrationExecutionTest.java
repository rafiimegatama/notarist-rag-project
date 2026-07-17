package com.notarist.infra.migration;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the real Flyway migration set (V1–V14) against a real PostgreSQL — a genuine embedded
 * server binary started by zonky embedded-postgres, NOT Docker and NOT H2. This proves, by execution,
 * that the migrations apply cleanly on a cold, empty database (Priority 4: fresh database) and that the
 * RLS objects they declare actually create on PostgreSQL (Priority 8).
 */
class FlywayMigrationExecutionTest {

    @Test
    void migrationsApplyCleanlyOnAFreshDatabase() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.start()) {
            DataSource ds = pg.getPostgresDatabase();

            MigrateResult result = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/postgres/flyway")
                    .load()
                    .migrate();

            // Every migration on disk applied, in order, with no failure.
            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isEqualTo(14);
            assertThat(result.targetSchemaVersion).isEqualTo("14");

            // The tables V9–V13 enable RLS on must all end up FORCE'd (that is exactly what V14 fixes).
            List<String> forced = tablesWithForcedRls(ds);
            assertThat(forced).contains(
                    "notarist_user", "dokumen_legal", "ingestion_job",
                    "notarial_case", "case_timeline",
                    "bundle", "bundle_workflow", "bundle_timeline",
                    "ocr_review", "verification");
        }
    }

    private List<String> tablesWithForcedRls(DataSource ds) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT relname FROM pg_class "
                             + "WHERE relrowsecurity = true AND relforcerowsecurity = true "
                             + "AND relkind = 'r' ORDER BY relname")) {
            while (rs.next()) {
                tables.add(rs.getString("relname"));
            }
        }
        return tables;
    }
}
