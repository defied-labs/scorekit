package dev.defied.scorekit.storage.jdbc;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
class MySqlJdbcStorageTest extends JdbcStorageTest {

    @Container
    static final MySQLContainer DATABASE = new MySQLContainer("mysql:8.4")
            .withCommand("--innodb-flush-log-at-trx-commit=0", "--skip-log-bin");

    @BeforeAll
    static void start() {
        connect(DATABASE);
    }

    @Override
    protected boolean supportsInfiniteScores() {
        return false;
    }
}