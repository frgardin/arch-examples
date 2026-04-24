package com.example.onboarding.adapter.controller.request;

import com.example.onboarding.usecase.onboard.OnboardCompanyInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * HTTP shape. Separate from OnboardCompanyInput so controller-level validation annotations
 * never leak into Ring 2.
 */
public record OnboardCompanyRequest(
        @NotBlank String name,
        @NotBlank String taxId,
        @NotNull @Valid List<Department> departments,
        @NotNull @Valid List<Office> offices,
        @NotNull @Valid List<Project> projects
) {
    public record Department(@NotBlank String name,
                             @NotNull @Valid List<Employee> employees) { }

    public record Employee(@NotBlank String name,
                           @Email @NotBlank String email) { }

    public record Office(@NotBlank String name,
                         @NotBlank String city,
                         @NotNull @Valid List<Room> rooms) { }

    public record Room(@PositiveOrZero int floor,
                       @NotBlank String number) { }

    public record Project(@NotBlank String name,
                          @NotNull @Valid List<Task> tasks) { }

    public record Task(@NotBlank String title, String description) { }

    public OnboardCompanyInput toInput(String idempotencyKey) {
        return new OnboardCompanyInput(
                idempotencyKey,
                name,
                taxId,
                departments.stream()
                        .map(d -> new OnboardCompanyInput.DepartmentInput(
                                d.name(),
                                d.employees().stream()
                                        .map(e -> new OnboardCompanyInput.EmployeeInput(e.name(), e.email()))
                                        .toList()))
                        .toList(),
                offices.stream()
                        .map(o -> new OnboardCompanyInput.OfficeInput(
                                o.name(), o.city(),
                                o.rooms().stream()
                                        .map(r -> new OnboardCompanyInput.RoomInput(r.floor(), r.number()))
                                        .toList()))
                        .toList(),
                projects.stream()
                        .map(p -> new OnboardCompanyInput.ProjectInput(
                                p.name(),
                                p.tasks().stream()
                                        .map(t -> new OnboardCompanyInput.TaskInput(t.title(), t.description()))
                                        .toList()))
                        .toList()
        );
    }
}
