package dev.jagadeesh.oms.catalog;

import dev.jagadeesh.oms.common.events.OrderEvents.Line;
import dev.jagadeesh.oms.common.events.OrderEvents.OrderCancelled;
import dev.jagadeesh.oms.common.events.OrderEvents.OrderConfirmed;
import dev.jagadeesh.oms.common.events.OrderEvents.OrderCreated;
import dev.jagadeesh.oms.common.outbox.OutboxEvent;
import dev.jagadeesh.oms.common.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class StockServiceTest {

    @Autowired StockService stock;
    @Autowired ProductRepository products;
    @Autowired ReservationRepository reservations;
    @Autowired OutboxRepository outbox;

    Product phone;
    Product charger;

    @BeforeEach
    void setUp() {
        phone = products.save(new Product("PH-" + UUID.randomUUID(), "Phone", "electronics", new BigDecimal("15000"), 5));
        charger = products.save(new Product("CH-" + UUID.randomUUID(), "Charger", "electronics", new BigDecimal("900"), 2));
    }

    @Test
    void reservesEveryLineAndPublishesStockReserved() {
        UUID orderId = UUID.randomUUID();
        stock.onOrderCreated("e1-" + orderId, created(orderId, new Line(phone.getId(), 2, phone.getPrice()), new Line(charger.getId(), 1, charger.getPrice())));

        assertThat(products.findById(phone.getId()).orElseThrow().getAvailableQty()).isEqualTo(3);
        assertThat(products.findById(phone.getId()).orElseThrow().getReservedQty()).isEqualTo(2);
        assertThat(types(orderId)).containsExactly("StockReserved");
    }

    @Test
    void oneShortLineRejectsTheWholeOrderAndReservesNothing() {
        UUID orderId = UUID.randomUUID();
        stock.onOrderCreated("e2-" + orderId, created(orderId, new Line(phone.getId(), 1, phone.getPrice()), new Line(charger.getId(), 3, charger.getPrice())));

        assertThat(products.findById(phone.getId()).orElseThrow().getAvailableQty()).isEqualTo(5);
        assertThat(products.findById(charger.getId()).orElseThrow().getAvailableQty()).isEqualTo(2);
        assertThat(reservations.findById(orderId)).isEmpty();
        assertThat(types(orderId)).containsExactly("StockRejected");
    }

    @Test
    void redeliveredEventIsIgnored() {
        UUID orderId = UUID.randomUUID();
        OrderCreated e = created(orderId, new Line(phone.getId(), 1, phone.getPrice()));
        stock.onOrderCreated("same-" + orderId, e);
        stock.onOrderCreated("same-" + orderId, e);

        assertThat(products.findById(phone.getId()).orElseThrow().getAvailableQty()).isEqualTo(4);
        assertThat(types(orderId)).containsExactly("StockReserved");
    }

    @Test
    void cancelReleasesAndConfirmSells() {
        UUID cancelled = UUID.randomUUID();
        UUID confirmed = UUID.randomUUID();
        stock.onOrderCreated("c1-" + cancelled, created(cancelled, new Line(phone.getId(), 2, phone.getPrice())));
        stock.onOrderCreated("c2-" + confirmed, created(confirmed, new Line(phone.getId(), 1, phone.getPrice())));

        stock.onOrderCancelled("c3-" + cancelled, new OrderCancelled(cancelled, "cust", "payment declined", true));
        stock.onOrderConfirmed("c4-" + confirmed, new OrderConfirmed(confirmed, "cust", phone.getPrice()));

        Product p = products.findById(phone.getId()).orElseThrow();
        assertThat(p.getAvailableQty()).isEqualTo(4);   // 5 - 2 - 1 + 2 released
        assertThat(p.getReservedQty()).isZero();         // 1 sold, 2 released
        assertThat(reservations.findById(cancelled).orElseThrow().getStatus()).isEqualTo(Reservation.Status.RELEASED);
        assertThat(reservations.findById(confirmed).orElseThrow().getStatus()).isEqualTo(Reservation.Status.COMMITTED);
    }

    @Test
    void twentyBuyersForFiveUnitsNeverOversell() throws Exception {
        int buyers = 20;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<UUID> orders = new ArrayList<>();
        List<Future<?>> done = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            UUID orderId = UUID.randomUUID();
            orders.add(orderId);
            done.add(pool.submit(() -> {
                start.await();
                stock.onOrderCreated("race-" + orderId, created(orderId, new Line(phone.getId(), 1, phone.getPrice())));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : done) {
            f.get();
        }
        pool.shutdown();

        long reserved = orders.stream().filter(o -> types(o).contains("StockReserved")).count();
        long rejected = orders.stream().filter(o -> types(o).contains("StockRejected")).count();
        assertThat(reserved).isEqualTo(5);
        assertThat(rejected).isEqualTo(15);
        assertThat(products.findById(phone.getId()).orElseThrow().getAvailableQty()).isZero();
    }

    @Test
    void sameProductOnTwoLinesIsMerged() {
        assertThat(StockService.merge(List.of(new Line(phone.getId(), 1, BigDecimal.ONE), new Line(phone.getId(), 2, BigDecimal.ONE))))
                .containsExactly(new Reservation.Item(phone.getId(), 3));
    }

    private OrderCreated created(UUID orderId, Line... lines) {
        BigDecimal total = List.of(lines).stream().map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderCreated(orderId, "cust-1", List.of(lines), total);
    }

    private List<String> types(UUID orderId) {
        return outbox.findByAggregateIdOrderByCreatedAtAsc(orderId.toString()).stream().map(OutboxEvent::getEventType).toList();
    }
}
