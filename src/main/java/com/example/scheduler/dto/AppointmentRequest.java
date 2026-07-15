package com.example.scheduler.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record AppointmentRequest(
        @NotNull UUID customerId,
        @NotNull UUID vehicleId,
        @NotNull UUID serviceTypeId,
        @NotNull UUID dealershipId,
        @NotNull Instant desiredStartTime
) {
}
