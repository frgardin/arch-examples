package com.example.onboarding.adapter.gateway.persistence.repository;

import com.example.onboarding.adapter.gateway.persistence.model.ProjectJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<ProjectJpa, UUID> { }
