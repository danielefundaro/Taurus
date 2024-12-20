package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.Tracks;
import io.r2dbc.spi.Row;
import org.springframework.stereotype.Service;

/**
 * Converter between {@link Row} to {@link Tracks}, with proper type conversions.
 */
@Service
public class TracksRowMapper extends CommonRowMapper<Tracks> {

    public TracksRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     *
     * @return the {@link Tracks} stored in the database.
     */
    @Override
    public Tracks apply(Row row, String prefix) {
        Tracks entity = new Tracks(super.apply(row, prefix));
        entity.setName(getConverter().fromRow(row, prefix + "_name", String.class));
        entity.setDescription(getConverter().fromRow(row, prefix + "_description", String.class));
        entity.setComposer(getConverter().fromRow(row, prefix + "_composer", String.class));
        entity.setArranger(getConverter().fromRow(row, prefix + "_arranger", String.class));
        entity.setTypeId(getConverter().fromRow(row, prefix + "_type_id", Long.class));
        return entity;
    }
}
