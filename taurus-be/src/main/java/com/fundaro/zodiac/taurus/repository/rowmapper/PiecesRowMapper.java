package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.Pieces;
import com.fundaro.zodiac.taurus.domain.enumeration.PieceTypeEnum;
import io.r2dbc.spi.Row;
import java.time.ZonedDateTime;
import java.util.function.BiFunction;
import org.springframework.stereotype.Service;

/**
 * Converter between {@link Row} to {@link Pieces}, with proper type conversions.
 */
@Service
public class PiecesRowMapper extends CommonRowMapper<Pieces> {

    public PiecesRowMapper(ColumnConverter converter) {
        super(converter);
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     * @return the {@link Pieces} stored in the database.
     */
    @Override
    public Pieces apply(Row row, String prefix) {
        Pieces entity = new Pieces(super.apply(row, prefix));
        entity.setName(getConverter().fromRow(row, prefix + "_name", String.class));
        entity.setDescription(getConverter().fromRow(row, prefix + "_description", String.class));
        entity.setType(getConverter().fromRow(row, prefix + "_type", PieceTypeEnum.class));
        entity.setContentType(getConverter().fromRow(row, prefix + "_content_type", String.class));
        entity.setPath(getConverter().fromRow(row, prefix + "_path", String.class));
        entity.setOrderNumber(getConverter().fromRow(row, prefix + "_order_number", Long.class));
        entity.setMediaId(getConverter().fromRow(row, prefix + "_media_id", Long.class));
        return entity;
    }
}
