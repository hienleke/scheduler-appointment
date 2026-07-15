package com.example.scheduler.repository;

import com.example.scheduler.domain.ServiceBay;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ServiceBayRepository extends JpaRepository<ServiceBay, UUID> {

    List<ServiceBay> findByDealershipIdAndActiveTrue(UUID dealershipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b FROM ServiceBay b
            WHERE b.dealership.id = :dealershipId AND b.active = true
            ORDER BY b.id ASC
            """)
    List<ServiceBay> findByDealershipIdAndActiveTrueForUpdate(@Param("dealershipId") UUID dealershipId);
}
