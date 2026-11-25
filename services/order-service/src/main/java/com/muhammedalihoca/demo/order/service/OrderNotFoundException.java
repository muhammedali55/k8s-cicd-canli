package com.muhammedalihoca.demo.order.service;

// İstenen id ile sipariş yoksa fırlatılır; dışarıya 404 olarak döner.
public class OrderNotFoundException extends RuntimeException {

	public OrderNotFoundException(Long orderId) {
		super("Sipariş bulunamadı: " + orderId);
	}
}
