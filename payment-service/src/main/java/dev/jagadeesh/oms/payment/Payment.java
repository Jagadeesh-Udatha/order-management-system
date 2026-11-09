package dev.jagadeesh.oms.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row per order (unique order_id), which is what makes "charge once" hold even if an event is delivered twice. */
@Entity
@Table(name = "payment")
public class Payment {

    public enum Status { CAPTURED, DECLINED, REFUNDED }

    @Id
    private UUID id;
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(name = "gateway_ref")
    private String gatewayRef;
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    public Payment(UUID orderId, String customerId, BigDecimal amount, Status status, String gatewayRef, String reason) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
        this.gatewayRef = gatewayRef;
        this.reason = reason;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void refunded(String why) {
        this.status = Status.REFUNDED;
        this.reason = why;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getGatewayRef() { return gatewayRef; }
    public String getReason() { return reason; }
}
