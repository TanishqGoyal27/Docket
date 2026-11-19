package com.tiet.docket.workflow;

import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that WorkflowDataSeeder seeds the correct number of required documents
 * and that running the seeder twice does not create duplicates.
 *
 * @SpringBootTest runs the full app context, which triggers the seeder once via
 * CommandLineRunner.  We verify the counts that the seeder should have produced.
 *
 * @Transactional rolls back each test so tests remain independent.
 */
@SpringBootTest
@Transactional
class RequiredDocumentSeedTest {

    @Autowired private RequiredDocumentRepository requiredDocumentRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private WorkflowDataSeeder workflowDataSeeder;

    // ----------------------------------------------------------------
    // 1. ID Card has 2 documents (the fee receipt is generated after online payment)
    // ----------------------------------------------------------------
    @Test
    void idCardHasTwoRequiredDocuments() {
        com.tiet.docket.model.Service idCard =
                serviceRepository.findByName("ID Card").orElseThrow();
        List<RequiredDocument> docs = requiredDocumentRepository.findByService(idCard);
        assertEquals(2, docs.size(),
                "ID Card should have exactly 2 documents");
    }

    // ----------------------------------------------------------------
    // 2. Reimbursement must have exactly 3 required documents
    // ----------------------------------------------------------------
    @Test
    void reimbursementHasThreeRequiredDocuments() {
        com.tiet.docket.model.Service reimbursement =
                serviceRepository.findByName("Reimbursement").orElseThrow();
        List<RequiredDocument> docs = requiredDocumentRepository.findByService(reimbursement);
        assertEquals(3, docs.size(),
                "Reimbursement should have exactly 3 required documents");
    }

    // ----------------------------------------------------------------
    // 3. Details Correction must have exactly 1 required document
    // ----------------------------------------------------------------
    @Test
    void detailsCorrectionHasOneRequiredDocument() {
        com.tiet.docket.model.Service detailsCorrection =
                serviceRepository.findByName("Details Correction").orElseThrow();
        List<RequiredDocument> docs = requiredDocumentRepository.findByService(detailsCorrection);
        assertEquals(1, docs.size(),
                "Details Correction should have exactly 1 required document");
    }

    // ----------------------------------------------------------------
    // 4. Running the seeder a second time must not create duplicates
    // ----------------------------------------------------------------
    @Test
    void runningSeederTwiceDoesNotCreateDuplicates() throws Exception {
        // Count before second run
        long countBefore = requiredDocumentRepository.count();

        // Run the seeder again (simulates an app restart)
        workflowDataSeeder.run();

        // Count should be the same
        long countAfter = requiredDocumentRepository.count();
        assertEquals(countBefore, countAfter,
                "Running the seeder twice should not create duplicate required documents");
    }

    // ----------------------------------------------------------------
    // 5. Verify the optional flag is set correctly for ID Card
    // ----------------------------------------------------------------
    @Test
    void idCardThirdDocumentIsOptional() {
        com.tiet.docket.model.Service idCard =
                serviceRepository.findByName("ID Card").orElseThrow();
        List<RequiredDocument> docs = requiredDocumentRepository.findByService(idCard);

        // Find the optional document (the FIR/previous ID card one)
        boolean hasOptional = docs.stream()
                .anyMatch(d -> !d.isMandatory());
        assertTrue(hasOptional, "ID Card should have at least one optional document");

        // Only the photo is always mandatory (the FIR is required for a lost card)
        long mandatoryCount = docs.stream().filter(RequiredDocument::isMandatory).count();
        assertEquals(1, mandatoryCount,
                "ID Card should have exactly 1 mandatory document");
    }
}
