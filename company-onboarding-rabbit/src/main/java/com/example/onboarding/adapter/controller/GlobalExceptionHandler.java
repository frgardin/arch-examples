package com.example.onboarding.adapter.controller;

import com.example.onboarding.usecase.gateway.DuplicateCompanyNameException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateCompanyNameException.class)
    public ResponseEntity<Map<String, Object>> duplicate(DuplicateCompanyNameException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "duplicate_company",
                "message", e.getMessage()));
    }

    /**
     * The partial unique index uq_onboarding_job_active_company trips when two concurrent
     * onboardings target the same company name — surface as 409.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        HttpStatus status = msg != null && msg.contains("uq_onboarding_job_active_company")
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of(
                "error", "integrity_violation",
                "message", msg));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> missingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "missing_header",
                "message", e.getMessage()));
    }
}
