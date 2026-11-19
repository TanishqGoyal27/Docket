package com.tiet.docket.api;

import com.tiet.docket.model.Application;
import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Role;
import com.tiet.docket.model.Service;
import com.tiet.docket.model.User;
import com.tiet.docket.repository.ApplicationDocumentRepository;
import com.tiet.docket.repository.ApplicationRepository;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.repository.UserRepository;
import com.tiet.docket.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login, CSRF and the role-by-role access matrix (QR13, QR14, QR16) through the REST API. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiSecurityTest {

    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private RequiredDocumentRepository requiredDocumentRepository;
    @Autowired private WorkflowService workflowService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private ApplicationDocumentRepository documentRepository;

    private static final String STUDENT = "student@docket.com";   // seeded by DataInitializer

    @Test
    void apiNeedsLogin() throws Exception {
        mvc.perform(get("/api/student/applications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfTokenIsRefused() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"student@docket.com\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginReturnsRole() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"Student@Docket.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registrationAlwaysCreatesAStudentAndLogsIn() throws Exception {
        // A "role" field in the request is ignored: nobody can sign up as staff
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Student\",\"email\":\"test-new@docket.com\",\"password\":\"secret123\","
                                + "\"rollNumber\":\"TEST-999\",\"role\":\"ADMINISTRATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.rollNumber").value("TEST-999"));

        // Same email or same roll number cannot register twice
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Copy\",\"email\":\"test-new@docket.com\",\"password\":\"secret123\",\"rollNumber\":\"TEST-1000\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Copy\",\"email\":\"test-copy@docket.com\",\"password\":\"secret123\",\"rollNumber\":\"TEST-999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("An account with this roll number already exists"));
        // Short password and missing roll number are refused
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"test-x@docket.com\",\"password\":\"short\",\"rollNumber\":\"TEST-1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"email\":\"test-y@docket.com\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void threeWrongPasswordsLockTheAccount() throws Exception {
        User u = new User();
        u.setName("Lock Test");
        u.setEmail("test-lock@docket.com");
        u.setPassword(passwordEncoder.encode("right-password"));
        u.setRole(Role.STUDENT);
        userRepository.save(u);

        String wrong = "{\"email\":\"test-lock@docket.com\",\"password\":\"wrong\"}";
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isLocked());
        // Even the right password is refused while locked
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test-lock@docket.com\",\"password\":\"right-password\"}"))
                .andExpect(status().isLocked());
    }

    @Test
    void eachRoleReachesOnlyItsOwnArea() throws Exception {
        mvc.perform(get("/api/staff/queue").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/student/services").with(user("hod@docket.com").roles("HOD"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(user("hod@docket.com").roles("HOD"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/staff/queue").with(user("admin@docket.com").roles("ADMINISTRATOR"))).andExpect(status().isForbidden());

        mvc.perform(get("/api/student/services").with(user(STUDENT).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(get("/api/staff/queue").with(user("hod@docket.com").roles("HOD"))).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").with(user("admin@docket.com").roles("ADMINISTRATOR"))).andExpect(status().isOk());
    }

    @Test
    void studentCannotSeeAnotherStudentsApplication() throws Exception {
        User other = new User();
        other.setName("Other");
        other.setEmail("test-other@docket.com");
        other.setPassword("x");
        other.setRole(Role.STUDENT);
        other = userRepository.save(other);

        Application app = new Application();
        app.setStudent(other);
        app.setService(serviceRepository.findByName("ID Card").orElseThrow());
        app = workflowService.submit(app);

        mvc.perform(get("/api/applications/" + app.getId()).with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isNotFound());
        // Officer of a different stage cannot open it either
        mvc.perform(get("/api/applications/" + app.getId()).with(user("hod@docket.com").roles("HOD")))
                .andExpect(status().isNotFound());
        // The Admin Officer handling stage 1 can
        mvc.perform(get("/api/applications/" + app.getId()).with(user("adminofficer@docket.com").roles("ADMIN_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actions.canAct").value(true));
    }

    @Test
    void submissionListsEveryMissingItem() throws Exception {
        Service idCard = serviceRepository.findByName("ID Card").orElseThrow();
        mvc.perform(multipart("/api/student/applications").param("serviceId", idCard.getId().toString())
                        .param("fields", "{}").with(csrf()).with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));   // 2 fields + the mandatory photo
    }

    @Test
    void lostCardNeedsFirAndFilesAreStoredInDatabase() throws Exception {
        Service idCard = serviceRepository.findByName("ID Card").orElseThrow();
        List<RequiredDocument> docs = requiredDocumentRepository.findByService(idCard);
        String lost = "{\"requestType\":\"Replacement (lost)\",\"reason\":\"Lost it\"}";
        byte[] photo = {7, 7, 7};

        MockMultipartHttpServletRequestBuilder withoutFir = multipart("/api/student/applications");
        docs.stream().filter(RequiredDocument::isMandatory).forEach(d -> withoutFir.file(
                new MockMultipartFile("doc_" + d.getId(), "f.png", "image/png", photo)));
        mvc.perform(withoutFir.param("serviceId", idCard.getId().toString()).param("fields", lost)
                        .with(csrf()).with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("An FIR copy is required to replace a lost ID card."));

        MockMultipartHttpServletRequestBuilder withFir = multipart("/api/student/applications");
        docs.forEach(d -> withFir.file(new MockMultipartFile("doc_" + d.getId(), "f.png", "image/png", photo)));
        String body = mvc.perform(withFir.param("serviceId", idCard.getId().toString()).param("fields", lost)
                        .with(csrf()).with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long appId = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // The owner downloads exactly the bytes that were uploaded, read back from the database
        Long docId = documentRepository.findByApplication(
                applicationRepository.findById(appId).orElseThrow()).get(0).getId();
        mvc.perform(get("/api/documents/" + docId).with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(photo));
    }

    @Test
    void completeSubmissionIsAcceptedAndRejectsWrongFileType() throws Exception {
        Service idCard = serviceRepository.findByName("ID Card").orElseThrow();
        MockMultipartHttpServletRequestBuilder request = multipart("/api/student/applications");
        for (RequiredDocument doc : requiredDocumentRepository.findByService(idCard)) {
            if (doc.isMandatory()) {
                request.file(new MockMultipartFile("doc_" + doc.getId(), "proof.png", "image/png", new byte[] {1, 2, 3}));
            }
        }
        mvc.perform(request.param("serviceId", idCard.getId().toString())
                        .param("fields", "{\"requestType\":\"New card\",\"reason\":\"First year\"}")
                        .with(csrf()).with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))   // the ID card fee is paid next
                .andExpect(jsonPath("$.referenceNumber").exists());

        RequiredDocument photo = requiredDocumentRepository.findByService(idCard).get(0);
        mvc.perform(multipart("/api/student/applications")
                        .file(new MockMultipartFile("doc_" + photo.getId(), "virus.exe", "application/x-msdownload", new byte[] {1}))
                        .param("serviceId", idCard.getId().toString())
                        .param("fields", "{\"requestType\":\"New card\",\"reason\":\"x\"}")
                        .with(csrf()).with(user(STUDENT).roles("STUDENT")))
                .andExpect(status().isBadRequest());
    }
}
