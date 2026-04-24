package com.example.onboarding.adapter.gateway.persistence.repository;

import com.example.onboarding.adapter.gateway.persistence.model.CompanyJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyJpaRepository extends JpaRepository<CompanyJpa, UUID> { }
