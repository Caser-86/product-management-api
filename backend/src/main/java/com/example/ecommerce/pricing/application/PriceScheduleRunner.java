package com.example.ecommerce.pricing.application;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceScheduleRunner {

    private static final int DUE_SCHEDULE_BATCH_SIZE = 20;

    private final PricingService pricingService;

    public PriceScheduleRunner(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @Scheduled(fixedDelayString = "${pricing.schedule.fixed-delay-ms:30000}")
    @SchedulerLock(
        name = "${pricing.schedule.lock-name:pricing.due-schedule-runner}",
        lockAtMostFor = "${pricing.schedule.lock-at-most-for:PT1M}",
        lockAtLeastFor = "${pricing.schedule.lock-at-least-for:PT5S}"
    )
    public void runDueSchedules() {
        LockAssert.assertLocked();
        pricingService.applyDueSchedules(DUE_SCHEDULE_BATCH_SIZE);
    }
}
