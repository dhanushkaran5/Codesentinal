package com.codesentinel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured output model produced by ReviewAgent containing overall verdict,
 * summary, findings list, and any skipped files.
 */
public class ReviewResult {

    private String verdict; // APPROVED, CHANGES_REQUESTED, COMMENT
    private String summary;
    private List<ReviewFinding> findings = new ArrayList<>();
    private List<String> skippedFiles = new ArrayList<>();

    public ReviewResult() {}

    public ReviewResult(String verdict, String summary, List<ReviewFinding> findings, List<String> skippedFiles) {
        this.verdict = verdict;
        this.summary = summary;
        this.findings = findings != null ? findings : new ArrayList<>();
        this.skippedFiles = skippedFiles != null ? skippedFiles : new ArrayList<>();
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

    public List<ReviewFinding> getFindings() {
        return findings;
    }

    public void setFindings(List<ReviewFinding> findings) {
        this.findings = findings;
    }

    public List<String> getSkippedFiles() {
        return skippedFiles;
    }

    public void setSkippedFiles(List<String> skippedFiles) {
        this.skippedFiles = skippedFiles;
    }

    @Override
    public String toString() {
        return String.format("ReviewResult{verdict='%s', findingsCount=%d, skippedFiles=%s}",
                verdict, findings != null ? findings.size() : 0, skippedFiles);
    }
}
