package dev.jagadeesh.oms.catalog;

import java.util.UUID;

class InsufficientStockException extends RuntimeException {
    InsufficientStockException(UUID productId, int quantity) {
        super("Not enough stock for product " + productId + " (wanted " + quantity + ")");
    }
}
