package com.muhammedalihoca.demo.product.service;

// İstenen id'de ürün yoksa fırlatılır; HTTP 404'e çevrilir.
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("Ürün bulunamadı: " + id);
    }
}
