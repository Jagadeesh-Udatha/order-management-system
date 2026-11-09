package dev.jagadeesh.oms.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs instead of sending. A real one would look up the address from auth-service (or carry it on the event)
 * and call SES/SMTP; swapping this class is the only change needed.
 */
@Component
public class EmailSender {
    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    public void send(String customerId, String subject, String body) {
        log.info("EMAIL to customer {}: {} - {}", customerId, subject, body);
    }
}
