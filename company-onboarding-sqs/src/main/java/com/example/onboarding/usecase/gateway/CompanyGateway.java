package com.example.onboarding.usecase.gateway;

import com.example.onboarding.entity.Company;
import com.example.onboarding.entity.Department;
import com.example.onboarding.entity.Employee;
import com.example.onboarding.entity.Office;
import com.example.onboarding.entity.Project;
import com.example.onboarding.entity.Room;
import com.example.onboarding.entity.Task;

import java.util.List;

/**
 * Ring 2 output boundary: the use case layer owns this interface; its JPA implementation lives in Ring 3.
 * Shaped around the Company aggregate, not individual entities — Clean Architecture gateways align with
 * aggregate boundaries, not storage tables.
 */
public interface CompanyGateway {

    /** @throws DuplicateCompanyNameException if a company with the same name already exists. */
    Company saveCompany(Company company);

    List<Department> saveDepartments(List<Department> departments);

    List<Employee>   saveEmployees(List<Employee> employees);

    List<Office>     saveOffices(List<Office> offices);

    List<Room>       saveRooms(List<Room> rooms);

    List<Project>    saveProjects(List<Project> projects);

    List<Task>       saveTasks(List<Task> tasks);
}
