package dev.jagadeesh.oms.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** Stand-in for a real provider: approves everything up to a limit, declines above it. Deterministic for tests and demos. */
@Component
public class SimulatedGateway implements PaymentGateway {

    private final BigDecimal limit;

    public SimulatedGateway(@Value("${oms.payments.decline-above:100000}") BigDecimal limit) {
        this.limit = limit;
    }

    @Override
    public Result charge(UUID idempotencyKey, String customerId, BigDecimal amount) {
        if (amount.compareTo(limit) > 0) {
            return new Result(false, null, "Amount above the card limit");
        }
        return new Result(true, "sim_" + idempotencyKey, null);
    }

    @Override
    public void refund(String reference, BigDecimal amount) {
        // nothing to call in the simulator
    }
}
