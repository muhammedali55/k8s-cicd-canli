package com.muhammedalihoca.demo.order.client;

import java.math.BigDecimal;

// product-service'ten gelen ürün (sözleşmedeki "Product" şeması).
public record ProductResponse(Long id, String name, BigDecimal price, Integer stock) {
}
