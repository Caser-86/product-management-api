package com.example.ecommerce.pricing.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PriceScheduleJobTest {

    @Test
    void executes_due_price_schedule() {
        PriceScheduleJob job = new PriceScheduleJob();

        assertThat(job.isDue("2026-04-10T00:00:00", "2026-04-10T00:01:00")).isTrue();
    }
}
