package com.muhammedalihoca.demo.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.muhammedalihoca.demo.order.client.InsufficientStockException;
import com.muhammedalihoca.demo.order.client.ProductClient;
import com.muhammedalihoca.demo.order.client.ProductNotFoundException;
import com.muhammedalihoca.demo.order.client.ProductResponse;
import com.muhammedalihoca.demo.order.client.ProductServiceUnavailableException;
import com.muhammedalihoca.demo.order.domain.OrderStatus;
import com.muhammedalihoca.demo.order.dto.CreateOrderRequest;
import com.muhammedalihoca.demo.order.dto.OrderResponse;
import com.muhammedalihoca.demo.order.repository.OrderRepository;

// OrderService'in sipariş akışını test eder. ProductClient sahte (mock), veritabanı gerçek (H2 + Flyway).
@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderRepository orderRepository;

	@MockitoBean
	private ProductClient productClient;

	@BeforeEach
	void cleanDatabase() {
		orderRepository.deleteAll();
	}

	@Test
	@DisplayName("Stok yeterliyse sipariş CREATED olarak kaydedilir ve toplam fiyat = birim fiyat × adet")
	void placeOrder_success() {
		// given
		given(productClient.getProduct(1L)).willReturn(new ProductResponse(1L, "Klavye", new BigDecimal("750.00"), 10));
		given(productClient.decreaseStock(1L, 2)).willReturn(new ProductResponse(1L, "Klavye", new BigDecimal("750.00"), 8));

		// when
		OrderResponse order = orderService.placeOrder(new CreateOrderRequest(1L, 2));

		// then
		assertThat(order.id()).isNotNull();
		assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
		assertThat(order.totalPrice()).isEqualByComparingTo("1500.00");
		assertThat(order.createdAt()).isNotNull();
		assertThat(orderRepository.findById(order.id())).get()
				.extracting(saved -> saved.getStatus())
				.isEqualTo(OrderStatus.CREATED);
		verify(productClient).decreaseStock(1L, 2);
	}

	@Test
	@DisplayName("Stok yetersizse sipariş REJECTED olarak kaydedilir ve OrderRejectedException fırlatılır")
	void placeOrder_insufficientStock() {
		// given
		given(productClient.getProduct(1L)).willReturn(new ProductResponse(1L, "Klavye", new BigDecimal("750.00"), 10));
		given(productClient.decreaseStock(1L, 20)).willThrow(new InsufficientStockException(1L, 20));

		// when / then
		assertThatThrownBy(() -> orderService.placeOrder(new CreateOrderRequest(1L, 20)))
				.isInstanceOf(OrderRejectedException.class)
				.satisfies(ex -> {
					OrderResponse rejected = ((OrderRejectedException) ex).getOrder();
					assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
					assertThat(rejected.id()).isNotNull();
				});

		// then: REJECTED kayıt veritabanında kalmış olmalı (transaction geri alınmamalı)
		assertThat(orderRepository.findAll())
				.singleElement()
				.extracting(saved -> saved.getStatus())
				.isEqualTo(OrderStatus.REJECTED);
	}

	@Test
	@DisplayName("product-service kapalıysa ProductServiceUnavailableException fırlatılır ve sipariş kaydedilmez")
	void placeOrder_productServiceDown() {
		// given
		given(productClient.getProduct(1L)).willThrow(
				new ProductServiceUnavailableException("product-service'e şu anda ulaşılamıyor", null));

		// when / then
		assertThatThrownBy(() -> orderService.placeOrder(new CreateOrderRequest(1L, 2)))
				.isInstanceOf(ProductServiceUnavailableException.class);

		// then
		assertThat(orderRepository.count()).isZero();
		verify(productClient, never()).decreaseStock(anyLong(), anyInt());
	}

	@Test
	@DisplayName("Ürün yoksa ProductNotFoundException fırlatılır ve sipariş kaydedilmez")
	void placeOrder_productNotFound() {
		// given
		given(productClient.getProduct(99L)).willThrow(new ProductNotFoundException(99L));

		// when / then
		assertThatThrownBy(() -> orderService.placeOrder(new CreateOrderRequest(99L, 1)))
				.isInstanceOf(ProductNotFoundException.class);

		// then
		assertThat(orderRepository.count()).isZero();
	}

	@Test
	@DisplayName("Olmayan sipariş istenirse OrderNotFoundException fırlatılır")
	void findById_notFound() {
		// given: boş veritabanı

		// when / then
		assertThatThrownBy(() -> orderService.findById(12345L))
				.isInstanceOf(OrderNotFoundException.class)
				.hasMessageContaining("12345");
	}
}
