package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.LastResearch;
import io.r2dbc.spi.Row;
import org.springframework.stereotype.Service;

/**
 * Converter between {@link Row} to {@link LastResearch}, with proper type conversions.
 */
@Service
public class LastResearchRowMapper extends CommonRowMapper<LastResearch> {

    public LastResearchRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     *
     * @return the {@link LastResearch} stored in the database.
     */
    @Override
    public LastResearch apply(Row row, String prefix) {
        LastResearch entity = new LastResearch(super.apply(row, prefix));
        entity.setUserId(getConverter().fromRow(row, prefix + "_user_id", String.class));
        entity.setValue(getConverter().fromRow(row, prefix + "_value", String.class));
        entity.setField(getConverter().fromRow(row, prefix + "_field", String.class));
        return entity;
    }
}
