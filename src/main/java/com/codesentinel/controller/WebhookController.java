package com.codesentinel.controller;

import com.codesentinel.config.GitHubConfig;
import com.codesentinel.model.Review;
import com.codesentinel.service.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/**
 * Controller for receiving GitHub webhook events.
 * Handles HMAC-SHA256 signature verification (X-Hub-Signature-256)
 * and dispatches pull_request events to ReviewService.
 */
@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final GitHubConfig gitHubConfig;
    private final ObjectMapper objectMapper;
    private final ReviewService reviewService;

    public WebhookController(GitHubConfig gitHubConfig, ObjectMapper objectMapper, ReviewService reviewService) {
        this.gitHubConfig = gitHubConfig;
        this.objectMapper = objectMapper;
        this.reviewService = reviewService;
    }

    /**
     * Endpoint for GitHub webhooks.
     * Verifies the HMAC-SHA256 signature and processes pull_request events.
     *
     * @param signatureHeader X-Hub-Signature-256 header sent by GitHub
     * @param eventType       X-GitHub-Event header sent by GitHub
     * @param rawPayload      The raw request body payload
     * @return ResponseEntity with status and details
     */
    @PostMapping(value = "/github", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handleGitHubWebhook(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestHeader(value = "X-GitHub-Event", required = false, defaultValue = "unknown") String eventType,
            @RequestBody String rawPayload) {

        // 1. Verify webhook signature
        if (!verifySignature(rawPayload, signatureHeader)) {
            log.warn("Unauthorized webhook request: signature verification failed for event '{}'", eventType);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "status", "error",
                            "message", "Invalid or missing X-Hub-Signature-256 signature"
                    ));
        }

        log.info("Received valid GitHub webhook event: {}", eventType);

        // 2. Handle 'ping' event sent upon webhook setup
        if ("ping".equalsIgnoreCase(eventType)) {
            log.info("GitHub ping event acknowledged successfully");
            return ResponseEntity.ok(Map.of(
                    "status", "pong",
                    "message", "GitHub webhook configured successfully"
            ));
        }

        // 3. Handle 'pull_request' events
        if ("pull_request".equalsIgnoreCase(eventType)) {
            try {
                JsonNode root = objectMapper.readTree(rawPayload);
                String action = root.path("action").asText("");
                int prNumber = root.path("pull_request").path("number").asInt(-1);
                String repoFullName = root.path("repository").path("full_name").asText("");

                log.info("Received pull_request event. Action: '{}', Repo: '{}', PR: #{}", action, repoFullName, prNumber);

                if ("opened".equalsIgnoreCase(action) || "synchronize".equalsIgnoreCase(action) || "reopened".equalsIgnoreCase(action)) {
                    log.info("Processing review for repository '{}', PR #{} (action: {})", repoFullName, prNumber, action);

                    Review review = reviewService.processPullRequest(repoFullName, prNumber);

                    return ResponseEntity.ok(Map.of(
                            "status", "accepted",
                            "repository", repoFullName,
                            "prNumber", prNumber,
                            "action", action,
                            "reviewId", review != null && review.getId() != null ? review.getId() : -1,
                            "reviewStatus", review != null ? review.getStatus() : "DRAFT",
                            "message", "Pull request review processed. Status: " + (review != null ? review.getStatus() : "DRAFT")
                    ));
                } else {
                    log.info("Ignoring pull_request action: '{}'", action);
                    return ResponseEntity.ok(Map.of(
                            "status", "ignored",
                            "action", action,
                            "message", "Action does not trigger a code review"
                    ));
                }
            } catch (Exception e) {
                log.error("Failed to parse or process pull_request payload: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("status", "error", "message", "Invalid payload or processing error: " + e.getMessage()));
            }
        }

        // 4. Any other unhandled event
        return ResponseEntity.ok(Map.of(
                "status", "ignored",
                "event", eventType,
                "message", "Event type not processed by CodeSentinel"
        ));
    }

    /**
     * Validates GitHub webhook HMAC-SHA256 signature against GITHUB_WEBHOOK_SECRET.
     * Uses constant-time comparison to prevent timing attacks.
     */
    public boolean verifySignature(String payload, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        String secret = gitHubConfig.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("GITHUB_WEBHOOK_SECRET is not configured; rejecting webhook request.");
            return false;
        }

        try {
            String expectedSignature = signatureHeader.substring(SIGNATURE_PREFIX.length()).trim();

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);

            byte[] computedHashBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedSignature = HexFormat.of().formatHex(computedHashBytes);

            return MessageDigest.isEqual(
                    computedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error computing webhook HMAC signature: {}", e.getMessage());
            return false;
        }
    }
}
