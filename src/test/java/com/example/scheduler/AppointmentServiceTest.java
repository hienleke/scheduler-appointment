package com.example.scheduler;

import com.example.scheduler.domain.AppointmentStatus;
import com.example.scheduler.dto.AppointmentRequest;
import com.example.scheduler.dto.AppointmentResponse;
import com.example.scheduler.exception.SlotNotAvailableException;
import com.example.scheduler.repository.AppointmentRepository;
import com.example.scheduler.repository.CustomerRepository;
import com.example.scheduler.repository.DealershipRepository;
import com.example.scheduler.repository.ServiceTypeRepository;
import com.example.scheduler.repository.VehicleRepository;
import com.example.scheduler.service.AppointmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AppointmentServiceTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private DealershipRepository dealershipRepository;

    private UUID customerId;
    private UUID vehicleId;
    private UUID serviceTypeId;
    private UUID dealershipId;

    @BeforeEach
    void setUp() {
        customerId = customerRepository.findAll().get(0).getId();
        vehicleId = vehicleRepository.findAll().get(0).getId();
        serviceTypeId = serviceTypeRepository.findAll().stream()
                .filter(type -> "Oil Change".equals(type.getName()))
                .findFirst()
                .orElseThrow()
                .getId();
        dealershipId = dealershipRepository.findAll().get(0).getId();
    }

    @Test
    void booksAppointmentWhenBayAndTechnicianAreAvailable() {
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

        AppointmentResponse response = appointmentService.bookAppointment(new AppointmentRequest(
                customerId,
                vehicleId,
                serviceTypeId,
                dealershipId,
                start
        ));

        assertThat(response.status().name()).isEqualTo("CONFIRMED");
        assertThat(response.serviceBayName()).isNotBlank();
        assertThat(response.technicianName()).isNotBlank();
        assertThat(response.endTime()).isEqualTo(start.plusSeconds(60 * 60));
    }

    @Test
    void rejectsDoubleBookingWhenTechnicianIsBusy() {
        UUID brakeServiceId = serviceTypeRepository.findAll().stream()
                .filter(type -> "Brake Service".equals(type.getName()))
                .findFirst()
                .orElseThrow()
                .getId();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        AppointmentRequest request = new AppointmentRequest(
                customerId,
                vehicleId,
                brakeServiceId,
                dealershipId,
                start
        );

        appointmentService.bookAppointment(request);

        assertThatThrownBy(() -> appointmentService.bookAppointment(request))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void onlyOneBookingSucceedsUnderConcurrentRequests() throws Exception {
        // Brake Service has only one qualified technician at the seeded dealership,
        // so concurrent requests compete for the same scarce resource.
        UUID brakeServiceId = serviceTypeRepository.findAll().stream()
                .filter(type -> "Brake Service".equals(type.getName()))
                .findFirst()
                .orElseThrow()
                .getId();
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        AppointmentRequest request = new AppointmentRequest(
                customerId,
                vehicleId,
                brakeServiceId,
                dealershipId,
                start
        );

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                startGate.await();
                try {
                    appointmentService.bookAppointment(request);
                    success.incrementAndGet();
                } catch (SlotNotAvailableException ex) {
                    conflict.incrementAndGet();
                }
                return null;
            }));
        }

        ready.await();
        startGate.countDown();

        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(threads - 1);
        assertThat(appointmentRepository.findAll().stream()
                .filter(a -> a.getStartTime().equals(start))
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
                .count()).isEqualTo(1);
    }
}
