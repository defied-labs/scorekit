package dev.defied.scorekit.storage.jdbc;

import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StorageException;
import dev.defied.scorekit.storage.StoredEntry;
import dev.defied.scorekit.storage.VersionedEntry;
import dev.defied.scorekit.storage.VersionedStorage;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class JdbcStorage<S> implements VersionedStorage<S> {

    private static final int PUT_ATTEMPTS = 3;
    private static final String[] VERSION_COLUMN = {"version"};

    private final DataSource dataSource;
    private final LeaderboardId id;
    private final ScoreColumn<S> column;
    private final String update;
    private final String insert;
    private final String delete;
    private final String select;
    private final String rank;
    private final String top;
    private final String count;
    private final String scanFirst;
    private final String scanNext;
    private final String changesFirst;
    private final String changesNext;
    private final String purge;

    JdbcStorage(DataSource dataSource, LeaderboardId id, ScoreColumn<S> column) {
        this.dataSource = dataSource;
        this.id = id;
        this.column = column;
        String table = column.table();
        String scope = " WHERE provider = ? AND leaderboard = ?";
        String live = scope + " AND NOT removed";
        String nextVersion = "CASE WHEN version >= ? THEN version + 1 ELSE ? END";
        String columns = "SELECT entry_key, score, version, removed FROM " + table;
        update = "UPDATE " + table + " SET score = ?, removed = FALSE, version = " + nextVersion + scope + " AND entry_key = ?";
        insert = "INSERT INTO " + table + " (provider, leaderboard, entry_key, score, version) VALUES (?, ?, ?, ?, ?)";
        delete = "UPDATE " + table + " SET removed = TRUE, version = " + nextVersion + live + " AND entry_key = ?";
        select = "SELECT score FROM " + table + live + " AND entry_key = ?";
        rank = "SELECT (SELECT COUNT(*) FROM " + table + " o WHERE o.provider = e.provider AND o.leaderboard = e.leaderboard"
                + " AND NOT o.removed AND (o.score > e.score OR (o.score = e.score AND o.entry_key > e.entry_key)))"
                + " FROM " + table + " e WHERE e.provider = ? AND e.leaderboard = ? AND NOT e.removed AND e.entry_key = ?";
        top = "SELECT entry_key, score FROM " + table + live + " ORDER BY score DESC, entry_key DESC";
        count = "SELECT COUNT(*) FROM " + table + live;
        scanFirst = columns + live + " ORDER BY entry_key";
        scanNext = columns + live + " AND entry_key > ? ORDER BY entry_key";
        changesFirst = columns + scope + " AND version >= ? ORDER BY entry_key";
        changesNext = columns + scope + " AND version >= ? AND entry_key > ? ORDER BY entry_key";
        purge = "DELETE FROM " + table + scope + " AND removed AND version < ?";
    }

    @Override
    public long putVersioned(String key, S score) {
        return execute(connection -> {
            for (int attempt = 1; ; attempt++) {
                long now = VersionedStorage.now();
                OptionalLong updated = updateReturningVersion(connection, update, statement -> {
                    column.writer().write(statement, 1, score);
                    statement.setLong(2, now);
                    statement.setLong(3, now);
                    bindEntry(statement, 4, key);
                });
                if (updated.isPresent()) {
                    return updated.getAsLong();
                }
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    bindEntry(statement, 1, key);
                    column.writer().write(statement, 4, score);
                    statement.setLong(5, now);
                    statement.executeUpdate();
                    return now;
                } catch (SQLException e) {
                    if (!isConstraintViolation(e) || attempt == PUT_ATTEMPTS) {
                        throw e;
                    }
                }
            }
        });
    }

    @Override
    public OptionalLong removeVersioned(String key) {
        return execute(connection -> {
            long now = VersionedStorage.now();
            return updateReturningVersion(connection, delete, statement -> {
                statement.setLong(1, now);
                statement.setLong(2, now);
                bindEntry(statement, 3, key);
            });
        });
    }

    @Override
    public Optional<S> score(String key) {
        return query(select, statement -> bindEntry(statement, 1, key),
                result -> result.next() ? Optional.of(column.reader().read(result, 1)) : Optional.empty());
    }

    @Override
    public OptionalLong rank(String key) {
        return query(rank, statement -> bindEntry(statement, 1, key),
                result -> result.next() ? OptionalLong.of(result.getLong(1) + 1) : OptionalLong.empty());
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        if (n == 0) {
            return List.of();
        }
        return query(top, statement -> {
            bindScope(statement);
            statement.setMaxRows(n);
        }, result -> {
            List<StoredEntry<S>> entries = new ArrayList<>();
            while (result.next()) {
                entries.add(new StoredEntry<>(result.getString(1), column.reader().read(result, 2)));
            }
            return entries;
        });
    }

    @Override
    public long size() {
        return query(count, this::bindScope, result -> {
            result.next();
            return result.getLong(1);
        });
    }

    @Override
    public ScanPage<S> scan(String cursor, int count) {
        if (cursor == null) {
            return page(scanFirst, this::bindScope, count);
        }
        return page(scanNext, statement -> bindEntry(statement, 1, cursor), count);
    }

    @Override
    public ScanPage<S> scanChanges(long sinceVersion, String cursor, int count) {
        return page(cursor == null ? changesFirst : changesNext, statement -> {
            bindScope(statement);
            statement.setLong(3, sinceVersion);
            if (cursor != null) {
                statement.setString(4, cursor);
            }
        }, count);
    }

    @Override
    public void purgeRemoved(long beforeVersion) {
        execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(purge)) {
                bindScope(statement);
                statement.setLong(3, beforeVersion);
                return statement.executeUpdate();
            }
        });
    }

    private ScanPage<S> page(String sql, Binder binder, int count) {
        return ScanPage.of(query(sql, statement -> {
            binder.bind(statement);
            statement.setMaxRows(count);
        }, result -> {
            List<VersionedEntry<S>> entries = new ArrayList<>();
            while (result.next()) {
                S score = result.getBoolean(4) ? null : column.reader().read(result, 2);
                entries.add(new VersionedEntry<>(result.getString(1), score, result.getLong(3)));
            }
            return entries;
        }), count);
    }

    private OptionalLong updateReturningVersion(Connection connection, String sql, Binder binder) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql, VERSION_COLUMN)) {
            binder.bind(statement);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? OptionalLong.of(keys.getLong(1)) : OptionalLong.empty();
            }
        }
    }

    private <T> T query(String sql, Binder binder, ResultReader<T> reader) {
        return execute(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet result = statement.executeQuery()) {
                    return reader.read(result);
                }
            }
        });
    }

    private void bindScope(PreparedStatement statement) throws SQLException {
        statement.setString(1, id.provider());
        statement.setString(2, id.leaderboard());
    }

    private void bindEntry(PreparedStatement statement, int index, String key) throws SQLException {
        statement.setString(index, id.provider());
        statement.setString(index + 1, id.leaderboard());
        statement.setString(index + 2, key);
    }

    private static boolean isConstraintViolation(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith("23");
    }

    private <T> T execute(Work<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            return work.run(connection);
        } catch (SQLException e) {
            throw new StorageException("Storage failure on leaderboard " + id, e);
        }
    }

    private interface Work<T> {
        T run(Connection connection) throws SQLException;
    }

    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private interface ResultReader<T> {
        T read(ResultSet result) throws SQLException;
    }
}
