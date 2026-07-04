package com.lvn.codementor.ai.administration.application;

import com.lvn.codementor.ai.common.error.AppException;
import com.lvn.codementor.ai.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GitHubWebhookSignatureVerifier {

    private final String secret;

    public GitHubWebhookSignatureVerifier(@Value("${codementor.webhooks.github.secret:}") String secret) {
        this.secret = secret == null ? "" : secret;
    }

    public boolean verify(String payload, String signatureHeader) {
        if (secret.isBlank()) {
            return false;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            throw new AppException(ErrorCode.FORBIDDEN, "Invalid GitHub webhook signature");
        }
        String expected = "sha256=" + hmacSha256(payload);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Invalid GitHub webhook signature");
        }
        return true;
    }

    private String hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Webhook signature verification failed");
        }
    }
}
