package com.tiet.docket.workflow;

import com.tiet.docket.model.AuditLog;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(User actor, String action, String details) {
        auditLogRepository.save(new AuditLog(actor, action, details));
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAll() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }
}
