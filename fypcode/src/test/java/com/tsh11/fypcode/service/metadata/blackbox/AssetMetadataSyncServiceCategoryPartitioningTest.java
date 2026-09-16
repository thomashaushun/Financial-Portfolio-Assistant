package com.tsh11.fypcode.service.metadata.blackbox;

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
class AssetMetadataSyncServiceCategoryPartitioningTest {

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

    // Verifies that equity metadata, such as AAPL,
    // creates an EQUITY asset profile with provider metadata, currency,
    // sector, market, and optimisation support.
    @Test
    void equityMetadata_shouldCreateEquityAssetProfile() {
        AssetMetadata metadata = metadata(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "usd",
                DataSourceType.ALPHA_VANTAGE,
                "Equity",
                true,
                true,
                true,
                false,
                true
        );

        when(marketDataProvider.getAssetMetadata("AAPL"))
                .thenReturn(Optional.of(metadata));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.empty());

        service.ensureMetadataForSymbol(userId, " aapl ", "GBP");

        AssetProfile saved = captureSavedProfile();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getSymbol()).isEqualTo("AAPL");
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
        assertThat(saved.isManualValuationRequired()).isFalse();
        assertThat(saved.isPriceDataSupported()).isTrue();
    }

    // Verifies that ETF metadata creates an ETF asset profile and preserves ETF-specific provider information.
    @Test
    void etfMetadata_shouldCreateEtfAssetProfile() {
        AssetMetadata metadata = metadata(
                "VOO",
                "Vanguard S&P 500 ETF",
                AssetClass.ETF,
                "Diversified",
                "NYSE",
                "USD",
                DataSourceType.ALPHA_VANTAGE,
                "ETF",
                true,
                true,
                true,
                false,
                true
        );

        when(marketDataProvider.getAssetMetadata("VOO"))
                .thenReturn(Optional.of(metadata));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "VOO"))
                .thenReturn(Optional.empty());

        service.ensureMetadataForSymbol(userId, "VOO", "USD");

        AssetProfile saved = captureSavedProfile();

        assertThat(saved.getSymbol()).isEqualTo("VOO");
        assertThat(saved.getDisplayName()).isEqualTo("Vanguard S&P 500 ETF");
        assertThat(saved.getAssetClass()).isEqualTo(AssetClass.ETF);
        assertThat(saved.getSector()).isEqualTo("Diversified");
        assertThat(saved.getMarket()).isEqualTo("NYSE");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getProviderAssetType()).isEqualTo("ETF");
        assertThat(saved.isOptimisationSupported()).isTrue();
    }

    // Verifies that manual or real-estate metadata creates a profile that requires
    // manual valuation and is not supported for price data or optimisation.
    @Test
    void realEstateManualMetadata_shouldCreateManualValuationAssetProfile() {
        AssetMetadata metadata = metadata(
                "HOUSE1",
                "Rental Property",
                AssetClass.REAL_ESTATE,
                null,
                null,
                "GBP",
                DataSourceType.MANUAL,
                "Manual Real Estate",
                false,
                false,
                false,
                true,
                false
        );

        when(marketDataProvider.getAssetMetadata("HOUSE1"))
                .thenReturn(Optional.of(metadata));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "HOUSE1"))
                .thenReturn(Optional.empty());

        service.ensureMetadataForSymbol(userId, "HOUSE1", "GBP");

        AssetProfile saved = captureSavedProfile();

        assertThat(saved.getSymbol()).isEqualTo("HOUSE1");
        assertThat(saved.getDisplayName()).isEqualTo("Rental Property");
        assertThat(saved.getAssetClass()).isEqualTo(AssetClass.REAL_ESTATE);
        assertThat(saved.getCurrency()).isEqualTo("GBP");
        assertThat(saved.getDataSource()).isEqualTo(DataSourceType.MANUAL);
        assertThat(saved.isManualValuationRequired()).isTrue();
        assertThat(saved.isPriceDataSupported()).isFalse();
        assertThat(saved.isQuoteSupported()).isFalse();
        assertThat(saved.isHistoricalPriceSupported()).isFalse();
        assertThat(saved.isOptimisationSupported()).isFalse();
    }

    private AssetProfile captureSavedProfile() {
        ArgumentCaptor<AssetProfile> captor = ArgumentCaptor.forClass(AssetProfile.class);
        verify(assetProfileRepository).save(captor.capture());
        return captor.getValue();
    }

    private AssetMetadata metadata(
            String symbol,
            String displayName,
            AssetClass assetClass,
            String sector,
            String market,
            String currency,
            DataSourceType dataSource,
            String providerAssetType,
            boolean quoteSupported,
            boolean historicalPriceSupported,
            boolean optimisationSupported,
            boolean manualValuationRequired,
            boolean priceDataSupported
    ) {
        AssetMetadata metadata = new AssetMetadata();
        metadata.setSymbol(symbol);
        metadata.setDisplayName(displayName);
        metadata.setAssetClass(assetClass);
        metadata.setSector(sector);
        metadata.setMarket(market);
        metadata.setCurrency(currency);
        metadata.setDataSource(dataSource);
        metadata.setProviderAssetType(providerAssetType);
        metadata.setQuoteSupported(quoteSupported);
        metadata.setHistoricalPriceSupported(historicalPriceSupported);
        metadata.setOptimisationSupported(optimisationSupported);
        metadata.setManualValuationRequired(manualValuationRequired);
        metadata.setPriceDataSupported(priceDataSupported);
        return metadata;
    }
}