package com.tiet.docket.payment;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Docket's own test gateway, used when no Razorpay keys are configured. It
 * behaves like Razorpay test mode (orders, payment ids, signed responses) but
 * no money moves. The secret is new on every start, so signatures cannot be forged.
 */
public class SandboxGateway implements PaymentGateway {

    private final SecureRandom random = new SecureRandom();
    private final String secret = randomId(32);

    @Override
    public String name() {
        return "SANDBOX";
    }

    @Override
    public String publicKey() {
        return null;
    }

    @Override
    public String createOrder(long amountPaise, String receipt) {
        return "order_SBX" + randomId(7);
    }

    @Override
    public boolean verifySignature(String orderId, String paymentId, String signature) {
        return PaymentGateway.signatureMatches(orderId, paymentId, signature, secret);
    }

    /** Plays the gateway's part: "completes" the payment and signs the result. */
    public SimulatedPayment pay(String orderId) {
        String paymentId = "pay_SBX" + randomId(7);
        return new SimulatedPayment(paymentId, PaymentGateway.hmacSha256Hex(orderId + "|" + paymentId, secret));
    }

    public record SimulatedPayment(String paymentId, String signature) {}

    private String randomId(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return HexFormat.of().formatHex(b).toUpperCase();
    }
}
