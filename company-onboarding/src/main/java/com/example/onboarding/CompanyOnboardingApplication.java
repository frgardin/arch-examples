package com.example.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class CompanyOnboardingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanyOnboardingApplication.class, args);
    }
}
