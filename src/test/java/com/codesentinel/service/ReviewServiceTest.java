package com.codesentinel.service;

import com.codesentinel.agent.AgentTools;
import com.codesentinel.agent.ReviewAgent;
import com.codesentinel.model.Finding;
import com.codesentinel.model.Review;
import com.codesentinel.model.ReviewFinding;
import com.codesentinel.model.ReviewResult;
import com.codesentinel.model.Severity;
import com.codesentinel.repository.ReviewRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewAgent reviewAgent;

    @Mock
    private GitHubService gitHubService;

    @Mock
    private AgentTools agentTools;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ChatLanguageModel chatLanguageModel;

    private static final String SAMPLE_VULNERABLE_DIFF = """
            --- a/src/main/java/com/example/UserService.java
            +++ b/src/main/java/com/example/UserService.java
            @@ -15,4 +15,6 @@
             public User findByUsername(String username) {
            -    return userRepository.findByName(username);
            +    String query = "SELECT * FROM users WHERE username = '" + username + "'";
            +    return entityManager.createNativeQuery(query, User.class).getSingleResult();
             }
            """;

    @Test
    @DisplayName("Should successfully dispatch diff to ReviewAgent and print review to console")
    void shouldReviewDiffAndLogToConsole() {
        ReviewService reviewService = new ReviewService(reviewAgent, gitHubService, agentTools, reviewRepository);

        String simulatedReview = "### CodeSentinel Review: [CRITICAL] SQL Injection";
        when(reviewAgent.reviewDiff(SAMPLE_VULNERABLE_DIFF)).thenReturn(simulatedReview);

        String result = reviewService.reviewDiffAndLog(SAMPLE_VULNERABLE_DIFF);

        assertThat(result).contains("[CRITICAL]");
        verify(reviewAgent, times(1)).reviewDiff(SAMPLE_VULNERABLE_DIFF);
    }

    @Test
    @DisplayName("Should save review as DRAFT and not post comments to GitHub when autoPost is false")
    void shouldSaveReviewAsDraftWhenAutoPostIsFalse() {
        ReviewService reviewService = new ReviewService(reviewAgent, gitHubService, agentTools, reviewRepository);
        reviewService.setAutoPost(false);

        ReviewFinding finding = new ReviewFinding(
                "src/main/UserService.java",
                16,
                Severity.CRITICAL,
                "SQL Injection vulnerability",
                "Use parameterized query"
        );
        ReviewResult agentResult = new ReviewResult("CHANGES_REQUESTED", "Security issue found", List.of(finding), List.of("package-lock.json"));

        when(reviewAgent.reviewPullRequest(anyString())).thenReturn(agentResult);
        when(gitHubService.getHeadCommitSha("octocat/Hello-World", 42)).thenReturn("commit123456");
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review savedReview = reviewService.processPullRequest("octocat/Hello-World", 42);

        assertThat(savedReview.getStatus()).isEqualTo("DRAFT");
        assertThat(savedReview.getVerdict()).isEqualTo("CHANGES_REQUESTED");
        assertThat(savedReview.getFindings()).hasSize(1);
        assertThat(savedReview.getSkippedFiles()).contains("package-lock.json");

        // Guardrail verified: NO comments posted to GitHub while in DRAFT
        verify(gitHubService, never()).postReviewComment(anyString(), anyInt(), anyString(), anyInt(), anyString());
        verify(gitHubService, never()).postReviewSummary(anyString(), anyInt(), anyString(), anyString());
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("Should post comments to GitHub immediately when autoPost is true")
    void shouldPostImmediatelyWhenAutoPostIsTrue() {
        ReviewService reviewService = new ReviewService(reviewAgent, gitHubService, agentTools, reviewRepository);
        reviewService.setAutoPost(true);

        ReviewFinding finding = new ReviewFinding(
                "src/main/UserService.java",
                16,
                Severity.CRITICAL,
                "SQL Injection vulnerability",
                "Use parameterized query"
        );
        ReviewResult agentResult = new ReviewResult("CHANGES_REQUESTED", "Security issue found", List.of(finding), List.of());

        when(reviewAgent.reviewPullRequest(anyString())).thenReturn(agentResult);
        when(gitHubService.getHeadCommitSha("octocat/Hello-World", 42)).thenReturn("commit123456");
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review savedReview = reviewService.processPullRequest("octocat/Hello-World", 42);

        assertThat(savedReview.getStatus()).isEqualTo("POSTED");
        assertThat(savedReview.getPostedAt()).isNotNull();

        // Comments must be posted to GitHub immediately
        verify(gitHubService).postReviewComment(eq("octocat/Hello-World"), eq(42), eq("src/main/UserService.java"), eq(16), anyString());
        verify(gitHubService).postReviewSummary(eq("octocat/Hello-World"), eq(42), eq("CHANGES_REQUESTED"), anyString());
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("Should approve DRAFT review and post all comments to GitHub")
    void shouldApproveDraftReviewAndPostComments() {
        ReviewService reviewService = new ReviewService(reviewAgent, gitHubService, agentTools, reviewRepository);

        Review draftReview = new Review("octocat/Hello-World", 42, "commit123456", "CHANGES_REQUESTED", "Fix security bug", "DRAFT");
        draftReview.setId(10L);
        draftReview.addFinding(new Finding("src/Main.java", 20, Severity.CRITICAL, "SQL Injection", "Use param"));

        when(reviewRepository.findById(10L)).thenReturn(Optional.of(draftReview));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review approvedReview = reviewService.approveAndPostReview(10L);

        assertThat(approvedReview.getStatus()).isEqualTo("POSTED");
        assertThat(approvedReview.getPostedAt()).isNotNull();

        verify(gitHubService).postReviewComment(eq("octocat/Hello-World"), eq(42), eq("src/Main.java"), eq(20), anyString());
        verify(gitHubService).postReviewSummary(eq("octocat/Hello-World"), eq(42), eq("CHANGES_REQUESTED"), anyString());
    }

    @Test
    @DisplayName("Should throw exception when attempting to approve already POSTED review")
    void shouldRejectApprovingAlreadyPostedReview() {
        ReviewService reviewService = new ReviewService(reviewAgent, gitHubService, agentTools, reviewRepository);

        Review postedReview = new Review("octocat/Hello-World", 42, "commit123456", "APPROVED", "All good", "POSTED");
        postedReview.setId(10L);

        when(reviewRepository.findById(10L)).thenReturn(Optional.of(postedReview));

        assertThatThrownBy(() -> reviewService.approveAndPostReview(10L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been posted");
    }

    @Test
    @DisplayName("Should wire LangChain4j ReviewAgent with ChatLanguageModel and generate review")
    void shouldWireLangChain4jReviewAgentWithChatModel() {
        String mockResponse = "Review complete: [CRITICAL] SQL Injection on line 16 in UserService.java";
        when(chatLanguageModel.generate(anyList())).thenReturn(Response.from(AiMessage.from(mockResponse)));

        ReviewAgent agent = AiServices.builder(ReviewAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        String review = agent.reviewDiff(SAMPLE_VULNERABLE_DIFF);

        assertThat(review).contains("Review complete: [CRITICAL] SQL Injection on line 16 in UserService.java");
        verify(chatLanguageModel, times(1)).generate(anyList());
    }
}
