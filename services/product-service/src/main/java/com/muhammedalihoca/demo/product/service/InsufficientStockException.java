package com.muhammedalihoca.demo.product.service;

// Stok, istenen miktarı karşılamıyorsa fırlatılır; HTTP 409'a çevrilir.
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(Long productId, int requested, int available) {
        super("Yetersiz stok: ürün " + productId + " için istenen " + requested + ", mevcut " + available);
    }
}
