package com.tiet.docket.api;

import com.tiet.docket.api.ApiDtos.ApplicationSummary;
import com.tiet.docket.api.ApiDtos.AppointmentDto;
import com.tiet.docket.api.ApiDtos.DecisionRequest;
import com.tiet.docket.api.ApiDtos.FeeCheckRequest;
import com.tiet.docket.api.ApiDtos.FeeDto;
import com.tiet.docket.api.ApiDtos.PaymentRequest;
import com.tiet.docket.model.DecisionHistory;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.DecisionHistoryRepository;
import com.tiet.docket.payment.PaymentService;
import com.tiet.docket.workflow.AppointmentService;
import com.tiet.docket.workflow.WorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Officer side: pending queue, decisions, payment recording, history, visits (UC-07, FR17-FR27). */
@RestController
@RequestMapping("/api/staff")
public class StaffApiController {

    private final ApplicationViewService viewService;
    private final WorkflowService workflowService;
    private final DecisionHistoryRepository historyRepository;
    private final AppointmentService appointmentService;
    private final PaymentService paymentService;

    public StaffApiController(ApplicationViewService viewService, WorkflowService workflowService,
                              DecisionHistoryRepository historyRepository,
                              AppointmentService appointmentService,
                              PaymentService paymentService) {
        this.viewService = viewService;
        this.workflowService = workflowService;
        this.historyRepository = historyRepository;
        this.appointmentService = appointmentService;
        this.paymentService = paymentService;
    }

    @GetMapping("/queue")
    public List<ApplicationSummary> queue(Authentication auth) {
        return workflowService.getPendingFor(viewService.currentUser(auth)).stream()
                .map(ApplicationSummary::of).toList();
    }

    // Applications this officer has already acted on (FR23)
    @GetMapping("/history")
    public List<ApplicationSummary> history(Authentication auth) {
        return historyRepository.findByOfficerOrderByDecidedAtDesc(viewService.currentUser(auth)).stream()
                .map(DecisionHistory::getApplication)
                .distinct()
                .map(ApplicationSummary::of)
                .toList();
    }

    @PostMapping("/applications/{id}/approve")
    public ApplicationSummary approve(@PathVariable Long id, @RequestBody(required = false) DecisionRequest body,
                                      Authentication auth) {
        boolean forward = body == null || body.forward() == null || body.forward();
        return ApplicationSummary.of(workflowService.approve(id, viewService.currentUser(auth), forward));
    }

    @PostMapping("/applications/{id}/reject")
    public ApplicationSummary reject(@PathVariable Long id, @RequestBody DecisionRequest body, Authentication auth) {
        return ApplicationSummary.of(workflowService.reject(id, viewService.currentUser(auth), body.reason()));
    }

    @PostMapping("/applications/{id}/correction")
    public ApplicationSummary correction(@PathVariable Long id, @RequestBody DecisionRequest body,
                                         Authentication auth) {
        return ApplicationSummary.of(
                workflowService.requestCorrection(id, viewService.currentUser(auth), body.reason()));
    }

    @PostMapping("/applications/{id}/payment")
    public ApplicationSummary payment(@PathVariable Long id, @RequestBody PaymentRequest body, Authentication auth) {
        return ApplicationSummary.of(workflowService.recordPaymentAndProcess(
                id, viewService.currentUser(auth), body.reference(), body.date()));
    }

    // Finance cross-check: every paid application fee, straight from the payment records
    @GetMapping("/fees")
    public List<FeeDto> fees(Authentication auth) {
        if (viewService.currentUser(auth).getRole() != Role.FINANCE_OFFICER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return paymentService.paidFees().stream().map(FeeDto::of).toList();
    }

    @PostMapping("/applications/{id}/fee-check")
    public FeeDto feeCheck(@PathVariable Long id, @RequestBody FeeCheckRequest body, Authentication auth) {
        return FeeDto.of(paymentService.verifyFee(id, viewService.currentUser(auth),
                Boolean.TRUE.equals(body.found()), body.remark()));
    }

    // Upcoming in-person visits, for the Administrative Officer who meets the students
    @GetMapping("/appointments")
    public List<AppointmentDto> appointments(Authentication auth) {
        User officer = viewService.currentUser(auth);
        if (officer.getRole() != Role.ADMIN_OFFICER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return appointmentService.upcoming().stream().map(AppointmentDto::of).toList();
    }
}
