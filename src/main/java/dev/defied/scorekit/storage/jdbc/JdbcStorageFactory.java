package dev.defied.scorekit.storage.jdbc;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.StorageException;
import dev.defied.scorekit.storage.VersionedStorage;
import dev.defied.scorekit.storage.VersionedStorageFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public final class JdbcStorageFactory implements VersionedStorageFactory {

    private final DataSource dataSource;
    private final JdbcDialect dialect;

    public JdbcStorageFactory(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = JdbcDialect.of(dataSource);
    }

    public void createSchema() {
        try (InputStream schema = JdbcStorageFactory.class.getResourceAsStream(dialect.schema());
             Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : new String(schema.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        } catch (IOException | SQLException e) {
            throw new StorageException("Cannot create the " + dialect + " schema", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> VersionedStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        ScoreColumn<?> column = type == ScoreType.NUMERIC ? ScoreColumn.NUMERIC : ScoreColumn.TEXT;
        return new JdbcStorage<>(dataSource, dialect, id, (ScoreColumn<S>) column);
    }
}