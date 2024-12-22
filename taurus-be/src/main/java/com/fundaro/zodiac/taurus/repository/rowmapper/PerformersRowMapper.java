package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.Performers;
import io.r2dbc.spi.Row;
import org.springframework.stereotype.Service;

/**
 * Converter between {@link Row} to {@link Performers}, with proper type conversions.
 */
@Service
public class PerformersRowMapper extends CommonRowMapper<Performers> {

    public PerformersRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     *
     * @return the {@link Performers} stored in the database.
     */
    @Override
    public Performers apply(Row row, String prefix) {
        Performers entity = new Performers();
        entity.setDescription(getConverter().fromRow(row, prefix + "_description", String.class));
        entity.setInstrumentId(getConverter().fromRow(row, prefix + "_instrument_id", Long.class));
        entity.setMediaId(getConverter().fromRow(row, prefix + "_media_id", Long.class));
        return entity;
    }
}
