package dev.jagadeesh.oms.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** What was held for one order, so a later cancel or confirm knows exactly what to undo or sell. */
@Entity
@Table(name = "reservation")
public class Reservation {

    public enum Status { RESERVED, RELEASED, COMMITTED }

    @Embeddable
    public record Item(@Column(name = "product_id") UUID productId, @Column(nullable = false) int quantity) {
    }

    @Id
    @Column(name = "order_id")
    private UUID orderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_item", joinColumns = @JoinColumn(name = "order_id"))
    private List<Item> items = new ArrayList<>();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    public Reservation(UUID orderId, List<Item> items) {
        this.orderId = orderId;
        this.items = new ArrayList<>(items);
        this.status = Status.RESERVED;
        this.updatedAt = Instant.now();
    }

    public void moveTo(Status next) {
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public UUID getOrderId() { return orderId; }
    public Status getStatus() { return status; }
    public List<Item> getItems() { return items; }
}
