package com.tsh11.fypcode.integration.marketdata;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.DataSourceType;

public class AssetMetadata {

    private String symbol;
    private String displayName;
    private AssetClass assetClass; //internal classification, e.g. EQUITY
    private String sector;
    private String market;
    private String currency;
    private boolean manualValuationRequired;
    private boolean priceDataSupported;
    private DataSourceType dataSource;
    private String providerAssetType; //raw Alpha Vantage type, e.g. "Equity"
    private boolean quoteSupported; //can fetch latest/current price
    private boolean historicalPriceSupported; //can fetch historical prices
    private boolean optimisationSupported; //can be used in portfolio optimization algorithms

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

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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

    public DataSourceType getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSourceType dataSource) {
        this.dataSource = dataSource;
    }

    public String getProviderAssetType() {
        return providerAssetType;
    }

    public void setProviderAssetType(String providerAssetType) {
        this.providerAssetType = providerAssetType;
    }

    public boolean isQuoteSupported() {
        return quoteSupported;
    }

    public void setQuoteSupported(boolean quoteSupported) {
        this.quoteSupported = quoteSupported;
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