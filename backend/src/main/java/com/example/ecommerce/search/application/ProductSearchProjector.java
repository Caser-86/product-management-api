package com.example.ecommerce.search.application;

import com.example.ecommerce.shared.outbox.OutboxEvent;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchProjector {

    public void project(OutboxEvent event) {
        if (!"ProductChanged".equals(event.eventType())) {
            return;
        }
    }
}
