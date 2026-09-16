package com.tsh11.fypcode.dto.response;

import com.tsh11.fypcode.dto.ExplanationMode;

public class PortfolioExplanationResponse {

    private ExplanationMode mode;
    private String title;
    private String explanation;
    private boolean fallbackUsed;

    public ExplanationMode getMode() {
        return mode;
    }

    public void setMode(ExplanationMode mode) {
        this.mode = mode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public boolean isFallbackUsed() {
        return fallbackUsed;
    }

    public void setFallbackUsed(boolean fallbackUsed) {
        this.fallbackUsed = fallbackUsed;
    }
}