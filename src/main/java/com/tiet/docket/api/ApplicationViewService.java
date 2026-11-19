package com.tiet.docket.api;

import com.tiet.docket.api.ApiDtos.Actions;
import com.tiet.docket.api.ApiDtos.ApplicationDetail;
import com.tiet.docket.api.ApiDtos.ApplicationSummary;
import com.tiet.docket.api.ApiDtos.AppointmentDto;
import com.tiet.docket.api.ApiDtos.DocumentDto;
import com.tiet.docket.api.ApiDtos.FeeDto;
import com.tiet.docket.api.ApiDtos.HistoryDto;
import com.tiet.docket.api.ApiDtos.PaymentDto;
import com.tiet.docket.api.ApiDtos.RequiredDocDto;
import com.tiet.docket.api.ApiDtos.StageDto;
import com.tiet.docket.api.ApiDtos.SupervisorDto;
import com.tiet.docket.api.ApiDtos.UserDto;
import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationDocument;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.DecisionHistory;
import com.tiet.docket.model.DecisionType;
import com.tiet.docket.model.FeePayment;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Stage;
import com.tiet.docket.model.SupervisorApproval;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationDocumentRepository;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.AppointmentRepository;
import com.tiet.docket.repository.DecisionHistoryRepository;
import com.tiet.docket.repository.FeePaymentRepository;
import com.tiet.docket.repository.PaymentRecordRepository;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.StageRepository;
import com.tiet.docket.repository.SupervisorApprovalRepository;
import com.tiet.docket.repository.UserRepository;
import com.tiet.docket.workflow.AppointmentService;
import com.tiet.docket.workflow.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the application detail view and decides who may see it (FR3, FR10, QR14).
 */
