package dev.jagadeesh.oms.order;

public enum OrderStatus {
    /** saved, waiting for inventory */
    PENDING,
    /** stock held, waiting for payment */
    STOCK_RESERVED,
    CONFIRMED,
    CANCELLED;

    public boolean isFinal() {
        return this == CONFIRMED || this == CANCELLED;
    }
}
