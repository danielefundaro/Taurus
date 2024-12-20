package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.Collections;
import io.r2dbc.spi.Row;
import org.springframework.stereotype.Service;

/**
 * Converter between {@link Row} to {@link Collections}, with proper type conversions.
 */
@Service
public class CollectionsRowMapper extends CommonRowMapper<Collections> {

    public CollectionsRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     *
     * @return the {@link Collections} stored in the database.
     */
    @Override
    public Collections apply(Row row, String prefix) {
        Collections entity = new Collections(super.apply(row, prefix));
        entity.setOrderNumber(getConverter().fromRow(row, prefix + "_order_number", Long.class));
        entity.setAlbumId(getConverter().fromRow(row, prefix + "_album_id", Long.class));
        entity.setTrackId(getConverter().fromRow(row, prefix + "_track_id", Long.class));
        return entity;
    }
}
