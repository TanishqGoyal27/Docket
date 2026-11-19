package com.tiet.docket.api;

import com.tiet.docket.model.RequiredDocument;
import com.tiet.docket.model.Service;
import com.tiet.docket.repository.RequiredDocumentRepository;
import com.tiet.docket.repository.ServiceRepository;
import com.tiet.docket.payment.ReceiptPdf;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pay while applying, receipt, and the Finance Officer's parallel fee check (sandbox gateway). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private RequiredDocumentRepository requiredDocumentRepository;
    private final ObjectMapper json = new ObjectMapper();

    private static final RequestPostProcessor STUDENT = user("student@docket.com").roles("STUDENT");
    private static final RequestPostProcessor ADMIN_OFFICER = user("adminofficer@docket.com").roles("ADMIN_OFFICER");
    private static final RequestPostProcessor FINANCE = user("finance@docket.com").roles("FINANCE_OFFICER");

    @Test
    void paidApplicationIsSubmittedAndApprovedOnlyAfterFinanceConfirms() throws Exception {
        long id = applyForIdCard();

        // Saved, but not submitted until paid: no officer sees it yet
        mvc.perform(get("/api/applications/" + id).with(STUDENT))
                .andExpect(jsonPath("$.summary.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.actions.canPay").value(true));
        mvc.perform(get("/api/applications/" + id).with(ADMIN_OFFICER)).andExpect(status().isNotFound());

        JsonNode order = postJson("/api/student/applications/" + id + "/payment/order", "{}", STUDENT);
        String orderId = order.get("orderId").asText();
        assertTrue(order.get("amountPaise").asLong() == 20000);

        // A forged signature is refused and nothing is submitted
        mvc.perform(post("/api/student/applications/" + id + "/payment/confirm").with(csrf()).with(STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"paymentId\":\"pay_FAKE\",\"signature\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        JsonNode paid = postJson("/api/student/payments/sandbox/" + orderId + "/pay", "{}", STUDENT);
        mvc.perform(post("/api/student/applications/" + id + "/payment/confirm").with(csrf()).with(STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + orderId + "\",\"paymentId\":\"" + paid.get("paymentId").asText()
                                + "\",\"signature\":\"" + paid.get("signature").asText() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // The receipt is visible to the student and Finance, not to the Administrative Officer
        mvc.perform(get("/api/applications/" + id).with(STUDENT))
                .andExpect(jsonPath("$.documents[?(@.feeReceipt == true)]").exists())
                .andExpect(jsonPath("$.fee.verification").value("PENDING"));
        mvc.perform(get("/api/applications/" + id).with(ADMIN_OFFICER))
                .andExpect(jsonPath("$.documents[?(@.feeReceipt == true)]").doesNotExist())
                .andExpect(jsonPath("$.actions.canAct").value(true));

        // Final approval is blocked until Finance confirms the payment
        mvc.perform(post("/api/staff/applications/" + id + "/approve").with(csrf()).with(ADMIN_OFFICER)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/staff/fees").with(FINANCE))
                .andExpect(jsonPath("$[?(@.applicationId == " + id + ")].verification").value("PENDING"));
        mvc.perform(post("/api/staff/applications/" + id + "/fee-check").with(csrf()).with(FINANCE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"found\":true}"))
                .andExpect(jsonPath("$.verification").value("CONFIRMED"));

        mvc.perform(post("/api/staff/applications/" + id + "/approve").with(csrf()).with(ADMIN_OFFICER)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void feeNotFoundRejectsTheApplication() throws Exception {
        long id = applyForIdCard();
        JsonNode order = postJson("/api/student/applications/" + id + "/payment/order", "{}", STUDENT);
        String orderId = order.get("orderId").asText();
        JsonNode paid = postJson("/api/student/payments/sandbox/" + orderId + "/pay", "{}", STUDENT);
        postJson("/api/student/applications/" + id + "/payment/confirm", "{\"orderId\":\"" + orderId
                + "\",\"paymentId\":\"" + paid.get("paymentId").asText() + "\",\"signature\":\""
                + paid.get("signature").asText() + "\"}", STUDENT);

        // A reason is required
        mvc.perform(post("/api/staff/applications/" + id + "/fee-check").with(csrf()).with(FINANCE)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"found\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/staff/applications/" + id + "/fee-check").with(csrf()).with(FINANCE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"found\":false,\"remark\":\"No credit in bank statement\"}"))
                .andExpect(jsonPath("$.verification").value("NOT_FOUND"))
                .andExpect(jsonPath("$.applicationStatus").value("REJECTED"));
    }

    @Test
    void upiPaymentWithUtrSubmitsAndUtrCannotBeReused() throws Exception {
        long id = applyForIdCard();
        mvc.perform(post("/api/student/applications/" + id + "/payment/upi").with(csrf()).with(STUDENT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"utr\":\"12345\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/student/applications/" + id + "/payment/upi").with(csrf()).with(STUDENT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"utr\":\"432198765012\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(get("/api/staff/fees").with(FINANCE))
                .andExpect(jsonPath("$[?(@.applicationId == " + id + ")].gateway").value("UPI"))
                .andExpect(jsonPath("$[?(@.applicationId == " + id + ")].paymentId").value("432198765012"));

        long second = applyForIdCard();
        mvc.perform(post("/api/student/applications/" + second + "/payment/upi").with(csrf()).with(STUDENT)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"utr\":\"432198765012\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void receiptPdfIsAValidPdf() {
        String pdf = new String(ReceiptPdf.build("FEE RECEIPT", List.of("Amount (Rs. 200)")), StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF-1.4"));
        assertTrue(pdf.contains("(Amount \\(Rs. 200\\)) Tj"));
        assertTrue(pdf.trim().endsWith("%%EOF"));
    }

    private long applyForIdCard() throws Exception {
        Service idCard = serviceRepository.findByName("ID Card").orElseThrow();
        MockMultipartHttpServletRequestBuilder request = multipart("/api/student/applications");
        for (RequiredDocument doc : requiredDocumentRepository.findByService(idCard)) {
            if (doc.isMandatory()) {
                request.file(new MockMultipartFile("doc_" + doc.getId(), "photo.png", "image/png", new byte[] {1}));
            }
        }
        String body = mvc.perform(request.param("serviceId", idCard.getId().toString())
                        .param("fields", "{\"requestType\":\"New card\",\"reason\":\"First year\"}")
                        .with(csrf()).with(STUDENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private JsonNode postJson(String url, String body, RequestPostProcessor who) throws Exception {
        String response = mvc.perform(post(url).with(csrf()).with(who)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }
}
