package dev.defied.scorekit.storage.jdbc;

import dev.defied.scorekit.storage.LeaderboardId;
import dev.defied.scorekit.storage.LeaderboardStorage;
import dev.defied.scorekit.storage.ScanPage;
import dev.defied.scorekit.storage.StorageException;
import dev.defied.scorekit.storage.StoredEntry;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class JdbcStorage<S> implements LeaderboardStorage<S> {

    private static final int PUT_ATTEMPTS = 3;

    private final DataSource dataSource;
    private final LeaderboardId id;
    private final ScoreColumn<S> column;
    private final String update;
    private final String insert;
    private final String select;
    private final String rank;
    private final String top;
    private final String delete;
    private final String count;
    private final String scanFirst;
    private final String scanNext;
    private final String clear;

    JdbcStorage(DataSource dataSource, LeaderboardId id, ScoreColumn<S> column) {
        this.dataSource = dataSource;
        this.id = id;
        this.column = column;
        String table = column.table();
        String scope = " WHERE provider = ? AND leaderboard = ?";
        String entry = scope + " AND entry_key = ?";
        update = "UPDATE " + table + " SET score = ?" + entry;
        insert = "INSERT INTO " + table + " (provider, leaderboard, entry_key, score) VALUES (?, ?, ?, ?)";
        select = "SELECT score FROM " + table + entry;
        rank = "SELECT (SELECT COUNT(*) FROM " + table + " o WHERE o.provider = e.provider AND o.leaderboard = e.leaderboard"
                + " AND (o.score > e.score OR (o.score = e.score AND o.entry_key > e.entry_key)))"
                + " FROM " + table + " e WHERE e.provider = ? AND e.leaderboard = ? AND e.entry_key = ?";
        top = "SELECT entry_key, score FROM " + table + scope + " ORDER BY score DESC, entry_key DESC";
        delete = "DELETE FROM " + table + entry;
        count = "SELECT COUNT(*) FROM " + table + scope;
        scanFirst = "SELECT entry_key, score FROM " + table + scope + " ORDER BY entry_key";
        scanNext = "SELECT entry_key, score FROM " + table + scope + " AND entry_key > ? ORDER BY entry_key";
        clear = "DELETE FROM " + table + scope;
    }

    @Override
    public void put(String key, S score) {
        execute(connection -> {
            for (int attempt = 1; ; attempt++) {
                if (update(connection, key, score) > 0) {
                    return null;
                }
                try (PreparedStatement statement = scoped(connection, insert, key)) {
                    column.writer().write(statement, 4, score);
                    statement.executeUpdate();
                    return null;
                } catch (SQLException e) {
                    if (!isConstraintViolation(e) || attempt == PUT_ATTEMPTS) {
                        throw e;
                    }
                }
            }
        });
    }

    @Override
    public Optional<S> score(String key) {
        return execute(connection -> {
            try (PreparedStatement statement = scoped(connection, select, key); ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(column.reader().read(result, 1)) : Optional.empty();
            }
        });
    }

    @Override
    public OptionalLong rank(String key) {
        return execute(connection -> {
            try (PreparedStatement statement = scoped(connection, rank, key); ResultSet result = statement.executeQuery()) {
                return result.next() ? OptionalLong.of(result.getLong(1) + 1) : OptionalLong.empty();
            }
        });
    }

    @Override
    public List<StoredEntry<S>> top(int n) {
        if (n == 0) {
            return List.of();
        }
        return entries(top, null, n);
    }

    @Override
    public ScanPage<S> scan(String cursor, int count) {
        List<StoredEntry<S>> entries = cursor == null ? entries(scanFirst, null, count) : entries(scanNext, cursor, count);
        return new ScanPage<>(entries, entries.isEmpty() || entries.size() < count ? null : entries.getLast().key());
    }

    @Override
    public void clear() {
        execute(connection -> {
            try (PreparedStatement statement = scoped(connection, clear, null)) {
                return statement.executeUpdate();
            }
        });
    }

    private List<StoredEntry<S>> entries(String sql, String key, int limit) {
        return execute(connection -> {
            try (PreparedStatement statement = scoped(connection, sql, key)) {
                statement.setMaxRows(limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<StoredEntry<S>> entries = new ArrayList<>();
                    while (result.next()) {
                        entries.add(new StoredEntry<>(result.getString(1), column.reader().read(result, 2)));
                    }
                    return entries;
                }
            }
        });
    }

    @Override
    public boolean remove(String key) {
        return execute(connection -> {
            try (PreparedStatement statement = scoped(connection, delete, key)) {
                return statement.executeUpdate() > 0;
            }
        });
    }

    @Override
    public long size() {
        return execute(connection -> {
            try (PreparedStatement statement = scoped(connection, count, null); ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        });
    }

    private int update(Connection connection, String key, S score) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            column.writer().write(statement, 1, score);
            statement.setString(2, id.provider());
            statement.setString(3, id.leaderboard());
            statement.setString(4, key);
            return statement.executeUpdate();
        }
    }

    private PreparedStatement scoped(Connection connection, String sql, String key) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, id.provider());
        statement.setString(2, id.leaderboard());
        if (key != null) {
            statement.setString(3, key);
        }
        return statement;
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
}
