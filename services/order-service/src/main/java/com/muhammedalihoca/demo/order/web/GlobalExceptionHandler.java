package com.muhammedalihoca.demo.order.web;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.muhammedalihoca.demo.order.client.ProductNotFoundException;
import com.muhammedalihoca.demo.order.client.ProductServiceBadResponseException;
import com.muhammedalihoca.demo.order.client.ProductServiceUnavailableException;
import com.muhammedalihoca.demo.order.service.OrderNotFoundException;
import com.muhammedalihoca.demo.order.service.OrderRejectedException;

// Uygulamadaki exception'ları RFC 9457 ProblemDetail yanıtlarına çevirir.
// ResponseEntityExceptionHandler'dan türediği için Spring'in kendi hataları (400 vb.) da ProblemDetail döner.
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	// GET /api/orders/{id} → sipariş yok → 404
	@ExceptionHandler(OrderNotFoundException.class)
	public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	// Stok yetmedi → sipariş REJECTED kaydedildi → 409; kaydedilen sipariş "order" alanında döner.
	@ExceptionHandler(OrderRejectedException.class)
	public ProblemDetail handleOrderRejected(OrderRejectedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setProperty("order", ex.getOrder());
		return problem;
	}

	// Sipariş verilen ürün product-service'te yok → 422. İstek biçimce doğru ama içindeki productId geçersiz.
	// (404 seçmedik: POST /api/orders için 404, "bu adres yok" gibi yanlış anlaşılır.)
	@ExceptionHandler(ProductNotFoundException.class)
	public ProblemDetail handleProductNotFound(ProductNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
	}

	// product-service'e ulaşılamadı → 503
	@ExceptionHandler(ProductServiceUnavailableException.class)
	public ProblemDetail handleProductServiceUnavailable(ProductServiceUnavailableException ex) {
		log.warn("product-service çağrısı başarısız: {}", ex.getCause() == null ? ex.getMessage() : ex.getCause().toString());
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
	}

	// product-service beklenmedik bir 4xx döndü → 502 Bad Gateway (arkadaki servisle konuşmamızda sorun var)
	@ExceptionHandler(ProductServiceBadResponseException.class)
	public ProblemDetail handleProductServiceBadResponse(ProductServiceBadResponseException ex) {
		log.error("product-service beklenmedik yanıt: {}", ex.getMessage());
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
	}

	// @Valid hatalarını "alan: mesaj" biçiminde okunur bir detaya çevirir.
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + ": " + error.getDefaultMessage())
				.sorted()
				.collect(Collectors.joining(", "));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}
}
