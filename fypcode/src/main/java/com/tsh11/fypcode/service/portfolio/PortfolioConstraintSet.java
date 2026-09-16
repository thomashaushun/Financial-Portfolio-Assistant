package com.tsh11.fypcode.service.portfolio;

import java.math.BigDecimal;

public class PortfolioConstraintSet {

    private boolean longOnly = true;
    private BigDecimal minWeightPerAsset = BigDecimal.ZERO;
    private BigDecimal maxWeightPerAsset = new BigDecimal("0.60");

    public boolean isLongOnly() {
        return longOnly;
    }

    public void setLongOnly(boolean longOnly) {
        this.longOnly = longOnly;
    }

    public BigDecimal getMinWeightPerAsset() {
        return minWeightPerAsset;
    }

    public void setMinWeightPerAsset(BigDecimal minWeightPerAsset) {
        this.minWeightPerAsset = minWeightPerAsset;
    }

    public BigDecimal getMaxWeightPerAsset() {
        return maxWeightPerAsset;
    }

    public void setMaxWeightPerAsset(BigDecimal maxWeightPerAsset) {
        this.maxWeightPerAsset = maxWeightPerAsset;
    }
}