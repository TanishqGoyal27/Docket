package com.tiet.docket.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * A payment gateway Docket can take the application fee through. Both
 * implementations follow Razorpay's flow: create an order on the server, the
 * student pays in the gateway's checkout, and the server checks the returned
 * signature HMAC-SHA256(orderId + "|" + paymentId, secret) before trusting it.
 */
public interface PaymentGateway {

    /** "SANDBOX" or "RAZORPAY", stored with each payment. */
    String name();

    /** Public key the browser checkout needs; null for the sandbox. */
    String publicKey();

    /** Creates an order for the amount (in paise) and returns the gateway's order id. */
    String createOrder(long amountPaise, String receipt);

    /** True only if the gateway really signed this order and payment. */
    boolean verifySignature(String orderId, String paymentId, String signature);

    static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    // Constant-time comparison so the signature cannot be guessed byte by byte
    static boolean signatureMatches(String orderId, String paymentId, String signature, String secret) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        String expected = hmacSha256Hex(orderId + "|" + paymentId, secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
