package dev.jagadeesh.oms.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jagadeesh.oms.common.events.EventHeaders;
import dev.jagadeesh.oms.common.events.OrderEvents.OrderCancelled;
import dev.jagadeesh.oms.common.events.OrderEvents.OrderConfirmed;
import dev.jagadeesh.oms.common.events.OrderEvents.OrderCreated;
import dev.jagadeesh.oms.common.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private final StockService stock;
    private final ObjectMapper json;

    public OrderEventsListener(StockService stock, ObjectMapper json) {
        this.stock = stock;
        this.json = json;
    }

    @KafkaListener(topics = Topics.ORDERS)
    public void on(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String id = EventHeaders.eventId(record);
        switch (EventHeaders.eventType(record)) {
            case "OrderCreated" -> stock.onOrderCreated(id, json.readValue(record.value(), OrderCreated.class));
            case "OrderCancelled" -> stock.onOrderCancelled(id, json.readValue(record.value(), OrderCancelled.class));
            case "OrderConfirmed" -> stock.onOrderConfirmed(id, json.readValue(record.value(), OrderConfirmed.class));
            default -> { } // events this service does not care about
        }
    }
}
