package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.Albums;
import io.r2dbc.spi.Row;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

/**
 * Converter between {@link Row} to {@link Albums}, with proper type conversions.
 */
@Service
public class AlbumsRowMapper extends CommonRowMapper<Albums> {

    public AlbumsRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     *
     * @return the {@link Albums} stored in the database.
     */
    @Override
    public Albums apply(Row row, String prefix) {
        Albums entity = new Albums(super.apply(row, prefix));
        entity.setName(super.getConverter().fromRow(row, prefix + "_name", String.class));
        entity.setDescription(super.getConverter().fromRow(row, prefix + "_description", String.class));
        entity.setDate(super.getConverter().fromRow(row, prefix + "_date", ZonedDateTime.class));
        return entity;
    }
}
