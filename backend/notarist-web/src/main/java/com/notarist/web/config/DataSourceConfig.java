package com.notarist.web.config;

import oracle.jdbc.pool.OracleDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * DataSource configuration skeleton for Oracle (primary) and PostgreSQL.
 * TODO (STEP 8B): configure proper connection pooling (HikariCP), VPD context setter.
 */
@Configuration
public class DataSourceConfig {

    @Value("${notarist.database.oracle.url}")
    private String oracleUrl;

    @Value("${notarist.database.oracle.username}")
    private String oracleUsername;

    @Value("${notarist.database.oracle.password}")
    private String oraclePassword;

    @Bean
    @Primary
    public DataSource oracleDataSource() throws SQLException {
        OracleDataSource ds = new OracleDataSource();
        ds.setURL(oracleUrl);
        ds.setUser(oracleUsername);
        ds.setPassword(oraclePassword);
        // TODO (STEP 8B): configure HikariCP pool wrapper, VPD context injection
        return ds;
    }
}
