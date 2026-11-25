package com.muhammedalihoca.demo.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.muhammedalihoca.demo.order.domain.Order;

// Siparişleri veritabanına yazan/okuyan Spring Data JPA repository'si; metotları Spring üretir.
public interface OrderRepository extends JpaRepository<Order, Long> {
}
