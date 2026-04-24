package com.example.onboarding.usecase.onboard.step;

import com.example.onboarding.entity.Project;
import com.example.onboarding.entity.Task;
import com.example.onboarding.usecase.gateway.CompanyGateway;
import com.example.onboarding.usecase.onboard.OnboardCompanyInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CreateProjectsStep extends CreationStep {

    private final CompanyGateway companyGateway;

    public CreateProjectsStep(CompanyGateway companyGateway) {
        this.companyGateway = companyGateway;
    }

    @Override public int order() { return 4; }
    @Override public String name() { return "create-projects"; }

    @Override
    protected void doExecute(CreationStepContext ctx) {
        List<OnboardCompanyInput.ProjectInput> inputs = ctx.input().projects();

        List<Project> projects = inputs.stream()
                .map(p -> Project.create(ctx.companyId(), p.name()))
                .toList();
        List<Project> saved = companyGateway.saveProjects(projects);

        List<Task> tasks = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            UUID projectId = saved.get(i).id();
            for (OnboardCompanyInput.TaskInput t : inputs.get(i).tasks()) {
                tasks.add(Task.create(projectId, t.title(), t.description()));
            }
        }
        if (!tasks.isEmpty()) {
            companyGateway.saveTasks(tasks);
        }
    }
}
