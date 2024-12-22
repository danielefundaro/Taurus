package com.fundaro.zodiac.taurus.repository;

import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Table;

import java.util.List;

public class PerformersSqlHelper extends CommonSqlHelper {

    public List<Expression> getColumns(Table table, String columnPrefix) {
        List<Expression> columns = super.getCommonColumns(table, columnPrefix);
        columns.add(Column.aliased("description", table, columnPrefix + "_description"));
        columns.add(Column.aliased("instrument_id", table, columnPrefix + "_instrument_id"));
        columns.add(Column.aliased("media_id", table, columnPrefix + "_media_id"));

        return columns;
    }
}
