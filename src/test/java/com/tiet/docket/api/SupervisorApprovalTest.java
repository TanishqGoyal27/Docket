package com.tiet.docket.api;

import com.tiet.docket.model.EmailLog;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Service;
import com.tiet.docket.repository.EmailLogRepository;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reimbursement: the teacher approves from an emailed link before the HOD sees the claim. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupervisorApprovalTest {

    @Autowired private MockMvc mvc;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private RequiredDocumentRepository requiredDocumentRepository;
    @Autowired private EmailLogRepository emailLogRepository;
    private final ObjectMapper json = new ObjectMapper();

    private static final RequestPostProcessor STUDENT = user("student@docket.com").roles("STUDENT");
    private static final RequestPostProcessor HOD = user("hod@docket.com").roles("HOD");
    private static final Pattern LINK = Pattern.compile("/supervisor/([0-9a-f]{64})");

    @Test
    void teacherApprovesByEmailThenHodSeesTheClaim() throws Exception {
        long id = submitClaim("dr.mehta@thapar.edu");

        mvc.perform(get("/api/applications/" + id).with(STUDENT))
                .andExpect(jsonPath("$.summary.status").value("SUPERVISOR_PENDING"))
                .andExpect(jsonPath("$.supervisor.teacherEmail").value("dr.mehta@thapar.edu"))
                .andExpect(jsonPath("$.actions.canResendSupervisor").value(true));
        // Not with the HOD yet
        assertTrue(hodQueue().noneMatch(qid -> qid == id));

        // The email was recorded in the outbox (no mail server in tests) with a one-time link
        EmailLog email = emailLogRepository.findAllByOrderByCreatedAtDesc().get(0);
        assertEquals("dr.mehta@thapar.edu", email.getRecipient());
        assertEquals(EmailLog.Status.NOT_SENT_NO_SMTP, email.getStatus());
        String token = token(email.getBody());

        // The teacher opens the link without logging in and sees the claim
        mvc.perform(get("/api/public/supervisor/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.formData.amount").value("4500"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/public/supervisor/" + "0".repeat(64))).andExpect(status().isBadRequest());

        // Rejecting needs a reason; approving releases the claim to the HOD
        decide(token, "{\"approve\":false}").andExpect(status().isBadRequest());
        decide(token, "{\"approve\":true,\"remark\":\"Genuine project expense\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        decide(token, "{\"approve\":true}").andExpect(status().isBadRequest());   // link works once

        assertTrue(hodQueue().anyMatch(qid -> qid == id));
        mvc.perform(get("/api/applications/" + id).with(HOD))
                .andExpect(jsonPath("$.supervisor.status").value("APPROVED"))
                .andExpect(jsonPath("$.supervisor.remark").value("Genuine project expense"))
                .andExpect(jsonPath("$.actions.canAct").value(true));
        mvc.perform(post("/api/staff/applications/" + id + "/approve").with(csrf()).with(HOD)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.currentStage").value("Bill verification"));
    }

    @Test
    void teacherRejectionEndsTheClaim() throws Exception {
        long id = submitClaim("dr.rao@thapar.edu");
        String token = token(emailLogRepository.findAllByOrderByCreatedAtDesc().get(0).getBody());
        decide(token, "{\"approve\":false,\"remark\":\"I did not supervise this project\"}").andExpect(status().isOk());
        mvc.perform(get("/api/applications/" + id).with(STUDENT))
                .andExpect(jsonPath("$.summary.status").value("REJECTED"));
        assertTrue(hodQueue().noneMatch(qid -> qid == id));
    }

    @Test
    void studentCannotNameThemselvesAsTeacher() throws Exception {
        Service service = serviceRepository.findByName("Reimbursement").orElseThrow();
        mvc.perform(claimRequest(service, "student@docket.com")).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------

    private long submitClaim(String teacherEmail) throws Exception {
        Service service = serviceRepository.findByName("Reimbursement").orElseThrow();
        String body = mvc.perform(claimRequest(service, teacherEmail))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUPERVISOR_PENDING"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private MockMultipartHttpServletRequestBuilder claimRequest(Service service, String teacherEmail) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/student/applications");
        for (RequiredDocument doc : requiredDocumentRepository.findByService(service)) {
            request.file(new MockMultipartFile("doc_" + doc.getId(), "bill.pdf", "application/pdf", new byte[] {1}));
        }
        request.param("serviceId", service.getId().toString())
                .param("fields", "{\"projectTitle\":\"Smart India Hackathon\",\"teacherName\":\"Dr. Mehta\","
                        + "\"teacherDepartment\":\"Computer Science\",\"teacherEmail\":\"" + teacherEmail + "\","
                        + "\"amount\":\"4500\",\"description\":\"Travel\"}");
        request.with(csrf()).with(STUDENT);
        return request;
    }

    private org.springframework.test.web.servlet.ResultActions decide(String token, String body) throws Exception {
        return mvc.perform(post("/api/public/supervisor/" + token).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private java.util.stream.LongStream hodQueue() throws Exception {
        String body = mvc.perform(get("/api/staff/queue").with(HOD)).andReturn().getResponse().getContentAsString();
        java.util.List<Long> ids = new java.util.ArrayList<>();
        json.readTree(body).forEach(n -> ids.add(n.get("id").asLong()));
        return ids.stream().mapToLong(Long::longValue);
    }

    private static String token(String emailBody) {
        Matcher m = LINK.matcher(emailBody);
        assertTrue(m.find(), "The email should contain the approval link");
        return m.group(1);
    }
}
