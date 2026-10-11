package com.example.scheduler.dto;

import com.example.scheduler.domain.AppointmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id,
        UUID customerId,
        String customerName,
        UUID vehicleId,
        String vehicleVin,
        UUID serviceTypeId,
        String serviceTypeName,
        UUID dealershipId,
        String dealershipName,
        UUID serviceBayId,
        String serviceBayName,
        UUID technicianId,
        String technicianName,
        Instant startTime,
        Instant endTime,
        AppointmentStatus status,
        Instant createdAt
) {
}
