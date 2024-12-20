package com.fundaro.zodiac.taurus.repository;

import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Table;

import java.util.List;

public class PiecesSqlHelper extends CommonSqlHelper {

    public List<Expression> getColumns(Table table, String columnPrefix) {
        List<Expression> columns = super.getCommonColumns(table, columnPrefix);
        columns.add(Column.aliased("name", table, columnPrefix + "_name"));
        columns.add(Column.aliased("description", table, columnPrefix + "_description"));
        columns.add(Column.aliased("type", table, columnPrefix + "_type"));
        columns.add(Column.aliased("content_type", table, columnPrefix + "_content_type"));
        columns.add(Column.aliased("path", table, columnPrefix + "_path"));
        columns.add(Column.aliased("order_number", table, columnPrefix + "_order_number"));
        columns.add(Column.aliased("media_id", table, columnPrefix + "_media_id"));

        return columns;
    }
}
