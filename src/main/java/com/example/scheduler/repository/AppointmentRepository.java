package com.example.scheduler.repository;

import com.example.scheduler.domain.Appointment;
import com.example.scheduler.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Appointment a
            WHERE a.serviceBay.id = :bayId
              AND a.status = :status
              AND a.startTime < :endTime
              AND a.endTime > :startTime
            """)
    boolean existsBayConflict(
            @Param("bayId") UUID bayId,
            @Param("status") AppointmentStatus status,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM Appointment a
            WHERE a.technician.id = :technicianId
              AND a.status = :status
              AND a.startTime < :endTime
              AND a.endTime > :startTime
            """)
    boolean existsTechnicianConflict(
            @Param("technicianId") UUID technicianId,
            @Param("status") AppointmentStatus status,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}
