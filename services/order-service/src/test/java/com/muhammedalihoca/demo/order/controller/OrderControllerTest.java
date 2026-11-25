package com.muhammedalihoca.demo.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.muhammedalihoca.demo.order.client.ProductNotFoundException;
import com.muhammedalihoca.demo.order.client.ProductServiceUnavailableException;
import com.muhammedalihoca.demo.order.domain.OrderStatus;
import com.muhammedalihoca.demo.order.dto.CreateOrderRequest;
import com.muhammedalihoca.demo.order.dto.OrderResponse;
import com.muhammedalihoca.demo.order.service.OrderNotFoundException;
import com.muhammedalihoca.demo.order.service.OrderRejectedException;
import com.muhammedalihoca.demo.order.service.OrderService;

// OrderController'ın HTTP davranışını test eder: status kodları, JSON gövdeleri, ProblemDetail hataları.
// Yalnızca web katmanı ayağa kalkar; OrderService sahte (mock).
@WebMvcTest(OrderController.class)
class OrderControllerTest {

	private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private OrderService orderService;

	@Test
	@DisplayName("POST /api/orders başarılıysa 201, Location başlığı ve sipariş döner")
	void create_returns201() throws Exception {
		// given
		given(orderService.placeOrder(new CreateOrderRequest(1L, 2))).willReturn(
				new OrderResponse(7L, 1L, 2, new BigDecimal("1500.00"), OrderStatus.CREATED, NOW));

		// when / then
		mockMvc.perform(post("/api/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "productId": 1, "quantity": 2 }
								"""))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/orders/7"))
				.andExpect(jsonPath("$.id").value(7))
				.andExpect(jsonPath("$.status").value("CREATED"))
				.andExpect(jsonPath("$.totalPrice").value(1500.00));
	}

	@Test
	@DisplayName("Stok yetersizse 409 ProblemDetail ve REJECTED sipariş döner")
	void create_insufficientStock_returns409() throws Exception {
		// given
		OrderResponse rejected = new OrderResponse(8L, 1L, 20, new BigDecimal("15000.00"), OrderStatus.REJECTED, NOW);
		given(orderService.placeOrder(any())).willThrow(
				new OrderRejectedException(rejected, "Yetersiz stok: ürün 1 için istenen 20 adet karşılanamadı"));

		// when / then
		mockMvc.perform(post("/api/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "productId": 1, "quantity": 20 }
								"""))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value("Yetersiz stok: ürün 1 için istenen 20 adet karşılanamadı"))
				.andExpect(jsonPath("$.order.id").value(8))
				.andExpect(jsonPath("$.order.status").value("REJECTED"));
	}

	@Test
	@DisplayName("product-service erişilemezse 503 ProblemDetail döner")
	void create_productServiceDown_returns503() throws Exception {
		// given
		given(orderService.placeOrder(any())).willThrow(
				new ProductServiceUnavailableException("product-service'e şu anda ulaşılamıyor, lütfen biraz sonra tekrar deneyin", null));

		// when / then
		mockMvc.perform(post("/api/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "productId": 1, "quantity": 2 }
								"""))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.detail").value("product-service'e şu anda ulaşılamıyor, lütfen biraz sonra tekrar deneyin"));
	}

	@Test
	@DisplayName("Olmayan ürünle sipariş verilirse 422 ProblemDetail döner")
	void create_productNotFound_returns422() throws Exception {
		// given
		given(orderService.placeOrder(any())).willThrow(new ProductNotFoundException(99L));

		// when / then
		mockMvc.perform(post("/api/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "productId": 99, "quantity": 1 }
								"""))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value("Ürün bulunamadı: 99"));
	}

	@Test
	@DisplayName("Adet 0 ise 400 ProblemDetail ve alan hatası döner")
	void create_invalidQuantity_returns400() throws Exception {
		// given: geçersiz gövde

		// when / then
		mockMvc.perform(post("/api/orders")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "productId": 1, "quantity": 0 }
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("quantity: 0'dan büyük olmalı"));
	}

	@Test
	@DisplayName("GET /api/orders tüm siparişleri döner")
	void findAll_returnsList() throws Exception {
		// given
		given(orderService.findAll()).willReturn(List.of(
				new OrderResponse(1L, 1L, 2, new BigDecimal("1500.00"), OrderStatus.CREATED, NOW),
				new OrderResponse(2L, 3L, 9, new BigDecimal("46800.00"), OrderStatus.REJECTED, NOW)));

		// when / then
		mockMvc.perform(get("/api/orders"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[1].status").value("REJECTED"));
	}

	@Test
	@DisplayName("GET /api/orders/{id} sipariş yoksa 404 ProblemDetail döner")
	void findById_notFound_returns404() throws Exception {
		// given
		given(orderService.findById(42L)).willThrow(new OrderNotFoundException(42L));

		// when / then
		mockMvc.perform(get("/api/orders/42"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Sipariş bulunamadı: 42"));
	}
}
