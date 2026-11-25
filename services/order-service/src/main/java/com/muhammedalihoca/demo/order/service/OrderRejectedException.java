package com.muhammedalihoca.demo.order.service;

import com.muhammedalihoca.demo.order.dto.OrderResponse;

// Stok yetmediği için sipariş REJECTED olarak kaydedildiğinde fırlatılır.
// Kaydedilen siparişi taşır ki 409 yanıtında istemciye gösterebilelim.
public class OrderRejectedException extends RuntimeException {

	private final OrderResponse order;

	public OrderRejectedException(OrderResponse order, String message) {
		super(message);
		this.order = order;
	}

	public OrderResponse getOrder() {
		return order;
	}
}
