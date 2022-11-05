package com.fnd.taurus.model;

public enum QuerySyntax {
    EQUALITY,
    NEGATION,
    GREATER_THAN,
    LESS_THAN,
    GREATER_EQUAL_THAN,
    LESS_EQUAL_THAN,
    LIKE,
    STARTS_WITH,
    ENDS_WITH,
    CONTAINS;

    public static final String[] SIMPLE_OPERATION_SET = { "<=", ">=", ":", "!", ">", "<", "~"};

    public static final String ZERO_OR_MORE_REGEX = "*";

    public static final String OR_OPERATOR = "OR";

    public static final String AND_OPERATOR = "AND";

    public static final String LEFT_PARENTHESIS = "(";

    public static final String RIGHT_PARENTHESIS = ")";

    public static QuerySyntax getSimpleOperation(final String input) {
        return switch (input) {
            case ":" -> EQUALITY;
            case "!" -> NEGATION;
            case ">" -> GREATER_THAN;
            case "<" -> LESS_THAN;
            case ">=" -> GREATER_EQUAL_THAN;
            case "<=" -> LESS_EQUAL_THAN;
            case "~" -> LIKE;
            default -> null;
        };
    }
}