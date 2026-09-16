package com.tsh11.fypcode.integration.portfolio;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.PortfolioOptimisationRequest;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import com.tsh11.fypcode.integration.marketdata.HistoricalPricePoint;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.repository.ActivityRepository;
import com.tsh11.fypcode.repository.AssetProfileRepository;
import com.tsh11.fypcode.service.AssetProfileService;
import com.tsh11.fypcode.service.HoldingService;
import com.tsh11.fypcode.service.PortfolioOptimisationService;
import com.tsh11.fypcode.service.portfolio.PortfolioOptimisationAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioOptimisationEligibilityRegressionTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private AssetProfileRepository assetProfileRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    @Mock
    private PortfolioOptimisationAlgorithm algorithm;

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

        optimisationService = new PortfolioOptimisationService(
                holdingService,
                marketDataProvider,
                List.of(algorithm),
                assetProfileService
        );

        userId = UUID.randomUUID();
        from = LocalDate.of(2025, 1, 1);
        to = LocalDate.of(2025, 12, 31);
    }

    // Recreates the bug pattern from log: latest prices are zero/missing,
    // but assets are normal equities with positive activities.
    // The test expects optimisation to attempt historical prices for multiple assets instead of stopping with only one eligible asset.
    @Test
    void optimisation_shouldAttemptHistoricalPricesForMultipleMarketAssetsEvenWhenLatestPricesAreMissing() {
        Activity sbhmyBuy = buy("SBHMY", "100", "14.30", "USD", "2025-01-01T10:00:00Z");
        Activity adobeBuy = buy("ADBE", "20", "14.50", "USD", "2025-01-01T10:00:00Z");
        Activity severnBuy = buy("SVTRF", "20", "20.10", "USD", "2025-01-01T10:00:00Z");

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(severnBuy, adobeBuy, sbhmyBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "SBHMY"))
                .thenReturn(Optional.of(staleEquityProfile("SBHMY", "Sino Biopharmaceutical Ltd")));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "ADBE"))
                .thenReturn(Optional.of(staleEquityProfile("ADBE", "Adobe Inc")));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "SVTRF"))
                .thenReturn(Optional.of(staleEquityProfile("SVTRF", "Severn Trent plc")));

        /*
         Simulates Alpha Vantage bulk quote returning no latest prices.
         This should not exclude otherwise valid market assets from optimisation,
         because PortfolioOptimisationService can use totalCost as fallback value.
         */
        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of());

        when(marketDataProvider.getHistoricalPrices("SBHMY", from, to))
                .thenReturn(prices("10", "11", "12", "13"));

        when(marketDataProvider.getHistoricalPrices("ADBE", from, to))
                .thenReturn(prices("20", "22", "24", "26"));

        when(marketDataProvider.getHistoricalPrices("SVTRF", from, to))
                .thenReturn(prices("30", "31", "32", "33"));

        when(algorithm.getAlgorithmType())
                .thenReturn(PortfolioAlgorithm.MEAN_VARIANCE);

        when(algorithm.optimise(any(PortfolioOptimisationRequest.class)))
                .thenReturn(resultWithAssets("SBHMY", "ADBE", "SVTRF"));

        PortfolioOptimisationResult result = optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        assertThat(result).isNotNull();

        verify(marketDataProvider).getHistoricalPrices("SBHMY", from, to);
        verify(marketDataProvider).getHistoricalPrices("ADBE", from, to);
        verify(marketDataProvider).getHistoricalPrices("SVTRF", from, to);

        ArgumentCaptor<PortfolioOptimisationRequest> requestCaptor =
                ArgumentCaptor.forClass(PortfolioOptimisationRequest.class);

        verify(algorithm).optimise(requestCaptor.capture());

        PortfolioOptimisationRequest request = requestCaptor.getValue();

        assertThat(request.getSymbols())
                .containsExactly("ADBE", "SBHMY", "SVTRF");

        assertThat(request.getSymbols()).hasSizeGreaterThanOrEqualTo(2);
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

    private AssetProfile staleEquityProfile(String symbol, String displayName) {
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        profile.setSymbol(symbol);
        profile.setDisplayName(displayName);
        profile.setAssetClass(AssetClass.EQUITY);
        profile.setSector("Technology");
        profile.setMarket("United States");
        profile.setCurrency("USD");
        profile.setManualValuationRequired(false);
        profile.setPriceDataSupported(true);

        /*
         Simulates old profile flags.
         The application should infer support for normal market assets
         and then let the historical data fetch decide whether the asset is usable.
         */
        profile.setHistoricalPriceSupported(false);
        profile.setOptimisationSupported(false);

        return profile;
    }

    private List<HistoricalPricePoint> prices(String... closes) {
        LocalDate start = from;
        List<HistoricalPricePoint> result = new ArrayList<>();

        for (int i = 0; i < closes.length; i++) {
            result.add(new HistoricalPricePoint(start.plusDays(i), bd(closes[i])));
        }

        return result;
    }

    private PortfolioOptimisationResult resultWithAssets(String... symbols) {
        PortfolioOptimisationResult result = new PortfolioOptimisationResult();
        result.setAlgorithm(PortfolioAlgorithm.MEAN_VARIANCE);

        List<PortfolioOptimisationAssetResult> assets = new ArrayList<>();

        for (String symbol : symbols) {
            PortfolioOptimisationAssetResult asset = new PortfolioOptimisationAssetResult();
            asset.setSymbol(symbol);
            asset.setCurrentWeight(BigDecimal.ZERO);
            asset.setTargetWeight(BigDecimal.ZERO);
            asset.setWeightDifference(BigDecimal.ZERO);
            assets.add(asset);
        }

        result.setAssets(assets);
        result.setSuggestions(new ArrayList<>());
        result.setWarnings(new ArrayList<>());
        result.setEfficientFrontier(new ArrayList<>());

        return result;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}