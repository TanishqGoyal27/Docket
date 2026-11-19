package com.tiet.docket.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Uses Razorpay when test keys are configured, otherwise Docket's sandbox gateway
@Configuration
public class PaymentConfig {

    @Bean
    public PaymentGateway paymentGateway(@Value("${docket.payment.razorpay.key-id:}") String keyId,
                                         @Value("${docket.payment.razorpay.key-secret:}") String keySecret) {
        if (keyId.isBlank() || keySecret.isBlank()) {
            return new SandboxGateway();
        }
        return new RazorpayGateway(keyId, keySecret);
    }
}
