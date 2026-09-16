package com.tsh11.fypcode.service.holding.whitebox;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.repository.ActivityRepository;
import com.tsh11.fypcode.repository.AssetProfileRepository;
import com.tsh11.fypcode.service.HoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class HoldingServiceBranchTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private AssetProfileRepository assetProfileRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    private HoldingService holdingService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        holdingService = new HoldingService(
                activityRepository,
                assetProfileRepository,
                marketDataProvider
        );

        userId = UUID.randomUUID();
    }

    @Test
    void assetProfileExists_shouldUseAssetProfileMetadataBranch() {
        Activity buy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "GBP",
                "2026-01-01T10:00:00Z"
        );

        AssetProfile profile = assetProfile(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile));

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        assertThat(holding.getDisplayName()).isEqualTo("Apple Inc.");
        assertThat(holding.getAssetClass()).isEqualTo(AssetClass.EQUITY);
        assertThat(holding.getSector()).isEqualTo("Technology");
        assertThat(holding.getMarket()).isEqualTo("NASDAQ");

        // AssetProfile currency should take priority over activity currency.
        assertThat(holding.getCurrency()).isEqualTo("USD");
    }

    @Test
    void assetProfileMissing_shouldUseFallbackMetadataBranch() {
        Activity buy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "GBP",
                "2026-01-01T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.empty());

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        assertThat(holding.getSymbol()).isEqualTo("AAPL");
        assertThat(holding.getDisplayName()).isEqualTo("AAPL");
        assertThat(holding.getAssetClass()).isNull();
        assertThat(holding.getSector()).isNull();
        assertThat(holding.getMarket()).isNull();
        assertThat(holding.getCurrency()).isEqualTo("GBP");
        assertThat(holding.isManualValuationRequired()).isFalse();
        assertThat(holding.isPriceDataSupported()).isTrue();
    }

    @Test
    void manualValuationRequired_shouldNotFetchPriceForThatSymbol() {
        Activity buy = activity(
                ActivityType.BUY,
                "HOUSE1",
                "1",
                "250000",
                "0",
                "GBP",
                "2026-01-01T10:00:00Z"
        );

        AssetProfile profile = assetProfile(
                "HOUSE1",
                "Manual Property",
                AssetClass.REAL_ESTATE,
                "Real Estate",
                "Manual",
                "GBP"
        );

        profile.setManualValuationRequired(true);
        profile.setPriceDataSupported(false);

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "HOUSE1"))
                .thenReturn(Optional.of(profile));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        assertThat(holding.getSymbol()).isEqualTo("HOUSE1");
        assertThat(holding.isManualValuationRequired()).isTrue();
        assertThat(holding.isPriceDataSupported()).isFalse();
        assertThat(holding.getLatestPrice()).isEqualByComparingTo("250000.000000");
        assertThat(holding.getMarketValue()).isEqualByComparingTo("250000.000000");
        assertThat(holding.getUnrealizedGainLoss()).isEqualByComparingTo("0.000000");
        assertThat(holding.getUnrealizedGainLossPercent()).isEqualByComparingTo("0");

        verify(marketDataProvider, never()).getLatestPrices(anyList());
        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    @Test
    void priceDataUnsupported_shouldNotFetchPriceForThatSymbol() {
        Activity buy = activity(
                ActivityType.BUY,
                "PRIVATE1",
                "10",
                "50",
                "0",
                "GBP",
                "2026-01-01T10:00:00Z"
        );

        AssetProfile profile = assetProfile(
                "PRIVATE1",
                "Private Asset",
                AssetClass.OTHER,
                "Other",
                "Manual",
                "GBP"
        );

        profile.setManualValuationRequired(false);
        profile.setPriceDataSupported(false);

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "PRIVATE1"))
                .thenReturn(Optional.of(profile));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        assertThat(holding.getSymbol()).isEqualTo("PRIVATE1");
        assertThat(holding.isManualValuationRequired()).isFalse();
        assertThat(holding.isPriceDataSupported()).isFalse();
        assertThat(holding.getLatestPrice()).isEqualByComparingTo("50.000000");
        assertThat(holding.getMarketValue()).isEqualByComparingTo("500.000000");
        assertThat(holding.getUnrealizedGainLoss()).isEqualByComparingTo("0.000000");
        assertThat(holding.getUnrealizedGainLossPercent()).isEqualByComparingTo("0");

        verify(marketDataProvider, never()).getLatestPrices(anyList());
        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    @Test
    void symbolWithLowercaseAndWhitespace_shouldBeNormalisedBeforeGroupingLookupAndResponse() {
        Activity buy = activity(
                ActivityType.BUY,
                "  aapl  ",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        AssetProfile profile = assetProfile(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile));

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");

        verify(assetProfileRepository).findByUserIdAndSymbolIgnoreCase(userId, "AAPL");

        ArgumentCaptor<List<String>> symbolsCaptor = ArgumentCaptor.forClass(List.class);
        verify(marketDataProvider).getLatestPrices(symbolsCaptor.capture());

        assertThat(symbolsCaptor.getValue()).containsExactly("AAPL");
    }

    private Activity activity(
            ActivityType type,
            String symbol,
            String quantity,
            String unitPrice,
            String fee,
            String currency,
            String date
    ) {
        Activity activity = new Activity();
        activity.setUserId(userId);
        activity.setType(type);
        activity.setSymbol(symbol);
        activity.setQuantity(bd(quantity));
        activity.setUnitPrice(bd(unitPrice));
        activity.setFee(bd(fee));
        activity.setCurrency(currency);
        activity.setDate(Instant.parse(date));
        return activity;
    }

    private AssetProfile assetProfile(
            String symbol,
            String displayName,
            AssetClass assetClass,
            String sector,
            String market,
            String currency
    ) {
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        profile.setSymbol(symbol);
        profile.setDisplayName(displayName);
        profile.setAssetClass(assetClass);
        profile.setSector(sector);
        profile.setMarket(market);
        profile.setCurrency(currency);
        profile.setManualValuationRequired(false);
        profile.setPriceDataSupported(true);
        profile.setHistoricalPriceSupported(true);
        profile.setOptimisationSupported(true);
        return profile;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}