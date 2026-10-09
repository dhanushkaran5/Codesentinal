package com.codesentinel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

    @Mock
    private GitHub gitHub;

    @Mock
    private GHRepository repository;

    @Mock
    private GHPullRequest pullRequest;

    @Mock
    private GHCommitPointer headPointer;

    @Mock
    private GHCommitPointer basePointer;

    @Mock
    private GHContent ghContent;

    private GitHubService gitHubService;

    @BeforeEach
    void setUp() {
        gitHubService = new GitHubService(gitHub);
    }

    @Test
    @DisplayName("Should successfully fetch PR diff with valid code changes and head info")
    void shouldFetchPullRequestDiff() throws Exception {
        when(gitHub.getRepository("octocat/Hello-World")).thenReturn(repository);
        when(repository.getPullRequest(42)).thenReturn(pullRequest);

        when(pullRequest.getNumber()).thenReturn(42);
        when(pullRequest.getTitle()).thenReturn("Add security fix");
        when(pullRequest.getHead()).thenReturn(headPointer);
        when(pullRequest.getBase()).thenReturn(basePointer);
        when(headPointer.getRef()).thenReturn("feature/security-patch");
        when(basePointer.getRef()).thenReturn("main");
        when(headPointer.getSha()).thenReturn("a1b2c3d4e5f6");

        GHPullRequestFileDetail fileDetail = mock(GHPullRequestFileDetail.class);
        when(fileDetail.getFilename()).thenReturn("src/main/UserService.java");
        when(fileDetail.getStatus()).thenReturn("modified");
        when(fileDetail.getAdditions()).thenReturn(3);
        when(fileDetail.getDeletions()).thenReturn(1);
        when(fileDetail.getChanges()).thenReturn(4);
        when(fileDetail.getPatch()).thenReturn("@@ -10,3 +10,5 @@\n- String q = 'SELECT * FROM users WHERE id=' + id;\n+ String q = 'SELECT * FROM users WHERE id = ?';");

        PagedIterable<GHPullRequestFileDetail> pagedIterable = mockPagedIterable(List.of(fileDetail));
        when(pullRequest.listFiles()).thenReturn(pagedIterable);

        String result = gitHubService.getPullRequestDiff("octocat/Hello-World", 42);

        assertThat(result).contains("=== Pull Request #42: Add security fix ===");
        assertThat(result).contains("feature/security-patch -> main");
        assertThat(result).contains("Head Commit: a1b2c3d4e5f6");
        assertThat(result).contains("--- a/src/main/UserService.java");
        assertThat(result).contains("+ String q = 'SELECT * FROM users WHERE id = ?';");
    }

    @Test
    @DisplayName("Should skip lock files and binary files and document them in diff summary")
    void shouldSkipLockFilesAndBinaryFilesInDiff() throws Exception {
        when(gitHub.getRepository("octocat/Hello-World")).thenReturn(repository);
        when(repository.getPullRequest(42)).thenReturn(pullRequest);
        when(pullRequest.getNumber()).thenReturn(42);
        when(pullRequest.getTitle()).thenReturn("Update assets and deps");
        when(pullRequest.getHead()).thenReturn(headPointer);
        when(pullRequest.getBase()).thenReturn(basePointer);
        when(headPointer.getRef()).thenReturn("chore/deps");
        when(basePointer.getRef()).thenReturn("main");
        when(headPointer.getSha()).thenReturn("c0ffee123456");

        GHPullRequestFileDetail lockFile = mock(GHPullRequestFileDetail.class);
        when(lockFile.getFilename()).thenReturn("package-lock.json");

        GHPullRequestFileDetail binaryFile = mock(GHPullRequestFileDetail.class);
        when(binaryFile.getFilename()).thenReturn("assets/logo.png");

        GHPullRequestFileDetail codeFile = mock(GHPullRequestFileDetail.class);
        when(codeFile.getFilename()).thenReturn("src/App.js");
        when(codeFile.getStatus()).thenReturn("modified");
        when(codeFile.getAdditions()).thenReturn(2);
        when(codeFile.getDeletions()).thenReturn(0);
        when(codeFile.getChanges()).thenReturn(2);
        when(codeFile.getPatch()).thenReturn("@@ -1,2 +1,4 @@\n+ import Logo from './logo';");

        PagedIterable<GHPullRequestFileDetail> pagedIterable = mockPagedIterable(List.of(lockFile, binaryFile, codeFile));
        when(pullRequest.listFiles()).thenReturn(pagedIterable);

        String result = gitHubService.getPullRequestDiff("octocat/Hello-World", 42);

        assertThat(result).contains("=== Skipped Files (per review guardrails) ===");
        assertThat(result).contains("package-lock.json (Lock file excluded from automated review)");
        assertThat(result).contains("assets/logo.png (Binary file or no patch available)");
        assertThat(result).contains("--- a/src/App.js");
    }

    @Test
    @DisplayName("Should fetch and format file content with line numbers")
    void shouldFetchFileContent() throws Exception {
        when(gitHub.getRepository("octocat/Hello-World")).thenReturn(repository);
        when(repository.getFileContent("src/App.java", "main")).thenReturn(ghContent);
        when(ghContent.isFile()).thenReturn(true);

        String fileBody = "package com.example;\n\npublic class App {\n    public static void main(String[] args) {}\n}";
        InputStream is = new ByteArrayInputStream(fileBody.getBytes(StandardCharsets.UTF_8));
        when(ghContent.read()).thenReturn(is);

        String content = gitHubService.getFileContent("octocat/Hello-World", "src/App.java", "main");

        assertThat(content).contains("=== File: src/App.java (ref: main, 5 lines) ===");
        assertThat(content).contains("   1 | package com.example;");
        assertThat(content).contains("   3 | public class App {");
    }

    @Test
    @DisplayName("Should skip files exceeding 500 lines when fetching content")
    void shouldSkipLargeFilesExceeding500Lines() throws Exception {
        when(gitHub.getRepository("octocat/Hello-World")).thenReturn(repository);
        when(repository.getFileContent("src/HugeFile.java", "main")).thenReturn(ghContent);
        when(ghContent.isFile()).thenReturn(true);

        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 505; i++) {
            sb.append("line ").append(i).append("\n");
        }
        InputStream is = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
        when(ghContent.read()).thenReturn(is);

        String content = gitHubService.getFileContent("octocat/Hello-World", "src/HugeFile.java", "main");

        assertThat(content).contains("[SKIPPED: File 'src/HugeFile.java' exceeds 500 lines limit");
    }

    @Test
    @DisplayName("Should directly skip lock and binary files without calling GitHub API")
    void shouldDirectlySkipLockAndBinaryFiles() {
        String lockResult = gitHubService.getFileContent("octocat/Hello-World", "yarn.lock", "main");
        assertThat(lockResult).contains("[SKIPPED: File 'yarn.lock' is a lock file");

        String binaryResult = gitHubService.getFileContent("octocat/Hello-World", "photo.jpg", "main");
        assertThat(binaryResult).contains("[SKIPPED: File 'photo.jpg' is a binary file");

        verifyNoInteractions(gitHub);
    }

    @Test
    @DisplayName("Should post review comment to GitHub using commit SHA and line number")
    void shouldPostReviewComment() throws Exception {
        when(gitHub.getRepository("octocat/Hello-World")).thenReturn(repository);
        when(repository.getPullRequest(42)).thenReturn(pullRequest);
        when(pullRequest.getHead()).thenReturn(headPointer);
        when(headPointer.getSha()).thenReturn("commit_sha_123");

        GHPullRequestReviewCommentBuilder commentBuilder = mock(GHPullRequestReviewCommentBuilder.class);
        when(pullRequest.createReviewComment()).thenReturn(commentBuilder);
        when(commentBuilder.commitId("commit_sha_123")).thenReturn(commentBuilder);
        when(commentBuilder.path("src/App.java")).thenReturn(commentBuilder);
        when(commentBuilder.line(15)).thenReturn(commentBuilder);
        when(commentBuilder.body("Fix this issue")).thenReturn(commentBuilder);
        when(commentBuilder.create()).thenReturn(mock(GHPullRequestReviewComment.class));

        String result = gitHubService.postReviewComment("octocat/Hello-World", 42, "src/App.java", 15, "Fix this issue");

        assertThat(result).contains("Successfully posted inline comment on src/App.java:15");
        verify(commentBuilder).create();
    }

    @Test
    @DisplayName("Should post review summary comment to GitHub PR")
    void shouldPostReviewSummary() throws Exception {
        when(gitHub.getRepository("octocat/Hello-World")).thenReturn(repository);
        when(repository.getPullRequest(42)).thenReturn(pullRequest);

        String result = gitHubService.postReviewSummary("octocat/Hello-World", 42, "APPROVED", "All checks passed.");

        assertThat(result).contains("Successfully posted review summary comment on PR #42");
        verify(pullRequest).comment(contains("APPROVED"));
    }

    @Test
    @DisplayName("Should retrieve head commit SHA for PR")
    void shouldGetHeadCommitSha() throws Exception {
        when(gitHub.getRepository("octocat/Hello-World")).thenReturn(repository);
        when(repository.getPullRequest(42)).thenReturn(pullRequest);
        when(pullRequest.getHead()).thenReturn(headPointer);
        when(headPointer.getSha()).thenReturn("head_sha_999");

        String sha = gitHubService.getHeadCommitSha("octocat/Hello-World", 42);

        assertThat(sha).isEqualTo("head_sha_999");
    }

    @Test
    @DisplayName("Should handle GitHub API exceptions gracefully without crashing")
    void shouldHandleGitHubApiErrorsGracefully() throws Exception {
        when(gitHub.getRepository(anyString())).thenThrow(new IOException("Repository not found or rate limit exceeded"));

        String diff = gitHubService.getPullRequestDiff("unknown/repo", 99);
        assertThat(diff).contains("Error retrieving PR diff: Repository not found");

        String content = gitHubService.getFileContent("unknown/repo", "README.md", "main");
        assertThat(content).contains("Error retrieving file content: Repository not found");
    }

    @SuppressWarnings("unchecked")
    private <T> PagedIterable<T> mockPagedIterable(List<T> list) throws IOException {
        PagedIterable<T> pagedIterable = mock(PagedIterable.class);
        when(pagedIterable.toList()).thenReturn(list);
        return pagedIterable;
    }
}
