package com.codesentinel.controller;

import com.codesentinel.config.GitHubConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codesentinel.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookSignatureVerificationUnitTest {

    @Mock
    private GitHubConfig gitHubConfig;

    @Mock
    private ReviewService reviewService;

    private WebhookController webhookController;
    private static final String SECRET = "top-secret-webhook-key";
    private static final String SAMPLE_PAYLOAD = "{\"action\":\"opened\",\"number\":1}";

    @BeforeEach
    void setUp() {
        webhookController = new WebhookController(gitHubConfig, new ObjectMapper(), reviewService);
    }

    private String calculateHmac(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Should return true for perfectly matched HMAC-SHA256 signature")
    void shouldVerifyValidSignature() throws Exception {
        when(gitHubConfig.getWebhookSecret()).thenReturn(SECRET);
        String signature = calculateHmac(SAMPLE_PAYLOAD, SECRET);

        boolean isValid = webhookController.verifySignature(SAMPLE_PAYLOAD, signature);

        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Should return false when payload is tampered by even a single character")
    void shouldFailWhenPayloadIsTampered() throws Exception {
        when(gitHubConfig.getWebhookSecret()).thenReturn(SECRET);
        String signature = calculateHmac(SAMPLE_PAYLOAD, SECRET);

        // Tamper with payload
        String tampered = SAMPLE_PAYLOAD + " ";
        boolean isValid = webhookController.verifySignature(tampered, signature);

        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should return false when signature header is null")
    void shouldFailWhenSignatureIsNull() {
        boolean isValid = webhookController.verifySignature(SAMPLE_PAYLOAD, null);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should return false when signature header does not start with sha256=")
    void shouldFailWhenPrefixIsMissing() throws Exception {
        String rawHex = calculateHmac(SAMPLE_PAYLOAD, SECRET).substring("sha256=".length());

        boolean isValid = webhookController.verifySignature(SAMPLE_PAYLOAD, rawHex);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should return false when webhook secret is not configured or blank")
    void shouldFailWhenSecretIsMissing() throws Exception {
        when(gitHubConfig.getWebhookSecret()).thenReturn("");
        String signature = calculateHmac(SAMPLE_PAYLOAD, SECRET);

        boolean isValid = webhookController.verifySignature(SAMPLE_PAYLOAD, signature);
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should return false when signature is generated with a different secret")
    void shouldFailWhenSignedWithWrongSecret() throws Exception {
        when(gitHubConfig.getWebhookSecret()).thenReturn(SECRET);
        String wrongSignature = calculateHmac(SAMPLE_PAYLOAD, "wrong-secret-key");

        boolean isValid = webhookController.verifySignature(SAMPLE_PAYLOAD, wrongSignature);
        assertThat(isValid).isFalse();
    }
}
