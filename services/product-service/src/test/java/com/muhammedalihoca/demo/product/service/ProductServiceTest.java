package com.muhammedalihoca.demo.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.muhammedalihoca.demo.product.domain.Product;
import com.muhammedalihoca.demo.product.dto.ProductResponse;
import com.muhammedalihoca.demo.product.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// ProductService'in birim testleri: veritabanı yok, repository Mockito ile taklit edilir.
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    @DisplayName("Stok yeterliyse istenen miktar kadar düşer")
    void decreaseStock_yeterliStok() {
        // given
        Product klavye = new Product("Klavye", new BigDecimal("750.00"), 10);
        when(productRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(klavye));

        // when
        ProductResponse result = productService.decreaseStock(1L, 3);

        // then
        assertThat(result.stock()).isEqualTo(7);
        assertThat(klavye.getStock()).isEqualTo(7);
    }

    @Test
    @DisplayName("Stok yetersizse hata fırlar ve stok değişmez")
    void decreaseStock_yetersizStok() {
        // given
        Product monitor = new Product("Monitör", new BigDecimal("5200.00"), 5);
        when(productRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(monitor));

        // when / then
        assertThatThrownBy(() -> productService.decreaseStock(3L, 6))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("istenen 6, mevcut 5");
        assertThat(monitor.getStock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Ürün yoksa ProductNotFoundException fırlar")
    void decreaseStock_urunYok() {
        // given
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> productService.decreaseStock(99L, 1))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Ürün bulunamadı: 99");
    }
}
