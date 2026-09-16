package com.tsh11.fypcode.service.metadata.whitebox;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.domain.asset.DataSourceType;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetMetadataSyncServiceFailureHandlingTest {

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

    // Verifies that a null user ID is ignored safely and no external/repository calls are made.
    @Test
    void nullUserId_shouldReturnWithoutCallingDependencies() {
        service.ensureMetadataForSymbol(null, "AAPL", "USD");

        verifyNoInteractions(marketDataProvider);
        verifyNoInteractions(assetProfileRepository);
    }

    // Verifies that a blank symbol is ignored safely and no external/repository calls are made.
    @Test
    void blankSymbol_shouldReturnWithoutCallingDependencies() {
        service.ensureMetadataForSymbol(userId, "   ", "USD");

        verifyNoInteractions(marketDataProvider);
        verifyNoInteractions(assetProfileRepository);
    }

    // Verifies that missing external metadata creates a safe fallback
    // profile if the user does not already have one.
    @Test
    void metadataMissingAndProfileDoesNotExist_shouldCreateFallbackProfile() {
        when(marketDataProvider.getAssetMetadata("AAPL"))
                .thenReturn(Optional.empty());

        when(assetProfileRepository.existsByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(false);

        service.ensureMetadataForSymbol(userId, " aapl ", "gbp");

        AssetProfile saved = captureSavedProfile();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getSymbol()).isEqualTo("AAPL");
        assertThat(saved.getDisplayName()).isEqualTo("AAPL");
        assertThat(saved.getAssetClass()).isEqualTo(AssetClass.OTHER);
        assertThat(saved.getSector()).isNull();
        assertThat(saved.getMarket()).isNull();
        assertThat(saved.getCurrency()).isEqualTo("GBP");
        assertThat(saved.getDataSource()).isEqualTo(DataSourceType.OTHER);

        assertThat(saved.isManualValuationRequired()).isFalse();
        assertThat(saved.isPriceDataSupported()).isFalse();
        assertThat(saved.isQuoteSupported()).isFalse();
        assertThat(saved.isHistoricalPriceSupported()).isFalse();
        assertThat(saved.isOptimisationSupported()).isFalse();
    }

    // Verifies that the service does not create duplicate fallback profiles when a profile already exists.
    @Test
    void metadataMissingAndProfileAlreadyExists_shouldNotCreateFallbackProfile() {
        when(marketDataProvider.getAssetMetadata("AAPL"))
                .thenReturn(Optional.empty());

        when(assetProfileRepository.existsByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(true);

        service.ensureMetadataForSymbol(userId, "AAPL", "USD");

        verify(assetProfileRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // Verifies that fallback profile creation defaults currency to USD
    // when no metadata currency or fallback currency is available.
    @Test
    void metadataMissingAndFallbackCurrencyBlank_shouldCreateFallbackProfileWithUsdCurrency() {
        when(marketDataProvider.getAssetMetadata("UNKNOWN1"))
                .thenReturn(Optional.empty());

        when(assetProfileRepository.existsByUserIdAndSymbolIgnoreCase(userId, "UNKNOWN1"))
                .thenReturn(false);

        service.ensureMetadataForSymbol(userId, "UNKNOWN1", "   ");

        AssetProfile saved = captureSavedProfile();

        assertThat(saved.getCurrency()).isEqualTo("USD");
    }

    private AssetProfile captureSavedProfile() {
        ArgumentCaptor<AssetProfile> captor = ArgumentCaptor.forClass(AssetProfile.class);
        verify(assetProfileRepository).save(captor.capture());
        return captor.getValue();
    }
}