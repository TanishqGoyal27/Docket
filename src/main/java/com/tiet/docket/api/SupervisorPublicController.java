package com.tiet.docket.api;

import com.tiet.docket.api.ApiDtos.DocumentDto;
import com.tiet.docket.api.ApiDtos.SupervisorDecision;
import com.tiet.docket.api.ApiDtos.SupervisorView;
import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationDocument;
import com.tiet.docket.model.SupervisorApproval;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationDocumentRepository;
import com.tiet.docket.workflow.SupervisorApprovalService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/**
 * Pages behind the link emailed to a supervising teacher. No login: the
 * secret token in the link is the only key, and it only opens that one
 * application (never the fee receipt).
 */
@RestController
@RequestMapping("/api/public/supervisor/{token}")
public class SupervisorPublicController {

    private final SupervisorApprovalService supervisorApprovalService;
    private final ApplicationDocumentRepository documentRepository;
    private final ApplicationViewService viewService;

    public SupervisorPublicController(SupervisorApprovalService supervisorApprovalService,
                                      ApplicationDocumentRepository documentRepository,
                                      ApplicationViewService viewService) {
        this.supervisorApprovalService = supervisorApprovalService;
        this.documentRepository = documentRepository;
        this.viewService = viewService;
    }

    @GetMapping
    public SupervisorView view(@PathVariable String token) {
        return toView(supervisorApprovalService.byToken(token));
    }

    @PostMapping
    public SupervisorView decide(@PathVariable String token, @RequestBody SupervisorDecision body) {
        return toView(supervisorApprovalService.decide(token, Boolean.TRUE.equals(body.approve()), body.remark()));
    }

    @GetMapping("/documents/{docId}")
    public ResponseEntity<Resource> document(@PathVariable String token, @PathVariable Long docId) {
        Application app = supervisorApprovalService.byToken(token).getApplication();
        ApplicationDocument doc = documentRepository.findById(docId)
                .filter(d -> d.getApplication().getId().equals(app.getId()) && !d.isFeeReceipt())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return ResponseEntity.ok()
                .contentType(doc.getFileType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(doc.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(doc.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(doc.getData()));
    }

    private SupervisorView toView(SupervisorApproval approval) {
        Application app = approval.getApplication();
        User s = app.getStudent();
        return new SupervisorView(app.getReferenceNumber(), app.getService().getName(), s.getName(),
                s.getRollNumber(), s.getCourse(), viewService.parseFormData(app.getFormData()),
                documentRepository.findByApplication(app).stream()
                        .filter(d -> !d.isFeeReceipt()).map(DocumentDto::of).toList(),
                approval.getTeacherName(), approval.getStatus().name(), approval.getRemark(), approval.getDecidedAt());
    }
}
