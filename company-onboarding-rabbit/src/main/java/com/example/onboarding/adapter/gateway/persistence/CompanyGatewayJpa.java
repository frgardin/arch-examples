package com.example.onboarding.adapter.gateway.persistence;

import com.example.onboarding.adapter.gateway.persistence.mapper.CompanyJpaMapper;
import com.example.onboarding.adapter.gateway.persistence.repository.CompanyJpaRepository;
import com.example.onboarding.adapter.gateway.persistence.repository.DepartmentJpaRepository;
import com.example.onboarding.adapter.gateway.persistence.repository.EmployeeJpaRepository;
import com.example.onboarding.adapter.gateway.persistence.repository.OfficeJpaRepository;
import com.example.onboarding.adapter.gateway.persistence.repository.ProjectJpaRepository;
import com.example.onboarding.adapter.gateway.persistence.repository.RoomJpaRepository;
import com.example.onboarding.adapter.gateway.persistence.repository.TaskJpaRepository;
import com.example.onboarding.entity.Company;
import com.example.onboarding.entity.Department;
import com.example.onboarding.entity.Employee;
import com.example.onboarding.entity.Office;
import com.example.onboarding.entity.Project;
import com.example.onboarding.entity.Room;
import com.example.onboarding.entity.Task;
import com.example.onboarding.usecase.gateway.CompanyGateway;
import com.example.onboarding.usecase.gateway.DuplicateCompanyNameException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CompanyGatewayJpa implements CompanyGateway {

    private final CompanyJpaRepository companies;
    private final DepartmentJpaRepository departments;
    private final EmployeeJpaRepository employees;
    private final OfficeJpaRepository offices;
    private final RoomJpaRepository rooms;
    private final ProjectJpaRepository projects;
    private final TaskJpaRepository tasks;
    private final CompanyJpaMapper mapper;

    public CompanyGatewayJpa(CompanyJpaRepository companies,
                             DepartmentJpaRepository departments,
                             EmployeeJpaRepository employees,
                             OfficeJpaRepository offices,
                             RoomJpaRepository rooms,
                             ProjectJpaRepository projects,
                             TaskJpaRepository tasks,
                             CompanyJpaMapper mapper) {
        this.companies = companies;
        this.departments = departments;
        this.employees = employees;
        this.offices = offices;
        this.rooms = rooms;
        this.projects = projects;
        this.tasks = tasks;
        this.mapper = mapper;
    }

    @Override
    public Company saveCompany(Company company) {
        try {
            return mapper.toDomain(companies.save(mapper.toJpa(company)));
        } catch (DataIntegrityViolationException e) {
            String msg = e.getMostSpecificCause().getMessage();
            if (msg != null && msg.contains("company_name_key")) {
                throw new DuplicateCompanyNameException(company.name());
            }
            throw e;
        }
    }

    @Override
    public List<Department> saveDepartments(List<Department> list) {
        return mapper.toDepartmentDomain(departments.saveAll(mapper.toDepartmentJpa(list)));
    }

    @Override
    public List<Employee> saveEmployees(List<Employee> list) {
        return mapper.toEmployeeDomain(employees.saveAll(mapper.toEmployeeJpa(list)));
    }

    @Override
    public List<Office> saveOffices(List<Office> list) {
        return mapper.toOfficeDomain(offices.saveAll(mapper.toOfficeJpa(list)));
    }

    @Override
    public List<Room> saveRooms(List<Room> list) {
        return mapper.toRoomDomain(rooms.saveAll(mapper.toRoomJpa(list)));
    }

    @Override
    public List<Project> saveProjects(List<Project> list) {
        return mapper.toProjectDomain(projects.saveAll(mapper.toProjectJpa(list)));
    }

    @Override
    public List<Task> saveTasks(List<Task> list) {
        return mapper.toTaskDomain(tasks.saveAll(mapper.toTaskJpa(list)));
    }
}
