package com.example.onboarding.usecase.onboard;

import java.util.List;

/**
 * Framework-free input to the use case. The controller maps the HTTP request to this shape,
 * so the interactor never sees a Jackson/Spring type.
 */
public record OnboardCompanyInput(
        String idempotencyKey,
        String companyName,
        String taxId,
        List<DepartmentInput> departments,
        List<OfficeInput> offices,
        List<ProjectInput> projects
) {
    public record DepartmentInput(String name, List<EmployeeInput> employees) { }
    public record EmployeeInput(String name, String email) { }
    public record OfficeInput(String name, String city, List<RoomInput> rooms) { }
    public record RoomInput(int floor, String number) { }
    public record ProjectInput(String name, List<TaskInput> tasks) { }
    public record TaskInput(String title, String description) { }
}
