package com.example.scheduler.service;

import com.example.scheduler.domain.Appointment;
import com.example.scheduler.domain.AppointmentStatus;
import com.example.scheduler.domain.Customer;
import com.example.scheduler.domain.Dealership;
import com.example.scheduler.domain.ServiceType;
import com.example.scheduler.domain.Vehicle;
import com.example.scheduler.dto.AppointmentRequest;
import com.example.scheduler.dto.AppointmentResponse;
import com.example.scheduler.dto.AvailabilityResponse;
import com.example.scheduler.exception.BadRequestException;
import com.example.scheduler.exception.ResourceNotFoundException;
import com.example.scheduler.exception.SlotNotAvailableException;
import com.example.scheduler.repository.AppointmentRepository;
import com.example.scheduler.repository.CustomerRepository;
import com.example.scheduler.repository.DealershipRepository;
import com.example.scheduler.repository.ServiceTypeRepository;
import com.example.scheduler.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AppointmentService {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final DealershipRepository dealershipRepository;
    private final AppointmentRepository appointmentRepository;
    private final AvailabilityService availabilityService;

    public AppointmentService(
            CustomerRepository customerRepository,
            VehicleRepository vehicleRepository,
            ServiceTypeRepository serviceTypeRepository,
            DealershipRepository dealershipRepository,
            AppointmentRepository appointmentRepository,
            AvailabilityService availabilityService
    ) {
        this.customerRepository = customerRepository;
        this.vehicleRepository = vehicleRepository;
        this.serviceTypeRepository = serviceTypeRepository;
        this.dealershipRepository = dealershipRepository;
        this.appointmentRepository = appointmentRepository;
        this.availabilityService = availabilityService;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse checkAvailability(AppointmentRequest request) {
        BookingContext context = resolveBookingContext(request);
        AvailabilityCapacity capacity = availabilityService.getAvailabilityCapacity(
                context.dealership().getId(),
                context.serviceType().getId(),
                context.startTime(),
                context.endTime()
        );

        return new AvailabilityResponse(
                capacity.isAvailable(),
                new AvailabilityResponse.RequestedTime(context.startTime(), context.endTime()),
                new AvailabilityResponse.ServiceTypeInfo(
                        context.serviceType().getId(),
                        context.serviceType().getName(),
                        context.serviceType().getDurationMinutes()
                ),
                new AvailabilityResponse.Capacity(
                        capacity.availableTechnicians(),
                        capacity.availableServiceBays()
                )
        );
    }

    @Transactional
    public AppointmentResponse bookAppointment(AppointmentRequest request) {
        BookingContext context = resolveBookingContext(request);

        // Pessimistic lock on candidate bays + technicians, then allocate under lock.
        AvailableSlot slot = availabilityService.findAvailableSlotForUpdate(
                        context.dealership().getId(),
                        context.serviceType().getId(),
                        context.startTime(),
                        context.endTime()
                )
                .orElseThrow(() -> new SlotNotAvailableException(
                        "No service bay and qualified technician available for the requested time slot"
                ));

        Appointment appointment = new Appointment();
        appointment.setCustomer(context.customer());
        appointment.setVehicle(context.vehicle());
        appointment.setServiceType(context.serviceType());
        appointment.setDealership(context.dealership());
        appointment.setServiceBay(slot.serviceBay());
        appointment.setTechnician(slot.technician());
        appointment.setStartTime(context.startTime());
        appointment.setEndTime(context.endTime());
        appointment.setStatus(AppointmentStatus.CONFIRMED);

        return toResponse(appointmentRepository.save(appointment));
    }

    private BookingContext resolveBookingContext(AppointmentRequest request) {
        if (!request.desiredStartTime().isAfter(Instant.now())) {
            throw new BadRequestException("Desired start time must be in the future");
        }

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + request.customerId()));

        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + request.vehicleId()));

        if (!vehicle.getCustomer().getId().equals(customer.getId())) {
            throw new BadRequestException("Vehicle does not belong to the customer");
        }

        ServiceType serviceType = serviceTypeRepository.findById(request.serviceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found: " + request.serviceTypeId()));

        Dealership dealership = dealershipRepository.findById(request.dealershipId())
                .orElseThrow(() -> new ResourceNotFoundException("Dealership not found: " + request.dealershipId()));

        Instant endTime = request.desiredStartTime().plusSeconds(serviceType.getDurationMinutes() * 60L);
        return new BookingContext(customer, vehicle, serviceType, dealership, request.desiredStartTime(), endTime);
    }

    private AppointmentResponse toResponse(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getCustomer().getId(),
                appointment.getCustomer().getName(),
                appointment.getVehicle().getId(),
                appointment.getVehicle().getVin(),
                appointment.getServiceType().getId(),
                appointment.getServiceType().getName(),
                appointment.getDealership().getId(),
                appointment.getDealership().getName(),
                appointment.getServiceBay().getId(),
                appointment.getServiceBay().getName(),
                appointment.getTechnician().getId(),
                appointment.getTechnician().getName(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus(),
                appointment.getCreatedAt()
        );
    }

    private record BookingContext(
            Customer customer,
            Vehicle vehicle,
            ServiceType serviceType,
            Dealership dealership,
            Instant startTime,
            Instant endTime
    ) {
    }
}
