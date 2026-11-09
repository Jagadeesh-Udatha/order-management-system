package dev.jagadeesh.oms.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    public record PaymentView(UUID id, UUID orderId, BigDecimal amount, String status, String reason) {
    }

    @GetMapping("/order/{orderId}")
    public PaymentView forOrder(@PathVariable UUID orderId) {
        Payment p = payments.forOrder(orderId);
        return new PaymentView(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus().name(), p.getReason());
    }
}
