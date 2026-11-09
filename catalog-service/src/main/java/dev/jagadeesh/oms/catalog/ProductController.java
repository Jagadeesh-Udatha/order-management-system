package dev.jagadeesh.oms.catalog;

import dev.jagadeesh.oms.common.web.BusinessRuleException;
import dev.jagadeesh.oms.common.web.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    public record ProductRequest(@NotBlank String sku, @NotBlank String name, @NotBlank String category,
                                 @NotNull @DecimalMin("0.01") BigDecimal price, @Min(0) int stock) {
    }

    public record UpdateRequest(@NotBlank String name, @NotBlank String category, @NotNull @DecimalMin("0.01") BigDecimal price,
                                boolean active) {
    }

    public record RestockRequest(@Min(1) int quantity) {
    }

    public record ProductView(UUID id, String sku, String name, String category, BigDecimal price, int available, boolean active) {
        static ProductView of(Product p) {
            return new ProductView(p.getId(), p.getSku(), p.getName(), p.getCategory(), p.getPrice(), p.getAvailableQty(), p.isActive());
        }
    }

    @GetMapping
    public Page<ProductView> search(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by("name"));
        return products.search(blankToNull(q), blankToNull(category), pageable).map(ProductView::of);
    }

    @GetMapping("/{id}")
    public ProductView get(@PathVariable UUID id) {
        return ProductView.of(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ProductView create(@Valid @RequestBody ProductRequest r) {
        if (products.existsBySku(r.sku())) {
            throw new BusinessRuleException("SKU_TAKEN", "SKU " + r.sku() + " already exists");
        }
        return ProductView.of(products.save(new Product(r.sku(), r.name(), r.category(), r.price(), r.stock())));
    }

    @PutMapping("/{id}")
    @Transactional
    public ProductView update(@PathVariable UUID id, @Valid @RequestBody UpdateRequest r) {
        Product p = find(id);
        p.update(r.name(), r.category(), r.price(), r.active());
        return ProductView.of(p);
    }

    @PostMapping("/{id}/restock")
    @Transactional
    public ProductView restock(@PathVariable UUID id, @Valid @RequestBody RestockRequest r) {
        if (products.restock(id, r.quantity()) == 0) {
            throw new NotFoundException("Product " + id + " not found");
        }
        return ProductView.of(find(id));
    }

    private Product find(UUID id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
