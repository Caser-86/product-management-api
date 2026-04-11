package com.example.ecommerce.pricing.application;

import com.example.ecommerce.pricing.domain.PriceHistoryRepository;
import com.example.ecommerce.pricing.domain.PriceScheduleEntity;
import com.example.ecommerce.pricing.domain.PriceScheduleRepository;
import com.example.ecommerce.product.domain.ProductSkuEntity;
import com.example.ecommerce.product.domain.ProductSpuEntity;
import com.example.ecommerce.product.domain.ProductSpuRepository;
import com.example.ecommerce.product.domain.ProductWorkflowHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.task.scheduling.enabled=false",
    "pricing.schedule.lock-at-least-for=PT0S",
    "pricing.schedule.lock-at-most-for=PT5S"
})
class PriceScheduleJobTest {

    @Autowired
    private PricingService pricingService;

    @Autowired
    private PriceHistoryRepository priceHistoryRepository;

    @Autowired
    private PriceScheduleRepository priceScheduleRepository;

    @Autowired
    private ProductSpuRepository productSpuRepository;

    @Autowired
    private ProductWorkflowHistoryRepository productWorkflowHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long skuId;
    private Long dueScheduleId;
    private Long futureScheduleId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from shedlock");
        priceScheduleRepository.deleteAll();
        priceHistoryRepository.deleteAll();
        productWorkflowHistoryRepository.deleteAll();
        productSpuRepository.deleteAll();

        ProductSpuEntity spu = ProductSpuEntity.draft(2001L, "SPU-PRC-JOB-1", "pricing-job-demo", 33L);
        spu.addSku(ProductSkuEntity.of(2001L, "SKU-PRC-JOB-1", "{\"color\":\"black\"}", "pricing-job-hash-1"));
        skuId = productSpuRepository.save(spu).getSkus().get(0).getId();

        dueScheduleId = priceScheduleRepository.save(PriceScheduleEntity.pending(
            skuId,
            2001L,
            "{\"listPrice\":199.00,\"salePrice\":149.00,\"reason\":\"job due\",\"operatorId\":9001}",
            LocalDateTime.now().minusMinutes(5),
            null
        )).getId();

        futureScheduleId = priceScheduleRepository.save(PriceScheduleEntity.pending(
            skuId,
            2001L,
            "{\"listPrice\":299.00,\"salePrice\":239.00,\"reason\":\"job future\",\"operatorId\":9001}",
            LocalDateTime.now().plusMinutes(30),
            null
        )).getId();
    }

    @Test
    void applies_only_due_pending_schedules() {
        int applied = pricingService.applyDueSchedules(10);

        assertThat(applied).isEqualTo(1);
        assertThat(priceHistoryRepository.findBySkuId(
            skuId,
            PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        ).getContent()).hasSize(1);
        assertThat(priceScheduleRepository.findById(dueScheduleId)).get()
            .extracting(PriceScheduleEntity::getStatus)
            .isEqualTo("applied");
        assertThat(priceScheduleRepository.findById(futureScheduleId)).get()
            .extracting(PriceScheduleEntity::getStatus)
            .isEqualTo("pending");
    }
}
