package com.tsh11.fypcode.dto.response;

import com.tsh11.fypcode.domain.asset.AssetClass;

import java.math.BigDecimal;

public class HoldingResponse {

    private String symbol;
    private String displayName;
    private AssetClass assetClass;
    private String sector;
    private String market;
    private boolean manualValuationRequired;
    private boolean priceDataSupported;

    private boolean historicalPriceSupported;
    private boolean optimisationSupported;

    private BigDecimal totalQuantity;
    private BigDecimal averageCost;
    private BigDecimal totalCost;
    private String currency;
    private BigDecimal latestPrice;
    private BigDecimal marketValue;
    private BigDecimal unrealizedGainLoss;
    private BigDecimal unrealizedGainLossPercent;

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public AssetClass getAssetClass() {
        return assetClass;
    }

    public void setAssetClass(AssetClass assetClass) {
        this.assetClass = assetClass;
    }

    public String getSector() {
        return sector;
    }

    public void setSector(String sector) {
        this.sector = sector;
    }

    public String getMarket() {
        return market;
    }

    public void setMarket(String market) {
        this.market = market;
    }

    public boolean isManualValuationRequired() {
        return manualValuationRequired;
    }

    public void setManualValuationRequired(boolean manualValuationRequired) {
        this.manualValuationRequired = manualValuationRequired;
    }

    public boolean isPriceDataSupported() {
        return priceDataSupported;
    }

    public void setPriceDataSupported(boolean priceDataSupported) {
        this.priceDataSupported = priceDataSupported;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(BigDecimal totalCost) {
        this.totalCost = totalCost;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getLatestPrice() {
        return latestPrice;
    }

    public void setLatestPrice(BigDecimal latestPrice) {
        this.latestPrice = latestPrice;
    }

    public BigDecimal getMarketValue() {
        return marketValue;
    }

    public void setMarketValue(BigDecimal marketValue) {
        this.marketValue = marketValue;
    }

    public BigDecimal getUnrealizedGainLoss() {
        return unrealizedGainLoss;
    }

    public void setUnrealizedGainLoss(BigDecimal unrealizedGainLoss) {
        this.unrealizedGainLoss = unrealizedGainLoss;
    }

    public BigDecimal getUnrealizedGainLossPercent() {
        return unrealizedGainLossPercent;
    }

    public void setUnrealizedGainLossPercent(BigDecimal unrealizedGainLossPercent) {
        this.unrealizedGainLossPercent = unrealizedGainLossPercent;
    }

    public boolean isHistoricalPriceSupported() {
        return historicalPriceSupported;
    }

    public void setHistoricalPriceSupported(boolean historicalPriceSupported) {
        this.historicalPriceSupported = historicalPriceSupported;
    }

    public boolean isOptimisationSupported() {
        return optimisationSupported;
    }

    public void setOptimisationSupported(boolean optimisationSupported) {
        this.optimisationSupported = optimisationSupported;
    }
}