package dev.jagadeesh.oms.common.events;

/** One topic per owning service. Messages are keyed by order id, so every event of an order stays in order. */
public final class Topics {
    public static final String ORDERS = "orders.events";
    public static final String INVENTORY = "inventory.events";
    public static final String PAYMENTS = "payments.events";

    private Topics() {
    }
}
