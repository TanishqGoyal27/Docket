package com.tiet.docket.repository;

import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for RequiredDocument.
 * Spring generates the SQL automatically from the method names.
 */
public interface RequiredDocumentRepository extends JpaRepository<RequiredDocument, Long> {

    /**
     * Returns all required documents for a given service,
     * e.g. all documents needed to apply for "ID Card".
     */
    List<RequiredDocument> findByService(Service service);

    /**
     * Used by the seeder to check if a document already exists
     * so we never create the same row twice (idempotent seeding).
     */
    boolean existsByServiceAndDocumentName(Service service, String documentName);
}
