package com.fundaro.zodiac.taurus.test;

import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Docker è un prerequisito dei test P0 che usano PostgreSQL, RabbitMQ o Keycloak reali.
 *
 * <p>Sulla macchina di uno sviluppatore senza Docker quei test si saltano, così la suite resta
 * utilizzabile. In pipeline no: un test P0 saltato renderebbe il run incompleto senza che nessuno
 * se ne accorga, quindi con {@code -Dtaurus.test.requireDocker=true} l'assenza di Docker è un
 * fallimento.
 */
public final class DockerRequirement {

    public static final String PROPERTY = "taurus.test.requireDocker";

    private DockerRequirement() {
    }

    /** Ferma il test quando Docker manca: con un fallimento in pipeline, con un salto altrove. */
    public static void required() {
        boolean available = DockerClientFactory.instance().isDockerAvailable();
        if (Boolean.parseBoolean(System.getProperty(PROPERTY))) {
            assertThat(available)
                .as("Docker serve a questo test P0 e la pipeline non accetta un test saltato (-D%s=true)", PROPERTY)
                .isTrue();
            return;
        }

        assumeTrue(available, "Docker is required for this integration test");
    }
}
