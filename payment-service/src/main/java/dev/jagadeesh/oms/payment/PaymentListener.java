package dev.jagadeesh.oms.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jagadeesh.oms.common.events.EventHeaders;
import dev.jagadeesh.oms.common.events.OrderEvents;
import dev.jagadeesh.oms.common.events.Topics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentListener {

    private final PaymentService payments;
    private final ObjectMapper json;

    public PaymentListener(PaymentService payments, ObjectMapper json) {
        this.payments = payments;
        this.json = json;
    }

    @KafkaListener(topics = {Topics.INVENTORY, Topics.ORDERS})
    public void on(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String id = EventHeaders.eventId(record);
        switch (EventHeaders.eventType(record)) {
            case "StockReserved" -> payments.onStockReserved(id, json.readValue(record.value(), OrderEvents.StockReserved.class));
            case "RefundRequested" -> payments.onRefundRequested(id, json.readValue(record.value(), OrderEvents.RefundRequested.class));
            default -> { }
        }
    }
}
