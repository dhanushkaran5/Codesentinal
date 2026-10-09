package com.codesentinel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAnalysisServiceTest {

    private StaticAnalysisService staticAnalysisService;

    @BeforeEach
    void setUp() {
        staticAnalysisService = new StaticAnalysisService();
    }

    @Test
    @DisplayName("Should detect SQL Injection via string concatenation")
    void shouldDetectSqlInjection() {
        String code = """
                public User getUser(String id) {
                    String sql = "SELECT * FROM users WHERE id = " + id;
                    return query(sql);
                }
                """;

        String report = staticAnalysisService.runStaticAnalysis(code);

        assertThat(report).contains("Security/SQLInjection");
        assertThat(report).contains("[CRITICAL]");
        assertThat(report).contains("Line 2");
    }

    @Test
    @DisplayName("Should detect hardcoded secrets and API keys")
    void shouldDetectHardcodedSecrets() {
        String code = """
                public class Config {
                    private String apiKey = "sk_live_1234567890abcdef";
                    private String password = "SuperSecretPassword123";
                }
                """;

        String report = staticAnalysisService.runStaticAnalysis(code);

        assertThat(report).contains("Security/HardcodedSecret");
        assertThat(report).contains("[CRITICAL]");
    }

    @Test
    @DisplayName("Should detect empty catch blocks and swallowed exceptions")
    void shouldDetectEmptyCatchBlock() {
        String code = """
                try {
                    doRiskyOperation();
                } catch (IOException e) {}
                """;

        String report = staticAnalysisService.runStaticAnalysis(code);

        assertThat(report).contains("ErrorProne/EmptyCatchBlock");
        assertThat(report).contains("[MAJOR]");
    }

    @Test
    @DisplayName("Should return clean status when no rule violations exist")
    void shouldPassCleanCode() {
        String cleanCode = """
                public int sum(int a, int b) {
                    return a + b;
                }
                """;

        String report = staticAnalysisService.runStaticAnalysis(cleanCode);

        assertThat(report).contains("No rule violations detected");
    }

    @Test
    @DisplayName("Should handle null or empty input gracefully")
    void shouldHandleEmptyInputGracefully() {
        assertThat(staticAnalysisService.runStaticAnalysis(null)).contains("No code provided");
        assertThat(staticAnalysisService.runStaticAnalysis("   ")).contains("No code provided");
    }
}
