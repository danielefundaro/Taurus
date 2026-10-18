package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.config.ScoreRecognitionQueueConfiguration;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScoreRecognitionQueue;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScoreRecognitionQueue.JobMessage;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScoreRecognitionWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs the recognition jobs of the queue, as many at a time as configured. Messages that cannot be used are dropped
 * instead of being delivered again forever; the job table decides what still has to run.
 */
@Component
public class ScoreRecognitionListener {

    private static final Logger log = LoggerFactory.getLogger(ScoreRecognitionListener.class);

    private final ScoreRecognitionQueue queue;
    private final ScoreRecognitionWorker worker;
    private final TenantSchemaRegistry tenantSchemaRegistry;

    public ScoreRecognitionListener(ScoreRecognitionQueue queue, ScoreRecognitionWorker worker, TenantSchemaRegistry tenantSchemaRegistry) {
        this.queue = queue;
        this.worker = worker;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
    }

    @RabbitListener(
        id = "scoreRecognition",
        queues = ScoreRecognitionQueueConfiguration.QUEUE,
        concurrency = "${application.score-playback.omr.concurrency:1}"
    )
    public void receive(Message message) {
        JobMessage job;
        try {
            job = queue.read(message);
        } catch (IOException exception) {
            throw new AmqpRejectAndDontRequeueException("Unreadable recognition message", exception);
        }
        if (job.tenant() == null || job.jobId() == null || !tenantSchemaRegistry.isActive(job.tenant())) {
            log.warn("Recognition message for an unknown tenant or job dropped");
            throw new AmqpRejectAndDontRequeueException("Recognition message without an active tenant");
        }
        worker.run(job.tenant(), job.jobId());
    }
}
