package dev.jagadeesh.oms.catalog;

import dev.jagadeesh.oms.common.events.OrderEvents.Line;
import dev.jagadeesh.oms.common.events.OrderEvents.OrderCancelled;
import dev.jagadeesh.oms.common.events.OrderEvents.OrderConfirmed;
import dev.jagadeesh.oms.common.events.OrderEvents.OrderCreated;
import dev.jagadeesh.oms.common.events.OrderEvents.StockRejected;
import dev.jagadeesh.oms.common.events.OrderEvents.StockReserved;
import dev.jagadeesh.oms.common.events.Topics;
import dev.jagadeesh.oms.common.inbox.Inbox;
import dev.jagadeesh.oms.common.outbox.OutboxWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Inventory side of the order saga. A reservation is all-or-nothing: every line is reserved in one transaction,
 * and if any line is short the whole transaction rolls back and a StockRejected is written in a fresh one.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final Inbox inbox;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;

    public StockService(ProductRepository products, ReservationRepository reservations, Inbox inbox,
                        OutboxWriter outbox, TransactionTemplate tx) {
        this.products = products;
        this.reservations = reservations;
        this.inbox = inbox;
        this.outbox = outbox;
        this.tx = tx;
    }

    public void onOrderCreated(String eventId, OrderCreated e) {
        try {
            tx.executeWithoutResult(s -> {
                if (!inbox.firstTime(eventId)) {
                    return;
                }
                List<Reservation.Item> items = merge(e.lines());
                // fixed lock order (by product id) so two orders for the same products cannot deadlock
                for (Reservation.Item item : items) {
                    if (products.reserve(item.productId(), item.quantity()) == 0) {
                        throw new InsufficientStockException(item.productId(), item.quantity());
                    }
                }
                reservations.save(new Reservation(e.orderId(), items));
                outbox.write(Topics.INVENTORY, e.orderId(), new StockReserved(e.orderId(), e.customerId(), e.total()));
            });
        } catch (InsufficientStockException ex) {
            log.info("Order {} rejected: {}", e.orderId(), ex.getMessage());
            tx.executeWithoutResult(s -> {
                if (inbox.firstTime(eventId)) {
                    outbox.write(Topics.INVENTORY, e.orderId(), new StockRejected(e.orderId(), ex.getMessage()));
                }
            });
        }
    }

    public void onOrderCancelled(String eventId, OrderCancelled e) {
        settle(eventId, e.orderId(), Reservation.Status.RELEASED);
    }

    public void onOrderConfirmed(String eventId, OrderConfirmed e) {
        settle(eventId, e.orderId(), Reservation.Status.COMMITTED);
    }

    private void settle(String eventId, UUID orderId, Reservation.Status next) {
        tx.executeWithoutResult(s -> {
            if (!inbox.firstTime(eventId)) {
                return;
            }
            reservations.findById(orderId)
                    .filter(r -> r.getStatus() == Reservation.Status.RESERVED)
                    .ifPresent(r -> {
                        for (Reservation.Item item : r.getItems()) {
                            int changed = next == Reservation.Status.RELEASED
                                    ? products.release(item.productId(), item.quantity())
                                    : products.commit(item.productId(), item.quantity());
                            if (changed == 0) {
                                throw new IllegalStateException("Reserved count out of sync for " + item.productId());
                            }
                        }
                        r.moveTo(next);
                        reservations.save(r); // the bulk updates above cleared the persistence context, so merge
                    });
        });
    }

    /** Same product on two lines becomes one item; sorted by id for the lock order. */
    static List<Reservation.Item> merge(List<Line> lines) {
        Map<UUID, Integer> qty = lines.stream().collect(Collectors.toMap(Line::productId, Line::quantity, Integer::sum));
        return qty.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(en -> new Reservation.Item(en.getKey(), en.getValue()))
                .toList();
    }
}
