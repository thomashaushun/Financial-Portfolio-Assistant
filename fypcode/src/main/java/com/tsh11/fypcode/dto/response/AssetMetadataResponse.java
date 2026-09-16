package com.tsh11.fypcode.dto.response;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.DataSourceType;

public class AssetMetadataResponse {

    private String symbol;
    private String displayName;
    private AssetClass assetClass;
    private String sector;
    private String market;
    private String currency;
    private boolean manualValuationRequired;
    private boolean priceDataSupported;
    private DataSourceType dataSource;

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
}