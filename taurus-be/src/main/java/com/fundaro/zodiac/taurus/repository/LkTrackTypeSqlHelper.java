package com.fundaro.zodiac.taurus.repository;

import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Table;

import java.util.List;

public class LkTrackTypeSqlHelper extends CommonSqlHelper {

    public List<Expression> getColumns(Table table, String columnPrefix) {
        List<Expression> columns = super.getCommonColumns(table, columnPrefix);
        columns.add(Column.aliased("name", table, columnPrefix + "_name"));
        columns.add(Column.aliased("description", table, columnPrefix + "_description"));

        return columns;
    }
}
