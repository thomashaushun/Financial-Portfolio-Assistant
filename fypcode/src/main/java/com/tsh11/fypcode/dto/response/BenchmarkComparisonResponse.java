package com.tsh11.fypcode.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class BenchmarkComparisonResponse {

    private String benchmarkSymbol;
    private LocalDate fromDate;
    private LocalDate toDate;

    private BigDecimal startingPortfolioValue;
    private BigDecimal endingPortfolioValue;
    private BigDecimal portfolioReturnPercent;

    private BigDecimal startingBenchmarkValue;
    private BigDecimal endingBenchmarkValue;
    private BigDecimal benchmarkReturnPercent;

    private BigDecimal excessReturnPercent;
    private List<BenchmarkPointResponse> points;

    public String getBenchmarkSymbol() {
        return benchmarkSymbol;
    }

    public void setBenchmarkSymbol(String benchmarkSymbol) {
        this.benchmarkSymbol = benchmarkSymbol;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public BigDecimal getStartingPortfolioValue() {
        return startingPortfolioValue;
    }

    public void setStartingPortfolioValue(BigDecimal startingPortfolioValue) {
        this.startingPortfolioValue = startingPortfolioValue;
    }

    public BigDecimal getEndingPortfolioValue() {
        return endingPortfolioValue;
    }

    public void setEndingPortfolioValue(BigDecimal endingPortfolioValue) {
        this.endingPortfolioValue = endingPortfolioValue;
    }

    public BigDecimal getPortfolioReturnPercent() {
        return portfolioReturnPercent;
    }

    public void setPortfolioReturnPercent(BigDecimal portfolioReturnPercent) {
        this.portfolioReturnPercent = portfolioReturnPercent;
    }

    public BigDecimal getStartingBenchmarkValue() {
        return startingBenchmarkValue;
    }

    public void setStartingBenchmarkValue(BigDecimal startingBenchmarkValue) {
        this.startingBenchmarkValue = startingBenchmarkValue;
    }

    public BigDecimal getEndingBenchmarkValue() {
        return endingBenchmarkValue;
    }

    public void setEndingBenchmarkValue(BigDecimal endingBenchmarkValue) {
        this.endingBenchmarkValue = endingBenchmarkValue;
    }

    public BigDecimal getBenchmarkReturnPercent() {
        return benchmarkReturnPercent;
    }

    public void setBenchmarkReturnPercent(BigDecimal benchmarkReturnPercent) {
        this.benchmarkReturnPercent = benchmarkReturnPercent;
    }

    public BigDecimal getExcessReturnPercent() {
        return excessReturnPercent;
    }

    public void setExcessReturnPercent(BigDecimal excessReturnPercent) {
        this.excessReturnPercent = excessReturnPercent;
    }

    public List<BenchmarkPointResponse> getPoints() {
        return points;
    }

    public void setPoints(List<BenchmarkPointResponse> points) {
        this.points = points;
    }
}