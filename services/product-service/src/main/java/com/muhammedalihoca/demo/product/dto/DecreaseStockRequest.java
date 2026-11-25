package com.muhammedalihoca.demo.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Stok düşürme isteğinin gövdesi: { "quantity": n } (n >= 1).
public record DecreaseStockRequest(
        @NotNull(message = "zorunlu") @Min(value = 1, message = "en az 1 olmalı") Integer quantity) {
}
