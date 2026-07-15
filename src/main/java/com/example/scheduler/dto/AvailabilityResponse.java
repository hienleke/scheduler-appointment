package com.example.scheduler.dto;

import java.time.Instant;
import java.util.UUID;

public record AvailabilityResponse(
        boolean available,
        RequestedTime requestedTime,
        ServiceTypeInfo serviceType,
        Capacity capacity
) {
    public record RequestedTime(Instant startTime, Instant endTime) {
    }

    public record ServiceTypeInfo(UUID id, String name, int durationMinutes) {
    }

    public record Capacity(int availableTechnicians, int availableServiceBays) {
    }
}
