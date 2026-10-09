package com.codesentinel.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete code review for a GitHub Pull Request.
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String repository;

    @Column(nullable = false)
    private Integer prNumber;

    private String commitSha;

    @Column(nullable = false)
    private String verdict; // e.g. "APPROVED", "CHANGES_REQUESTED", "COMMENT"

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    private String status; // e.g. "DRAFT", "POSTED"

    @Column(columnDefinition = "TEXT")
    private String skippedFiles;

    private LocalDateTime createdAt;

    private LocalDateTime postedAt;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<Finding> findings = new ArrayList<>();

    public Review() {
        this.createdAt = LocalDateTime.now();
        this.status = "DRAFT";
    }

    public Review(String repository, Integer prNumber, String commitSha, String verdict, String summary, String status) {
        this.repository = repository;
        this.prNumber = prNumber;
        this.commitSha = commitSha;
        this.verdict = verdict;
        this.summary = summary;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public void addFinding(Finding finding) {
        findings.add(finding);
        finding.setReview(this);
    }

    public void removeFinding(Finding finding) {
        findings.remove(finding);
        finding.setReview(null);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRepository() {
        return repository;
    }

    public void setRepository(String repository) {
        this.repository = repository;
    }

    public Integer getPrNumber() {
        return prNumber;
    }

    public void setPrNumber(Integer prNumber) {
        this.prNumber = prNumber;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSkippedFiles() {
        return skippedFiles;
    }

    public void setSkippedFiles(String skippedFiles) {
        this.skippedFiles = skippedFiles;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(LocalDateTime postedAt) {
        this.postedAt = postedAt;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public void setFindings(List<Finding> findings) {
        this.findings = findings;
        if (findings != null) {
            for (Finding finding : findings) {
                finding.setReview(this);
            }
        }
    }
}
