package com.codesentinel.service;

import com.codesentinel.agent.AgentTools;
import com.codesentinel.agent.ReviewAgent;
import com.codesentinel.model.Finding;
import com.codesentinel.model.Review;
import com.codesentinel.model.ReviewFinding;
import com.codesentinel.model.ReviewResult;
import com.codesentinel.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for orchestrating code reviews, managing review drafts,
 * and posting review comments to GitHub upon approval or auto-post.
 */
@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewAgent reviewAgent;
    private final GitHubService gitHubService;
    private final AgentTools agentTools;
    private final ReviewRepository reviewRepository;

    @Value("${review.auto-post:false}")
    private boolean autoPost;

    public ReviewService(ReviewAgent reviewAgent,
                         GitHubService gitHubService,
                         AgentTools agentTools,
                         ReviewRepository reviewRepository) {
        this.reviewAgent = reviewAgent;
        this.gitHubService = gitHubService;
        this.agentTools = agentTools;
        this.reviewRepository = reviewRepository;
    }

    public boolean isAutoPost() {
        return autoPost;
    }

    public void setAutoPost(boolean autoPost) {
        this.autoPost = autoPost;
    }

    /**
     * Conducts a basic review on the given diff string and prints the result to the console.
     */
    public String reviewDiffAndLog(String diff) {
        log.info("Dispatching diff to CodeSentinel ReviewAgent...");
        String reviewOutput = reviewAgent.reviewDiff(diff);

        System.out.println("\n=======================================================");
        System.out.println("🛡️  CODESENTINEL PULL REQUEST REVIEW OUTPUT");
        System.out.println("=======================================================");
        System.out.println(reviewOutput);
        System.out.println("=======================================================\n");

        return reviewOutput;
    }

    /**
     * Basic PR diff review from GitHub (Phase 3).
     */
    public String reviewPullRequest(String repoName, int prNumber) {
        log.info("Fetching diff and reviewing PR {} #{}", repoName, prNumber);
        String diff = gitHubService.getPullRequestDiff(repoName, prNumber);
        return reviewDiffAndLog(diff);
    }

    /**
     * Runs autonomous ReviewAgent review using tools, returning structured findings.
     */
    public ReviewResult executeAgentReview(String repoName, int prNumber) {
        log.info("Starting autonomous agent review for {} #{}", repoName, prNumber);
        agentTools.resetCounter();

        String prompt = String.format(
                "Please review Pull Request #%d in repository '%s'.\n" +
                "1. Use getPullRequestDiff to inspect the changes.\n" +
                "2. Run runStaticAnalysis on modified code snippets.\n" +
                "3. If needed, retrieve file content with getFileContent.\n" +
                "4. Return a structured review with your verdict, summary, and all findings.",
                prNumber, repoName
        );

        ReviewResult result = reviewAgent.reviewPullRequest(prompt);
        logStructuredReview(repoName, prNumber, result);
        return result;
    }

    /**
     * Executes review workflow for a pull request, creating a Review entity.
     * If autoPost is true, posts comments immediately to GitHub.
     * If autoPost is false, saves review as DRAFT until manual approval.
     */
    @Transactional
    public Review processPullRequest(String repoName, int prNumber) {
        log.info("Processing pull request {} #{} (auto-post: {})", repoName, prNumber, autoPost);

        ReviewResult reviewResult = executeAgentReview(repoName, prNumber);
        String headSha = gitHubService.getHeadCommitSha(repoName, prNumber);

        Review review = new Review();
        review.setRepository(repoName);
        review.setPrNumber(prNumber);
        review.setCommitSha(headSha != null ? headSha : "");
        review.setVerdict(reviewResult.getVerdict() != null ? reviewResult.getVerdict() : "COMMENT");
        review.setSummary(reviewResult.getSummary());

        if (reviewResult.getSkippedFiles() != null && !reviewResult.getSkippedFiles().isEmpty()) {
            review.setSkippedFiles(String.join(", ", reviewResult.getSkippedFiles()));
        }

        if (reviewResult.getFindings() != null) {
            for (ReviewFinding rf : reviewResult.getFindings()) {
                Finding finding = new Finding(
                        rf.getFile(),
                        rf.getLine() != null ? rf.getLine() : 1,
                        rf.getSeverity(),
                        rf.getExplanation(),
                        rf.getSuggestedFix()
                );
                review.addFinding(finding);
            }
        }

        if (autoPost) {
            log.info("Auto-post enabled. Posting review comments immediately to GitHub for {} #{}", repoName, prNumber);
            postCommentsToGitHub(review);
            review.setStatus("POSTED");
            review.setPostedAt(LocalDateTime.now());
        } else {
            log.info("Auto-post disabled. Saving review as DRAFT for {} #{}", repoName, prNumber);
            review.setStatus("DRAFT");
        }

        return reviewRepository.save(review);
    }

    /**
     * Approves a DRAFT review and posts inline and summary comments to GitHub.
     *
     * @param reviewId the review ID to approve
     * @return Updated Review entity with POSTED status
     */
    @Transactional
    public Review approveAndPostReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found with id: " + reviewId));

        if ("POSTED".equalsIgnoreCase(review.getStatus())) {
            throw new IllegalStateException("Review with id " + reviewId + " has already been posted to GitHub.");
        }

        log.info("Approving review #{} for {} #{}. Posting comments to GitHub...",
                reviewId, review.getRepository(), review.getPrNumber());

        postCommentsToGitHub(review);

        review.setStatus("POSTED");
        review.setPostedAt(LocalDateTime.now());

        return reviewRepository.save(review);
    }

    /**
     * Posts inline review comments and an overall summary comment to GitHub.
     */
    public void postCommentsToGitHub(Review review) {
        String repo = review.getRepository();
        int prNumber = review.getPrNumber();

        // 1. Post inline comments for each finding
        if (review.getFindings() != null) {
            for (Finding finding : review.getFindings()) {
                String commentBody = formatFindingComment(finding);
                log.info("Posting inline finding comment to {} #{} on {}:{}", repo, prNumber, finding.getFile(), finding.getLine());
                gitHubService.postReviewComment(repo, prNumber, finding.getFile(), finding.getLine(), commentBody);
            }
        }

        // 2. Post overall summary comment
        String summaryBody = formatSummaryBody(review);
        log.info("Posting review summary comment to {} #{}, verdict: {}", repo, prNumber, review.getVerdict());
        gitHubService.postReviewSummary(repo, prNumber, review.getVerdict(), summaryBody);
    }

    public List<Review> getAllReviews(String repository, String status) {
        if (repository != null && !repository.isBlank() && status != null && !status.isBlank()) {
            return reviewRepository.findByRepositoryAndPrNumber(repository, null);
        } else if (repository != null && !repository.isBlank()) {
            return reviewRepository.findByRepository(repository.trim());
        } else if (status != null && !status.isBlank()) {
            return reviewRepository.findByStatus(status.trim().toUpperCase());
        }
        return reviewRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Review> getAllReviews() {
        return getAllReviews(null, null);
    }

    public Optional<Review> getReviewById(Long id) {
        return reviewRepository.findById(id);
    }

    private String formatFindingComment(Finding finding) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 🛡️ CodeSentinel Finding: **[%s]**\n\n", finding.getSeverity()));
        sb.append(String.format("**Explanation:**\n%s\n\n", finding.getExplanation()));
        if (finding.getSuggestedFix() != null && !finding.getSuggestedFix().isBlank()) {
            sb.append("**Suggested Fix:**\n```java\n")
              .append(finding.getSuggestedFix().trim())
              .append("\n```\n");
        }
        return sb.toString();
    }

    private String formatSummaryBody(Review review) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s\n\n", review.getSummary() != null ? review.getSummary() : "Review completed."));

        if (review.getFindings() != null && !review.getFindings().isEmpty()) {
            sb.append(String.format("#### 🔍 Findings (%d)\n\n", review.getFindings().size()));
            for (Finding f : review.getFindings()) {
                sb.append(String.format("- **[%s]** `%s:%d`: %s\n", f.getSeverity(), f.getFile(), f.getLine(), f.getExplanation()));
            }
            sb.append("\n");
        }

        if (review.getSkippedFiles() != null && !review.getSkippedFiles().isBlank()) {
            sb.append("#### ⚠️ Skipped Files (Per Review Guardrails)\n");
            sb.append(review.getSkippedFiles()).append("\n\n");
        }

        sb.append("---\n*Automated review provided by [CodeSentinel](https://github.com/)*");
        return sb.toString();
    }

    private void logStructuredReview(String repo, int prNumber, ReviewResult result) {
        System.out.println("\n=======================================================");
        System.out.println("🛡️  CODESENTINEL STRUCTURED REVIEW RESULT");
        System.out.println("=======================================================");
        System.out.printf("Repository: %s | PR #%d%n", repo, prNumber);
        System.out.printf("Verdict: %s%n", result.getVerdict());
        System.out.printf("Summary: %s%n", result.getSummary());
        System.out.printf("Total Findings: %d%n", result.getFindings() != null ? result.getFindings().size() : 0);

        if (result.getFindings() != null && !result.getFindings().isEmpty()) {
            System.out.println("Findings Detail:");
            for (ReviewFinding finding : result.getFindings()) {
                System.out.printf(" - [%s] %s:%d%n", finding.getSeverity(), finding.getFile(), finding.getLine());
                System.out.printf("   Explanation: %s%n", finding.getExplanation());
                if (finding.getSuggestedFix() != null && !finding.getSuggestedFix().isBlank()) {
                    System.out.printf("   Suggested Fix: %s%n", finding.getSuggestedFix());
                }
            }
        }
        if (result.getSkippedFiles() != null && !result.getSkippedFiles().isEmpty()) {
            System.out.println("Skipped Files: " + result.getSkippedFiles());
        }
        System.out.println("=======================================================\n");
    }
}
