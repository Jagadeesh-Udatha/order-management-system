package dev.jagadeesh.oms.payment;

import dev.jagadeesh.oms.common.events.OrderEvents;
import dev.jagadeesh.oms.common.outbox.OutboxEvent;
import dev.jagadeesh.oms.common.outbox.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "oms.payments.decline-above=50000")
@ActiveProfiles("test")
class PaymentServiceTest {

    @Autowired PaymentService service;
    @Autowired PaymentRepository payments;
    @Autowired OutboxRepository outbox;

    @Test
    void chargesAndPublishesPaymentCompleted() {
        UUID order = UUID.randomUUID();
        service.onStockReserved("e-" + order, new OrderEvents.StockReserved(order, "c1", new BigDecimal("1200.00")));

        assertThat(payments.findByOrderId(order).orElseThrow().getStatus()).isEqualTo(Payment.Status.CAPTURED);
        assertThat(types(order)).containsExactly("PaymentCompleted");
    }

    @Test
    void declineIsRecordedAndPublished() {
        UUID order = UUID.randomUUID();
        service.onStockReserved("e-" + order, new OrderEvents.StockReserved(order, "c1", new BigDecimal("75000")));

        Payment p = payments.findByOrderId(order).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(Payment.Status.DECLINED);
        assertThat(types(order)).containsExactly("PaymentFailed");
    }

    @Test
    void sameOrderIsChargedOnceEvenWithDifferentEventIds() {
        UUID order = UUID.randomUUID();
        var e = new OrderEvents.StockReserved(order, "c1", new BigDecimal("500"));
        service.onStockReserved("first-" + order, e);
        service.onStockReserved("second-" + order, e);

        assertThat(payments.findAll().stream().filter(p -> p.getOrderId().equals(order))).hasSize(1);
        assertThat(types(order)).containsExactly("PaymentCompleted");
    }

    @Test
    void refundRequestRefundsACapturedPaymentOnce() {
        UUID order = UUID.randomUUID();
        service.onStockReserved("e-" + order, new OrderEvents.StockReserved(order, "c1", new BigDecimal("999")));
        Payment p = payments.findByOrderId(order).orElseThrow();
        var refund = new OrderEvents.RefundRequested(order, p.getId(), p.getAmount(), "Order was cancelled");

        service.onRefundRequested("r-" + order, refund);
        service.onRefundRequested("r-" + order, refund);

        assertThat(payments.findByOrderId(order).orElseThrow().getStatus()).isEqualTo(Payment.Status.REFUNDED);
        assertThat(types(order)).containsExactly("PaymentCompleted", "PaymentRefunded");
    }

    private List<String> types(UUID order) {
        return outbox.findByAggregateIdOrderByCreatedAtAsc(order.toString()).stream().map(OutboxEvent::getEventType).toList();
    }
}
