package com.tsh11.fypcode.service.metadata.whitebox;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.domain.asset.DataSourceType;
import com.tsh11.fypcode.integration.marketdata.AssetMetadata;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.repository.AssetProfileRepository;
import com.tsh11.fypcode.service.AssetMetadataSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetMetadataSyncServiceBranchTest {

    @Mock
    private AssetProfileRepository assetProfileRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    private AssetMetadataSyncService service;

    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new AssetMetadataSyncService(assetProfileRepository, marketDataProvider);
        userId = UUID.randomUUID();
    }

    // Tests the update branch where an existing non-manual profile is overwritten with provider metadata.
    @Test
    void existingProviderProfile_shouldBeUpdatedWithProviderMetadata() {
        AssetProfile existing = new AssetProfile();
        existing.setUserId(userId);
        existing.setSymbol("AAPL");
        existing.setDisplayName("Old Apple Name");
        existing.setAssetClass(AssetClass.OTHER);
        existing.setCurrency("GBP");
        existing.setDataSource(DataSourceType.OTHER);

        AssetMetadata metadata = metadata();
        metadata.setDisplayName("Apple Inc.");
        metadata.setAssetClass(AssetClass.EQUITY);
        metadata.setSector("Technology");
        metadata.setMarket("NASDAQ");
        metadata.setCurrency("USD");
        metadata.setDataSource(DataSourceType.ALPHA_VANTAGE);
        metadata.setProviderAssetType("Equity");
        metadata.setQuoteSupported(true);
        metadata.setHistoricalPriceSupported(true);
        metadata.setOptimisationSupported(true);
        metadata.setPriceDataSupported(true);

        when(marketDataProvider.getAssetMetadata("AAPL"))
                .thenReturn(Optional.of(metadata));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(existing));

        service.ensureMetadataForSymbol(userId, "AAPL", "GBP");

        AssetProfile saved = captureSavedProfile();

        assertThat(saved.getDisplayName()).isEqualTo("Apple Inc.");
        assertThat(saved.getAssetClass()).isEqualTo(AssetClass.EQUITY);
        assertThat(saved.getSector()).isEqualTo("Technology");
        assertThat(saved.getMarket()).isEqualTo("NASDAQ");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getDataSource()).isEqualTo(DataSourceType.ALPHA_VANTAGE);
        assertThat(saved.getProviderAssetType()).isEqualTo("Equity");
        assertThat(saved.isQuoteSupported()).isTrue();
        assertThat(saved.isHistoricalPriceSupported()).isTrue();
        assertThat(saved.isOptimisationSupported()).isTrue();
    }

    // Tests the manual override branch. It verifies that user-defined manual
    // fields are preserved while missing provider type metadata can still be added.
    @Test
    void existingManualProfile_shouldOnlyApplyMinimalMetadataAndPreserveManualFields() {
        AssetProfile manualProfile = new AssetProfile();
        manualProfile.setUserId(userId);
        manualProfile.setSymbol("CUSTOM1");
        manualProfile.setDisplayName("My Custom Asset");
        manualProfile.setAssetClass(AssetClass.REAL_ESTATE);
        manualProfile.setSector("User Sector");
        manualProfile.setMarket("User Market");
        manualProfile.setCurrency("GBP");
        manualProfile.setDataSource(DataSourceType.MANUAL);
        manualProfile.setManualValuationRequired(true);
        manualProfile.setPriceDataSupported(false);

        AssetMetadata metadata = metadata();
        metadata.setDisplayName("Provider Name");
        metadata.setAssetClass(AssetClass.EQUITY);
        metadata.setSector("Technology");
        metadata.setMarket("NASDAQ");
        metadata.setCurrency("USD");
        metadata.setDataSource(DataSourceType.ALPHA_VANTAGE);
        metadata.setProviderAssetType("Equity");
        metadata.setQuoteSupported(true);
        metadata.setHistoricalPriceSupported(true);
        metadata.setOptimisationSupported(true);
        metadata.setPriceDataSupported(true);

        when(marketDataProvider.getAssetMetadata("CUSTOM1"))
                .thenReturn(Optional.of(metadata));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "CUSTOM1"))
                .thenReturn(Optional.of(manualProfile));

        service.ensureMetadataForSymbol(userId, "CUSTOM1", "USD");

        AssetProfile saved = captureSavedProfile();

        assertThat(saved.getDisplayName()).isEqualTo("My Custom Asset");
        assertThat(saved.getAssetClass()).isEqualTo(AssetClass.REAL_ESTATE);
        assertThat(saved.getSector()).isEqualTo("User Sector");
        assertThat(saved.getMarket()).isEqualTo("User Market");
        assertThat(saved.getCurrency()).isEqualTo("GBP");
        assertThat(saved.getDataSource()).isEqualTo(DataSourceType.MANUAL);
        assertThat(saved.isManualValuationRequired()).isTrue();
        assertThat(saved.isPriceDataSupported()).isFalse();

        assertThat(saved.getProviderAssetType()).isEqualTo("Equity");
    }

    // Tests fallback branches for missing display name, asset class, sector,
    // market, currency, data source, and provider asset type.
    @Test
    void missingDisplayNameAssetClassCurrencyAndSource_shouldUseFallbackBranches() {
        AssetMetadata metadata = metadata();
        metadata.setDisplayName("   ");
        metadata.setAssetClass(null);
        metadata.setSector("   ");
        metadata.setMarket("   ");
        metadata.setCurrency(null);
        metadata.setDataSource(null);
        metadata.setProviderAssetType("   ");

        when(marketDataProvider.getAssetMetadata("UNKNOWN1"))
                .thenReturn(Optional.of(metadata));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "UNKNOWN1"))
                .thenReturn(Optional.empty());

        service.ensureMetadataForSymbol(userId, "unknown1", "gbp");

        AssetProfile saved = captureSavedProfile();

        assertThat(saved.getSymbol()).isEqualTo("UNKNOWN1");
        assertThat(saved.getDisplayName()).isEqualTo("UNKNOWN1");
        assertThat(saved.getAssetClass()).isEqualTo(AssetClass.OTHER);
        assertThat(saved.getSector()).isNull();
        assertThat(saved.getMarket()).isNull();
        assertThat(saved.getCurrency()).isEqualTo("GBP");
        assertThat(saved.getDataSource()).isEqualTo(DataSourceType.ALPHA_VANTAGE);
        assertThat(saved.getProviderAssetType()).isNull();
    }

    // Tests the currency fallback branch where both metadata currency and
    // fallback currency are missing, so the service defaults to USD.
    @Test
    void blankFallbackCurrencyAndMissingMetadataCurrency_shouldDefaultToUsd() {
        AssetMetadata metadata = metadata();
        metadata.setDisplayName("Unknown Asset");
        metadata.setAssetClass(AssetClass.OTHER);
        metadata.setCurrency(null);

        when(marketDataProvider.getAssetMetadata("UNKNOWN2"))
                .thenReturn(Optional.of(metadata));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "UNKNOWN2"))
                .thenReturn(Optional.empty());

        service.ensureMetadataForSymbol(userId, "UNKNOWN2", "   ");

        AssetProfile saved = captureSavedProfile();

        assertThat(saved.getCurrency()).isEqualTo("USD");
    }

    private AssetProfile captureSavedProfile() {
        ArgumentCaptor<AssetProfile> captor = ArgumentCaptor.forClass(AssetProfile.class);
        verify(assetProfileRepository).save(captor.capture());
        return captor.getValue();
    }

    private AssetMetadata metadata() {
        AssetMetadata metadata = new AssetMetadata();
        metadata.setSymbol("AAPL");
        metadata.setDisplayName("Apple Inc.");
        metadata.setAssetClass(AssetClass.EQUITY);
        metadata.setSector("Technology");
        metadata.setMarket("NASDAQ");
        metadata.setCurrency("USD");
        metadata.setDataSource(DataSourceType.ALPHA_VANTAGE);
        metadata.setProviderAssetType("Equity");
        metadata.setQuoteSupported(true);
        metadata.setHistoricalPriceSupported(true);
        metadata.setOptimisationSupported(true);
        metadata.setManualValuationRequired(false);
        metadata.setPriceDataSupported(true);
        return metadata;
    }
}