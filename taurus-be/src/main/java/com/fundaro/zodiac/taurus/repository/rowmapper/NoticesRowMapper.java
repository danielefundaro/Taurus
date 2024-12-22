package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.Notices;
import io.r2dbc.spi.Row;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

/**
 * Converter between {@link Row} to {@link Notices}, with proper type conversions.
 */
@Service
public class NoticesRowMapper extends CommonRowMapper<Notices> {

    public NoticesRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     *
     * @return the {@link Notices} stored in the database.
     */
    @Override
    public Notices apply(Row row, String prefix) {
        Notices entity = super.apply(row, prefix);
        entity.setUserId(getConverter().fromRow(row, prefix + "_user_id", String.class));
        entity.setName(getConverter().fromRow(row, prefix + "_name", String.class));
        entity.setMessage(getConverter().fromRow(row, prefix + "_message", String.class));
        entity.setReadDate(getConverter().fromRow(row, prefix + "_read_date", ZonedDateTime.class));
        return entity;
    }
}
