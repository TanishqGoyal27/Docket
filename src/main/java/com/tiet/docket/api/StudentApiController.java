package com.tiet.docket.api;

import com.tiet.docket.api.ApiDtos.ApplicationSummary;
import com.tiet.docket.api.ApiDtos.AppointmentDto;
import com.tiet.docket.api.ApiDtos.BookingRequest;
import com.tiet.docket.api.ApiDtos.ServiceDto;
import com.tiet.docket.api.ApiDtos.PaymentConfirmation;
import com.tiet.docket.model.Application;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.StageRepository;
import com.tiet.docket.payment.PaymentService;
import com.tiet.docket.payment.SandboxGateway;
import com.tiet.docket.student.SubmissionService;
import com.tiet.docket.workflow.AppointmentService;
import com.tiet.docket.workflow.SupervisorApprovalService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Student side: services, submission, tracking, resubmission, visit booking. */
@RestController
@RequestMapping("/api/student")
public class StudentApiController {

    private final ApplicationViewService viewService;
    private final ServiceRepository serviceRepository;
    private final StageRepository stageRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;
    private final ApplicationRepository applicationRepository;
    private final SubmissionService submissionService;
    private final AppointmentService appointmentService;
    private final PaymentService paymentService;
    private final SupervisorApprovalService supervisorApprovalService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StudentApiController(ApplicationViewService viewService,
                                ServiceRepository serviceRepository,
                                StageRepository stageRepository,
                                RequiredDocumentRepository requiredDocumentRepository,
                                ApplicationRepository applicationRepository,
                                SubmissionService submissionService,
                                AppointmentService appointmentService,
                                PaymentService paymentService,
                                SupervisorApprovalService supervisorApprovalService) {
        this.viewService = viewService;
        this.serviceRepository = serviceRepository;
        this.stageRepository = stageRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.applicationRepository = applicationRepository;
        this.submissionService = submissionService;
        this.appointmentService = appointmentService;
        this.paymentService = paymentService;
        this.supervisorApprovalService = supervisorApprovalService;
    }

    // Emails the teacher a new approval link (the previous link stops working)
    @PostMapping("/applications/{id}/supervisor/resend")
    public ApiDtos.SupervisorDto resendSupervisorEmail(@PathVariable Long id, Authentication auth) {
        return ApiDtos.SupervisorDto.of(supervisorApprovalService.resend(id, viewService.currentUser(auth)));
    }

    // Active services with their stages and required-document checklist (FR32)
    @GetMapping("/services")
    public List<ServiceDto> services() {
        return serviceRepository.findByActiveTrue().stream()
                .filter(s -> !stageRepository.findByServiceOrderByStepOrderAsc(s).isEmpty())
                .map(s -> ServiceDto.of(s, stageRepository.findByServiceOrderByStepOrderAsc(s),
                        requiredDocumentRepository.findByService(s)))
                .toList();
    }

    @GetMapping("/applications")
    public List<ApplicationSummary> myApplications(Authentication auth) {
        return applicationRepository.findByStudentOrderBySubmittedAtDesc(viewService.currentUser(auth))
                .stream().map(ApplicationSummary::of).toList();
    }

    // Multipart: "serviceId", "fields" (JSON object of form answers) and files named doc_{id}
    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationSummary submit(@RequestParam Long serviceId,
                                     @RequestParam(defaultValue = "{}") String fields,
                                     MultipartHttpServletRequest request, Authentication auth) {
        User student = viewService.currentUser(auth);
        Application saved = submissionService.submit(student, serviceId, parseFields(fields), request::getFile);
        return ApplicationSummary.of(saved);
    }

    @PostMapping("/applications/{id}/resubmit")
    public ApplicationSummary resubmit(@PathVariable Long id,
                                       @RequestParam(required = false) String note,
                                       MultipartHttpServletRequest request, Authentication auth) {
        User student = viewService.currentUser(auth);
        return ApplicationSummary.of(submissionService.resubmit(student, id, note, request::getFile));
    }

    // ---------- application fee (payment gateway) ----------

    @PostMapping("/applications/{id}/payment/order")
    public PaymentService.CheckoutOrder startPayment(@PathVariable Long id, Authentication auth) {
        return paymentService.startPayment(id, viewService.currentUser(auth));
    }

    // Sandbox gateway only: stands in for the gateway's checkout page
    @PostMapping("/payments/sandbox/{orderId}/pay")
    public SandboxGateway.SimulatedPayment sandboxPay(@PathVariable String orderId, Authentication auth) {
        return paymentService.sandboxPay(orderId, viewService.currentUser(auth));
    }

    @PostMapping("/applications/{id}/payment/confirm")
    public ApplicationSummary confirmPayment(@PathVariable Long id, @RequestBody PaymentConfirmation body,
                                             Authentication auth) {
        return ApplicationSummary.of(paymentService.completePayment(id, viewService.currentUser(auth),
                body.orderId(), body.paymentId(), body.signature()));
    }

    @GetMapping("/slots")
    public List<LocalDateTime> slots() {
        return appointmentService.availableSlots();
    }

    @PostMapping("/applications/{id}/appointment")
    public AppointmentDto book(@PathVariable Long id, @RequestBody BookingRequest body, Authentication auth) {
        User student = viewService.currentUser(auth);
        Application app = viewService.loadVisible(id, student);
        return AppointmentDto.of(appointmentService.book(app, student, body.slot()));
    }

    private Map<String, String> parseFields(String json) {
        Map<String, String> fields = new LinkedHashMap<>();
        try {
            objectMapper.readTree(json).properties()
                    .forEach(e -> fields.put(e.getKey(), e.getValue().asText()));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("The form data could not be read");
        }
        return fields;
    }
}
