package com.tiet.docket.repository;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.ApplicationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for ApplicationDocument.
 * Spring generates the SQL automatically from the method names.
 */
public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, Long> {

    /**
     * Returns all files uploaded for a given application,
     * so the UI can show the student's document list.
     */
    List<ApplicationDocument> findByApplication(Application application);
}
