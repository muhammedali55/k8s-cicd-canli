package com.muhammedalihoca.demo.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.muhammedalihoca.demo.product.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Uygulamanın tamamını H2 ("test" profili) ile ayağa kaldırır; Flyway migration'larını doğrular.
@SpringBootTest
@ActiveProfiles("test")
class ProductServiceApplicationTests {

    @Autowired
    private ProductRepository productRepository;

    @Test
    @DisplayName("Uygulama açılır ve Flyway 3 örnek ürünü yükler")
    void flywayOrnekUrunleriYukler() {
        // given: uygulama "test" profiliyle açıldı

        // when
        long count = productRepository.count();

        // then
        assertThat(count).isEqualTo(3);
    }
}
