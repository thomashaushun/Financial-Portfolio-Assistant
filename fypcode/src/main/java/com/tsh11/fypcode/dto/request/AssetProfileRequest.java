package com.tsh11.fypcode.dto.request;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.DataSourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AssetProfileRequest {

    @NotBlank
    @Size(max = 50)
    private String symbol;

    @NotBlank
    @Size(max = 150)
    private String displayName;

    @NotNull
    private AssetClass assetClass;

    @Size(max = 100)
    private String sector;

    @Size(max = 100)
    private String market;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    private boolean manualValuationRequired;
    private boolean priceDataSupported = true;

    @NotNull
    private DataSourceType dataSource = DataSourceType.OTHER;

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