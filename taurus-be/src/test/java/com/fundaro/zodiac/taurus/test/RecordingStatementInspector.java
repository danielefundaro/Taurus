package com.fundaro.zodiac.taurus.test;

import java.util.ArrayList;
import java.util.List;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * Registra le istruzioni SQL preparate da Hibernate sul thread del test, mentre la registrazione è attiva. Si attiva
 * con {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}: i test che lo usano con le stesse
 * proprietà condividono il contesto Spring.
 */
public class RecordingStatementInspector implements StatementInspector {

    private static final ThreadLocal<List<String>> RECORDED = new ThreadLocal<>();

    public static void start() {
        RECORDED.set(new ArrayList<>());
    }

    public static List<String> stop() {
        List<String> statements = RECORDED.get();
        RECORDED.remove();
        return statements;
    }

    @Override
    public String inspect(String sql) {
        List<String> statements = RECORDED.get();
        if (statements != null) statements.add(sql);
        return sql;
    }
}
