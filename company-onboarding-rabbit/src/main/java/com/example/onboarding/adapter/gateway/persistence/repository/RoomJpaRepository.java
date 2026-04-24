package com.example.onboarding.adapter.gateway.persistence.repository;

import com.example.onboarding.adapter.gateway.persistence.model.RoomJpa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoomJpaRepository extends JpaRepository<RoomJpa, UUID> { }
