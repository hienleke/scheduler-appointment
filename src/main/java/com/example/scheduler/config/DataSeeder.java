package com.example.scheduler.config;

import com.example.scheduler.domain.Customer;
import com.example.scheduler.domain.Dealership;
import com.example.scheduler.domain.ServiceBay;
import com.example.scheduler.domain.ServiceType;
import com.example.scheduler.domain.Technician;
import com.example.scheduler.domain.TechnicianSkill;
import com.example.scheduler.domain.Vehicle;
import com.example.scheduler.repository.CustomerRepository;
import com.example.scheduler.repository.DealershipRepository;
import com.example.scheduler.repository.ServiceBayRepository;
import com.example.scheduler.repository.ServiceTypeRepository;
import com.example.scheduler.repository.TechnicianRepository;
import com.example.scheduler.repository.TechnicianSkillRepository;
import com.example.scheduler.repository.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seedDemoData(
            DealershipRepository dealershipRepository,
            ServiceBayRepository serviceBayRepository,
            ServiceTypeRepository serviceTypeRepository,
            TechnicianRepository technicianRepository,
            TechnicianSkillRepository technicianSkillRepository,
            CustomerRepository customerRepository,
            VehicleRepository vehicleRepository
    ) {
        return args -> {
            if (dealershipRepository.count() > 0) {
                return;
            }

            ServiceType oilChange = saveServiceType(serviceTypeRepository, "Oil Change", 60);
            ServiceType brakeService = saveServiceType(serviceTypeRepository, "Brake Service", 120);
            ServiceType tireRotation = saveServiceType(serviceTypeRepository, "Tire Rotation", 45);
            ServiceType fullInspection = saveServiceType(serviceTypeRepository, "Full Inspection", 90);

            Dealership hcmDealership = saveDealership(dealershipRepository, "Keyloop Demo Dealership", "Asia/Ho_Chi_Minh");
            Dealership hanoiDealership = saveDealership(dealershipRepository, "Keyloop Hanoi Center", "Asia/Ho_Chi_Minh");

            saveBay(serviceBayRepository, hcmDealership, "Bay-1");
            saveBay(serviceBayRepository, hcmDealership, "Bay-2");
            saveBay(serviceBayRepository, hcmDealership, "Bay-3");
            saveBay(serviceBayRepository, hanoiDealership, "Bay-A");
            saveBay(serviceBayRepository, hanoiDealership, "Bay-B");

            Technician hcmTechA = saveTechnician(technicianRepository, hcmDealership, "Tech-A");
            Technician hcmTechB = saveTechnician(technicianRepository, hcmDealership, "Tech-B");
            Technician hcmTechC = saveTechnician(technicianRepository, hcmDealership, "Tech-C");
            Technician hanoiTechD = saveTechnician(technicianRepository, hanoiDealership, "Tech-D");
            Technician hanoiTechE = saveTechnician(technicianRepository, hanoiDealership, "Tech-E");

            saveSkills(technicianSkillRepository, hcmTechA, oilChange, brakeService, tireRotation, fullInspection);
            saveSkills(technicianSkillRepository, hcmTechB, oilChange, tireRotation);
            saveSkills(technicianSkillRepository, hcmTechC, fullInspection);
            saveSkills(technicianSkillRepository, hanoiTechD, oilChange, brakeService, tireRotation);
            saveSkills(technicianSkillRepository, hanoiTechE, oilChange, fullInspection);

            Customer alice = saveCustomer(customerRepository, "Alice Nguyen", "alice@example.com", "+84901234567");
            saveVehicle(vehicleRepository, alice, "VIN123456789", "Toyota", "Camry");
            saveVehicle(vehicleRepository, alice, "VIN123456790", "Honda", "Civic");

            Customer bob = saveCustomer(customerRepository, "Bob Tran", "bob@example.com", "+84907654321");
            saveVehicle(vehicleRepository, bob, "VIN223456789", "Ford", "Ranger");

            Customer carol = saveCustomer(customerRepository, "Carol Le", "carol@example.com", "+84913456789");
            saveVehicle(vehicleRepository, carol, "VIN323456789", "Mazda", "CX-5");
            saveVehicle(vehicleRepository, carol, "VIN323456790", "Hyundai", "Tucson");

            Customer david = saveCustomer(customerRepository, "David Pham", "david@example.com", "+84924567890");
            saveVehicle(vehicleRepository, david, "VIN423456789", "BMW", "320i");

            Customer emma = saveCustomer(customerRepository, "Emma Vo", "emma@example.com", "+84935678901");
            saveVehicle(vehicleRepository, emma, "VIN523456789", "Mercedes-Benz", "C200");

            log.info(
                    "Seeded 2 dealerships, 5 service bays, 5 technicians, 4 service types, 5 customers, and 7 vehicles"
            );
        };
    }

    private Dealership saveDealership(DealershipRepository repository, String name, String timezone) {
        Dealership dealership = new Dealership();
        dealership.setName(name);
        dealership.setTimezone(timezone);
        return repository.save(dealership);
    }

    private ServiceType saveServiceType(ServiceTypeRepository repository, String name, int durationMinutes) {
        ServiceType serviceType = new ServiceType();
        serviceType.setName(name);
        serviceType.setDurationMinutes(durationMinutes);
        return repository.save(serviceType);
    }

    private void saveBay(ServiceBayRepository repository, Dealership dealership, String name) {
        ServiceBay bay = new ServiceBay();
        bay.setDealership(dealership);
        bay.setName(name);
        repository.save(bay);
    }

    private Technician saveTechnician(TechnicianRepository repository, Dealership dealership, String name) {
        Technician technician = new Technician();
        technician.setDealership(dealership);
        technician.setName(name);
        return repository.save(technician);
    }

    private void saveSkills(
            TechnicianSkillRepository repository,
            Technician technician,
            ServiceType... serviceTypes
    ) {
        for (ServiceType serviceType : serviceTypes) {
            repository.save(skill(technician, serviceType));
        }
    }

    private Customer saveCustomer(CustomerRepository repository, String name, String email, String phone) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setEmail(email);
        customer.setPhone(phone);
        return repository.save(customer);
    }

    private void saveVehicle(
            VehicleRepository repository,
            Customer customer,
            String vin,
            String make,
            String model
    ) {
        Vehicle vehicle = new Vehicle();
        vehicle.setCustomer(customer);
        vehicle.setVin(vin);
        vehicle.setMake(make);
        vehicle.setModel(model);
        repository.save(vehicle);
    }

    private TechnicianSkill skill(Technician technician, ServiceType serviceType) {
        TechnicianSkill technicianSkill = new TechnicianSkill();
        technicianSkill.setTechnician(technician);
        technicianSkill.setServiceType(serviceType);
        return technicianSkill;
    }
}
