package com.fundaro.zodiac.taurus.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Queue of the optical music recognition jobs, bound to the Taurus exchange. Messages are JSON
 * {@code {"tenant": "...", "jobId": 1}}; the job table remains the source of truth.
 */
@Configuration
public class ScoreRecognitionQueueConfiguration {

    public static final String QUEUE = "score.recognition";

    @Bean
    public Declarables scoreRecognitionDeclarables() {
        return new Declarables(
            new Queue(QUEUE, true),
            new Binding(QUEUE, Binding.DestinationType.QUEUE, RabbitMQConfig.topicExchangeName, QUEUE, null)
        );
    }
}
