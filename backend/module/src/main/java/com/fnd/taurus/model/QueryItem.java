package com.fnd.taurus.model;

import lombok.Getter;

@Getter
public class QueryItem {

    private final String key;
    private final QuerySyntax operation;
    private final Object value;

    public QueryItem(String key, String operation, String prefix, String value, String suffix) {
        QuerySyntax op = QuerySyntax.getSimpleOperation(operation);
        if (op != null) {
            if (op == QuerySyntax.EQUALITY) { // the operation may be complex operation
                final boolean startWithAsterisk = prefix != null && prefix.contains(QuerySyntax.ZERO_OR_MORE_REGEX);
                final boolean endWithAsterisk = suffix != null && suffix.contains(QuerySyntax.ZERO_OR_MORE_REGEX);

                if (startWithAsterisk && endWithAsterisk) {
                    op = QuerySyntax.CONTAINS;
                } else if (startWithAsterisk) {
                    op = QuerySyntax.ENDS_WITH;
                } else if (endWithAsterisk) {
                    op = QuerySyntax.STARTS_WITH;
                }
            }
        }

        this.key = key;
        this.operation = op;
        this.value = value;
    }

    @Override
    public String toString() {
        return key + "-" + operation + "-" + value;
    }
}