package com.example.scheduler.controller;

import com.example.scheduler.dto.AppointmentRequest;
import com.example.scheduler.dto.AppointmentResponse;
import com.example.scheduler.dto.AvailabilityResponse;
import com.example.scheduler.service.AppointmentService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final Logger log;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
        this.log = LoggerFactory.getLogger(AppointmentController.class);
    }

    @PostMapping("/availability")
    public AvailabilityResponse checkAvailability(@Valid @RequestBody AppointmentRequest request) {
        return appointmentService.checkAvailability(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse bookAppointment(@Valid @RequestBody AppointmentRequest request) {
        log.info("Booking appointment for request: {}", request);
        return appointmentService.bookAppointment(request);
    }
}
