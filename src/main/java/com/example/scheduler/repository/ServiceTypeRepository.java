package com.example.scheduler.repository;

import com.example.scheduler.domain.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, UUID> {
}
