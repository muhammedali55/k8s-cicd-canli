package com.muhammedalihoca.demo.product.repository;

import com.muhammedalihoca.demo.product.domain.Product;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Product tablosuna erişim. Temel CRUD metotları JpaRepository'den gelir.
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Ürünü satır kilidiyle (SELECT ... FOR UPDATE) okur.
     * Aynı anda gelen iki sipariş aynı stoğu iki kez düşüremesin diye kullanılır.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
