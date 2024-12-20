package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.Instruments;
import io.r2dbc.spi.Row;
import org.springframework.stereotype.Service;

/**
 * Converter between {@link Row} to {@link Instruments}, with proper type conversions.
 */
@Service
public class InstrumentsRowMapper extends CommonRowMapper<Instruments> {

    public InstrumentsRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     *
     * @return the {@link Instruments} stored in the database.
     */
    @Override
    public Instruments apply(Row row, String prefix) {
        Instruments entity = new Instruments(super.apply(row, prefix));
        entity.setName(getConverter().fromRow(row, prefix + "_name", String.class));
        entity.setDescription(getConverter().fromRow(row, prefix + "_description", String.class));
        return entity;
    }
}
