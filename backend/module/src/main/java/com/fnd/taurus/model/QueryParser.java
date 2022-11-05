package com.fnd.taurus.model;

import lombok.extern.slf4j.Slf4j;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class QueryParser {

    public static Map<String, Operator> ops = Map.of("AND", Operator.AND, "OR", Operator.OR, "or", Operator.OR, "and", Operator.AND);
    public static Pattern SpecCriteriaRegex = Pattern.compile(String.format("^(.*)(%s)(\\p{Punct}?)([^*]*)(\\p{Punct}?)$", String.join("|", QuerySyntax.SIMPLE_OPERATION_SET)));

    private String searchParam;

    public QueryParser(String searchParam) {
        this.searchParam = searchParam;
    }

    private static boolean isHigherPrecedenceOperator(String currOp, String prevOp) {
        return (ops.containsKey(prevOp) && ops.get(prevOp).precedence >= ops.get(currOp).precedence);
    }

    public Deque<?> parse() {
        Deque<Object> output = new LinkedList<>();
        Deque<String> stack = new LinkedList<>();

        this.searchParam = fixStringSpace(this.searchParam);

        for (String token : searchParam.split("\\s+")) {
            token = token.replace((char) 254, ' ');
            if (ops.containsKey(token)) {
                while (!stack.isEmpty() && isHigherPrecedenceOperator(token, stack.peek()))
                    output.push(stack.pop()
                            .equalsIgnoreCase(QuerySyntax.OR_OPERATOR) ? QuerySyntax.OR_OPERATOR : QuerySyntax.AND_OPERATOR);
                stack.push(token.equalsIgnoreCase(QuerySyntax.OR_OPERATOR) ? QuerySyntax.OR_OPERATOR : QuerySyntax.AND_OPERATOR);
            } else if (token.equals(QuerySyntax.LEFT_PARENTHESIS)) {
                stack.push(QuerySyntax.LEFT_PARENTHESIS);
            } else if (token.equals(QuerySyntax.RIGHT_PARENTHESIS)) {
                while (stack.peek() != null && !stack.peek().equals(QuerySyntax.LEFT_PARENTHESIS))
                    output.push(stack.pop());
                stack.pop();
            } else {
                Matcher matcher = SpecCriteriaRegex.matcher(token);
                while (matcher.find()) {
                    QueryItem item = new QueryItem(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4), matcher.group(5));
                    log.trace(item.toString());
                    output.push(item);
                }
            }
        }

        while (!stack.isEmpty())
            output.push(stack.pop());

        log.trace(output.toString());
        return output;
    }

    private String fixStringSpace(String text) {
        String[] split = text.split("\\*([^*]*)\\*|'([^']*)'");
        int initPos, endPos;
        StringBuilder res = new StringBuilder();
        endPos = -1;
        String el;

        for (String s : split) {
            el = s;
            initPos = text.indexOf(el);
            if (endPos != -1) {
                res.append(text.substring(endPos, initPos).replace(' ', (char) 254));
            }

            res.append(el);
            endPos = initPos + el.length();
        }

        if (endPos == -1) {
            res = new StringBuilder(text);
        } else if (endPos < text.length()) {
            res.append(text.substring(endPos).replace(' ', (char) 254));
        }

        return res.toString();
    }

    public enum Operator {
        OR(1), AND(2);
        final int precedence;

        Operator(int p) {
            precedence = p;
        }
    }
}