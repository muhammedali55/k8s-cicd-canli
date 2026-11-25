package com.muhammedalihoca.demo.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// POST /api/orders isteğinin gövdesi: hangi üründen kaç adet.
public record CreateOrderRequest(
		@NotNull(message = "boş olamaz") @Positive(message = "0'dan büyük olmalı") Long productId,
		@NotNull(message = "boş olamaz") @Positive(message = "0'dan büyük olmalı") Integer quantity) {
}
