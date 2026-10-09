package com.codesentinel.controller;

import com.codesentinel.model.Finding;
import com.codesentinel.model.Review;
import com.codesentinel.model.Severity;
import com.codesentinel.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewService reviewService;

    @Test
    @DisplayName("Should approve DRAFT review and post comments to GitHub")
    void shouldApproveDraftReview() throws Exception {
        Review approvedReview = new Review("octocat/Hello-World", 42, "abc1234", "CHANGES_REQUESTED", "Fix critical bug", "POSTED");
        approvedReview.setId(1L);
        approvedReview.addFinding(new Finding("src/Main.java", 15, Severity.CRITICAL, "SQL Injection", "Use param query"));

        when(reviewService.approveAndPostReview(1L)).thenReturn(approvedReview);

        mockMvc.perform(post("/api/reviews/1/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.reviewId").value(1))
                .andExpect(jsonPath("$.reviewStatus").value("POSTED"))
                .andExpect(jsonPath("$.findingsCount").value(1));
    }

    @Test
    @DisplayName("Should return 404 when approving non-existent review")
    void shouldReturn404ForNonExistentReview() throws Exception {
        when(reviewService.approveAndPostReview(999L))
                .thenThrow(new IllegalArgumentException("Review not found with id: 999"));

        mockMvc.perform(post("/api/reviews/999/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Review not found with id: 999"));
    }

    @Test
    @DisplayName("Should return 400 when review is already posted")
    void shouldReturn400WhenAlreadyPosted() throws Exception {
        when(reviewService.approveAndPostReview(2L))
                .thenThrow(new IllegalStateException("Review with id 2 has already been posted to GitHub."));

        mockMvc.perform(post("/api/reviews/2/approve")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Review with id 2 has already been posted to GitHub."));
    }

    @Test
    @DisplayName("Should retrieve all reviews via GET /api/reviews")
    void shouldGetAllReviews() throws Exception {
        Review review1 = new Review("octocat/Repo1", 10, "sha1", "APPROVED", "Looks good", "POSTED");
        review1.setId(1L);

        Review review2 = new Review("octocat/Repo2", 20, "sha2", "CHANGES_REQUESTED", "Found bugs", "DRAFT");
        review2.setId(2L);

        when(reviewService.getAllReviews(null, null)).thenReturn(List.of(review1, review2));

        mockMvc.perform(get("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].repository").value("octocat/Repo1"))
                .andExpect(jsonPath("$[1].repository").value("octocat/Repo2"));
    }

    @Test
    @DisplayName("Should retrieve reviews filtered by status via GET /api/reviews?status=DRAFT")
    void shouldGetReviewsFilteredByStatus() throws Exception {
        Review draft = new Review("octocat/Repo2", 20, "sha2", "CHANGES_REQUESTED", "Found bugs", "DRAFT");
        draft.setId(2L);

        when(reviewService.getAllReviews(null, "DRAFT")).thenReturn(List.of(draft));

        mockMvc.perform(get("/api/reviews?status=DRAFT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));
    }

    @Test
    @DisplayName("Should retrieve a specific review by ID via GET /api/reviews/{id}")
    void shouldGetReviewById() throws Exception {
        Review review = new Review("octocat/Hello-World", 42, "sha123", "CHANGES_REQUESTED", "Critical fix required", "DRAFT");
        review.setId(10L);
        review.addFinding(new Finding("src/App.java", 20, Severity.MAJOR, "Empty catch block", "Log exception"));

        when(reviewService.getReviewById(10L)).thenReturn(Optional.of(review));

        mockMvc.perform(get("/api/reviews/10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.repository").value("octocat/Hello-World"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.findings[0].file").value("src/App.java"))
                .andExpect(jsonPath("$.findings[0].severity").value("MAJOR"));
    }
}
