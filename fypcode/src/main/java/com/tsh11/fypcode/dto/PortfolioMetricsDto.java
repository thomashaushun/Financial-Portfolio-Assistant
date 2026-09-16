package com.tsh11.fypcode.dto;

import java.math.BigDecimal;

public class PortfolioMetricsDto {

    private BigDecimal expectedAnnualReturn;
    private BigDecimal annualVolatility;
    private BigDecimal sharpeRatio;

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