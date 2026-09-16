package com.tsh11.fypcode.integration.marketdata;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import com.tsh11.fypcode.repository.ActivityRepository;
import com.tsh11.fypcode.repository.AssetProfileRepository;
import com.tsh11.fypcode.service.AssetProfileService;
import com.tsh11.fypcode.service.HoldingService;
import com.tsh11.fypcode.service.PortfolioOptimisationService;
import com.tsh11.fypcode.service.portfolio.MeanVarianceOptimisationAlgorithm;
import com.tsh11.fypcode.service.portfolio.PortfolioStatisticsCalculator;
import com.tsh11.fypcode.service.portfolio.RebalanceSuggestionCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketDataFailureIntegrationTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private AssetProfileRepository assetProfileRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    @Mock
    private AssetProfileService assetProfileService;

    private HoldingService holdingService;
    private PortfolioOptimisationService optimisationService;

    private UUID userId;
    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        holdingService = new HoldingService(
                activityRepository,
                assetProfileRepository,
                marketDataProvider
        );

        MeanVarianceOptimisationAlgorithm meanVarianceAlgorithm =
                new MeanVarianceOptimisationAlgorithm(
                        new PortfolioStatisticsCalculator(),
                        new RebalanceSuggestionCalculator()
                );

        optimisationService = new PortfolioOptimisationService(
                holdingService,
                marketDataProvider,
                List.of(meanVarianceAlgorithm),
                assetProfileService
        );

        userId = UUID.randomUUID();
        from = LocalDate.of(2026, 1, 1);
        to = LocalDate.of(2026, 1, 31);
    }

    // Proves that if historical market data fails for one asset,
    // the workflow continues using remaining assets and marks the failed asset as unsupported.
    @Test
    void optimisationWorkflow_whenHistoricalPriceFetchFails_shouldExcludeAssetAndContinue() {
        Activity appleBuy = activity("AAPL", "10", "100", "USD", "2026-01-01T10:00:00Z");
        Activity microsoftBuy = activity("MSFT", "5", "200", "USD", "2026-01-02T10:00:00Z");
        Activity teslaBuy = activity("TSLA", "4", "250", "USD", "2026-01-03T10:00:00Z");

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(teslaBuy, microsoftBuy, appleBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile("AAPL", "Apple Inc.")));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "MSFT"))
                .thenReturn(Optional.of(profile("MSFT", "Microsoft Corporation")));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "TSLA"))
                .thenReturn(Optional.of(profile("TSLA", "Tesla Inc.")));

        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of(
                        "AAPL", bd("150"),
                        "MSFT", bd("300"),
                        "TSLA", bd("250")
                ));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121", "133.10"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "210", "220.50", "231.525"));

        when(marketDataProvider.getHistoricalPrices("TSLA", from, to))
                .thenThrow(new RuntimeException("Alpha Vantage failure"));

        PortfolioOptimisationResult result = optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        assertThat(result.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("AAPL", "MSFT");

        assertThat(result.getWarnings())
                .contains("TSLA was excluded because sufficient historical data was not available.");

        verify(assetProfileService).markHistoricalUnsupported(userId, "TSLA");
    }

    private Activity activity(
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

    private AssetProfile profile(String symbol, String displayName) {
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        profile.setSymbol(symbol);
        profile.setDisplayName(displayName);
        profile.setAssetClass(AssetClass.EQUITY);
        profile.setSector("Technology");
        profile.setMarket("NASDAQ");
        profile.setCurrency("USD");
        profile.setManualValuationRequired(false);
        profile.setPriceDataSupported(true);
        profile.setHistoricalPriceSupported(true);
        profile.setOptimisationSupported(true);
        return profile;
    }

    private List<HistoricalPricePoint> prices(String... closes) {
        LocalDate start = LocalDate.of(2026, 1, 1);

        java.util.ArrayList<HistoricalPricePoint> result = new java.util.ArrayList<>();

        for (int i = 0; i < closes.length; i++) {
            result.add(new HistoricalPricePoint(start.plusDays(i), bd(closes[i])));
        }

        return result;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}