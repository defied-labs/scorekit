package dev.defied.scorekit.storage.jdbc;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Testcontainers
class MariaDbJdbcStorageTest extends JdbcStorageTest {

    @Container
    static final MariaDBContainer DATABASE = new MariaDBContainer("mariadb:11.4")
            .withCommand("--innodb-flush-log-at-trx-commit=0");

    @BeforeAll
    static void start() {
        connect(DATABASE);
    }

    @Override
    protected boolean supportsInfiniteScores() {
        return false;
    }
}