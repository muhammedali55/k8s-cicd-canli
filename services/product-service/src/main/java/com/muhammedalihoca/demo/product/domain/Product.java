package com.muhammedalihoca.demo.product.domain;

import com.muhammedalihoca.demo.product.service.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

// "products" tablosundaki bir satırı temsil eden JPA entity'si.
// Controller'a sızmaz; dışarıya ProductResponse DTO'su olarak çıkar.
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    // JPA, nesneyi oluşturabilmek için argümansız bir constructor ister.
    protected Product() {
    }

    public Product(String name, BigDecimal price, Integer stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    /**
     * Stoğu verilen miktar kadar düşürür. Stok yetmezse hiçbir şey değiştirmeden hata fırlatır.
     */
    public void decreaseStock(int quantity) {
        if (quantity > stock) {
            throw new InsufficientStockException(id, quantity, stock);
        }
        this.stock = stock - quantity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }
}
