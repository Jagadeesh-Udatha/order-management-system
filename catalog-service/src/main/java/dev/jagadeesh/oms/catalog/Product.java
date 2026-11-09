package dev.jagadeesh.oms.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sellable item. available = what can still be ordered, reserved = held for orders waiting on payment.
 * Both counters are changed only by single conditional UPDATEs in {@link ProductRepository}, never read-modify-write.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String sku;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String category;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;
    @Column(name = "available_qty", nullable = false)
    private int availableQty;
    @Column(name = "reserved_qty", nullable = false)
    private int reservedQty;
    @Column(nullable = false)
    private boolean active = true;
    @Version
    private long version;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
    }

    public Product(String sku, String name, String category, BigDecimal price, int availableQty) {
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.price = price;
        this.availableQty = availableQty;
        this.updatedAt = Instant.now();
    }

    public void update(String name, String category, BigDecimal price, boolean active) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public int getAvailableQty() { return availableQty; }
    public int getReservedQty() { return reservedQty; }
    public boolean isActive() { return active; }
    public long getVersion() { return version; }
}
