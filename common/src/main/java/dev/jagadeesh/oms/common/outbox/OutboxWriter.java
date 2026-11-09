package dev.jagadeesh.oms.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Call inside the business transaction. If that transaction rolls back, the event is never published. */
@Component
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public OutboxWriter(OutboxRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String topic, Object aggregateId, Object event) {
        try {
            outbox.save(new OutboxEvent(topic, aggregateId.toString(), event.getClass().getSimpleName(),
                    json.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + event.getClass().getSimpleName(), e);
        }
    }
}
