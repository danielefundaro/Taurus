package com.fundaro.zodiac.taurus.repository.rowmapper;

import com.fundaro.zodiac.taurus.domain.CommonFields;
import io.r2dbc.spi.Row;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.function.BiFunction;

@Service
public class CommonRowMapper<E extends CommonFields> implements BiFunction<Row, String, E> {

    private final ColumnConverter converter;

    public CommonRowMapper(ColumnConverter converter) {
        this.converter = converter;
    }

    protected ColumnConverter getConverter() {
        return converter;
    }

    /**
     * Take a {@link Row} and a column prefix, and extract all the fields.
     *
     * @return the {@link CommonFields} stored in the database.
     */
    @Override
    public E apply(Row row, String prefix) {
        E entity = (E) new CommonFields();
        entity.setId(converter.fromRow(row, prefix + "_id", Long.class));
        entity.setDeleted(converter.fromRow(row, prefix + "_deleted", Boolean.class));
        entity.setInsertBy(converter.fromRow(row, prefix + "_insert_by", String.class));
        entity.setInsertDate(converter.fromRow(row, prefix + "_insert_date", ZonedDateTime.class));
        entity.setEditBy(converter.fromRow(row, prefix + "_edit_by", String.class));
        entity.setEditDate(converter.fromRow(row, prefix + "_edit_date", ZonedDateTime.class));
        return entity;
    }
}
