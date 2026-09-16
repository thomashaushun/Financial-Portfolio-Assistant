package com.tsh11.fypcode.service.portfolio;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public class PortfolioCandidate {

    private Map<String, BigDecimal> weights = new LinkedHashMap<>();
    private BigDecimal expectedAnnualReturn = BigDecimal.ZERO;
    private BigDecimal annualVolatility = BigDecimal.ZERO;
    private BigDecimal sharpeRatio = BigDecimal.ZERO;

    public Map<String, BigDecimal> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, BigDecimal> weights) {
        this.weights = weights;
    }

    public BigDecimal getExpectedAnnualReturn() {
        return expectedAnnualReturn;
    }

    public void setExpectedAnnualReturn(BigDecimal expectedAnnualReturn) {
        this.expectedAnnualReturn = expectedAnnualReturn;
    }

    public BigDecimal getAnnualVolatility() {
        return annualVolatility;
    }

    public void setAnnualVolatility(BigDecimal annualVolatility) {
        this.annualVolatility = annualVolatility;
    }

    public BigDecimal getSharpeRatio() {
        return sharpeRatio;
    }

    public void setSharpeRatio(BigDecimal sharpeRatio) {
        this.sharpeRatio = sharpeRatio;
    }
}