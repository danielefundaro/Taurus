package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.LkTrackType;
import io.r2dbc.spi.Row;
import java.time.ZonedDateTime;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;

/**
 * Converter between {@link Row} to {@link LkTrackType}, with proper type conversions.
 */
@Service
public class LkTrackTypeRowMapper extends CommonRowMapper<LkTrackType> {

    public LkTrackTypeRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     * @return the {@link LkTrackType} stored in the database.
     */
    @Override
    public LkTrackType apply(Row row, String prefix) {
        LkTrackType entity = new LkTrackType(super.apply(row, prefix));
        entity.setName(getConverter().fromRow(row, prefix + "_name", String.class));
        entity.setDescription(getConverter().fromRow(row, prefix + "_description", String.class));
        return entity;
    }
}
