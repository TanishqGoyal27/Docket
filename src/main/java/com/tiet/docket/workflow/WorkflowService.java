package com.tiet.docket.workflow;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationStatus;
import com.tiet.docket.model.Stage;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.StageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service   // Spring's annotation (we do NOT import our model.Service here)
public class WorkflowService {

    private final ApplicationRepository applicationRepository;
    private final StageRepository stageRepository;

    public WorkflowService(ApplicationRepository applicationRepository,
                           StageRepository stageRepository) {
        this.applicationRepository = applicationRepository;
        this.stageRepository = stageRepository;
    }

    // A student submits: the application goes to step 1 of its service.
    @Transactional
    public Application submit(Application application) {
        List<Stage> stages =
                stageRepository.findByServiceOrderByStepOrderAsc(application.getService());
        if (stages.isEmpty()) {
            throw new IllegalStateException("No stages are configured for this service");
        }
        application.setCurrentStage(stages.get(0));
        application.setStatus(ApplicationStatus.SUBMITTED);
        return applicationRepository.save(application);
    }

    // TODO (Person 1): move to next stage, or APPROVED at the last stage; save DecisionHistory
    @Transactional
    public Application approve(Long applicationId, User officer) {
        return findApplication(applicationId);
    }

    // TODO (Person 1): reason is mandatory; set REJECTED; save DecisionHistory
    @Transactional
    public Application reject(Long applicationId, User officer, String reason) {
        return findApplication(applicationId);
    }

    // TODO (Person 1): set AWAITING_CORRECTION; save message in DecisionHistory
    @Transactional
    public Application requestCorrection(Long applicationId, User officer, String message) {
        return findApplication(applicationId);
    }

    // TODO (Person 1): set back to UNDER_REVIEW at the same stage
    @Transactional
    public Application resubmit(Long applicationId, User student) {
        return findApplication(applicationId);
    }

    // Staff queue: applications waiting at a stage handled by this officer's role.
    @Transactional(readOnly = true)
    public List<Application> getPendingFor(User officer) {
        return applicationRepository.findByCurrentStage_HandlerRoleAndStatusIn(
                officer.getRole(),
                List.of(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW));
    }

    private Application findApplication(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + id));
    }
}