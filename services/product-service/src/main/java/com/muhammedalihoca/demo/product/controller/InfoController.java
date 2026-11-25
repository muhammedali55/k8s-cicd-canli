package com.muhammedalihoca.demo.product.controller;

import com.muhammedalihoca.demo.product.dto.InfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// GET /api/info: hangi sürümün çalıştığını gösterir (canlıda yeni sürüm geldi mi?).
@RestController
public class InfoController {

    private final String serviceName;
    private final String version;

    public InfoController(@Value("${spring.application.name}") String serviceName,
                          @Value("${APP_VERSION:local}") String version) {
        this.serviceName = serviceName;
        this.version = version;
    }

    @GetMapping("/api/info")
    public InfoResponse info() {
        return new InfoResponse(serviceName, version);
    }
}
