package com.tsh11.fypcode.dto.request;

import com.tsh11.fypcode.dto.ExplanationMode;
import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioMetricsDto;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;

import java.util.ArrayList;
import java.util.List;

public class PortfolioExplanationRequest {

    private ExplanationMode mode;
    private String userQuestion;
    private String term;
    private PortfolioAlgorithm algorithm;

    private PortfolioMetricsDto currentPortfolio;
    private PortfolioMetricsDto optimisedPortfolio;

    private List<PortfolioOptimisationAssetResult> assets = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    private String benchmarkSymbol;
    private String benchmarkSummary;

    public ExplanationMode getMode() {
        return mode;
    }

    public void setMode(ExplanationMode mode) {
        this.mode = mode;
    }

    public String getUserQuestion() {
        return userQuestion;
    }

    public void setUserQuestion(String userQuestion) {
        this.userQuestion = userQuestion;
    }

    public String getTerm() {
        return term;
    }

    public void setTerm(String term) {
        this.term = term;
    }

    public PortfolioAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(PortfolioAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public PortfolioMetricsDto getCurrentPortfolio() {
        return currentPortfolio;
    }

    public void setCurrentPortfolio(PortfolioMetricsDto currentPortfolio) {
        this.currentPortfolio = currentPortfolio;
    }

    public PortfolioMetricsDto getOptimisedPortfolio() {
        return optimisedPortfolio;
    }

    public void setOptimisedPortfolio(PortfolioMetricsDto optimisedPortfolio) {
        this.optimisedPortfolio = optimisedPortfolio;
    }

    public List<PortfolioOptimisationAssetResult> getAssets() {
        return assets;
    }

    public void setAssets(List<PortfolioOptimisationAssetResult> assets) {
        this.assets = assets;
    }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public String getBenchmarkSymbol() {
        return benchmarkSymbol;
    }

    public void setBenchmarkSymbol(String benchmarkSymbol) {
        this.benchmarkSymbol = benchmarkSymbol;
    }

    public String getBenchmarkSummary() {
        return benchmarkSummary;
    }

    public void setBenchmarkSummary(String benchmarkSummary) {
        this.benchmarkSummary = benchmarkSummary;
    }
}