package dev.defied.scorekit.storage.jdbc;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgresJdbcStorageTest extends JdbcStorageTest {

    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine");

    @BeforeAll
    static void start() {
        connect(DATABASE);
    }
}