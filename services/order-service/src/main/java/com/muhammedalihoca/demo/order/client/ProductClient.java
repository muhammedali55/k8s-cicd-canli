package com.muhammedalihoca.demo.order.client;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

// product-service'i HTTP üzerinden çağıran istemci. Tek bağımız contracts/product-api.yaml sözleşmesi.
// HTTP ayrıntılarını (status kodları, bağlantı hataları) burada anlamlı exception'lara çeviririz;
// service katmanı HTTP bilmez.
@Component
public class ProductClient {

	private final RestClient restClient;

	public ProductClient(RestClient productRestClient) {
		this.restClient = productRestClient;
	}

	// GET /api/products/{id}
	public ProductResponse getProduct(Long productId) {
		try {
			return restClient.get()
					.uri("/api/products/{id}", productId)
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
						if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
							throw new ProductNotFoundException(productId);
						}
						throw unexpected(response.getStatusCode());
					})
					.body(ProductResponse.class);
		}
		catch (ResourceAccessException | HttpServerErrorException ex) {
			throw unavailable(ex);
		}
	}

	// POST /api/products/{id}/decrease-stock — başarıda ürünün güncel hâli döner.
	public ProductResponse decreaseStock(Long productId, Integer quantity) {
		try {
			return restClient.post()
					.uri("/api/products/{id}/decrease-stock", productId)
					.contentType(MediaType.APPLICATION_JSON)
					.accept(MediaType.APPLICATION_JSON)
					.body(new DecreaseStockRequest(quantity))
					.retrieve()
					.onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
						HttpStatusCode status = response.getStatusCode();
						if (status.isSameCodeAs(HttpStatus.NOT_FOUND)) {
							throw new ProductNotFoundException(productId);
						}
						if (status.isSameCodeAs(HttpStatus.CONFLICT)) {
							throw new InsufficientStockException(productId, quantity);
						}
						throw unexpected(status);
					})
					.body(ProductResponse.class);
		}
		catch (ResourceAccessException | HttpServerErrorException ex) {
			throw unavailable(ex);
		}
	}

	// Sözleşmede tanımlı olmayan bir 4xx (ör. 400): isteği biz yanlış kurmuşuzdur → 502 olarak dışarı verilir.
	private ProductServiceBadResponseException unexpected(HttpStatusCode status) {
		return new ProductServiceBadResponseException(
				"product-service beklenmedik bir yanıt döndü: HTTP " + status.value());
	}

	// ResourceAccessException: bağlantı kurulamadı ya da timeout. HttpServerErrorException: karşı taraf 5xx döndü.
	private ProductServiceUnavailableException unavailable(Exception cause) {
		return new ProductServiceUnavailableException(
				"product-service'e şu anda ulaşılamıyor, lütfen biraz sonra tekrar deneyin", cause);
	}
}
