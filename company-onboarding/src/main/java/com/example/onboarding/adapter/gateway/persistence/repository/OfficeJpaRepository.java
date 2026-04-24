package com.example.onboarding.adapter.gateway.persistence.repository;

import com.example.onboarding.adapter.gateway.persistence.model.OfficeJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OfficeJpaRepository extends JpaRepository<OfficeJpa, UUID> { }
