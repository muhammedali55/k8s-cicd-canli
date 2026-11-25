package com.muhammedalihoca.demo.order.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.muhammedalihoca.demo.order.dto.InfoResponse;

// GET /api/info: hangi sürümün çalıştığını gösterir (canlıda yeni sürümün geldiğini görmek için).
@RestController
public class InfoController {

	private final String version;

	public InfoController(@Value("${app.version}") String version) {
		this.version = version;
	}

	@GetMapping("/api/info")
	public InfoResponse info() {
		return new InfoResponse("order-service", version);
	}
}
