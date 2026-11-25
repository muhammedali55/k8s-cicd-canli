package com.muhammedalihoca.demo.order.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// product-service'e giden RestClient'ı kurar: base URL (PRODUCT_SERVICE_URL) ve 2 saniyelik timeout'lar.
// Timeout olmazsa product-service takıldığında order-service'in thread'leri de sonsuza kadar bekler.
@Configuration
public class ProductClientConfig {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);

	@Bean
	public RestClient productRestClient(RestClient.Builder builder,
			@Value("${product-service.url}") String productServiceUrl) {
		// Bağlantı timeout'u: TCP bağlantısı kurulana kadar en fazla 2 sn.
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(TIMEOUT)
				.build();

		// Okuma timeout'u: yanıt gelene kadar en fazla 2 sn.
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(TIMEOUT);

		return builder
				.baseUrl(productServiceUrl)
				.requestFactory(requestFactory)
				.build();
	}
}
