package com.codesentinel.service;

import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service responsible for interacting with GitHub repositories and pull requests
 * via the Kohsuke GitHub API.
 * Enforces guardrails: skips lock files, binary files, and files larger than 500 lines.
 */
@Service
public class GitHubService {

    private static final Logger log = LoggerFactory.getLogger(GitHubService.class);
    private static final int MAX_FILE_LINES = 500;

    // Common lock files to skip per review guardrails
    private static final Set<String> LOCK_FILES = Set.of(
            "package-lock.json",
            "yarn.lock",
            "pnpm-lock.yaml",
            "gemfile.lock",
            "cargo.lock",
            "composer.lock",
            "poetry.lock",
            "go.sum",
            "gradle.lockfile",
            "packages.lock.json",
            "mix.lock"
    );

    // Common binary file extensions to skip per review guardrails
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".ico", ".webp",
            ".pdf", ".zip", ".tar", ".gz", ".tgz", ".7z", ".rar",
            ".jar", ".war", ".class", ".exe", ".dll", ".so", ".dylib",
            ".woff", ".woff2", ".ttf", ".eot", ".mp3", ".mp4", ".wav",
            ".bin", ".iso", ".dmg"
    );

    private final GitHub gitHub;

    public GitHubService(GitHub gitHub) {
        this.gitHub = gitHub;
    }

    /**
     * Fetches the PR details and changed file diffs.
     * Skips binary files, lock files, and files exceeding the 500 lines limit.
     *
     * @param repoName repository full name (e.g. "octocat/Hello-World")
     * @param prNumber pull request number
     * @return Formatted diff text including skipped files list and line changes
     */
    public String getPullRequestDiff(String repoName, int prNumber) {
        try {
            log.info("Fetching pull request diff for {} #{}", repoName, prNumber);
            GHRepository repository = gitHub.getRepository(repoName);
            GHPullRequest pr = repository.getPullRequest(prNumber);

            StringBuilder diffBuilder = new StringBuilder();
            diffBuilder.append(String.format("=== Pull Request #%d: %s ===\n", pr.getNumber(), pr.getTitle()));
            diffBuilder.append(String.format("Repository: %s\n", repoName));
            diffBuilder.append(String.format("Branches: %s -> %s\n", pr.getHead().getRef(), pr.getBase().getRef()));
            diffBuilder.append(String.format("Head Commit: %s\n\n", pr.getHead().getSha()));

            List<String> skippedFiles = new ArrayList<>();
            List<GHPullRequestFileDetail> validFiles = new ArrayList<>();

            for (GHPullRequestFileDetail fileDetail : pr.listFiles().toList()) {
                String filename = fileDetail.getFilename();

                if (isLockFile(filename)) {
                    skippedFiles.add(String.format("%s (Lock file excluded from automated review)", filename));
                    continue;
                }

                if (isBinaryFile(filename) || fileDetail.getPatch() == null) {
                    skippedFiles.add(String.format("%s (Binary file or no patch available)", filename));
                    continue;
                }

                int patchLines = countLines(fileDetail.getPatch());
                if (patchLines > MAX_FILE_LINES || fileDetail.getChanges() > MAX_FILE_LINES) {
                    skippedFiles.add(String.format("%s (Diff exceeds %d lines limit: %d lines changed)",
                            filename, MAX_FILE_LINES, Math.max(patchLines, fileDetail.getChanges())));
                    continue;
                }

                validFiles.add(fileDetail);
            }

            // Summary of skipped files
            if (!skippedFiles.isEmpty()) {
                diffBuilder.append("=== Skipped Files (per review guardrails) ===\n");
                for (String skipped : skippedFiles) {
                    diffBuilder.append("- ").append(skipped).append("\n");
                }
                diffBuilder.append("\n");
            }

            // Diffs of valid files
            diffBuilder.append(String.format("=== Changed Files (%d active files) ===\n\n", validFiles.size()));
            for (GHPullRequestFileDetail file : validFiles) {
                diffBuilder.append(String.format("--- a/%s\n", file.getFilename()));
                diffBuilder.append(String.format("+++ b/%s\n", file.getFilename()));
                diffBuilder.append(String.format("Status: %s (+%d / -%d)\n", file.getStatus(), file.getAdditions(), file.getDeletions()));
                diffBuilder.append(file.getPatch()).append("\n\n");
            }

            return diffBuilder.toString();
        } catch (Exception e) {
            log.error("Failed to fetch PR diff for {} #{}: {}", repoName, prNumber, e.getMessage());
            return "Error retrieving PR diff: " + e.getMessage();
        }
    }

    /**
     * Retrieves the complete content of a file in the repository at a specific git ref/commit/branch.
     * Enforces the 500-line guardrail and checks for lock/binary files.
     *
     * @param repoName repository full name
     * @param path     file path within repository
     * @param ref      git commit SHA or branch name
     * @return Content of the file with line numbers, or error/skip message
     */
    public String getFileContent(String repoName, String path, String ref) {
        if (isLockFile(path)) {
            return String.format("[SKIPPED: File '%s' is a lock file and is excluded from review]", path);
        }

        if (isBinaryFile(path)) {
            return String.format("[SKIPPED: File '%s' is a binary file and cannot be displayed as text]", path);
        }

        try {
            log.info("Fetching file content for {}/{} at ref {}", repoName, path, ref);
            GHRepository repository = gitHub.getRepository(repoName);
            GHContent content = repository.getFileContent(path, ref);

            if (!content.isFile()) {
                return String.format("[ERROR: Path '%s' is not a file]", path);
            }

            try (InputStream is = content.read();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    if (lines.size() > MAX_FILE_LINES) {
                        return String.format("[SKIPPED: File '%s' exceeds %d lines limit (contains > %d lines). " +
                                "Per guardrails, large files are excluded from automated review]", path, MAX_FILE_LINES, MAX_FILE_LINES);
                    }
                }

                StringBuilder numberedContent = new StringBuilder();
                numberedContent.append(String.format("=== File: %s (ref: %s, %d lines) ===\n", path, ref, lines.size()));
                for (int i = 0; i < lines.size(); i++) {
                    numberedContent.append(String.format("%4d | %s\n", i + 1, lines.get(i)));
                }

                return numberedContent.toString();
            }
        } catch (Exception e) {
            log.error("Failed to fetch file content for {}/{} at ref {}: {}", repoName, path, ref, e.getMessage());
            return "Error retrieving file content: " + e.getMessage();
        }
    }

    /**
     * Posts an inline review comment on a specific line of a file in a pull request.
     */
    public String postReviewComment(String repoName, int prNumber, String file, int line, String body) {
        try {
            log.info("Posting inline comment to {} #{} on {}:{}", repoName, prNumber, file, line);
            GHRepository repository = gitHub.getRepository(repoName);
            GHPullRequest pr = repository.getPullRequest(prNumber);
            String commitSha = pr.getHead().getSha();

            pr.createReviewComment()
                    .commitId(commitSha)
                    .path(file)
                    .line(line)
                    .body(body)
                    .create();

            return String.format("Successfully posted inline comment on %s:%d", file, line);
        } catch (Exception e) {
            log.error("Failed to post inline comment to {} #{}: {}", repoName, prNumber, e.getMessage());
            return "Error posting inline review comment: " + e.getMessage();
        }
    }

    /**
     * Posts the overall review summary comment to the pull request.
     */
    public String postReviewSummary(String repoName, int prNumber, String verdict, String body) {
        try {
            log.info("Posting review summary to {} #{}, verdict: {}", repoName, prNumber, verdict);
            GHRepository repository = gitHub.getRepository(repoName);
            GHPullRequest pr = repository.getPullRequest(prNumber);

            String formattedSummary = String.format("### 🛡️ CodeSentinel Review Verdict: **%s**\n\n%s", verdict, body);
            pr.comment(formattedSummary);

            return "Successfully posted review summary comment on PR #" + prNumber;
        } catch (Exception e) {
            log.error("Failed to post review summary to {} #{}: {}", repoName, prNumber, e.getMessage());
            return "Error posting review summary: " + e.getMessage();
        }
    }

    /**
     * Retrieves the latest commit SHA for the PR head branch.
     */
    public String getHeadCommitSha(String repoName, int prNumber) {
        try {
            GHRepository repository = gitHub.getRepository(repoName);
            GHPullRequest pr = repository.getPullRequest(prNumber);
            return pr.getHead().getSha();
        } catch (Exception e) {
            log.error("Failed to get head commit SHA for {} #{}: {}", repoName, prNumber, e.getMessage());
            return "";
        }
    }

    /**
     * Checks if the filename corresponds to a package manager lock file.
     */
    public boolean isLockFile(String path) {
        if (path == null) return false;
        String fileName = extractFileName(path).toLowerCase(Locale.ROOT);
        return LOCK_FILES.contains(fileName);
    }

    /**
     * Checks if the filename corresponds to a known binary format.
     */
    public boolean isBinaryFile(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return BINARY_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private String extractFileName(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
    }

    private int countLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
}
