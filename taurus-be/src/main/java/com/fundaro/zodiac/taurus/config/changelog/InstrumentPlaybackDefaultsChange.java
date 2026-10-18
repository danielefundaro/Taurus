package com.fundaro.zodiac.taurus.config.changelog;

import com.fundaro.zodiac.taurus.service.scoreplayback.InstrumentPlaybackSuggester;
import com.fundaro.zodiac.taurus.service.scoreplayback.InstrumentPlaybackSuggester.Suggestion;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Fills the playback settings of the instruments of a tenant schema with the proposal computed from their name.
 * Runs once per schema, both for existing tenants and for new ones after the instrument seed, and only touches
 * instruments without any playback setting: values set by the tenant are never overwritten.
 */
public class InstrumentPlaybackDefaultsChange implements CustomTaskChange {

    private int updated;

    @Override
    public void execute(Database database) throws CustomChangeException {
        String table = database.escapeTableName(null, database.getDefaultSchemaName(), "instrument");
        Connection connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
        try (
            PreparedStatement select = connection.prepareStatement(
                "SELECT id, name FROM " + table +
                    " WHERE deleted = FALSE AND transposition_semitones IS NULL AND sound_program IS NULL AND percussion_key IS NULL"
            );
            PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table +
                    " SET transposition_semitones = ?, bass_clef_transposition_semitones = ?, sound_bank = ?, sound_program = ?," +
                    " percussion_key = ?, edit_by = 'system', edit_date = CURRENT_TIMESTAMP WHERE id = ?"
            );
            ResultSet rows = select.executeQuery()
        ) {
            while (rows.next()) {
                Suggestion value = InstrumentPlaybackSuggester.suggest(rows.getString("name")).orElse(null);
                if (value == null) continue;
                setNullable(update, 1, value.transposition());
                setNullable(update, 2, value.bassClefTransposition());
                setNullable(update, 3, value.soundBank());
                setNullable(update, 4, value.soundProgram());
                setNullable(update, 5, value.percussionKey());
                update.setLong(6, rows.getLong("id"));
                update.addBatch();
                updated++;
            }
            if (updated > 0) update.executeBatch();
        } catch (SQLException exception) {
            throw new CustomChangeException("Could not fill the playback settings of the instruments", exception);
        }
    }

    private static void setNullable(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, Types.SMALLINT);
        else statement.setInt(index, value);
    }

    @Override
    public String getConfirmationMessage() {
        return "Playback settings proposed for " + updated + " instruments";
    }

    @Override
    public void setUp() {}

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {}

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
