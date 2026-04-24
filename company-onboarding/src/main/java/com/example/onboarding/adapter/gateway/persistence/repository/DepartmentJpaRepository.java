package com.example.onboarding.adapter.gateway.persistence.repository;

import com.example.onboarding.adapter.gateway.persistence.model.DepartmentJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DepartmentJpaRepository extends JpaRepository<DepartmentJpa, UUID> { }
