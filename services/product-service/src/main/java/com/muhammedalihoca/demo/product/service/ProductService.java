package com.muhammedalihoca.demo.product.service;

import com.muhammedalihoca.demo.product.domain.Product;
import com.muhammedalihoca.demo.product.dto.CreateProductRequest;
import com.muhammedalihoca.demo.product.dto.ProductResponse;
import com.muhammedalihoca.demo.product.repository.ProductRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Ürün iş kuralları: listeleme, oluşturma ve stok düşürme.
// Entity'leri DTO'ya çevirerek controller'a yalnızca DTO verir.
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        Product product = new Product(request.name(), request.price(), request.stock());
        return ProductResponse.from(productRepository.save(product));
    }

    /**
     * Stoğu düşürür. Stok yetmezse InsufficientStockException fırlar ve hiçbir şey değişmez.
     * Değişiklik, transaction sonunda JPA tarafından otomatik kaydedilir (dirty checking).
     */
    @Transactional
    public ProductResponse decreaseStock(Long id, int quantity) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        product.decreaseStock(quantity);
        return ProductResponse.from(product);
    }
}
