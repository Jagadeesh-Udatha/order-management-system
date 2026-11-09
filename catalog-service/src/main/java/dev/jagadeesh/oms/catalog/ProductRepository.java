package dev.jagadeesh.oms.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySku(String sku);

    @Query("""
            select p from Product p
            where p.active = true
              and (:category is null or p.category = :category)
              and (:q is null or lower(p.name) like lower(concat('%', :q, '%')) or lower(p.sku) = lower(:q))
            """)
    Page<Product> search(@Param("q") String q, @Param("category") String category, Pageable page);

    /** Moves qty from available to reserved only if enough is available. Returns 0 when it is not. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Product p set p.availableQty = p.availableQty - :qty, p.reservedQty = p.reservedQty + :qty,
                                 p.version = p.version + 1
            where p.id = :id and p.active = true and p.availableQty >= :qty
            """)
    int reserve(@Param("id") UUID id, @Param("qty") int qty);

    /** Order cancelled: the held units go back on sale. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update Product p set p.availableQty = p.availableQty + :qty, p.reservedQty = p.reservedQty - :qty,
                                 p.version = p.version + 1
            where p.id = :id and p.reservedQty >= :qty
            """)
    int release(@Param("id") UUID id, @Param("qty") int qty);

    /** Order confirmed: the held units are sold. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.reservedQty = p.reservedQty - :qty, p.version = p.version + 1 where p.id = :id and p.reservedQty >= :qty")
    int commit(@Param("id") UUID id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.availableQty = p.availableQty + :qty, p.version = p.version + 1 where p.id = :id")
    int restock(@Param("id") UUID id, @Param("qty") int qty);
}
