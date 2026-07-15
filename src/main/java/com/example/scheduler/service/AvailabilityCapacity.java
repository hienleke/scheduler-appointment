package com.example.scheduler.service;

public record AvailabilityCapacity(
        int availableTechnicians,
        int availableServiceBays
) {
    public boolean isAvailable() {
        return availableTechnicians > 0 && availableServiceBays > 0;
    }
}
