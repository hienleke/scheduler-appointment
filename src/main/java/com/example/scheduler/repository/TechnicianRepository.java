package com.example.scheduler.repository;

import com.example.scheduler.domain.Technician;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TechnicianRepository extends JpaRepository<Technician, UUID> {

    @Query("""
            SELECT t FROM Technician t
            WHERE t.dealership.id = :dealershipId
              AND t.active = true
              AND EXISTS (
                  SELECT 1 FROM TechnicianSkill ts
                  WHERE ts.technician = t AND ts.serviceType.id = :serviceTypeId
              )
            ORDER BY t.id ASC
            """)
    List<Technician> findQualifiedActiveByDealershipAndServiceType(
            @Param("dealershipId") UUID dealershipId,
            @Param("serviceTypeId") UUID serviceTypeId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t FROM Technician t
            WHERE t.dealership.id = :dealershipId
              AND t.active = true
              AND EXISTS (
                  SELECT 1 FROM TechnicianSkill ts
                  WHERE ts.technician = t AND ts.serviceType.id = :serviceTypeId
              )
            ORDER BY t.id ASC
            """)
    List<Technician> findQualifiedActiveByDealershipAndServiceTypeForUpdate(
            @Param("dealershipId") UUID dealershipId,
            @Param("serviceTypeId") UUID serviceTypeId
    );
}
