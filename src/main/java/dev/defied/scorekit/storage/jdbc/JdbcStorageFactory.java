package dev.defied.scorekit.storage.jdbc;

import dev.defied.scorekit.codec.ScoreType;
import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.StorageFactory;

import javax.sql.DataSource;
import java.util.Objects;

public final class JdbcStorageFactory implements StorageFactory {

    private final DataSource dataSource;

    public JdbcStorageFactory(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> LeaderboardStorage<S> create(LeaderboardId id, ScoreType<S> type) {
        ScoreColumn<?> column = type == ScoreType.NUMERIC ? ScoreColumn.NUMERIC : ScoreColumn.TEXT;
        return new JdbcStorage<>(dataSource, id, (ScoreColumn<S>) column);
    }
}
