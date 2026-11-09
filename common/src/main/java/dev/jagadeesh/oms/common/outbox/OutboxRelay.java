package dev.jagadeesh.oms.common.outbox;

import dev.jagadeesh.oms.common.events.EventHeaders;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Publishes pending outbox rows in creation order, keyed by aggregate id. At-least-once: a crash between send and
 * commit sends the row again, which is why every consumer de-duplicates on the event-id header (see Inbox).
 */
@Component
@ConditionalOnProperty(name = "oms.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka, MeterRegistry meters) {
        this.outbox = outbox;
        this.kafka = kafka;
        Gauge.builder("oms.outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull).register(meters);
    }

    @Scheduled(fixedDelayString = "${oms.outbox.poll-ms:500}")
    @Transactional
    public int publishPending() {
        int sent = 0;
        for (OutboxEvent e : outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                var record = new ProducerRecord<>(e.getTopic(), e.getAggregateId(), e.getPayload());
                record.headers().add(EventHeaders.EVENT_ID, e.getId().toString().getBytes(StandardCharsets.UTF_8));
                record.headers().add(EventHeaders.EVENT_TYPE, e.getEventType().getBytes(StandardCharsets.UTF_8));
                kafka.send(record).get(5, TimeUnit.SECONDS);
                e.markPublished(Instant.now());
                sent++;
            } catch (Exception ex) {
                // stop here so later events of the same order are not published before this one
                log.warn("Outbox publish failed for {} ({}), retrying next poll: {}", e.getId(), e.getEventType(), ex.getMessage());
                break;
            }
        }
        return sent;
    }
}
