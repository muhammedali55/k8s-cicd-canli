package com.muhammedalihoca.demo.order.client;

// product-service "ürün yok" (404) dediğinde fırlatılır.
public class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(Long productId) {
		super("Ürün bulunamadı: " + productId);
	}
}
