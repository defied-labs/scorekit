package dev.defied.scorekit.storage.jdbc;

import dev.defied.scorekit.storage.StorageException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

enum JdbcDialect {

    POSTGRESQL("postgresql.sql", true),
    MYSQL("mysql.sql", false),
    MARIADB("mariadb.sql", false);

    private final String schema;
    private final boolean postgres;

    JdbcDialect(String schema, boolean postgres) {
        this.schema = schema;
        this.postgres = postgres;
    }

    String schema() {
        return schema;
    }

    boolean returning() {
        return postgres;
    }

    boolean infiniteScores() {
        return postgres;
    }

    boolean correlatedRank() {
        return postgres;
    }

    String version(String expression) {
        return postgres ? expression : "LAST_INSERT_ID(" + expression + ")";
    }

    static JdbcDialect of(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String product = metadata.getDatabaseProductName();
            if (product.equals("PostgreSQL")) {
                return POSTGRESQL;
            }
            if (product.equals("MariaDB") || metadata.getDatabaseProductVersion().contains("MariaDB")) {
                return MARIADB;
            }
            if (product.equals("MySQL")) {
                return MYSQL;
            }
            throw new IllegalArgumentException("Unsupported database: " + product);
        } catch (SQLException e) {
            throw new StorageException("Cannot detect the database", e);
        }
    }
}
