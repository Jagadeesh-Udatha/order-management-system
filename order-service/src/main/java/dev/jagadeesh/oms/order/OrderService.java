package dev.jagadeesh.oms.order;

import dev.jagadeesh.oms.common.events.OrderEvents;
import dev.jagadeesh.oms.common.events.Topics;
import dev.jagadeesh.oms.common.inbox.Inbox;
import dev.jagadeesh.oms.common.outbox.OutboxWriter;
import dev.jagadeesh.oms.common.web.BusinessRuleException;
import dev.jagadeesh.oms.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Owns the order and the saga state (choreography: each service reacts to the previous one's event).
 *
 * <pre>
 * PENDING --StockReserved--> STOCK_RESERVED --PaymentCompleted--> CONFIRMED
 *    |                              |
 *    +--StockRejected--> CANCELLED <+--PaymentFailed
 * </pre>
 * Inventory and payment events come on different topics, so PaymentCompleted can be read before StockReserved.
 * That is why PENDING can go straight to CONFIRMED or CANCELLED, and a late StockReserved is simply ignored.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    public record LineRequest(UUID productId, int quantity) {
    }

    public record Placed(Order order, boolean replay) {
    }

    private final OrderRepository orders;
    private final CatalogClient catalog;
    private final OutboxWriter outbox;
    private final Inbox inbox;
    private final TransactionTemplate tx;
    private final OrderProperties props;

    public OrderService(OrderRepository orders, CatalogClient catalog, OutboxWriter outbox, Inbox inbox,
                        TransactionTemplate tx, OrderProperties props) {
        this.orders = orders;
        this.catalog = catalog;
        this.outbox = outbox;
        this.inbox = inbox;
        this.tx = tx;
        this.props = props;
    }

    /** Same key + same body returns the first order; same key + different body is refused. */
    public Placed place(String customerId, String idempotencyKey, List<LineRequest> request) {
        String hash = hash(request);
        var existing = orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), hash);
        }
        List<Order.Line> lines = request.stream().map(this::price).toList();
        try {
            return tx.execute(s -> {
                Order order = orders.save(new Order(customerId, idempotencyKey, hash, lines));
                outbox.write(Topics.ORDERS, order.getId(), new OrderEvents.OrderCreated(order.getId(), customerId,
                        lines.stream().map(l -> new OrderEvents.Line(l.productId(), l.quantity(), l.unitPrice())).toList(),
                        order.getTotal()));
                return new Placed(order, false);
            });
        } catch (DataIntegrityViolationException race) {
            // two requests with the same key at the same moment: the unique index let one through
            return replay(orders.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey).orElseThrow(() -> race), hash);
        }
    }

    public Order get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order " + id + " not found"));
    }

    void onStockReserved(String eventId, OrderEvents.StockReserved e) {
        handle(eventId, e.orderId(), o -> o.moveTo(OrderStatus.STOCK_RESERVED, null, OrderStatus.PENDING));
    }

    void onStockRejected(String eventId, OrderEvents.StockRejected e) {
        handle(eventId, e.orderId(), o -> {
            if (o.moveTo(OrderStatus.CANCELLED, e.reason(), OrderStatus.PENDING)) {
                outbox.write(Topics.ORDERS, o.getId(), new OrderEvents.OrderCancelled(o.getId(), o.getCustomerId(), e.reason(), false));
            }
        });
    }

    void onPaymentCompleted(String eventId, OrderEvents.PaymentCompleted e) {
        handle(eventId, e.orderId(), o -> {
            if (o.moveTo(OrderStatus.CONFIRMED, null, OrderStatus.PENDING, OrderStatus.STOCK_RESERVED)) {
                outbox.write(Topics.ORDERS, o.getId(), new OrderEvents.OrderConfirmed(o.getId(), o.getCustomerId(), o.getTotal()));
            } else if (o.getStatus() == OrderStatus.CANCELLED) {
                outbox.write(Topics.ORDERS, o.getId(), new OrderEvents.RefundRequested(o.getId(), e.paymentId(), e.amount(),
                        "Order was cancelled before payment completed"));
            }
        });
    }

    void onPaymentFailed(String eventId, OrderEvents.PaymentFailed e) {
        handle(eventId, e.orderId(), o -> {
            if (o.moveTo(OrderStatus.CANCELLED, e.reason(), OrderStatus.PENDING, OrderStatus.STOCK_RESERVED)) {
                outbox.write(Topics.ORDERS, o.getId(), new OrderEvents.OrderCancelled(o.getId(), o.getCustomerId(), e.reason(), true));
            }
        });
    }

    /** Orders stuck waiting (a service down, an event lost) are cancelled so stock is not held forever. */
    @Scheduled(fixedDelayString = "${oms.orders.timeout-check-ms:60000}")
    public int cancelStuckOrders() {
        Instant cutoff = Instant.now().minus(props.pendingTimeout());
        int cancelled = 0;
        for (Order stuck : orders.findTop100ByStatusInAndUpdatedAtBefore(List.of(OrderStatus.PENDING, OrderStatus.STOCK_RESERVED), cutoff)) {
            Boolean done = tx.execute(s -> {
                Order o = orders.findById(stuck.getId()).orElseThrow();
                if (o.moveTo(OrderStatus.CANCELLED, "Timed out", OrderStatus.PENDING, OrderStatus.STOCK_RESERVED)) {
                    // stockReserved=true is safe even if nothing was held: inventory only releases what it has
                    outbox.write(Topics.ORDERS, o.getId(), new OrderEvents.OrderCancelled(o.getId(), o.getCustomerId(), "Timed out", true));
                    return true;
                }
                return false;
            });
            if (Boolean.TRUE.equals(done)) {
                cancelled++;
            }
        }
        if (cancelled > 0) {
            log.warn("Cancelled {} orders that timed out", cancelled);
        }
        return cancelled;
    }

    private void handle(String eventId, UUID orderId, java.util.function.Consumer<Order> change) {
        tx.executeWithoutResult(s -> {
            if (!inbox.firstTime(eventId)) {
                return;
            }
            orders.findById(orderId).ifPresentOrElse(change, () -> log.warn("Event {} for unknown order {}", eventId, orderId));
        });
    }

    private Order.Line price(LineRequest r) {
        CatalogClient.ProductInfo p = catalog.product(r.productId())
                .filter(CatalogClient.ProductInfo::active)
                .orElseThrow(() -> new BusinessRuleException("PRODUCT_UNAVAILABLE", "Product " + r.productId() + " is not available"));
        return new Order.Line(p.id(), p.name(), r.quantity(), p.price());
    }

    private Placed replay(Order order, String hash) {
        if (!order.getRequestHash().equals(hash)) {
            throw new BusinessRuleException("IDEMPOTENCY_KEY_REUSED", "This Idempotency-Key was already used for a different order");
        }
        return new Placed(order, true);
    }

    static String hash(List<LineRequest> lines) {
        StringBuilder sb = new StringBuilder();
        lines.forEach(l -> sb.append(l.productId()).append('x').append(l.quantity()).append(';'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
