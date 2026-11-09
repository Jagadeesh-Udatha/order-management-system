package dev.jagadeesh.oms.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByCustomerIdAndIdempotencyKey(String customerId, String idempotencyKey);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable page);

    List<Order> findTop100ByStatusInAndUpdatedAtBefore(List<OrderStatus> statuses, Instant before);
}
