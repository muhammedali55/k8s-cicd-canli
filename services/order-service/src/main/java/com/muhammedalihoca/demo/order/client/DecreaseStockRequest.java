package com.muhammedalihoca.demo.order.client;

// product-service'e gönderilen stok düşürme gövdesi (sözleşmedeki "DecreaseStockRequest" şeması).
public record DecreaseStockRequest(Integer quantity) {
}
