package com.tiet.docket.api;

import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Service;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every place an officer can return an application for correction, end to end through the API:
 * the student sees the request, edits the answer and replaces a file, and review resumes at the
 * same stage with the change on record.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CorrectionFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private RequiredDocumentRepository requiredDocumentRepository;
    private final ObjectMapper json = new ObjectMapper();

    private static final RequestPostProcessor STUDENT = user("student@docket.com").roles("STUDENT");
    private static final Map<String, RequestPostProcessor> OFFICERS = Map.of(
            "ADMIN_OFFICER", user("adminofficer@docket.com").roles("ADMIN_OFFICER"),
            "HOD", user("hod@docket.com").roles("HOD"),
            "FINANCE_OFFICER", user("finance@docket.com").roles("FINANCE_OFFICER"),
            "SCHOLARSHIP_OFFICER", user("scholarship@docket.com").roles("SCHOLARSHIP_OFFICER"));

    private static final String ID_CARD = "{\"requestType\":\"New card\",\"reason\":\"First year\"}";
    private static final String CLAIM = "{\"projectTitle\":\"SIH\",\"teacherName\":\"Dr. A\",\"teacherDepartment\":\"CSE\","
            + "\"teacherEmail\":\"a@thapar.edu\",\"amount\":\"4500\",\"description\":\"Travel\"}";
    private static final String CORRECTION = "{\"field\":\"phone\",\"newValue\":\"9000000001\",\"reason\":\"New number\"}";
    private static final String DOCUMENT = "{\"documentType\":\"Transcript\",\"purpose\":\"Higher studies\"}";
    private static final String SCHOLARSHIP = "{\"scholarshipName\":\"Merit\",\"familyIncome\":\"300000\"}";

    // service, form, officers approving before the return, officer who returns, field the student edits, new value
    static Stream<Arguments> returnPoints() {
        return Stream.of(
                Arguments.of("ID Card", ID_CARD, List.of(), "ADMIN_OFFICER", "reason", "Joined in 2026"),
                Arguments.of("Reimbursement", CLAIM, List.of(), "HOD", "amount", "4000"),
                Arguments.of("Reimbursement", CLAIM, List.of("HOD"), "FINANCE_OFFICER", "amount", "3900"),
                Arguments.of("Details Correction", CORRECTION, List.of(), "ADMIN_OFFICER", "newValue", "9000000002"),
                Arguments.of("Details Correction", CORRECTION, List.of("ADMIN_OFFICER"), "ADMIN_OFFICER", "newValue", "9000000003"),
                Arguments.of("Document Verification", DOCUMENT, List.of(), "ADMIN_OFFICER", "purpose", "Job verification"),
                Arguments.of("Scholarship Verification", SCHOLARSHIP, List.of(), "SCHOLARSHIP_OFFICER", "familyIncome", "250000"),
                Arguments.of("Scholarship Verification", SCHOLARSHIP, List.of("SCHOLARSHIP_OFFICER"), "FINANCE_OFFICER", "familyIncome", "240000"));
    }

    @BeforeEach
    void skipPreSubmissionSteps() {
        // Fee payment and the teacher's email approval have their own tests; here they only add setup
        serviceRepository.findByName("ID Card").orElseThrow().setFeeAmount(0);
        serviceRepository.findByName("Reimbursement").orElseThrow().setRequiresSupervisorApproval(false);
    }

    @ParameterizedTest(name = "{0}: returned by {3}")
    @MethodSource("returnPoints")
    void returnedApplicationCanBeCorrectedAndResumesAtTheSameStage(String serviceName, String form, List<String> before,
                                                                   String returner, String field, String newValue)
            throws Exception {
        Service service = serviceRepository.findByName(serviceName).orElseThrow();
        long id = submit(service, form);
        for (String role : before) {
            post("/api/staff/applications/" + id + "/approve", "{}", OFFICERS.get(role));
        }
        RequestPostProcessor officer = OFFICERS.get(returner);
        String stage = detail(id, officer).get("summary").get("currentStage").asText();

        // Officer returns it
        post("/api/staff/applications/" + id + "/correction", "{\"reason\":\"Please fix the " + field + "\"}", officer);

        // Student sees it everywhere: status, what to fix, the resubmit action, a notification
        JsonNode returned = detail(id, STUDENT);
        assertEquals("AWAITING_CORRECTION", returned.get("summary").get("status").asText());
        assertEquals("Please fix the " + field, returned.get("pendingMessage").asText());
        assertTrue(returned.get("actions").get("canResubmit").asBoolean());
        assertTrue(text(get("/api/notifications"), STUDENT).contains("needs correction"));
        assertFalse(queue(officer).contains(id), "a returned application leaves the officer's queue");

        // Student edits the answer and replaces the first document
        RequiredDocument first = requiredDocumentRepository.findByService(service).get(0);
        mvc.perform(multipart("/api/student/applications/" + id + "/resubmit")
                        .file(new MockMultipartFile("doc_" + first.getId(), "new.pdf", "application/pdf", new byte[] {9}))
                        .param("note", "Fixed")
                        .param("fields", "{\"" + field + "\":\"" + newValue + "\"}")
                        .with(csrf()).with(STUDENT))
                .andExpect(status().isOk());

        // Back with the same officer at the same stage, with the change on record
        JsonNode resumed = detail(id, officer);
        assertEquals("UNDER_REVIEW", resumed.get("summary").get("status").asText());
        assertEquals(stage, resumed.get("summary").get("currentStage").asText());
        assertEquals(newValue, resumed.get("formData").get(field).asText());
        JsonNode last = resumed.get("history").get(resumed.get("history").size() - 1);
        assertEquals("RESUBMITTED", last.get("decision").asText());
        assertTrue(last.get("reason").asText().contains("-> " + newValue));
        assertTrue(resumed.get("documents").toString().contains("\"replaced\":true"));
        assertTrue(queue(officer).contains(id), "the resubmitted application is back in the queue");
    }

    // ---------------------------------------------------------------

    private long submit(Service service, String form) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/student/applications");
        for (RequiredDocument doc : requiredDocumentRepository.findByService(service)) {
            request.file(new MockMultipartFile("doc_" + doc.getId(), "f.pdf", "application/pdf", new byte[] {1}));
        }
        String body = mvc.perform(request.param("serviceId", service.getId().toString()).param("fields", form)
                        .with(csrf()).with(STUDENT))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private void post(String url, String body, RequestPostProcessor who) throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).with(csrf()).with(who)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private JsonNode detail(long id, RequestPostProcessor who) throws Exception {
        return json.readTree(text(get("/api/applications/" + id), who));
    }

    private List<Long> queue(RequestPostProcessor officer) throws Exception {
        List<Long> ids = new java.util.ArrayList<>();
        json.readTree(text(get("/api/staff/queue"), officer)).forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private String text(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                        RequestPostProcessor who) throws Exception {
        return mvc.perform(req.with(who)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
