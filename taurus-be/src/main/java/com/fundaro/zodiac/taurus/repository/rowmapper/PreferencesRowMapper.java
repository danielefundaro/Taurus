package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.Preferences;
import io.r2dbc.spi.Row;
import org.springframework.stereotype.Service;

/**
 * Converter between {@link Row} to {@link Preferences}, with proper type conversions.
 */
@Service
public class PreferencesRowMapper extends CommonRowMapper<Preferences> {

    public PreferencesRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     *
     * @return the {@link Preferences} stored in the database.
     */
    @Override
    public Preferences apply(Row row, String prefix) {
        Preferences entity = new Preferences(super.apply(row, prefix));
        entity.setUserId(getConverter().fromRow(row, prefix + "_user_id", String.class));
        entity.setKey(getConverter().fromRow(row, prefix + "_key", String.class));
        entity.setValue(getConverter().fromRow(row, prefix + "_value", String.class));
        return entity;
    }
}
