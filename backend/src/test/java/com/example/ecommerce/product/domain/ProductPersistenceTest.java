package com.example.ecommerce.product.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ProductPersistenceTest {

    @Autowired
    private ProductSpuRepository spuRepository;

    @Test
    void saves_spu_with_single_sku() {
        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-1001", "男士连帽卫衣", 33L);
        spu.addSku(ProductSkuEntity.of(2001L, "SKU-1001-BLK-M", "{\"颜色\":\"黑色\",\"尺寸\":\"M\"}", "spec-hash-1"));

        ProductSpuEntity saved = spuRepository.save(spu);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSkus()).hasSize(1);
    }
}
