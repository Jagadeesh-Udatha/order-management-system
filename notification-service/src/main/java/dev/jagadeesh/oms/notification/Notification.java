package dev.jagadeesh.oms.notification;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** _id is the Kafka event id: inserting the same event twice fails on the key, so a customer is never told twice. */
@Document("notifications")
@CompoundIndex(name = "customer_sent", def = "{'customerId': 1, 'sentAt': -1}")
public record Notification(@Id String eventId, String customerId, String orderId, String type, String channel,
                           String subject, String body, Instant sentAt) {
}
