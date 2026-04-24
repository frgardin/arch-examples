package com.example.onboarding.usecase.gateway;

/** Signaled by CompanyGateway when the company name already exists. */
public class DuplicateCompanyNameException extends RuntimeException {
    public DuplicateCompanyNameException(String name) {
        super("Company already exists: " + name);
    }
}
