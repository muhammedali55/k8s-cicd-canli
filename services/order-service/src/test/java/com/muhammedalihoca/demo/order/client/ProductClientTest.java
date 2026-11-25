package com.muhammedalihoca.demo.order.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.muhammedalihoca.demo.order.config.ProductClientConfig;

// ProductClient'ın HTTP hatalarını doğru exception'lara çevirdiğini doğrular
// (bağlantı hatası → ProductServiceUnavailableException, beklenmedik 4xx → ProductServiceBadResponseException).
class ProductClientTest {

	@Test
	@DisplayName("product-service'e bağlanılamazsa ProductServiceUnavailableException fırlatılır")
	void getProduct_connectionRefused() {
		// given: 1 numaralı porta kimse dinlemiyor → bağlantı reddedilir
		RestClient restClient = new ProductClientConfig()
				.productRestClient(RestClient.builder(), "http://localhost:1");
		ProductClient productClient = new ProductClient(restClient);

		// when / then
		assertThatThrownBy(() -> productClient.getProduct(1L))
				.isInstanceOf(ProductServiceUnavailableException.class)
				.hasMessageContaining("ulaşılamıyor");
	}

	@Test
	@DisplayName("product-service beklenmedik 400 dönerse ProductServiceBadResponseException fırlatılır")
	void decreaseStock_unexpectedBadRequest() {
		// given: sahte sunucu decrease-stock çağrısına 400 döner
		RestClient.Builder builder = RestClient.builder().baseUrl("http://product-service");
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://product-service/api/products/1/decrease-stock"))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST));
		ProductClient productClient = new ProductClient(builder.build());

		// when / then
		assertThatThrownBy(() -> productClient.decreaseStock(1L, 2))
				.isInstanceOf(ProductServiceBadResponseException.class)
				.hasMessageContaining("HTTP 400");
		server.verify();
	}
}
