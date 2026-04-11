package com.example.ecommerce.shared.config;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "${pricing.schedule.lock-at-most-for:PT1M}")
public class SchedulingConfiguration {
}
