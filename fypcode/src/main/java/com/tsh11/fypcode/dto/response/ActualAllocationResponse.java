package com.tsh11.fypcode.dto.response;

import java.math.BigDecimal;

//Represents the user’s actual portfolio allocation calculated from current holdings
//The system reads holdings from HoldingService,
// groups them into assistant allocation buckets,
// then calculates percentages based on portfolio value.

//example:
//Equity: 75%
//Bond: 0%
//Cash: 5%
//Crypto: 20%

public class ActualAllocationResponse {
    private BigDecimal equityPercent = BigDecimal.ZERO;
    private BigDecimal bondPercent = BigDecimal.ZERO;
    private BigDecimal cashPercent = BigDecimal.ZERO;
    private BigDecimal cryptoPercent = BigDecimal.ZERO;
    private BigDecimal realEstatePercent = BigDecimal.ZERO;
    private BigDecimal otherPercent = BigDecimal.ZERO;
    private BigDecimal unclassifiedPercent = BigDecimal.ZERO;
    private BigDecimal totalPortfolioValue = BigDecimal.ZERO;
    private boolean etfAssumptionUsed;

    public BigDecimal getEquityPercent() {
        return equityPercent;
    }

    public void setEquityPercent(BigDecimal equityPercent) {
        this.equityPercent = equityPercent;
    }

    public BigDecimal getBondPercent() {
        return bondPercent;
    }

    public void setBondPercent(BigDecimal bondPercent) {
        this.bondPercent = bondPercent;
    }

    public BigDecimal getCashPercent() {
        return cashPercent;
    }

    public void setCashPercent(BigDecimal cashPercent) {
        this.cashPercent = cashPercent;
    }

    public BigDecimal getCryptoPercent() {
        return cryptoPercent;
    }

    public void setCryptoPercent(BigDecimal cryptoPercent) {
        this.cryptoPercent = cryptoPercent;
    }

    public BigDecimal getRealEstatePercent() {
        return realEstatePercent;
    }

    public void setRealEstatePercent(BigDecimal realEstatePercent) {
        this.realEstatePercent = realEstatePercent;
    }

    public BigDecimal getOtherPercent() {
        return otherPercent;
    }

    public void setOtherPercent(BigDecimal otherPercent) {
        this.otherPercent = otherPercent;
    }

    public BigDecimal getUnclassifiedPercent() {
        return unclassifiedPercent;
    }

    public void setUnclassifiedPercent(BigDecimal unclassifiedPercent) {
        this.unclassifiedPercent = unclassifiedPercent;
    }

    public BigDecimal getTotalPortfolioValue() {
        return totalPortfolioValue;
    }

    public void setTotalPortfolioValue(BigDecimal totalPortfolioValue) {
        this.totalPortfolioValue = totalPortfolioValue;
    }

    public boolean isEtfAssumptionUsed() {
        return etfAssumptionUsed;
    }

    public void setEtfAssumptionUsed(boolean etfAssumptionUsed) {
        this.etfAssumptionUsed = etfAssumptionUsed;
    }
}
