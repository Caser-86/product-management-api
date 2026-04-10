package com.example.ecommerce.shared.outbox;

import java.time.Instant;

public record OutboxEvent(String eventType, String aggregateType, String aggregateId, String payload, Instant occurredAt) {
}
