package com.example.ecommerce.pricing.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceScheduleRunner {

    private final PricingService pricingService;

    public PriceScheduleRunner(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @Scheduled(fixedDelayString = "${pricing.schedule.fixed-delay-ms:30000}")
    public int runDueSchedules() {
        return pricingService.applyDueSchedules(20);
    }
}
