package com.fundaro.zodiac.taurus.rabbitmq.impl;

import com.fundaro.zodiac.taurus.config.RabbitMQConfig;
import com.fundaro.zodiac.taurus.rabbitmq.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class SenderImpl implements Sender {

    private final Logger log;

    private final RabbitTemplate template;

    public SenderImpl(RabbitTemplate template) {
        this.log = LoggerFactory.getLogger(Sender.class);
        this.template = template;
    }

    @Override
    public void send(byte[] message) {
        log.debug("Send message: {}", message);
        template.convertAndSend(RabbitMQConfig.topicExchangeName, RabbitMQConfig.queueNameListener, message);
    }
}
