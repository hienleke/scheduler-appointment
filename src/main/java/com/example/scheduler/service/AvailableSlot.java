package com.example.scheduler.service;

import com.example.scheduler.domain.ServiceBay;
import com.example.scheduler.domain.Technician;

public record AvailableSlot(ServiceBay serviceBay, Technician technician) {
}
