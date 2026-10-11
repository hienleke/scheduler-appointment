package com.example.scheduler.service;

import com.example.scheduler.domain.AppointmentStatus;
import com.example.scheduler.domain.ServiceBay;
import com.example.scheduler.domain.Technician;
import com.example.scheduler.repository.AppointmentRepository;
import com.example.scheduler.repository.ServiceBayRepository;
import com.example.scheduler.repository.TechnicianRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AvailabilityService {

    private final ServiceBayRepository serviceBayRepository;
    private final TechnicianRepository technicianRepository;
    private final AppointmentRepository appointmentRepository;

    public AvailabilityService(
            ServiceBayRepository serviceBayRepository,
            TechnicianRepository technicianRepository,
            AppointmentRepository appointmentRepository
    ) {
        this.serviceBayRepository = serviceBayRepository;
        this.technicianRepository = technicianRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Read-only capacity check — counts free bays and qualified technicians (no row locks).
     */
    public AvailabilityCapacity getAvailabilityCapacity(
            UUID dealershipId,
            UUID serviceTypeId,
            Instant startTime,
            Instant endTime
    ) {
        List<ServiceBay> bays = serviceBayRepository.findByDealershipIdAndActiveTrue(dealershipId);
        List<Technician> technicians = technicianRepository.findQualifiedActiveByDealershipAndServiceType(
                dealershipId,
                serviceTypeId
        );

        int availableServiceBays = 0;
        for (ServiceBay bay : bays) {
            if (!hasBayConflict(bay.getId(), startTime, endTime)) {
                availableServiceBays++;
            }
        }

        int availableTechnicians = 0;
        for (Technician technician : technicians) {
            if (!hasTechnicianConflict(technician.getId(), startTime, endTime)) {
                availableTechnicians++;
            }
        }

        return new AvailabilityCapacity(availableTechnicians, availableServiceBays);
    }

    /**
     * Booking path — locks all candidate bays then technicians (ordered by id)
     * with PESSIMISTIC_WRITE so concurrent bookings cannot double-allocate the same resources.
     */
    public Optional<AvailableSlot> findAvailableSlotForUpdate(
            UUID dealershipId,
            UUID serviceTypeId,
            Instant startTime,
            Instant endTime
    ) {
        // Lock order is always: bays first (by id), then technicians (by id) — prevents deadlocks.
        List<ServiceBay> bays = serviceBayRepository.findByDealershipIdAndActiveTrueForUpdate(dealershipId);
        List<Technician> technicians = technicianRepository.findQualifiedActiveByDealershipAndServiceTypeForUpdate(
                dealershipId,
                serviceTypeId
        );
        return selectFirstFreeSlot(bays, technicians, startTime, endTime);
    }

    private Optional<AvailableSlot> selectFirstFreeSlot(
            List<ServiceBay> bays,
            List<Technician> technicians,
            Instant startTime,
            Instant endTime
    ) {
        for (ServiceBay bay : bays) {
            if (hasBayConflict(bay.getId(), startTime, endTime)) {
                continue;
            }
            for (Technician technician : technicians) {
                if (!hasTechnicianConflict(technician.getId(), startTime, endTime)) {
                    return Optional.of(new AvailableSlot(bay, technician));
                }
            }
        }
        return Optional.empty();
    }

    public boolean hasBayConflict(UUID bayId, Instant startTime, Instant endTime) {
        return appointmentRepository.existsBayConflict(
                bayId,
                AppointmentStatus.CONFIRMED,
                startTime,
                endTime
        );
    }

    public boolean hasTechnicianConflict(UUID technicianId, Instant startTime, Instant endTime) {
        return appointmentRepository.existsTechnicianConflict(
                technicianId,
                AppointmentStatus.CONFIRMED,
                startTime,
                endTime
        );
    }
}
