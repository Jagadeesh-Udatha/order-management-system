package dev.jagadeesh.oms.order;

import dev.jagadeesh.oms.common.events.EventHeaders;
import dev.jagadeesh.oms.common.events.Topics;
import dev.jagadeesh.oms.common.outbox.OutboxRelay;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Real Kafka (embedded): the relay publishes OrderCreated, and a StockReserved sent to the topic moves the order on. */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.listener.auto-startup=true",
        "oms.outbox.relay-enabled=true",
        "oms.outbox.poll-ms=600000"
})
@EmbeddedKafka(partitions = 1, topics = {Topics.ORDERS, Topics.INVENTORY, Topics.PAYMENTS})
@ActiveProfiles("test")
class KafkaRoundTripTest {

    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired OutboxRelay relay;
    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EmbeddedKafkaBroker broker;
    @MockitoBean CatalogClient catalog;

    @Test
    void orderCreatedGoesOutAndStockReservedComesBack() throws Exception {
        UUID product = UUID.randomUUID();
        when(catalog.product(any())).thenReturn(Optional.of(new CatalogClient.ProductInfo(product, "Microwave", new BigDecimal("8999"), true)));
        Order o = service.place("cust-k", "kafka-key-1", List.of(new OrderService.LineRequest(product, 1))).order();

        assertThat(relay.publishPending()).isGreaterThanOrEqualTo(1); // other test classes share the in-memory DB
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps("rt-test", "true", broker), new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, Topics.ORDERS);
            ConsumerRecord<String, String> rec = null;
            for (ConsumerRecord<String, String> r : KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10))) {
                if (r.key().equals(o.getId().toString())) {
                    rec = r;
                }
            }
            assertThat(rec).isNotNull();
            assertThat(new String(rec.headers().lastHeader(EventHeaders.EVENT_TYPE).value(), StandardCharsets.UTF_8)).isEqualTo("OrderCreated");
        }

        var reply = new ProducerRecord<>(Topics.INVENTORY, o.getId().toString(),
                "{\"orderId\":\"" + o.getId() + "\",\"customerId\":\"cust-k\",\"total\":8999}");
        reply.headers().add(EventHeaders.EVENT_ID, UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        reply.headers().add(EventHeaders.EVENT_TYPE, "StockReserved".getBytes(StandardCharsets.UTF_8));
        kafka.send(reply).get();

        long deadline = System.currentTimeMillis() + 15_000;
        while (orders.findById(o.getId()).orElseThrow().getStatus() != OrderStatus.STOCK_RESERVED && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertThat(orders.findById(o.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.STOCK_RESERVED);
    }
}
