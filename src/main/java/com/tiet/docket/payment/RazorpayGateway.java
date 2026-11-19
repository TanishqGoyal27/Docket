package com.tiet.docket.payment;

import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Razorpay (use test-mode keys for the project). Orders are created through
 * Razorpay's REST API; the browser opens Razorpay Checkout with the order id.
 * Docs: https://razorpay.com/docs/payments/server-integration/java/
 */
public class RazorpayGateway implements PaymentGateway {

    private static final URI ORDERS = URI.create("https://api.razorpay.com/v1/orders");

    private final String keyId;
    private final String keySecret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RazorpayGateway(String keyId, String keySecret) {
        this.keyId = keyId;
        this.keySecret = keySecret;
    }

    @Override
    public String name() {
        return "RAZORPAY";
    }

    @Override
    public String publicKey() {
        return keyId;
    }

    @Override
    public String createOrder(long amountPaise, String receipt) {
        String body = objectMapper.writeValueAsString(
                Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt));
        String auth = Base64.getEncoder()
                .encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(ORDERS)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Razorpay refused the order (HTTP " + response.statusCode() + ")");
            }
            return objectMapper.readTree(response.body()).get("id").asText();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Could not reach Razorpay. Please try again.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment was interrupted. Please try again.", e);
        }
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        return PaymentGateway.signatureMatches(orderId, paymentId, signature, keySecret);
    }
}
