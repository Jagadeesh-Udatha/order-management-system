package dev.jagadeesh.oms.common.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Event contracts of the order saga. The record's simple name travels in the "event-type" header and the record
 * itself as JSON. New fields must be optional so older consumers keep working.
 */
public final class OrderEvents {

    private OrderEvents() {
    }

    public record Line(UUID productId, int quantity, BigDecimal unitPrice) {
    }

    /** order-service: a customer placed an order; inventory should reserve stock. */
    public record OrderCreated(UUID orderId, String customerId, List<Line> lines, BigDecimal total) {
    }

    /** catalog-service: every line was reserved; payment can charge the customer. */
    public record StockReserved(UUID orderId, String customerId, BigDecimal total) {
    }

    /** catalog-service: at least one line had too little stock; nothing was reserved. */
    public record StockRejected(UUID orderId, String reason) {
    }

    /** payment-service */
    public record PaymentCompleted(UUID orderId, UUID paymentId, BigDecimal amount) {
    }

    /** payment-service */
    public record PaymentFailed(UUID orderId, String reason) {
    }

    /** order-service: final happy state. Inventory turns the reservation into a sale. */
    public record OrderConfirmed(UUID orderId, String customerId, BigDecimal total) {
    }

    /** order-service: final failed state. stockReserved tells inventory whether it has something to release. */
    public record OrderCancelled(UUID orderId, String customerId, String reason, boolean stockReserved) {
    }

    /** order-service: payment arrived for an order that had already been cancelled (e.g. timed out); give it back. */
    public record RefundRequested(UUID orderId, UUID paymentId, BigDecimal amount, String reason) {
    }

    /** payment-service */
    public record PaymentRefunded(UUID orderId, UUID paymentId, BigDecimal amount) {
    }
}
