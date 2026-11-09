package dev.jagadeesh.oms.notification;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final EmailSender email;

    public NotificationService(NotificationRepository notifications, EmailSender email) {
        this.notifications = notifications;
        this.email = email;
    }

    /** Returns false for an event already handled. The record is saved first, then sent, so a crash re-sends at most once. */
    public boolean handle(String eventId, String type, JsonNode e) {
        String orderId = e.path("orderId").asText();
        String customerId = e.path("customerId").asText(null);
        String subject;
        String body;
        switch (type) {
            case "OrderConfirmed" -> {
                subject = "Order confirmed";
                body = "Your order " + orderId + " for Rs " + e.path("total").asText() + " is confirmed.";
            }
            case "OrderCancelled" -> {
                subject = "Order cancelled";
                body = "Your order " + orderId + " was cancelled: " + e.path("reason").asText() + ".";
            }
            default -> {
                return false;
            }
        }
        if (customerId == null) {
            return false;
        }
        try {
            notifications.insert(new Notification(eventId, customerId, orderId, type, "EMAIL", subject, body, Instant.now()));
        } catch (DuplicateKeyException alreadySent) {
            return false;
        }
        email.send(customerId, subject, body);
        return true;
    }
}
