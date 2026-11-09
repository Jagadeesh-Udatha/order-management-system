package dev.jagadeesh.oms.order;

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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "customer_order")
public class Order {

    @Embeddable
    public record Line(@Column(name = "product_id") UUID productId, @Column(name = "product_name") String productName,
                       int quantity, @Column(name = "unit_price") BigDecimal unitPrice) {
        BigDecimal amount() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    @Id
    private UUID id;
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    @Column(name = "request_hash", nullable = false)
    private String requestHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    @Column(name = "status_reason")
    private String statusReason;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_line", joinColumns = @JoinColumn(name = "order_id"))
    @OrderColumn(name = "line_no")
    private List<Line> lines = new ArrayList<>();
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Order() {
    }

    public Order(String customerId, String idempotencyKey, String requestHash, List<Line> lines) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.lines = new ArrayList<>(lines);
        this.total = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Moves to {@code next} only from one of {@code allowed}. Returns false (and changes nothing) for a stale or
     * duplicate event, e.g. a StockReserved that arrives after payment already confirmed the order.
     */
    boolean moveTo(OrderStatus next, String reason, OrderStatus... allowed) {
        for (OrderStatus from : allowed) {
            if (status == from) {
                status = next;
                statusReason = reason;
                updatedAt = Instant.now();
                return true;
            }
        }
        return false;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public OrderStatus getStatus() { return status; }
    public String getStatusReason() { return statusReason; }
    public BigDecimal getTotal() { return total; }
    public List<Line> getLines() { return lines; }
    public Instant getCreatedAt() { return createdAt; }
}
