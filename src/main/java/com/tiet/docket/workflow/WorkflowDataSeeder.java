package com.tiet.docket.workflow;

import com.tiet.docket.model.Role;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.Stage;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.StageRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component   // not Spring's @Service, so no name clash with our Service entity
public class WorkflowDataSeeder implements CommandLineRunner {

    private final ServiceRepository serviceRepository;
    private final StageRepository stageRepository;

    public WorkflowDataSeeder(ServiceRepository serviceRepository,
                              StageRepository stageRepository) {
        this.serviceRepository = serviceRepository;
        this.stageRepository = stageRepository;
    }

    @Override
    public void run(String... args) {

        createWorkflow("ID Card",
                "Apply for a new or replacement student ID card",
                stage("Document verification", Role.ADMIN_OFFICER),
                stage("Fee verification", Role.FINANCE_OFFICER),
                stage("Final approval", Role.ADMIN_OFFICER));

        createWorkflow("Reimbursement",
                "Claim reimbursement for project expenses",
                stage("Professor verification", Role.PROFESSOR),
                stage("HOD approval", Role.HOD),
                stage("Bill verification", Role.FINANCE_OFFICER),
                stage("Payment processing", Role.ACCOUNTS_OFFICER));

        createWorkflow("Details Correction",
                "Request a correction to your student record",
                stage("Initial review", Role.ADMIN_OFFICER),
                stage("Correction approval", Role.ADMINISTRATOR));
    }

    // Creates a service and its stages, numbering the steps 1, 2, 3...
    private void createWorkflow(String name, String description, Stage... steps) {
        if (serviceRepository.findByName(name).isPresent()) {
            return;   // already seeded
        }

        Service service = new Service();
        service.setName(name);
        service.setDescription(description);
        service = serviceRepository.save(service);

        int order = 1;
        for (Stage step : steps) {
            step.setService(service);
            step.setStepOrder(order++);
            stageRepository.save(step);
        }
    }

    private Stage stage(String name, Role handlerRole) {
        Stage stage = new Stage();
        stage.setName(name);
        stage.setHandlerRole(handlerRole);
        return stage;
    }
}