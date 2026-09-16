package com.tsh11.fypcode.dto.response;

import com.tsh11.fypcode.domain.investor.RiskProfileType;

import java.math.BigDecimal;

//the recommended target allocation for the user’s risk profile.
//example:
//Balanced:
//60% equity
//30% bond
//10% cash

public class TargetAllocationResponse {
    private RiskProfileType riskProfileType;
    private BigDecimal equityPercent;
    private BigDecimal bondPercent;
    private BigDecimal cashPercent;

    public RiskProfileType getRiskProfileType() {
        return riskProfileType;
    }

    public void setRiskProfileType(RiskProfileType riskProfileType) {
        this.riskProfileType = riskProfileType;
    }

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
}
