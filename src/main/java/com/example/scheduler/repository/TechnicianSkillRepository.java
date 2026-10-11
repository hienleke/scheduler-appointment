package com.example.scheduler.repository;

import com.example.scheduler.domain.TechnicianSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TechnicianSkillRepository extends JpaRepository<TechnicianSkill, UUID> {
}
