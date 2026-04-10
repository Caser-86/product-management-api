package com.example.ecommerce.pricing.application;

import java.time.LocalDateTime;

public class PriceScheduleJob {

    public boolean isDue(String effectiveAt, String now) {
        return !LocalDateTime.parse(effectiveAt).isAfter(LocalDateTime.parse(now));
    }
}
