package com.muhammedalihoca.demo.order.client;

// product-service "stok yetersiz" (409) dediğinde fırlatılır.
public class InsufficientStockException extends RuntimeException {

	public InsufficientStockException(Long productId, Integer quantity) {
		super("Yetersiz stok: ürün " + productId + " için istenen " + quantity);
	}
}
