package dev.jagadeesh.oms.common.inbox;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumer-side de-duplication. Call inside the handler's transaction: the event id is stored with the business
 * change, so a redelivered event is skipped, and two concurrent deliveries collide on the primary key.
 */
@Component
public class Inbox {

    private final ProcessedEventRepository processed;

    public Inbox(ProcessedEventRepository processed) {
        this.processed = processed;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String eventId) {
        if (processed.existsById(eventId)) {
            return false;
        }
        processed.saveAndFlush(new ProcessedEvent(eventId));
        return true;
    }
}
