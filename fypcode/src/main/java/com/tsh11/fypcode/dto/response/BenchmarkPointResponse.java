package com.tsh11.fypcode.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BenchmarkPointResponse {

    private LocalDate date;
    private BigDecimal portfolioValue;
    private BigDecimal portfolioIndexed;
    private BigDecimal benchmarkValue;
    private BigDecimal benchmarkIndexed;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getPortfolioValue() {
        return portfolioValue;
    }

    public void setPortfolioValue(BigDecimal portfolioValue) {
        this.portfolioValue = portfolioValue;
    }

    public BigDecimal getPortfolioIndexed() {
        return portfolioIndexed;
    }

    public void setPortfolioIndexed(BigDecimal portfolioIndexed) {
        this.portfolioIndexed = portfolioIndexed;
    }

    public BigDecimal getBenchmarkValue() {
        return benchmarkValue;
    }

    public void setBenchmarkValue(BigDecimal benchmarkValue) {
        this.benchmarkValue = benchmarkValue;
    }

    public BigDecimal getBenchmarkIndexed() {
        return benchmarkIndexed;
    }

    public void setBenchmarkIndexed(BigDecimal benchmarkIndexed) {
        this.benchmarkIndexed = benchmarkIndexed;
    }
}