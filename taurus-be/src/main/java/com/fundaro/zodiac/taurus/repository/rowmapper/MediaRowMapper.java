package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.Media;
import io.r2dbc.spi.Row;
import java.time.ZonedDateTime;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;

/**
 * Converter between {@link Row} to {@link Media}, with proper type conversions.
 */
@Service
public class MediaRowMapper extends CommonRowMapper<Media> {

    public MediaRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     * @return the {@link Media} stored in the database.
     */
    @Override
    public Media apply(Row row, String prefix) {
        Media entity = new Media(super.apply(row, prefix));
        entity.setName(getConverter().fromRow(row, prefix + "_name", String.class));
        entity.setDescription(getConverter().fromRow(row, prefix + "_description", String.class));
        entity.setOrderNumber(getConverter().fromRow(row, prefix + "_order_number", Long.class));
        entity.setTrackId(getConverter().fromRow(row, prefix + "_track_id", Long.class));
        return entity;
    }
}
