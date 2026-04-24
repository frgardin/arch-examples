package com.example.onboarding.adapter.gateway.persistence.mapper;

import com.example.onboarding.adapter.gateway.persistence.model.CompanyJpa;
import com.example.onboarding.adapter.gateway.persistence.model.DepartmentJpa;
import com.example.onboarding.adapter.gateway.persistence.model.EmployeeJpa;
import com.example.onboarding.adapter.gateway.persistence.model.OfficeJpa;
import com.example.onboarding.adapter.gateway.persistence.model.ProjectJpa;
import com.example.onboarding.adapter.gateway.persistence.model.RoomJpa;
import com.example.onboarding.adapter.gateway.persistence.model.TaskJpa;
import com.example.onboarding.entity.Company;
import com.example.onboarding.entity.Department;
import com.example.onboarding.entity.Employee;
import com.example.onboarding.entity.Office;
import com.example.onboarding.entity.Project;
import com.example.onboarding.entity.Room;
import com.example.onboarding.entity.Task;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CompanyJpaMapper {

    // ---- Company ----
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    CompanyJpa toJpa(Company company);
    Company toDomain(CompanyJpa jpa);

    // ---- Department ----
    @Mapping(target = "createdAt", ignore = true)
    DepartmentJpa toJpa(Department department);
    Department toDomain(DepartmentJpa jpa);
    List<DepartmentJpa> toDepartmentJpa(List<Department> source);
    List<Department>    toDepartmentDomain(List<DepartmentJpa> source);

    // ---- Employee ----
    @Mapping(target = "createdAt", ignore = true)
    EmployeeJpa toJpa(Employee employee);
    Employee toDomain(EmployeeJpa jpa);
    List<EmployeeJpa> toEmployeeJpa(List<Employee> source);
    List<Employee>    toEmployeeDomain(List<EmployeeJpa> source);

    // ---- Office ----
    @Mapping(target = "createdAt", ignore = true)
    OfficeJpa toJpa(Office office);
    Office toDomain(OfficeJpa jpa);
    List<OfficeJpa> toOfficeJpa(List<Office> source);
    List<Office>    toOfficeDomain(List<OfficeJpa> source);

    // ---- Room ----
    @Mapping(target = "createdAt", ignore = true)
    RoomJpa toJpa(Room room);
    Room toDomain(RoomJpa jpa);
    List<RoomJpa> toRoomJpa(List<Room> source);
    List<Room>    toRoomDomain(List<RoomJpa> source);

    // ---- Project ----
    @Mapping(target = "createdAt", ignore = true)
    ProjectJpa toJpa(Project project);
    Project toDomain(ProjectJpa jpa);
    List<ProjectJpa> toProjectJpa(List<Project> source);
    List<Project>    toProjectDomain(List<ProjectJpa> source);

    // ---- Task ----
    @Mapping(target = "createdAt", ignore = true)
    TaskJpa toJpa(Task task);
    Task toDomain(TaskJpa jpa);
    List<TaskJpa> toTaskJpa(List<Task> source);
    List<Task>    toTaskDomain(List<TaskJpa> source);
}
