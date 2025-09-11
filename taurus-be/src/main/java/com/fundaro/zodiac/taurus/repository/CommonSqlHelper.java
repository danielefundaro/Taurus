package com.fundaro.zodiac.taurus.repository;

import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Table;

import java.util.ArrayList;
import java.util.List;

public abstract class CommonSqlHelper {

    public abstract List<Expression> getColumns(Table table, String columnPrefix);

    protected List<Expression> getCommonColumns(Table table, String columnPrefix) {
        List<Expression> columns = new ArrayList<>();
        columns.add(Column.aliased("id", table, columnPrefix + "_id"));
        columns.add(Column.aliased("deleted", table, columnPrefix + "_deleted"));
        columns.add(Column.aliased("insert_by", table, columnPrefix + "_insert_by"));
        columns.add(Column.aliased("insert_date", table, columnPrefix + "_insert_date"));
        columns.add(Column.aliased("edit_by", table, columnPrefix + "_edit_by"));
        columns.add(Column.aliased("edit_date", table, columnPrefix + "_edit_date"));
        columns.add(Column.aliased("user_id", table, columnPrefix + "_user_id"));

        return columns;
    }
}
