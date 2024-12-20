package com.fundaro.zodiac.taurus.repository;

import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Table;

import java.util.List;

public class CollectionsSqlHelper extends CommonSqlHelper {

    public List<Expression> getColumns(Table table, String columnPrefix) {
        List<Expression> columns = super.getCommonColumns(table, columnPrefix);
        columns.add(Column.aliased("order_number", table, columnPrefix + "_order_number"));
        columns.add(Column.aliased("album_id", table, columnPrefix + "_album_id"));
        columns.add(Column.aliased("track_id", table, columnPrefix + "_track_id"));

        return columns;
    }
}
