package com.lvn.codementor.ai.codeanalysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.lvn.codementor.ai.codeanalysis.application.SecretMasker.MaskResult;
import org.junit.jupiter.api.Test;

/** Secret detection/masking rules. The original secret must never survive in the sanitized output. */
class SecretMaskerTest {

    private final SecretMasker masker = new SecretMasker();

    @Test
    void masksGitHubToken() {
        String secret = "ghp_" + "a".repeat(30);
        String content = "public void connect() {\n"
                + "  String token = \"" + secret + "\";\n"
                + "  client.authenticateWith(token, timeoutSeconds, maxRetryAttempts);\n"
                + "  logger.info(\"connected to the upstream service successfully\");\n"
                + "}\n";
        MaskResult result = masker.mask(content);

        assertThat(result.sanitized()).doesNotContain(secret).contains(SecretMasker.REDACTED);
        assertThat(result.maskedCount()).isEqualTo(1);
        assertThat(result.pureSecretFile()).isFalse();
    }

    @Test
    void masksApiKeyAssignmentValueOnly() {
        MaskResult result = masker.mask("api_key = \"supersecretapikeyvalue\"");

        assertThat(result.sanitized()).doesNotContain("supersecretapikeyvalue");
        assertThat(result.sanitized()).contains("api_key").contains(SecretMasker.REDACTED);
        assertThat(result.maskedCount()).isEqualTo(1);
    }

    @Test
    void masksJwtAndAwsKeyAndEnvSecret() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI.eyJzdWIiOiIxMjM0NTY3.SflKxwRJSMeKKF2QT4";
        String content = "jwt=" + jwt + "\nAKIAABCDEFGHIJKLMNOP\nDB_PASSWORD=hunter2value\n";
        MaskResult result = masker.mask(content);

        assertThat(result.sanitized())
                .doesNotContain(jwt)
                .doesNotContain("AKIAABCDEFGHIJKLMNOP")
                .doesNotContain("hunter2value");
        assertThat(result.maskedCount()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void flagsPrivateKeyBlockAsPureSecretFile() {
        String content = "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA\n-----END RSA PRIVATE KEY-----\n";
        MaskResult result = masker.mask(content);

        assertThat(result.pureSecretFile()).isTrue();
        assertThat(result.sanitized()).doesNotContain("MIIEpAIBAAKCAQEA");
        assertThat(result.maskedCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void flagsMostlySecretFileAsPureSecret() {
        String content = "SECRET_TOKEN=abcdefghijklmnop\nAPI_KEY=zzzzzzzzzzzzzzzz\n";
        MaskResult result = masker.mask(content);

        assertThat(result.pureSecretFile()).isTrue();
    }

    @Test
    void leavesCleanContentUntouched() {
        String content = "public int add(int a, int b) {\n    return a + b;\n}\n";
        MaskResult result = masker.mask(content);

        assertThat(result.sanitized()).isEqualTo(content);
        assertThat(result.maskedCount()).isZero();
        assertThat(result.pureSecretFile()).isFalse();
    }
}
