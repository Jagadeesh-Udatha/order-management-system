package dev.jagadeesh.oms.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jagadeesh.oms.common.events.EventHeaders;
import dev.jagadeesh.oms.common.events.OrderEvents;
import dev.jagadeesh.oms.common.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class SagaListener {

    private final OrderService orders;
    private final ObjectMapper json;

    public SagaListener(OrderService orders, ObjectMapper json) {
        this.orders = orders;
        this.json = json;
    }

    @KafkaListener(topics = {Topics.INVENTORY, Topics.PAYMENTS})
    public void on(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String id = EventHeaders.eventId(record);
        String body = record.value();
        switch (EventHeaders.eventType(record)) {
            case "StockReserved" -> orders.onStockReserved(id, json.readValue(body, OrderEvents.StockReserved.class));
            case "StockRejected" -> orders.onStockRejected(id, json.readValue(body, OrderEvents.StockRejected.class));
            case "PaymentCompleted" -> orders.onPaymentCompleted(id, json.readValue(body, OrderEvents.PaymentCompleted.class));
            case "PaymentFailed" -> orders.onPaymentFailed(id, json.readValue(body, OrderEvents.PaymentFailed.class));
            default -> { }
        }
    }
}
