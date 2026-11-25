package com.muhammedalihoca.demo.order.client;

// product-service beklenmedik bir 4xx döndüğünde (sözleşmede bu çağrı için tanımlı olmayan bir hata) fırlatılır.
// Sorun bizim tarafımızdadır (product-service'e yanlış istek gönderdik); dışarıya 502 Bad Gateway olarak döner.
public class ProductServiceBadResponseException extends RuntimeException {

	public ProductServiceBadResponseException(String message) {
		super(message);
	}
}
