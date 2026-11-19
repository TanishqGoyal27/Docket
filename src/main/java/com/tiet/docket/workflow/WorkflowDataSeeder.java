package com.tiet.docket.workflow;

import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.Stage;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.StageRepository;
import com.tiet.docket.student.SubmissionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component   // not Spring's @Service, so no name clash with our Service entity
public class WorkflowDataSeeder implements CommandLineRunner {

    private final ServiceRepository serviceRepository;
    private final StageRepository stageRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;

    public WorkflowDataSeeder(ServiceRepository serviceRepository,
                              StageRepository stageRepository,
                              RequiredDocumentRepository requiredDocumentRepository) {
        this.serviceRepository = serviceRepository;
        this.stageRepository = stageRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
    }

    @Override
    public void run(String... args) {

        // ----------------------------------------------------------------
        // 1.  Seed the three workflows (services + stages).
        //     createWorkflow() is idempotent: it does nothing if the
        //     service already exists.
        // ----------------------------------------------------------------
        createWorkflow("ID Card",
                "Apply for a new or replacement student ID card",
                // The fee is paid online and checked by Finance in parallel (PaymentService),
                // so it is not a stage; the final approval waits for that check
                stage("Document verification and approval", Role.ADMIN_OFFICER));

        createWorkflow("Reimbursement",
                "Claim reimbursement for project expenses",
                // The supervising teacher approves by email first (SupervisorApprovalService)
                stage("HOD approval", Role.HOD),
                stage("Bill verification", Role.FINANCE_OFFICER),
                stage("Payment processing", Role.ACCOUNTS_OFFICER));

        createWorkflow("Details Correction",
                "Request a correction to your student record",
                stage("Initial review", Role.ADMIN_OFFICER),
                // SRS Appendix A point 4: authorised staff = an Administrative Officer
                stage("Correction approval and record update", Role.ADMIN_OFFICER));

        // Release 2 services run on the same engine; their outcome is "Verified"
        createWorkflow("Document Verification",
                "Submit documents (certificates, transcripts, etc.) for official verification",
                stage("Document review", Role.ADMIN_OFFICER));

        Stage financeReview = stage("Finance review (where required)", Role.FINANCE_OFFICER);
        financeReview.setOptional(true);
        createWorkflow("Scholarship Verification",
                "Submit scholarship documents for eligibility verification. Verified does not mean approved or paid.",
                stage("Eligibility verification", Role.SCHOLARSHIP_OFFICER),
                financeReview);

        // Details Correction updates student profile upon final approval
        serviceRepository.findByName("Details Correction").ifPresent(service -> {
            if (!service.isUpdatesStudentRecord()) {
                service.setUpdatesStudentRecord(true);
                serviceRepository.save(service);
            }
        });
        serviceRepository.findByName("ID Card").ifPresent(service -> {
            service.setRequiresVisit(true);   // approved cards are collected in person
            service.setFeeAmount(200);        // paid online while applying
            serviceRepository.save(service);
        });
        serviceRepository.findByName("Reimbursement").ifPresent(service -> {
            service.setRequiresSupervisorApproval(true);
            serviceRepository.save(service);
        });
        for (String name : new String[] {"Document Verification", "Scholarship Verification"}) {
            serviceRepository.findByName(name).ifPresent(service -> {
                service.setFinalStatus(ApplicationStatus.VERIFIED);
                serviceRepository.save(service);
            });
        }

        // ----------------------------------------------------------------
        // 2.  Seed required documents for each service.
        //     addDocument() is idempotent: it skips if the document
        //     already exists for that service.
        // ----------------------------------------------------------------

        // ID Card: 2 documents (1 mandatory, 1 optional). The fee receipt is generated after payment.
        Service idCard = serviceRepository.findByName("ID Card").orElseThrow();
        addDocument(idCard, "Passport-size photo", true);
        // Optional in general, but SubmissionService requires it when the card was lost
        addDocument(idCard, SubmissionService.FIR_DOCUMENT, false);

        // Reimbursement: 3 mandatory documents
        Service reimbursement = serviceRepository.findByName("Reimbursement").orElseThrow();
        addDocument(reimbursement, "Original expense bills", true);
        addDocument(reimbursement, "Project approval letter", true);
        addDocument(reimbursement, "Bank account details", true);

        // Details Correction: 1 mandatory document
        Service detailsCorrection = serviceRepository.findByName("Details Correction").orElseThrow();
        addDocument(detailsCorrection, "Supporting proof document", true);

        Service documentVerification = serviceRepository.findByName("Document Verification").orElseThrow();
        addDocument(documentVerification, "Document to be verified", true);
        addDocument(documentVerification, "Additional supporting document", false);

        Service scholarship = serviceRepository.findByName("Scholarship Verification").orElseThrow();
        addDocument(scholarship, "Income certificate", true);
        addDocument(scholarship, "Previous semester marksheet", true);
        addDocument(scholarship, "Category certificate (if applicable)", false);
    }

    // ----------------------------------------------------------------
    // Creates a service and its stages, numbering the steps 1, 2, 3...
    // Skips entirely if a service with this name already exists.
    // ----------------------------------------------------------------
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

    // ----------------------------------------------------------------
    // Helper: adds a required document for a service.
    // Skips if this document name already exists for the same service,
    // so running the seeder twice never creates duplicate rows.
    // ----------------------------------------------------------------
    private void addDocument(Service service, String name, boolean mandatory) {
        if (requiredDocumentRepository.existsByServiceAndDocumentName(service, name)) {
            return;   // already seeded, nothing to do
        }
        RequiredDocument doc = new RequiredDocument();
        doc.setService(service);
        doc.setDocumentName(name);
        doc.setMandatory(mandatory);
        requiredDocumentRepository.save(doc);
    }

    // ----------------------------------------------------------------
    // Builds an unsaved Stage object with a name and handler role.
    // The service and step-order are set later inside createWorkflow().
    // ----------------------------------------------------------------
    private Stage stage(String name, Role handlerRole) {
        Stage stage = new Stage();
        stage.setName(name);
        stage.setHandlerRole(handlerRole);
        return stage;
    }
}