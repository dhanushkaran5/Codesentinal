package com.codesentinel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service providing static code analysis in the style of PMD and SpotBugs.
 * Detects security vulnerabilities (SQL injection, hardcoded secrets),
 * resource leaks, empty catch blocks, and concurrency issues.
 */
@Service
public class StaticAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StaticAnalysisService.class);

    private record Rule(String name, String severity, Pattern pattern, String description) {}

    private final List<Rule> rules = new ArrayList<>();

    public StaticAnalysisService() {
        initRules();
    }

    private void initRules() {
        // 1. SQL Injection via string concatenation
        rules.add(new Rule(
                "Security/SQLInjection",
                "CRITICAL",
                Pattern.compile("(?i)(SELECT|INSERT INTO|UPDATE|DELETE|FROM)\\s+.*\\+\\s*[a-zA-Z0-9_]+|createNativeQuery\\s*\\(.*\\+"),
                "Potential SQL injection: Dynamic string concatenation detected in SQL statement."
        ));

        // 2. Hardcoded secrets and credentials
        rules.add(new Rule(
                "Security/HardcodedSecret",
                "CRITICAL",
                Pattern.compile("(?i)(password|secret|api_key|token|access_key|auth_token|private_key)\\s*=\\s*[\"'][^\"'\\s]{4,}[\"']"),
                "Potential hardcoded secret or credential detected in source code."
        ));

        // 3. Command injection
        rules.add(new Rule(
                "Security/CommandInjection",
                "CRITICAL",
                Pattern.compile("Runtime\\.getRuntime\\(\\)\\.exec\\(|new\\s+ProcessBuilder\\("),
                "Command execution invocation detected; potential command injection vulnerability if user input is unvalidated."
        ));

        // 4. Empty catch block / swallowed exception
        rules.add(new Rule(
                "ErrorProne/EmptyCatchBlock",
                "MAJOR",
                Pattern.compile("catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}"),
                "Empty catch block silently swallows exceptions, obscuring potential failures."
        ));

        // 5. Insecure thread-unsafe SimpleDateFormat in static field
        rules.add(new Rule(
                "Concurrency/UnsafeSimpleDateFormat",
                "MAJOR",
                Pattern.compile("static\\s+.*SimpleDateFormat"),
                "SimpleDateFormat is not thread-safe and must not be used in static fields. Use java.time.format.DateTimeFormatter instead."
        ));

        // 6. Insecure Random for sensitive operations
        rules.add(new Rule(
                "Security/InsecureRandom",
                "MINOR",
                Pattern.compile("new\\s+(java\\.util\\.)?Random\\("),
                "java.util.Random is not cryptographically secure. Use java.security.SecureRandom for security-sensitive tokens or numbers."
        ));

        // 7. System.out / System.err in production code
        rules.add(new Rule(
                "BestPractice/SystemPrintln",
                "MINOR",
                Pattern.compile("System\\.(out|err)\\.(print|println)"),
                "Direct printing to standard output/error. Prefer structured logging using SLF4J."
        ));

        // 8. Generic Exception caught
        rules.add(new Rule(
                "BestPractice/CatchGenericException",
                "MINOR",
                Pattern.compile("catch\\s*\\(\\s*(java\\.lang\\.)?Exception\\s+[a-zA-Z0-9_]+\\s*\\)"),
                "Catching generic java.lang.Exception may unintentionally intercept unexpected runtime exceptions."
        ));
    }

    /**
     * Analyzes source code and returns PMD/SpotBugs style findings.
     *
     * @param code The source code or patch to analyze
     * @return Formatted static analysis report
     */
    public String runStaticAnalysis(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "No code provided for static analysis.";
        }

        try {
            log.info("Executing static analysis rules on code snippet...");
            String[] lines = code.split("\\r?\\n");
            List<String> findings = new ArrayList<>();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int lineNumber = i + 1;

                for (Rule rule : rules) {
                    Matcher matcher = rule.pattern.matcher(line);
                    if (matcher.find()) {
                        findings.add(String.format("- [%s] Line %d | %s: %s (Snippet: `%s`)",
                                rule.severity, lineNumber, rule.name, rule.description, line.trim()));
                    }
                }
            }

            StringBuilder report = new StringBuilder();
            report.append("=== Static Code Analysis Results (PMD / SpotBugs Rules) ===\n");
            if (findings.isEmpty()) {
                report.append("No rule violations detected. Code passes all configured static analysis checks.\n");
            } else {
                report.append(String.format("Found %d rule violation(s):\n", findings.size()));
                for (String finding : findings) {
                    report.append(finding).append("\n");
                }
            }

            return report.toString();
        } catch (Exception e) {
            log.error("Error executing static analysis: {}", e.getMessage(), e);
            return "Error running static analysis: " + e.getMessage();
        }
    }
}
