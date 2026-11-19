package com.tiet.docket.api;

import com.tiet.docket.api.ApiDtos.ApplicationDetail;
import com.tiet.docket.api.ApiDtos.NotificationDto;
import com.tiet.docket.model.ApplicationDocument;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationDocumentRepository;
import com.tiet.docket.workflow.NotificationService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Endpoints every signed-in role uses: application detail, documents, notifications. */
@RestController
@RequestMapping("/api")
public class CommonApiController {

    private final ApplicationViewService viewService;
    private final ApplicationDocumentRepository documentRepository;
    private final NotificationService notificationService;

    public CommonApiController(ApplicationViewService viewService,
                               ApplicationDocumentRepository documentRepository,
                               NotificationService notificationService) {
        this.viewService = viewService;
        this.documentRepository = documentRepository;
        this.notificationService = notificationService;
    }

    @GetMapping("/applications/{id}")
    public ApplicationDetail detail(@PathVariable Long id, Authentication auth) {
        User viewer = viewService.currentUser(auth);
        return viewService.detail(viewService.loadVisible(id, viewer), viewer);
    }

    // Streams an uploaded file to the owner or the officer handling it (FR10)
    @GetMapping("/documents/{id}")
    public ResponseEntity<Resource> document(@PathVariable Long id, Authentication auth) {
        User viewer = viewService.currentUser(auth);
        ApplicationDocument doc = documentRepository.findById(id)
                .filter(d -> viewService.canDownload(d, viewer))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        byte[] data = doc.getData();
        if (data == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The stored file is missing");
        }
        ByteArrayResource file = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .contentType(doc.getFileType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(doc.getFileType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(doc.getFileName(), StandardCharsets.UTF_8).build().toString())
                .body(file);
    }

    @GetMapping("/notifications")
    public List<NotificationDto> notifications(Authentication auth) {
        return notificationService.getForUser(viewService.currentUser(auth)).stream()
                .map(NotificationDto::of).toList();
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("count", notificationService.countUnread(viewService.currentUser(auth)));
    }

    @PostMapping("/notifications/{id}/read")
    public void markRead(@PathVariable Long id, Authentication auth) {
        notificationService.markRead(id, viewService.currentUser(auth));
    }

    @PostMapping("/notifications/read-all")
    public void markAllRead(Authentication auth) {
        notificationService.markAllRead(viewService.currentUser(auth));
    }
}
