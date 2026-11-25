package com.muhammedalihoca.demo.order.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// Sipariş tablosunun JPA karşılığı. Yalnızca service/repository katmanında kullanılır, controller'a sızmaz.
// "order" SQL'de ayrılmış kelime (ORDER BY) olduğu için tablo adı "orders".
@Entity
@Table(name = "orders")
public class Order {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false)
	private Integer quantity;

	@Column(name = "total_price", nullable = false, precision = 12, scale = 2)
	private BigDecimal totalPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private OrderStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	// JPA için boş constructor zorunlu; dışarıdan kullanılmasın diye protected.
	protected Order() {
	}

	public Order(Long productId, Integer quantity, BigDecimal totalPrice, OrderStatus status) {
		this.productId = productId;
		this.quantity = quantity;
		this.totalPrice = totalPrice;
		this.status = status;
	}

	// Kayıt veritabanına ilk kez yazılmadan hemen önce oluşturulma zamanını doldurur.
	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getProductId() {
		return productId;
	}

	public Integer getQuantity() {
		return quantity;
	}

	public BigDecimal getTotalPrice() {
		return totalPrice;
	}

	public OrderStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
