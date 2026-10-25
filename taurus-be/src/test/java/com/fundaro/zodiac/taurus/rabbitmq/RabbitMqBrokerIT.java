package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prove sul broker reale: consegna, riconsegna dopo un errore, messaggio avvelenato e broker
 * irraggiungibile. Fino a questa suite le code erano sempre finte, quindi il comportamento del
 * broker — in particolare la riconsegna — non era mai stato verificato.
 *
 * <p>Il broker è un container avviato una volta per la suite e riusato; le code dei test sono
 * create e cancellate a ogni prova, così nessuna prova vede i messaggi di un'altra.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
    }
)
class RabbitMqBrokerIT {

    private static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:3.13-management-alpine").withReuse(true);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    static {
        BROKER.start();
    }

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", BROKER::getHost);
        registry.add("spring.rabbitmq.port", BROKER::getAmqpPort);
        registry.add("spring.rabbitmq.username", BROKER::getAdminUsername);
        registry.add("spring.rabbitmq.password", BROKER::getAdminPassword);
    }

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private RabbitAdmin admin;
    private String queueName;
    private SimpleMessageListenerContainer listener;

    @BeforeEach
    void declareQueue() {
        admin = new RabbitAdmin(connectionFactory);
        queueName = "test.broker-" + UUID.randomUUID();
        admin.declareQueue(new Queue(queueName, false, false, true));
    }

    @AfterEach
    void removeQueue() {
        if (listener != null) listener.stop();
        admin.deleteQueue(queueName);
    }

    @Test
    void deliversAPublishedMessageToItsConsumer() throws InterruptedException {
        List<String> received = new CopyOnWriteArrayList<>();
        CountDownLatch delivered = new CountDownLatch(1);
        listen(message -> {
            received.add(body(message));
            delivered.countDown();
        });

        rabbitTemplate.convertAndSend(queueName, "job-1");

        assertThat(delivered.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        assertThat(received).containsExactly("job-1");
    }

    @Test
    void redeliversAMessageWhoseProcessingFailedOnce() throws InterruptedException {
        List<String> attempts = new CopyOnWriteArrayList<>();
        CountDownLatch secondAttempt = new CountDownLatch(2);
        listen(message -> {
            attempts.add(body(message));
            secondAttempt.countDown();
            // Il primo tentativo fallisce: il broker rimette il messaggio in coda.
            if (attempts.size() == 1) throw new IllegalStateException("transient failure");
        });

        rabbitTemplate.convertAndSend(queueName, "job-2");

        assertThat(secondAttempt.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        assertThat(attempts).containsExactly("job-2", "job-2");
    }

    /**
     * Comportamento attuale, da conoscere prima di decidere una coda di scarto: senza coda di
     * scarto un messaggio che fallisce sempre viene riconsegnato senza fine. Il test si limita a
     * osservare le prime riconsegne e poi ferma il consumatore.
     */
    @Test
    void keepsRedeliveringAMessageThatAlwaysFails() throws InterruptedException {
        CountDownLatch attempts = new CountDownLatch(3);
        listen(message -> {
            attempts.countDown();
            throw new IllegalStateException("poison message");
        });

        rabbitTemplate.convertAndSend(queueName, "job-3");

        assertThat(attempts.await(TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
        listener.stop();
    }

    @Test
    void failsFastWhenTheBrokerIsUnreachable() {
        CachingConnectionFactory unreachable = new CachingConnectionFactory(BROKER.getHost(), findClosedPort());
        unreachable.setConnectionTimeout(1000);
        RabbitTemplate template = new RabbitTemplate(unreachable);

        try {
            assertThatThrownBy(() -> template.convertAndSend(queueName, "job-4")).isInstanceOf(AmqpException.class);
        } finally {
            unreachable.destroy();
        }
    }

    private void listen(org.springframework.amqp.core.MessageListener handler) {
        listener = new SimpleMessageListenerContainer(connectionFactory);
        listener.setQueueNames(queueName);
        listener.setAcknowledgeMode(AcknowledgeMode.AUTO);
        listener.setMessageListener(handler);
        listener.setPrefetchCount(1);
        listener.start();
    }

    private static String body(Message message) {
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }

    /** Una porta su cui non ascolta nessuno: il broker del container usa quella mappata. */
    private static int findClosedPort() {
        return BROKER.getAmqpPort() == 65_000 ? 64_999 : 65_000;
    }
}
