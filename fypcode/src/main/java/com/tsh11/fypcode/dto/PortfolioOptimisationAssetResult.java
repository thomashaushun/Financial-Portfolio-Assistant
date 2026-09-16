package com.tsh11.fypcode.dto;

import java.math.BigDecimal;

public class PortfolioOptimisationAssetResult {

    private String symbol;
    private BigDecimal currentWeight;
    private BigDecimal targetWeight;
    private BigDecimal weightDifference;
    private BigDecimal currentValue;
    private BigDecimal targetValue;
    private BigDecimal valueDifference;
    private BigDecimal latestPrice;
    private BigDecimal suggestedUnitChange;
    private String suggestedAction;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getCurrentWeight() {
        return currentWeight;
    }

    public void setCurrentWeight(BigDecimal currentWeight) {
        this.currentWeight = currentWeight;
    }

    public BigDecimal getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(BigDecimal targetWeight) {
        this.targetWeight = targetWeight;
    }

    public BigDecimal getWeightDifference() {
        return weightDifference;
    }

    public void setWeightDifference(BigDecimal weightDifference) {
        this.weightDifference = weightDifference;
    }

    public BigDecimal getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(BigDecimal currentValue) {
        this.currentValue = currentValue;
    }

    public BigDecimal getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(BigDecimal targetValue) {
        this.targetValue = targetValue;
    }

    public BigDecimal getValueDifference() {
        return valueDifference;
    }

    public void setValueDifference(BigDecimal valueDifference) {
        this.valueDifference = valueDifference;
    }

    public BigDecimal getLatestPrice() {
        return latestPrice;
    }

    public void setLatestPrice(BigDecimal latestPrice) {
        this.latestPrice = latestPrice;
    }

    public BigDecimal getSuggestedUnitChange() {
        return suggestedUnitChange;
    }

    public void setSuggestedUnitChange(BigDecimal suggestedUnitChange) {
        this.suggestedUnitChange = suggestedUnitChange;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public void setSuggestedAction(String suggestedAction) {
        this.suggestedAction = suggestedAction;
    }
}