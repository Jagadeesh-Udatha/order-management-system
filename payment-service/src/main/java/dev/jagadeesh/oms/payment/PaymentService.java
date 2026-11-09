package dev.jagadeesh.oms.payment;

import dev.jagadeesh.oms.common.events.OrderEvents;
import dev.jagadeesh.oms.common.events.Topics;
import dev.jagadeesh.oms.common.inbox.Inbox;
import dev.jagadeesh.oms.common.outbox.OutboxWriter;
import dev.jagadeesh.oms.common.web.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final Inbox inbox;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, Inbox inbox, OutboxWriter outbox, TransactionTemplate tx) {
        this.payments = payments;
        this.gateway = gateway;
        this.inbox = inbox;
        this.outbox = outbox;
        this.tx = tx;
    }

    /** Stock is held, so take the money. A second StockReserved for the same order (any event id) does nothing. */
    void onStockReserved(String eventId, OrderEvents.StockReserved e) {
        tx.executeWithoutResult(s -> {
            if (!inbox.firstTime(eventId) || payments.findByOrderId(e.orderId()).isPresent()) {
                return;
            }
            PaymentGateway.Result r = gateway.charge(e.orderId(), e.customerId(), e.total());
            if (r.approved()) {
                Payment p = payments.save(new Payment(e.orderId(), e.customerId(), e.total(), Payment.Status.CAPTURED, r.reference(), null));
                outbox.write(Topics.PAYMENTS, e.orderId(), new OrderEvents.PaymentCompleted(e.orderId(), p.getId(), p.getAmount()));
            } else {
                payments.save(new Payment(e.orderId(), e.customerId(), e.total(), Payment.Status.DECLINED, null, r.declineReason()));
                outbox.write(Topics.PAYMENTS, e.orderId(), new OrderEvents.PaymentFailed(e.orderId(), r.declineReason()));
            }
        });
    }

    void onRefundRequested(String eventId, OrderEvents.RefundRequested e) {
        tx.executeWithoutResult(s -> {
            if (!inbox.firstTime(eventId)) {
                return;
            }
            payments.findByOrderId(e.orderId())
                    .filter(p -> p.getStatus() == Payment.Status.CAPTURED)
                    .ifPresent(p -> {
                        gateway.refund(p.getGatewayRef(), p.getAmount());
                        p.refunded(e.reason());
                        outbox.write(Topics.PAYMENTS, p.getOrderId(), new OrderEvents.PaymentRefunded(p.getOrderId(), p.getId(), p.getAmount()));
                    });
        });
    }

    public Payment forOrder(UUID orderId) {
        return payments.findByOrderId(orderId).orElseThrow(() -> new NotFoundException("No payment for order " + orderId));
    }
}
