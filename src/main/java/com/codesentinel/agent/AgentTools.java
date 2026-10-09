package com.codesentinel.agent;

import com.codesentinel.service.GitHubService;
import com.codesentinel.service.StaticAnalysisService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tools available to the LangChain4j ReviewAgent for code inspection and analysis.
 * Enforces guardrails: error resilience without crashing, max 10 steps, and auto-post verification.
 */
@Component
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);
    private static final int MAX_TOOL_STEPS = 10;

    private final GitHubService gitHubService;
    private final StaticAnalysisService staticAnalysisService;
    private final AtomicInteger toolStepCounter = new AtomicInteger(0);

    @Value("${review.auto-post:false}")
    private boolean autoPost;

    public AgentTools(GitHubService gitHubService, StaticAnalysisService staticAnalysisService) {
        this.gitHubService = gitHubService;
        this.staticAnalysisService = staticAnalysisService;
    }

    /**
     * Resets the execution step counter before each review session.
     */
    public void resetCounter() {
        toolStepCounter.set(0);
    }

    public int getCurrentStepCount() {
        return toolStepCounter.get();
    }

    private boolean checkStepLimit(StringBuilder limitMsg) {
        int current = toolStepCounter.incrementAndGet();
        if (current > MAX_TOOL_STEPS) {
            log.warn("ReviewAgent reached maximum tool-call step limit ({}/{})", current, MAX_TOOL_STEPS);
            limitMsg.append(String.format("[STEP LIMIT REACHED: You have executed %d tool calls, reaching the maximum limit of %d steps. Please finalize and return your structured review findings now.]", current, MAX_TOOL_STEPS));
            return false;
        }
        return true;
    }

    @Tool("Fetches the pull request diff, commit metadata, and list of changed files for a repository and PR number. Also identifies any files skipped due to format or size.")
    public String getPullRequestDiff(
            @P("Repository full name in 'owner/repo' format, e.g. octocat/Hello-World") String repo,
            @P("Pull request number, e.g. 42") int prNumber) {
        StringBuilder limitMsg = new StringBuilder();
        if (!checkStepLimit(limitMsg)) {
            return limitMsg.toString();
        }

        try {
            log.info("Tool getPullRequestDiff invoked for {} #{} (step {})", repo, prNumber, toolStepCounter.get());
            return gitHubService.getPullRequestDiff(repo, prNumber);
        } catch (Exception e) {
            log.error("Error in tool getPullRequestDiff: {}", e.getMessage());
            return "Error retrieving PR diff: " + e.getMessage();
        }
    }

    @Tool("Retrieves the full content of a specific file in the repository at a given git commit reference, branch, or ref. Lines are numbered for accurate reference. Skips files > 500 lines.")
    public String getFileContent(
            @P("Repository full name in 'owner/repo' format") String repo,
            @P("Relative path to the file within the repository") String path,
            @P("Git reference, commit SHA, or branch name") String ref) {
        StringBuilder limitMsg = new StringBuilder();
        if (!checkStepLimit(limitMsg)) {
            return limitMsg.toString();
        }

        try {
            log.info("Tool getFileContent invoked for {}/{} at ref {} (step {})", repo, path, ref, toolStepCounter.get());
            return gitHubService.getFileContent(repo, path, ref);
        } catch (Exception e) {
            log.error("Error in tool getFileContent: {}", e.getMessage());
            return "Error retrieving file content: " + e.getMessage();
        }
    }

    @Tool("Runs PMD and SpotBugs style static code analysis on the provided source code to detect security risks (SQL injection, hardcoded secrets), resource leaks, empty catches, and code smells.")
    public String runStaticAnalysis(
            @P("Source code or diff snippet to analyze") String code) {
        StringBuilder limitMsg = new StringBuilder();
        if (!checkStepLimit(limitMsg)) {
            return limitMsg.toString();
        }

        try {
            log.info("Tool runStaticAnalysis invoked (step {})", toolStepCounter.get());
            return staticAnalysisService.runStaticAnalysis(code);
        } catch (Exception e) {
            log.error("Error in tool runStaticAnalysis: {}", e.getMessage());
            return "Error running static analysis: " + e.getMessage();
        }
    }

    @Tool("Adds an inline review comment on a specific line of code in a pull request file.")
    public String postReviewComment(
            @P("Repository full name in 'owner/repo' format") String repo,
            @P("Pull request number") int prNumber,
            @P("Relative path to the file being commented on") String file,
            @P("1-based line number in the file") int line,
            @P("Markdown comment body explaining the issue and suggested fix") String body) {
        StringBuilder limitMsg = new StringBuilder();
        if (!checkStepLimit(limitMsg)) {
            return limitMsg.toString();
        }

        if (!autoPost) {
            log.info("review.auto-post is false: inline comment for {}:{} queued as draft.", file, line);
            return String.format("[GUARDRAIL: review.auto-post is false. Inline comment on %s:%d will be saved as DRAFT and posted after approval.]", file, line);
        }

        try {
            log.info("Tool postReviewComment posting to {} #{} on {}:{} (step {})", repo, prNumber, file, line, toolStepCounter.get());
            return gitHubService.postReviewComment(repo, prNumber, file, line, body);
        } catch (Exception e) {
            log.error("Error in tool postReviewComment: {}", e.getMessage());
            return "Error posting inline review comment: " + e.getMessage();
        }
    }

    @Tool("Posts the overall review summary and verdict on the pull request.")
    public String postReviewSummary(
            @P("Repository full name in 'owner/repo' format") String repo,
            @P("Pull request number") int prNumber,
            @P("Review verdict: APPROVED, CHANGES_REQUESTED, or COMMENT") String verdict,
            @P("Overall summary text and conclusions") String body) {
        StringBuilder limitMsg = new StringBuilder();
        if (!checkStepLimit(limitMsg)) {
            return limitMsg.toString();
        }

        if (!autoPost) {
            log.info("review.auto-post is false: review summary for PR #{} queued as draft.", prNumber);
            return String.format("[GUARDRAIL: review.auto-post is false. Review summary (verdict: %s) will be saved as DRAFT and posted after approval.]", verdict);
        }

        try {
            log.info("Tool postReviewSummary posting to {} #{}, verdict: {} (step {})", repo, prNumber, verdict, toolStepCounter.get());
            return gitHubService.postReviewSummary(repo, prNumber, verdict, body);
        } catch (Exception e) {
            log.error("Error in tool postReviewSummary: {}", e.getMessage());
            return "Error posting review summary: " + e.getMessage();
        }
    }
}
