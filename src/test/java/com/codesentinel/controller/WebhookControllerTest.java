package com.codesentinel.controller;

import com.codesentinel.model.Review;
import com.codesentinel.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    // Matches the secret in src/test/resources/application-test.yml
    private static final String TEST_SECRET = "test-secret-key-12345";

    private static final String SAMPLE_PULL_REQUEST_OPENED_PAYLOAD = """
            {
              "action": "opened",
              "number": 42,
              "pull_request": {
                "number": 42,
                "title": "Fix SQL injection vulnerability in user lookup",
                "state": "open",
                "user": { "login": "alice" }
              },
              "repository": {
                "name": "Hello-World",
                "full_name": "octocat/Hello-World",
                "owner": { "login": "octocat" }
              },
              "sender": {
                "login": "alice"
              }
            }
            """;

    private static final String SAMPLE_PING_PAYLOAD = """
            {
              "zen": "Approachable is better than simple.",
              "hook_id": 999999,
              "hook": {
                "type": "Repository",
                "id": 999999,
                "name": "web",
                "active": true,
                "events": ["pull_request"]
              }
            }
            """;

    private static String computeHmacSha256(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hash);
    }

    @Test
    @DisplayName("Should accept valid webhook with sample pull_request opened payload and return 200 OK")
    void shouldAcceptValidPullRequestWebhook() throws Exception {
        String signature = computeHmacSha256(SAMPLE_PULL_REQUEST_OPENED_PAYLOAD, TEST_SECRET);

        Review mockReview = new Review("octocat/Hello-World", 42, "abc1234", "CHANGES_REQUESTED", "Issues found", "DRAFT");
        mockReview.setId(101L);
        when(reviewService.processPullRequest("octocat/Hello-World", 42)).thenReturn(mockReview);

        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(SAMPLE_PULL_REQUEST_OPENED_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.repository").value("octocat/Hello-World"))
                .andExpect(jsonPath("$.prNumber").value(42))
                .andExpect(jsonPath("$.action").value("opened"))
                .andExpect(jsonPath("$.reviewId").value(101))
                .andExpect(jsonPath("$.reviewStatus").value("DRAFT"));
    }

    @Test
    @DisplayName("Should reject webhook with invalid signature and return 401 Unauthorized")
    void shouldRejectInvalidSignature() throws Exception {
        String invalidSignature = "sha256=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", invalidSignature)
                        .content(SAMPLE_PULL_REQUEST_OPENED_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid or missing X-Hub-Signature-256 signature"));
    }

    @Test
    @DisplayName("Should reject webhook with missing signature header and return 401 Unauthorized")
    void shouldRejectMissingSignatureHeader() throws Exception {
        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .content(SAMPLE_PULL_REQUEST_OPENED_PAYLOAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Invalid or missing X-Hub-Signature-256 signature"));
    }

    @Test
    @DisplayName("Should respond with pong to GitHub ping event with valid signature")
    void shouldAcceptPingEvent() throws Exception {
        String signature = computeHmacSha256(SAMPLE_PING_PAYLOAD, TEST_SECRET);

        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "ping")
                        .header("X-Hub-Signature-256", signature)
                        .content(SAMPLE_PING_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pong"))
                .andExpect(jsonPath("$.message").value("GitHub webhook configured successfully"));
    }

    @Test
    @DisplayName("Should ignore non-review pull_request action (e.g. closed) with 200 OK")
    void shouldIgnoreClosedPullRequestAction() throws Exception {
        String closedPayload = """
                {
                  "action": "closed",
                  "number": 42,
                  "pull_request": { "number": 42 },
                  "repository": { "full_name": "octocat/Hello-World" }
                }
                """;
        String signature = computeHmacSha256(closedPayload, TEST_SECRET);

        mockMvc.perform(post("/api/webhook/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .content(closedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"))
                .andExpect(jsonPath("$.action").value("closed"));
    }
}
