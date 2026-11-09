package dev.jagadeesh.oms.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jagadeesh.oms.common.events.EventHeaders;
import dev.jagadeesh.oms.common.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private final NotificationService service;
    private final ObjectMapper json;

    public OrderEventsListener(NotificationService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @KafkaListener(topics = Topics.ORDERS)
    public void on(ConsumerRecord<String, String> record) throws JsonProcessingException {
        service.handle(EventHeaders.eventId(record), EventHeaders.eventType(record), json.readTree(record.value()));
    }
}
