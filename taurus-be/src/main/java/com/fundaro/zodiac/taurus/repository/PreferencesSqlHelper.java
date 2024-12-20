package com.fundaro.zodiac.taurus.repository;

import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Table;

import java.util.List;

public class PreferencesSqlHelper extends CommonSqlHelper {

    public List<Expression> getColumns(Table table, String columnPrefix) {
        List<Expression> columns = super.getCommonColumns(table, columnPrefix);
        columns.add(Column.aliased("user_id", table, columnPrefix + "_user_id"));
        columns.add(Column.aliased("key", table, columnPrefix + "_key"));
        columns.add(Column.aliased("value", table, columnPrefix + "_value"));

        return columns;
    }
}
