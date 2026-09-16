package com.tsh11.fypcode.dto.response;

import java.math.BigDecimal;

public class OverviewResponse {
    private int totalHoldingsCount;
    private BigDecimal totalQuantity;
    private BigDecimal totalCostBasis;
    private BigDecimal totalMarketValue;
    private BigDecimal totalUnrealizedGainLoss;
    private BigDecimal totalUnrealizedGainLossPercent;
    private String baseCurrency;

    public int getTotalHoldingsCount() {
        return totalHoldingsCount;
    }

    public void setTotalHoldingsCount(int totalHoldingsCount) {
        this.totalHoldingsCount = totalHoldingsCount;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public BigDecimal getTotalCostBasis() {
        return totalCostBasis;
    }

    public void setTotalCostBasis(BigDecimal totalCostBasis) {
        this.totalCostBasis = totalCostBasis;
    }

    public BigDecimal getTotalMarketValue() {
        return totalMarketValue;
    }

    public void setTotalMarketValue(BigDecimal totalMarketValue) {
        this.totalMarketValue = totalMarketValue;
    }

    public BigDecimal getTotalUnrealizedGainLoss() {
        return totalUnrealizedGainLoss;
    }

    public void setTotalUnrealizedGainLoss(BigDecimal totalUnrealizedGainLoss) {
        this.totalUnrealizedGainLoss = totalUnrealizedGainLoss;
    }

    public BigDecimal getTotalUnrealizedGainLossPercent() {
        return totalUnrealizedGainLossPercent;
    }

    public void setTotalUnrealizedGainLossPercent(BigDecimal totalUnrealizedGainLossPercent) {
        this.totalUnrealizedGainLossPercent = totalUnrealizedGainLossPercent;
    }

    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }
}