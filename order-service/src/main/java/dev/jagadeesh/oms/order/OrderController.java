package dev.jagadeesh.oms.order;

import dev.jagadeesh.oms.common.web.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;
    private final OrderRepository repo;

    public OrderController(OrderService orders, OrderRepository repo) {
        this.orders = orders;
        this.repo = repo;
    }

    public record LineInput(@NotNull UUID productId, @Min(1) @Max(100) int quantity) {
    }

    public record PlaceOrderRequest(@NotEmpty @Size(max = 50) List<@Valid LineInput> lines) {
    }

    public record OrderView(UUID id, String status, String statusReason, BigDecimal total, List<Order.Line> lines, Instant createdAt) {
        static OrderView of(Order o) {
            return new OrderView(o.getId(), o.getStatus().name(), o.getStatusReason(), o.getTotal(), o.getLines(), o.getCreatedAt());
        }
    }

    /** 202: the order is accepted, not finished. Poll GET /api/orders/{id} until CONFIRMED or CANCELLED. */
    @PostMapping
    public ResponseEntity<OrderView> place(@AuthenticationPrincipal Jwt user, @RequestHeader("Idempotency-Key") @Size(min = 8, max = 64) String key,
                                           @Valid @RequestBody PlaceOrderRequest r) {
        var placed = orders.place(user.getSubject(), key,
                r.lines().stream().map(l -> new OrderService.LineRequest(l.productId(), l.quantity())).toList());
        return ResponseEntity.status(placed.replay() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .header("Idempotent-Replay", String.valueOf(placed.replay()))
                .body(OrderView.of(placed.order()));
    }

    @GetMapping("/{id}")
    public OrderView get(@AuthenticationPrincipal Jwt user, @PathVariable UUID id) {
        Order o = orders.get(id);
        boolean admin = user.getClaimAsStringList("roles") != null && user.getClaimAsStringList("roles").contains("ADMIN");
        if (!admin && !o.getCustomerId().equals(user.getSubject())) {
            throw new NotFoundException("Order " + id + " not found"); // 404, not 403: don't reveal other people's order ids
        }
        return OrderView.of(o);
    }

    @GetMapping
    public Page<OrderView> mine(@AuthenticationPrincipal Jwt user, @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return repo.findByCustomerIdOrderByCreatedAtDesc(user.getSubject(), PageRequest.of(page, Math.min(size, 100))).map(OrderView::of);
    }
}
