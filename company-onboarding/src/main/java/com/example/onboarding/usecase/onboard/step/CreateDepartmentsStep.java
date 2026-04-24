package com.example.onboarding.usecase.onboard.step;

import com.example.onboarding.entity.Department;
import com.example.onboarding.entity.Employee;
import com.example.onboarding.usecase.gateway.CompanyGateway;
import com.example.onboarding.usecase.onboard.OnboardCompanyInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates all departments and their employees together so we can pair each
 * generated department id with its input's employees without crossing step
 * boundaries. Each step commits independently (REQUIRES_NEW) — cleaner than
 * returning persisted entities through the context map.
 */
@Component
public class CreateDepartmentsStep extends CreationStep {

    private final CompanyGateway companyGateway;

    public CreateDepartmentsStep(CompanyGateway companyGateway) {
        this.companyGateway = companyGateway;
    }

    @Override public int order() { return 2; }
    @Override public String name() { return "create-departments"; }

    @Override
    protected void doExecute(CreationStepContext ctx) {
        List<OnboardCompanyInput.DepartmentInput> inputs = ctx.input().departments();

        List<Department> departments = inputs.stream()
                .map(d -> Department.create(ctx.companyId(), d.name()))
                .toList();
        List<Department> saved = companyGateway.saveDepartments(departments);

        List<Employee> employees = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            UUID deptId = saved.get(i).id();
            for (OnboardCompanyInput.EmployeeInput e : inputs.get(i).employees()) {
                employees.add(Employee.create(deptId, e.name(), e.email()));
            }
        }
        if (!employees.isEmpty()) {
            companyGateway.saveEmployees(employees);
        }
    }
}