@Service
public class ApplicationViewService {

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final DecisionHistoryRepository historyRepository;
    private final PaymentRecordRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final StageRepository stageRepository;
    private final WorkflowService workflowService;
    private final AppointmentService appointmentService;
    private final FeePaymentRepository feePaymentRepository;
    private final SupervisorApprovalRepository supervisorApprovalRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApplicationViewService(UserRepository userRepository,
                                  ApplicationRepository applicationRepository,
                                  ApplicationDocumentRepository documentRepository,
                                  DecisionHistoryRepository historyRepository,
                                  PaymentRecordRepository paymentRepository,
                                  AppointmentRepository appointmentRepository,
                                  RequiredDocumentRepository requiredDocumentRepository,
                                  StageRepository stageRepository,
                                  WorkflowService workflowService,
                                  AppointmentService appointmentService,
                                  FeePaymentRepository feePaymentRepository,
                                  SupervisorApprovalRepository supervisorApprovalRepository) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.paymentRepository = paymentRepository;
        this.appointmentRepository = appointmentRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.stageRepository = stageRepository;
        this.workflowService = workflowService;
        this.appointmentService = appointmentService;
        this.feePaymentRepository = feePaymentRepository;
        this.supervisorApprovalRepository = supervisorApprovalRepository;
    }

    public User currentUser(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    // Loads an application the viewer may see; anything else looks like "not found"
    public Application loadVisible(Long id, User viewer) {
        return applicationRepository.findById(id)
                .filter(app -> canView(app, viewer))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    // Owner, the officer it is assigned to, an officer who already decided on it, Finance for any
    // paid fee (the parallel fee check), or the Administrator
    public boolean canView(Application app, User viewer) {
        return switch (viewer.getRole()) {
            case STUDENT -> app.getStudent().getId().equals(viewer.getId());
            case ADMINISTRATOR -> true;
            case FINANCE_OFFICER -> workflowService.canAct(app, viewer) || paidFee(app).isPresent()
                    || decidedBefore(app, viewer);
            default -> workflowService.canAct(app, viewer) || decidedBefore(app, viewer);
        };
    }

    // FR10: files are only for the owner and the officer currently handling the application.
    // The fee receipt goes to Finance instead; the Administrative Officer never sees it.
    public boolean canDownload(ApplicationDocument doc, User viewer) {
        Application app = doc.getApplication();
        if (app.getStudent().getId().equals(viewer.getId())) {
            return true;
        }
        return doc.isFeeReceipt()
                ? viewer.getRole() == Role.FINANCE_OFFICER
                : workflowService.canAct(app, viewer);
    }

    private boolean decidedBefore(Application app, User viewer) {
        return historyRepository.findByApplicationOrderByDecidedAtAsc(app).stream()
                .anyMatch(h -> h.getOfficer() != null && h.getOfficer().getId().equals(viewer.getId()));
    }

    private Optional<FeePayment> paidFee(Application app) {
        return feePaymentRepository.findByApplication(app).filter(p -> p.getStatus() == FeePayment.Status.PAID);
    }

    @Transactional(readOnly = true)
    public ApplicationDetail detail(Application app, User viewer) {
        List<DecisionHistory> history = historyRepository.findByApplicationOrderByDecidedAtAsc(app);
        boolean owner = app.getStudent().getId().equals(viewer.getId());
        boolean canAct = viewer.getRole() != Role.STUDENT && workflowService.canAct(app, viewer);
        Stage current = app.getCurrentStage();

        String nextName = null;
        boolean nextOptional = false;
        if (canAct) {
            Optional<Stage> next = workflowService.nextStage(current, true);
            nextName = next.map(Stage::getName).orElse(null);
            nextOptional = next.map(Stage::isOptional).orElse(false);
        }

        // The latest return message tells the student what to fix
        String pending = null;
        if (app.getStatus() == ApplicationStatus.AWAITING_CORRECTION) {
            for (int i = history.size() - 1; i >= 0; i--) {
                DecisionType d = history.get(i).getDecision();
                if (d == DecisionType.CORRECTION_REQUESTED || d == DecisionType.ISSUE_FOUND) {
                    pending = history.get(i).getReason();
                    break;
                }
            }
        }

        Optional<FeePayment> fee = paidFee(app);
        Optional<SupervisorApproval> supervisor = supervisorApprovalRepository.findByApplication(app);
        Actions actions = new Actions(canAct,
                owner && app.getStatus() == ApplicationStatus.AWAITING_CORRECTION,
                canAct && current.getHandlerRole() == Role.ACCOUNTS_OFFICER,
                nextName, nextOptional,
                owner && appointmentService.needsVisit(app)
                        && appointmentRepository.findByApplication(app).isEmpty(),
                owner && app.getStatus() == ApplicationStatus.PAYMENT_PENDING,
                viewer.getRole() == Role.FINANCE_OFFICER
                        && fee.map(p -> p.getVerification() == FeePayment.Verification.PENDING).orElse(false),
                owner && app.getStatus() == ApplicationStatus.SUPERVISOR_PENDING);
        return new ApplicationDetail(
                ApplicationSummary.of(app),
                app.getService().getDescription(),
                UserDto.of(app.getStudent()),
                parseFormData(app.getFormData()),
                documentRepository.findByApplication(app).stream()
                        .filter(doc -> canDownload(doc, viewer)).map(DocumentDto::of).toList(),
                history.stream().map(HistoryDto::of).toList(),
                stageRepository.findByServiceOrderByStepOrderAsc(app.getService()).stream().map(StageDto::of).toList(),
                paymentRepository.findByApplication(app).map(PaymentDto::of).orElse(null),
                appointmentRepository.findByApplication(app).map(AppointmentDto::of).orElse(null),
                pending,
                requiredDocumentRepository.findByService(app.getService()).stream().map(RequiredDocDto::of).toList(),
                app.getService().getFeeAmount(),
                fee.map(FeeDto::of).orElse(null),
                supervisor.map(SupervisorDto::of).orElse(null),
                actions);
    }

    public Map<String, String> parseFormData(String json) {
        Map<String, String> data = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return data;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            root.properties().forEach(e -> data.put(e.getKey(), e.getValue().asText()));
        } catch (RuntimeException e) {
            data.put("details", json);   // older rows may not be valid JSON; show them as-is
        }
        return data;
    }
}
