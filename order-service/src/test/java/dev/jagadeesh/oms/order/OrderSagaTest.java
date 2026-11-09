package dev.jagadeesh.oms.order;

import dev.jagadeesh.oms.common.events.OrderEvents;
import dev.jagadeesh.oms.common.outbox.OutboxEvent;
import dev.jagadeesh.oms.common.outbox.OutboxRepository;
import dev.jagadeesh.oms.common.web.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class OrderSagaTest {

    @Autowired OrderService service;
    @Autowired OrderRepository orders;
    @Autowired OutboxRepository outbox;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean CatalogClient catalog;

    final UUID laptop = UUID.randomUUID();

    @BeforeEach
    void prices() {
        when(catalog.product(any())).thenReturn(Optional.of(new CatalogClient.ProductInfo(laptop, "Laptop", new BigDecimal("52000.00"), true)));
    }

    @Test
    void placingAnOrderSavesItAndQueuesOrderCreatedInTheSameTransaction() {
        Order o = place("key-" + UUID.randomUUID(), 2).order();

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(o.getTotal()).isEqualByComparingTo("104000.00");
        assertThat(types(o)).containsExactly("OrderCreated");
    }

    @Test
    void sameKeySameBodyReturnsTheFirstOrder() {
        String key = "key-" + UUID.randomUUID();
        var first = place(key, 1);
        var again = place(key, 1);

        assertThat(again.replay()).isTrue();
        assertThat(again.order().getId()).isEqualTo(first.order().getId());
        assertThat(types(first.order())).hasSize(1);
    }

    @Test
    void sameKeyDifferentBodyIsRefused() {
        String key = "key-" + UUID.randomUUID();
        place(key, 1);
        assertThatThrownBy(() -> place(key, 3))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("different order");
    }

    @Test
    void inactiveProductIsRefused() {
        when(catalog.product(any())).thenReturn(Optional.of(new CatalogClient.ProductInfo(laptop, "Laptop", BigDecimal.TEN, false)));
        assertThatThrownBy(() -> place("key-" + UUID.randomUUID(), 1)).hasMessageContaining("not available");
    }

    @Test
    void happyPathEndsConfirmed() {
        Order o = place("key-" + UUID.randomUUID(), 1).order();
        service.onStockReserved("s-" + o.getId(), new OrderEvents.StockReserved(o.getId(), "c1", o.getTotal()));
        service.onPaymentCompleted("p-" + o.getId(), new OrderEvents.PaymentCompleted(o.getId(), UUID.randomUUID(), o.getTotal()));

        assertThat(reload(o).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(types(o)).containsExactly("OrderCreated", "OrderConfirmed");
    }

    @Test
    void noStockCancelsWithoutAskingInventoryToRelease() throws Exception {
        Order o = place("key-" + UUID.randomUUID(), 1).order();
        service.onStockRejected("r-" + o.getId(), new OrderEvents.StockRejected(o.getId(), "Not enough stock"));

        assertThat(reload(o).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(lastPayload(o)).contains("\"stockReserved\":false");
    }

    @Test
    void declinedPaymentCancelsAndReleasesStock() {
        Order o = place("key-" + UUID.randomUUID(), 1).order();
        service.onStockReserved("s-" + o.getId(), new OrderEvents.StockReserved(o.getId(), "c1", o.getTotal()));
        service.onPaymentFailed("f-" + o.getId(), new OrderEvents.PaymentFailed(o.getId(), "Card declined"));

        assertThat(reload(o).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(reload(o).getStatusReason()).isEqualTo("Card declined");
        assertThat(lastPayload(o)).contains("\"stockReserved\":true");
    }

    @Test
    void paymentReadBeforeStockReservedStillConfirmsAndTheLateEventIsIgnored() {
        Order o = place("key-" + UUID.randomUUID(), 1).order();
        service.onPaymentCompleted("p-" + o.getId(), new OrderEvents.PaymentCompleted(o.getId(), UUID.randomUUID(), o.getTotal()));
        service.onStockReserved("s-" + o.getId(), new OrderEvents.StockReserved(o.getId(), "c1", o.getTotal()));

        assertThat(reload(o).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(types(o)).containsExactly("OrderCreated", "OrderConfirmed");
    }

    @Test
    void duplicateEventChangesNothing() {
        Order o = place("key-" + UUID.randomUUID(), 1).order();
        var paid = new OrderEvents.PaymentCompleted(o.getId(), UUID.randomUUID(), o.getTotal());
        service.onPaymentCompleted("dup-" + o.getId(), paid);
        service.onPaymentCompleted("dup-" + o.getId(), paid);

        assertThat(types(o)).containsExactly("OrderCreated", "OrderConfirmed");
    }

    @Test
    void stuckOrderTimesOutAndALatePaymentIsRefunded() {
        Order o = place("key-" + UUID.randomUUID(), 1).order();
        jdbc.update("update customer_order set updated_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)), o.getId().toString());

        assertThat(service.cancelStuckOrders()).isGreaterThanOrEqualTo(1);
        assertThat(reload(o).getStatus()).isEqualTo(OrderStatus.CANCELLED);

        service.onPaymentCompleted("late-" + o.getId(), new OrderEvents.PaymentCompleted(o.getId(), UUID.randomUUID(), o.getTotal()));
        assertThat(reload(o).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(types(o)).containsExactly("OrderCreated", "OrderCancelled", "RefundRequested");
    }

    private OrderService.Placed place(String key, int qty) {
        return service.place("customer-1", key, List.of(new OrderService.LineRequest(laptop, qty)));
    }

    private Order reload(Order o) {
        return orders.findById(o.getId()).orElseThrow();
    }

    private List<String> types(Order o) {
        return outbox.findByAggregateIdOrderByCreatedAtAsc(o.getId().toString()).stream().map(OutboxEvent::getEventType).toList();
    }

    private String lastPayload(Order o) {
        List<OutboxEvent> all = outbox.findByAggregateIdOrderByCreatedAtAsc(o.getId().toString());
        return all.get(all.size() - 1).getPayload();
    }
}
