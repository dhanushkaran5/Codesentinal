package com.codesentinel.repository;

import com.codesentinel.model.Finding;
import com.codesentinel.model.Review;
import com.codesentinel.model.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    @DisplayName("Should persist Review and cascade save Findings to the database")
    void shouldPersistReviewWithFindings() {
        Review review = new Review("octocat/Hello-World", 42, "abc1234", "CHANGES_REQUESTED", "Security fixes required", "DRAFT");
        review.setSkippedFiles("package-lock.json");

        Finding finding1 = new Finding("src/Main.java", 15, Severity.CRITICAL, "SQL Injection detected", "Use parameterized query");
        Finding finding2 = new Finding("src/Util.java", 30, Severity.MAJOR, "Resource leak in FileInputStream", "Use try-with-resources");

        review.addFinding(finding1);
        review.addFinding(finding2);

        Review saved = reviewRepository.save(review);
        assertThat(saved.getId()).isNotNull();

        Optional<Review> retrieved = reviewRepository.findById(saved.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getRepository()).isEqualTo("octocat/Hello-World");
        assertThat(retrieved.get().getPrNumber()).isEqualTo(42);
        assertThat(retrieved.get().getStatus()).isEqualTo("DRAFT");
        assertThat(retrieved.get().getSkippedFiles()).isEqualTo("package-lock.json");
        assertThat(retrieved.get().getFindings()).hasSize(2);

        Finding retrievedFinding1 = retrieved.get().getFindings().get(0);
        assertThat(retrievedFinding1.getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(retrievedFinding1.getFile()).isEqualTo("src/Main.java");
        assertThat(retrievedFinding1.getLine()).isEqualTo(15);
    }

    @Test
    @DisplayName("Should query reviews by status (DRAFT vs POSTED)")
    void shouldFindReviewsByStatus() {
        Review draft = new Review("octocat/RepoA", 1, "sha1", "COMMENT", "Draft summary", "DRAFT");
        Review posted = new Review("octocat/RepoB", 2, "sha2", "APPROVED", "Approved summary", "POSTED");
        posted.setPostedAt(LocalDateTime.now());

        reviewRepository.save(draft);
        reviewRepository.save(posted);

        List<Review> drafts = reviewRepository.findByStatus("DRAFT");
        List<Review> posteds = reviewRepository.findByStatus("POSTED");

        assertThat(drafts).extracting(Review::getRepository).contains("octocat/RepoA");
        assertThat(posteds).extracting(Review::getRepository).contains("octocat/RepoB");
    }

    @Test
    @DisplayName("Should find reviews by repository and PR number")
    void shouldFindByRepositoryAndPrNumber() {
        Review review = new Review("octocat/SpecialRepo", 99, "sha99", "APPROVED", "Looks good", "POSTED");
        reviewRepository.save(review);

        List<Review> found = reviewRepository.findByRepositoryAndPrNumber("octocat/SpecialRepo", 99);

        assertThat(found).hasSize(1);
        assertThat(found.get(0).getVerdict()).isEqualTo("APPROVED");
    }
}
