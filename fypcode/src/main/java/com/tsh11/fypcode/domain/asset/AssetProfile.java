package com.tsh11.fypcode.domain.asset;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

//defines the metadata model that the rest of the portfolio system depends on
@Entity
@Table(
        //The same user cannot have two asset profiles for the same symbol.
        name = "asset_profiles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_asset_profile_user_symbol", columnNames = {"user_id", "symbol"})
        }
)
public class AssetProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String symbol;

    @Column(nullable = false, length = 150)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AssetClass assetClass;

    @Column(length = 100)
    private String sector;

    @Column(length = 100)
    private String market;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean manualValuationRequired = false;

    @Column(nullable = false)
    private boolean priceDataSupported = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DataSourceType dataSource = DataSourceType.OTHER;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(length = 100)
    private String providerAssetType;

    @Column(nullable = false)
    private boolean quoteSupported = true;

    @Column(nullable = false)
    private boolean historicalPriceSupported = false;

    @Column(nullable = false)
    private boolean optimisationSupported = false;

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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