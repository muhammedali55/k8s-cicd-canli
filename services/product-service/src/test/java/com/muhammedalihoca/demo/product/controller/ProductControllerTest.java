package com.muhammedalihoca.demo.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muhammedalihoca.demo.product.dto.CreateProductRequest;
import com.muhammedalihoca.demo.product.dto.ProductResponse;
import com.muhammedalihoca.demo.product.service.InsufficientStockException;
import com.muhammedalihoca.demo.product.service.ProductNotFoundException;
import com.muhammedalihoca.demo.product.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// ProductController'ın web katmanı testi: yalnızca MVC ayağa kalkar, servis taklit edilir.
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    @DisplayName("GET /api/products tüm ürünleri döner")
    void list() throws Exception {
        // given
        when(productService.findAll()).thenReturn(List.of(
                new ProductResponse(1L, "Klavye", new BigDecimal("750.00"), 10),
                new ProductResponse(2L, "Mouse", new BigDecimal("350.00"), 25)));

        // when / then
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Klavye"))
                .andExpect(jsonPath("$[1].stock").value(25));
    }

    @Test
    @DisplayName("GET /api/products/{id} ürün yoksa 404 ProblemDetail döner")
    void get_bulunamadi() throws Exception {
        // given
        when(productService.findById(99L)).thenThrow(new ProductNotFoundException(99L));

        // when / then
        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Ürün bulunamadı: 99"))
                .andExpect(jsonPath("$.instance").value("/api/products/99"));
    }

    @Test
    @DisplayName("POST /api/products geçerli gövdeyle 201 ve Location başlığı döner")
    void create_basarili() throws Exception {
        // given
        when(productService.create(any(CreateProductRequest.class)))
                .thenReturn(new ProductResponse(4L, "Kulaklık", new BigDecimal("1200.00"), 8));

        // when / then
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Kulaklık", "price": 1200.00, "stock": 8 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/4"))
                .andExpect(jsonPath("$.id").value(4));
    }

    @Test
    @DisplayName("POST /api/products fiyat 0 ise 400 ProblemDetail döner")
    void create_gecersizFiyat() throws Exception {
        // given
        String body = """
                { "name": "Kulaklık", "price": 0, "stock": 8 }
                """;

        // when / then
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("price: 0'dan büyük olmalı"));
    }

    @Test
    @DisplayName("POST /api/products/{id}/decrease-stock stok yeterliyse güncel ürünü döner")
    void decreaseStock_basarili() throws Exception {
        // given
        when(productService.decreaseStock(1L, 2))
                .thenReturn(new ProductResponse(1L, "Klavye", new BigDecimal("750.00"), 8));

        // when / then
        mockMvc.perform(post("/api/products/1/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantity": 2 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(8));
    }

    @Test
    @DisplayName("POST /api/products/{id}/decrease-stock stok yetersizse 409 ProblemDetail döner")
    void decreaseStock_yetersizStok() throws Exception {
        // given
        when(productService.decreaseStock(1L, 20))
                .thenThrow(new InsufficientStockException(1L, 20, 10));

        // when / then
        mockMvc.perform(post("/api/products/1/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantity": 20 }
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Yetersiz stok: ürün 1 için istenen 20, mevcut 10"));
    }

    @Test
    @DisplayName("POST /api/products/{id}/decrease-stock miktar 0 ise 400 döner")
    void decreaseStock_gecersizMiktar() throws Exception {
        // given
        String body = """
                { "quantity": 0 }
                """;

        // when / then
        mockMvc.perform(post("/api/products/1/decrease-stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("quantity: en az 1 olmalı"));
    }
}
