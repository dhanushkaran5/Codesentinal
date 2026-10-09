package com.codesentinel.agent;

import com.codesentinel.service.GitHubService;
import com.codesentinel.service.StaticAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentToolsTest {

    @Mock
    private GitHubService gitHubService;

    @Mock
    private StaticAnalysisService staticAnalysisService;

    private AgentTools agentTools;

    @BeforeEach
    void setUp() {
        agentTools = new AgentTools(gitHubService, staticAnalysisService);
        ReflectionTestUtils.setField(agentTools, "autoPost", false);
    }

    @Test
    @DisplayName("Tool getPullRequestDiff should delegate to GitHubService")
    void shouldDelegateGetPullRequestDiff() {
        when(gitHubService.getPullRequestDiff("octocat/Hello-World", 42)).thenReturn("diff content");

        String result = agentTools.getPullRequestDiff("octocat/Hello-World", 42);

        assertThat(result).isEqualTo("diff content");
        verify(gitHubService).getPullRequestDiff("octocat/Hello-World", 42);
    }

    @Test
    @DisplayName("Tool getFileContent should delegate to GitHubService")
    void shouldDelegateGetFileContent() {
        when(gitHubService.getFileContent("octocat/Hello-World", "src/Main.java", "sha123")).thenReturn("file content");

        String result = agentTools.getFileContent("octocat/Hello-World", "src/Main.java", "sha123");

        assertThat(result).isEqualTo("file content");
        verify(gitHubService).getFileContent("octocat/Hello-World", "src/Main.java", "sha123");
    }

    @Test
    @DisplayName("Tool runStaticAnalysis should delegate to StaticAnalysisService")
    void shouldDelegateRunStaticAnalysis() {
        when(staticAnalysisService.runStaticAnalysis("String x = 1;")).thenReturn("analysis findings");

        String result = agentTools.runStaticAnalysis("String x = 1;");

        assertThat(result).isEqualTo("analysis findings");
        verify(staticAnalysisService).runStaticAnalysis("String x = 1;");
    }

    @Test
    @DisplayName("Tool postReviewComment and postReviewSummary should respect review.auto-post=false guardrail")
    void shouldRespectAutoPostGuardrailWhenFalse() {
        String commentResult = agentTools.postReviewComment("octocat/Hello-World", 42, "src/App.java", 10, "Fix bug");
        assertThat(commentResult).contains("[GUARDRAIL: review.auto-post is false");
        verify(gitHubService, never()).postReviewComment(anyString(), anyInt(), anyString(), anyInt(), anyString());

        String summaryResult = agentTools.postReviewSummary("octocat/Hello-World", 42, "CHANGES_REQUESTED", "Please fix issues");
        assertThat(summaryResult).contains("[GUARDRAIL: review.auto-post is false");
        verify(gitHubService, never()).postReviewSummary(anyString(), anyInt(), anyString(), anyString());
    }

    @Test
    @DisplayName("Tool postReviewComment should post directly when autoPost is true")
    void shouldPostWhenAutoPostIsTrue() {
        ReflectionTestUtils.setField(agentTools, "autoPost", true);
        when(gitHubService.postReviewComment("octocat/Hello-World", 42, "src/App.java", 10, "Fix bug"))
                .thenReturn("Successfully posted");

        String result = agentTools.postReviewComment("octocat/Hello-World", 42, "src/App.java", 10, "Fix bug");
        assertThat(result).isEqualTo("Successfully posted");
        verify(gitHubService).postReviewComment("octocat/Hello-World", 42, "src/App.java", 10, "Fix bug");
    }

    @Test
    @DisplayName("Should enforce maximum of 10 tool calls per review session")
    void shouldEnforceMaxTenToolCalls() {
        when(gitHubService.getPullRequestDiff(anyString(), anyInt())).thenReturn("diff");

        // Execute 10 tool calls
        for (int i = 1; i <= 10; i++) {
            String res = agentTools.getPullRequestDiff("octocat/Hello-World", 42);
            assertThat(res).isEqualTo("diff");
        }

        // 11th tool call must be blocked per the 10-step guardrail
        String blocked = agentTools.getPullRequestDiff("octocat/Hello-World", 42);
        assertThat(blocked).contains("[STEP LIMIT REACHED");
        assertThat(blocked).contains("maximum limit of 10 steps");
    }

    @Test
    @DisplayName("Should handle tool errors gracefully without throwing exceptions")
    void shouldHandleToolErrorsGracefully() {
        when(gitHubService.getPullRequestDiff("bad/repo", 1)).thenThrow(new RuntimeException("Simulated API failure"));

        String result = agentTools.getPullRequestDiff("bad/repo", 1);

        assertThat(result).contains("Error retrieving PR diff: Simulated API failure");
    }
}
