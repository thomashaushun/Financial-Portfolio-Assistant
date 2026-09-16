package com.tsh11.fypcode.integration.marketdata;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.dto.response.HoldingResponse;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Prevents future changes from reintroducing per-symbol Alpha Vantage calls from holdings
@ExtendWith(MockitoExtension.class)
class MarketDataQuotaRegressionTest {

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

    //Ensures holdings request prices in one batch and never call getLatestPrice(symbol) repeatedly
    @Test
    void holdings_shouldFetchLatestPricesInOneBatchAndNeverUseSingleQuoteFallback() {
        Activity appleBuy = buy("AAPL", "10", "100", "USD", "2026-01-01T10:00:00Z");
        Activity microsoftBuy = buy("MSFT", "5", "200", "USD", "2026-01-02T10:00:00Z");
        Activity spyBuy = buy("SPY", "2", "400", "USD", "2026-01-03T10:00:00Z");

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(spyBuy, microsoftBuy, appleBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile("AAPL", "Apple Inc.", AssetClass.EQUITY)));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "MSFT"))
                .thenReturn(Optional.of(profile("MSFT", "Microsoft Corporation", AssetClass.EQUITY)));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "SPY"))
                .thenReturn(Optional.of(profile("SPY", "SPDR S&P 500 ETF Trust", AssetClass.ETF)));

        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of(
                        "AAPL", bd("150"),
                        "MSFT", bd("300"),
                        "SPY", bd("500")
                ));

        List<HoldingResponse> holdings = holdingService.getHoldings(userId);

        assertThat(holdings).hasSize(3);

        ArgumentCaptor<List<String>> symbolsCaptor = ArgumentCaptor.forClass(List.class);

        verify(marketDataProvider).getLatestPrices(symbolsCaptor.capture());

        assertThat(symbolsCaptor.getValue())
                .containsExactlyInAnyOrder("AAPL", "MSFT", "SPY");

        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    // Ensures manual assets do not trigger external quote calls
    @Test
    void holdings_withOnlyManualAssets_shouldNotRequestLatestPrices() {
        Activity propertyBuy = buy("HOUSE1", "1", "250000", "GBP", "2026-01-01T10:00:00Z");

        AssetProfile manualProperty = profile(
                "HOUSE1",
                "Rental Property",
                AssetClass.REAL_ESTATE
        );
        manualProperty.setManualValuationRequired(true);
        manualProperty.setPriceDataSupported(false);
        manualProperty.setHistoricalPriceSupported(false);
        manualProperty.setOptimisationSupported(false);

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(propertyBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "HOUSE1"))
                .thenReturn(Optional.of(manualProperty));

        List<HoldingResponse> holdings = holdingService.getHoldings(userId);

        assertThat(holdings).hasSize(1);
        assertThat(holdings.get(0).getSymbol()).isEqualTo("HOUSE1");
        assertThat(holdings.get(0).isManualValuationRequired()).isTrue();

        verify(marketDataProvider, never()).getLatestPrices(anyList());
        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    // Ensures missing bulk quote prices do not cause single quote fallback calls
    @Test
    void holdings_whenBatchProviderMissesOneSymbol_shouldNotFallbackToSingleQuote() {
        Activity appleBuy = buy("AAPL", "10", "100", "USD", "2026-01-01T10:00:00Z");
        Activity spyBuy = buy("SPY", "2", "400", "USD", "2026-01-02T10:00:00Z");

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(spyBuy, appleBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile("AAPL", "Apple Inc.", AssetClass.EQUITY)));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "SPY"))
                .thenReturn(Optional.of(profile("SPY", "SPDR S&P 500 ETF Trust", AssetClass.ETF)));

        /*
         Simulates Alpha Vantage bulk quote returning only AAPL.
         SPY is missing from the price map.
         HoldingService should not fall back to individual GLOBAL_QUOTE calls.
         Instead, the missing price should be handled by using average cost as
         a safe fallback valuation price.
         */
        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> holdings = holdingService.getHoldings(userId);

        HoldingResponse apple = findHolding(holdings, "AAPL");
        HoldingResponse spy = findHolding(holdings, "SPY");

        assertThat(apple.getLatestPrice()).isEqualByComparingTo("150");
        assertThat(apple.getMarketValue()).isEqualByComparingTo("1500");

        assertThat(spy.getLatestPrice()).isEqualByComparingTo("400.000000");
        assertThat(spy.getMarketValue()).isEqualByComparingTo("800.000000");
        assertThat(spy.getUnrealizedGainLoss()).isEqualByComparingTo("0.000000");
        assertThat(spy.getUnrealizedGainLossPercent()).isEqualByComparingTo("0");

        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    private Activity buy(
            String symbol,
            String quantity,
            String unitPrice,
            String currency,
            String date
    ) {
        Activity activity = new Activity();
        activity.setUserId(userId);
        activity.setType(ActivityType.BUY);
        activity.setSymbol(symbol);
        activity.setQuantity(bd(quantity));
        activity.setUnitPrice(bd(unitPrice));
        activity.setFee(BigDecimal.ZERO);
        activity.setCurrency(currency);
        activity.setDate(Instant.parse(date));
        return activity;
    }

    private AssetProfile profile(
            String symbol,
            String displayName,
            AssetClass assetClass
    ) {
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        profile.setSymbol(symbol);
        profile.setDisplayName(displayName);
        profile.setAssetClass(assetClass);
        profile.setSector("Technology");
        profile.setMarket("United States");
        profile.setCurrency("USD");
        profile.setManualValuationRequired(false);
        profile.setPriceDataSupported(true);
        profile.setHistoricalPriceSupported(true);
        profile.setOptimisationSupported(true);
        return profile;
    }

    private HoldingResponse findHolding(List<HoldingResponse> holdings, String symbol) {
        return holdings.stream()
                .filter(holding -> holding.getSymbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Holding not found: " + symbol));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}