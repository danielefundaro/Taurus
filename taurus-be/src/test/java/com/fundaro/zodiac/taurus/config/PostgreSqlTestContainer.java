package com.fundaro.zodiac.taurus.config;

import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

public class PostgreSqlTestContainer implements SqlTestContainer {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlTestContainer.class);

    private PostgreSQLContainer<?> postgreSQLContainer;

    @Override
    public void destroy() {
        if (null != postgreSQLContainer && postgreSQLContainer.isRunning()) {
            postgreSQLContainer.stop();
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (null == postgreSQLContainer) {
            postgreSQLContainer = new PostgreSQLContainer<>("postgres:17.0")
                .withDatabaseName("taurus")
                .withTmpFs(Collections.singletonMap("/testtmpfs", "rw"))
                // Spring tiene in cache un contesto per ogni configurazione di test e non ne chiude
                // nessuno fino a fine suite: con una decina di contesti vivi, i rispettivi pool di
                // connessioni superano il max_connections di default (100) e gli ultimi contesti non
                // riescono piu' ad avviarsi.
                .withCommand("postgres", "-c", "max_connections=400")
                .withLogConsumer(new Slf4jLogConsumer(LOG))
                .withReuse(true);
        }
        if (!postgreSQLContainer.isRunning()) {
            postgreSQLContainer.start();
        }
    }

    @Override
    public JdbcDatabaseContainer<?> getTestContainer() {
        return postgreSQLContainer;
    }
}
