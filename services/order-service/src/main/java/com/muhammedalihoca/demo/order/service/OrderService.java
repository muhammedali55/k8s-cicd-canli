package com.muhammedalihoca.demo.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.muhammedalihoca.demo.order.client.InsufficientStockException;
import com.muhammedalihoca.demo.order.client.ProductClient;
import com.muhammedalihoca.demo.order.client.ProductResponse;
import com.muhammedalihoca.demo.order.domain.Order;
import com.muhammedalihoca.demo.order.domain.OrderStatus;
import com.muhammedalihoca.demo.order.dto.CreateOrderRequest;
import com.muhammedalihoca.demo.order.dto.OrderResponse;
import com.muhammedalihoca.demo.order.repository.OrderRepository;

// Sipariş iş mantığı: ürünü getir → stok düşür → toplam fiyatı hesapla → kaydet.
@Service
public class OrderService {

	private final OrderRepository orderRepository;
	private final ProductClient productClient;

	public OrderService(OrderRepository orderRepository, ProductClient productClient) {
		this.orderRepository = orderRepository;
		this.productClient = productClient;
	}

	// Bilerek @Transactional DEĞİL:
	// 1) Uzak HTTP çağrısı sürerken veritabanı transaction'ı açık tutmak istemeyiz.
	// 2) REJECTED kaydından sonra exception fırlatıyoruz; transaction olsaydı bu kayıt geri alınırdı.
	// repository.save() kendi kısa transaction'ını zaten açar.
	public OrderResponse placeOrder(CreateOrderRequest request) {
		// 1. Ürünü getir (yoksa ProductNotFoundException, servis kapalıysa ProductServiceUnavailableException).
		ProductResponse product = productClient.getProduct(request.productId());
		// Kuruş hassasiyeti (2 hane), veritabanındaki NUMERIC(12, 2) ile aynı.
		BigDecimal totalPrice = product.price()
				.multiply(BigDecimal.valueOf(request.quantity()))
				.setScale(2, RoundingMode.HALF_UP);

		// 2. Stoğu düşür. Stok kontrolünü product-service yapar; biz önceden kontrol etmeyiz,
		//    çünkü arada başka bir sipariş stoğu değiştirmiş olabilir.
		try {
			productClient.decreaseStock(request.productId(), request.quantity());
		}
		catch (InsufficientStockException ex) {
			// 3a. Stok yetmedi: siparişi REJECTED olarak kaydet ve çağırana bildir (409).
			Order rejected = orderRepository.save(
					new Order(request.productId(), request.quantity(), totalPrice, OrderStatus.REJECTED));
			throw new OrderRejectedException(OrderResponse.from(rejected),
					"Yetersiz stok: ürün " + request.productId() + " için istenen " + request.quantity()
							+ " adet karşılanamadı");
		}

		// 3b. Stok düştü: siparişi CREATED olarak kaydet.
		Order created = orderRepository.save(
				new Order(request.productId(), request.quantity(), totalPrice, OrderStatus.CREATED));
		return OrderResponse.from(created);
	}

	@Transactional(readOnly = true)
	public List<OrderResponse> findAll() {
		return orderRepository.findAll().stream()
				.map(OrderResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public OrderResponse findById(Long id) {
		return orderRepository.findById(id)
				.map(OrderResponse::from)
				.orElseThrow(() -> new OrderNotFoundException(id));
	}
}
