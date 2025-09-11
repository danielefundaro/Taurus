package com.fundaro.zodiac.taurus.repository;

import org.springframework.data.relational.core.sql.Column;
import org.springframework.data.relational.core.sql.Expression;
import org.springframework.data.relational.core.sql.Table;

import java.util.List;

public class LastResearchSqlHelper extends CommonSqlHelper {

    public List<Expression> getColumns(Table table, String columnPrefix) {
        List<Expression> columns = super.getCommonColumns(table, columnPrefix);
        columns.add(Column.aliased("value", table, columnPrefix + "_value"));
        columns.add(Column.aliased("field", table, columnPrefix + "_field"));

        return columns;
    }
}
