package com.example.onboarding.usecase.onboard.step;

import com.example.onboarding.entity.Office;
import com.example.onboarding.entity.Room;
import com.example.onboarding.usecase.gateway.CompanyGateway;
import com.example.onboarding.usecase.onboard.OnboardCompanyInput;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CreateOfficesStep extends CreationStep {

    private final CompanyGateway companyGateway;

    public CreateOfficesStep(CompanyGateway companyGateway) {
        this.companyGateway = companyGateway;
    }

    @Override public int order() { return 3; }
    @Override public String name() { return "create-offices"; }

    @Override
    protected void doExecute(CreationStepContext ctx) {
        List<OnboardCompanyInput.OfficeInput> inputs = ctx.input().offices();

        List<Office> offices = inputs.stream()
                .map(o -> Office.create(ctx.companyId(), o.name(), o.city()))
                .toList();
        List<Office> saved = companyGateway.saveOffices(offices);

        List<Room> rooms = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            UUID officeId = saved.get(i).id();
            for (OnboardCompanyInput.RoomInput r : inputs.get(i).rooms()) {
                rooms.add(Room.create(officeId, r.floor(), r.number()));
            }
        }
        if (!rooms.isEmpty()) {
            companyGateway.saveRooms(rooms);
        }
    }
}
