package com.tsh11.fypcode.dto;

import com.tsh11.fypcode.service.portfolio.PortfolioConstraintSet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class PortfolioOptimisationRequest {

    private PortfolioAlgorithm algorithm;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal riskFreeRate;
    private List<String> symbols;
    private Map<String, BigDecimal> currentWeights;
    private Map<String, List<BigDecimal>> dailyReturns;
    private PortfolioConstraintSet constraints;

    public PortfolioAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(PortfolioAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public LocalDate getFrom() {
        return from;
    }

    public void setFrom(LocalDate from) {
        this.from = from;
    }

    public LocalDate getTo() {
        return to;
    }

    public void setTo(LocalDate to) {
        this.to = to;
    }

    public BigDecimal getRiskFreeRate() {
        return riskFreeRate;
    }

    public void setRiskFreeRate(BigDecimal riskFreeRate) {
        this.riskFreeRate = riskFreeRate;
    }

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    public Map<String, BigDecimal> getCurrentWeights() {
        return currentWeights;
    }

    public void setCurrentWeights(Map<String, BigDecimal> currentWeights) {
        this.currentWeights = currentWeights;
    }

    public Map<String, List<BigDecimal>> getDailyReturns() {
        return dailyReturns;
    }

    public void setDailyReturns(Map<String, List<BigDecimal>> dailyReturns) {
        this.dailyReturns = dailyReturns;
    }

    public PortfolioConstraintSet getConstraints() {
        return constraints;
    }

    public void setConstraints(PortfolioConstraintSet constraints) {
        this.constraints = constraints;
    }
}