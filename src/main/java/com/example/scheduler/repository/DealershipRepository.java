package com.example.scheduler.repository;

import com.example.scheduler.domain.Dealership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DealershipRepository extends JpaRepository<Dealership, UUID> {
}
