package com.codesentinel.config;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Configuration for GitHub API integration.
 * Securely loads credentials from environment variables without logging sensitive tokens.
 */
@Configuration
public class GitHubConfig {

    private static final Logger log = LoggerFactory.getLogger(GitHubConfig.class);

    @Value("${github.token:}")
    private String githubToken;

    @Value("${github.webhook-secret:}")
    private String webhookSecret;

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public String getGithubToken() {
        return githubToken;
    }

    /**
     * Creates a Kohsuke GitHub API client bean.
     * Uses the provided token if present, otherwise creates an anonymous client.
     */
    @Bean
    public GitHub gitHub() throws IOException {
        if (githubToken != null && !githubToken.trim().isEmpty()) {
            log.info("Configuring authenticated GitHub client");
            return new GitHubBuilder().withOAuthToken(githubToken.trim()).build();
        } else {
            log.warn("GITHUB_TOKEN not provided. Initializing anonymous GitHub client (rate limited).");
            return new GitHubBuilder().build();
        }
    }
}
