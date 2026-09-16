package com.tsh11.fypcode.dto.response;

import java.math.BigDecimal;

public class AllocationItemResponse {

    private String label;
    private BigDecimal value;
    private BigDecimal weightPercent;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }

    public BigDecimal getWeightPercent() {
        return weightPercent;
    }

    public void setWeightPercent(BigDecimal weightPercent) {
        this.weightPercent = weightPercent;
    }
}