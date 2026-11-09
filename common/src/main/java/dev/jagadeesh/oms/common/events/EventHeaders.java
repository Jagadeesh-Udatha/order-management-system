package dev.jagadeesh.oms.common.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

public final class EventHeaders {
    public static final String EVENT_ID = "event-id";
    public static final String EVENT_TYPE = "event-type";

    private EventHeaders() {
    }

    public static String eventId(ConsumerRecord<?, ?> record) {
        return header(record, EVENT_ID);
    }

    public static String eventType(ConsumerRecord<?, ?> record) {
        return header(record, EVENT_TYPE);
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header h = record.headers().lastHeader(name);
        if (h == null) {
            throw new IllegalArgumentException("Missing header " + name + " on " + record.topic() + "@" + record.offset());
        }
        return new String(h.value(), StandardCharsets.UTF_8);
    }
}
