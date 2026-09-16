package com.tsh11.fypcode.dto;

import java.util.ArrayList;
import java.util.List;

public class PortfolioOptimisationResult {

    private PortfolioAlgorithm algorithm;
    private PortfolioMetricsDto currentPortfolio;
    private PortfolioMetricsDto optimisedPortfolio;
    private List<PortfolioOptimisationAssetResult> assets = new ArrayList<>();
    private List<EfficientFrontierPointDto> efficientFrontier = new ArrayList<>();
    private List<String> suggestions = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

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

    public List<EfficientFrontierPointDto> getEfficientFrontier() {
        return efficientFrontier;
    }

    public void setEfficientFrontier(List<EfficientFrontierPointDto> efficientFrontier) {
        this.efficientFrontier = efficientFrontier;
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
}