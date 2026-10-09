package com.codesentinel.controller;

import com.codesentinel.model.Review;
import com.codesentinel.service.ReviewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for inspecting reviews and approving DRAFT reviews for GitHub posting.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final Logger log = LoggerFactory.getLogger(ReviewController.class);

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * Approves a DRAFT review and posts inline comments and summary to GitHub.
     *
     * @param id The ID of the review to approve
     * @return Confirmation of approval and posting
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approveReview(@PathVariable Long id) {
        log.info("Received request to approve review #{}", id);
        try {
            Review approvedReview = reviewService.approveAndPostReview(id);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Review approved and posted to GitHub successfully",
                    "reviewId", approvedReview.getId(),
                    "repository", approvedReview.getRepository(),
                    "prNumber", approvedReview.getPrNumber(),
                    "reviewStatus", approvedReview.getStatus(),
                    "findingsCount", approvedReview.getFindings().size()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Review #{} not found: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (IllegalStateException e) {
            log.warn("Review #{} already posted: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("status", "error", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to approve review #{}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Error approving review: " + e.getMessage()));
        }
    }

    /**
     * Retrieves all reviews saved in the database, with optional filtering.
     */
    @GetMapping
    public ResponseEntity<List<Review>> getAllReviews(
            @RequestParam(required = false) String repository,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(reviewService.getAllReviews(repository, status));
    }

    /**
     * Retrieves a single review by ID along with its findings.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getReviewById(@PathVariable Long id) {
        return reviewService.getReviewById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("status", "error", "message", "Review not found with id: " + id)));
    }
}
