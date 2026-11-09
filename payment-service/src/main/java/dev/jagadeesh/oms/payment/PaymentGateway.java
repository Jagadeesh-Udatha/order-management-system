package dev.jagadeesh.oms.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The card/UPI provider. The order id is sent as the provider's idempotency key, so if this service crashes after
 * charging but before committing, the retried charge returns the first result instead of charging twice.
 */
public interface PaymentGateway {

    record Result(boolean approved, String reference, String declineReason) {
    }

    Result charge(UUID idempotencyKey, String customerId, BigDecimal amount);

    void refund(String reference, BigDecimal amount);
}
