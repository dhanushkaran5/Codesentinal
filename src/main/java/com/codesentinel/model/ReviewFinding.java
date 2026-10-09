package com.codesentinel.model;

/**
 * Structured DTO representing an individual review finding returned by the agent.
 */
public class ReviewFinding {

    private String file;
    private Integer line;
    private Severity severity;
    private String explanation;
    private String suggestedFix;

    public ReviewFinding() {}

    public ReviewFinding(String file, Integer line, Severity severity, String explanation, String suggestedFix) {
        this.file = file;
        this.line = line;
        this.severity = severity;
        this.explanation = explanation;
        this.suggestedFix = suggestedFix;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public Integer getLine() {
        return line;
    }

    public void setLine(Integer line) {
        this.line = line;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSuggestedFix() {
        return suggestedFix;
    }

    public void setSuggestedFix(String suggestedFix) {
        this.suggestedFix = suggestedFix;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s:%d - %s", severity, file, line, explanation);
    }
}
