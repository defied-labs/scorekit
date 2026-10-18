package dev.defied.scorekit.storage.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

record ScoreColumn<S>(String table, Writer<S> writer, Reader<S> reader) {

    static final ScoreColumn<Double> NUMERIC = new ScoreColumn<>("scorekit_numeric_scores", PreparedStatement::setDouble, ResultSet::getDouble);
    static final ScoreColumn<String> TEXT = new ScoreColumn<>("scorekit_text_scores", PreparedStatement::setString, ResultSet::getString);

    interface Writer<S> {
        void write(PreparedStatement statement, int index, S score) throws SQLException;
    }

    interface Reader<S> {
        S read(ResultSet result, int index) throws SQLException;
    }
}
