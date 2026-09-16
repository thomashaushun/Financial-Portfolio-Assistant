package com.tsh11.fypcode.dto;

import java.math.BigDecimal;
import java.util.Map;

public class EfficientFrontierPointDto {

    private BigDecimal expectedAnnualReturn;
    private BigDecimal annualVolatility;
    private BigDecimal sharpeRatio;
    private Map<String, BigDecimal> weights;

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

    public Map<String, BigDecimal> getWeights() {
        return weights;
    }

    public void setWeights(Map<String, BigDecimal> weights) {
        this.weights = weights;
    }
}