package com.tsh11.fypcode.integration.portfolio;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.response.AllocationItemResponse;
import com.tsh11.fypcode.dto.response.AllocationResponse;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.integration.marketdata.HistoricalPricePoint;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.repository.ActivityRepository;
import com.tsh11.fypcode.repository.AssetProfileRepository;
import com.tsh11.fypcode.service.AllocationService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioWorkflowIntegrationTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private AssetProfileRepository assetProfileRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    @Mock
    private AssetProfileService assetProfileService;

    private HoldingService holdingService;
    private AllocationService allocationService;
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

        allocationService = new AllocationService(holdingService);

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

    // Proves that activities can flow through real HoldingService, real AllocationService,
    // real PortfolioOptimisationService, and real mean-variance algorithm.
    @Test
    void activityDrivenWorkflow_shouldDeriveHoldingsAllocationAndOptimisation() {
        Activity appleBuy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity microsoftBuy = activity(
                ActivityType.BUY,
                "MSFT",
                "5",
                "200",
                "0",
                "USD",
                "2026-01-02T10:00:00Z"
        );

        AssetProfile appleProfile = profile(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                false,
                true,
                true
        );

        AssetProfile microsoftProfile = profile(
                "MSFT",
                "Microsoft Corporation",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                false,
                true,
                true
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(microsoftBuy, appleBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(appleProfile));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "MSFT"))
                .thenReturn(Optional.of(microsoftProfile));

        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of(
                        "AAPL", bd("150"),
                        "MSFT", bd("300")
                ));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121", "133.10"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "210", "220.50", "231.525"));

        List<HoldingResponse> holdings = holdingService.getHoldings(userId);

        assertThat(holdings).hasSize(2);

        HoldingResponse appleHolding = findHolding(holdings, "AAPL");
        HoldingResponse microsoftHolding = findHolding(holdings, "MSFT");

        assertThat(appleHolding.getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(appleHolding.getMarketValue()).isEqualByComparingTo("1500");

        assertThat(microsoftHolding.getTotalQuantity()).isEqualByComparingTo("5");
        assertThat(microsoftHolding.getMarketValue()).isEqualByComparingTo("1500");

        AllocationResponse allocation = allocationService.getAllocation(userId);

        AllocationItemResponse equityAllocation = findAllocationItem(
                allocation.getByAssetClass(),
                "EQUITY"
        );

        AllocationItemResponse technologyAllocation = findAllocationItem(
                allocation.getBySector(),
                "Technology"
        );

        AllocationItemResponse usdAllocation = findAllocationItem(
                allocation.getByCurrency(),
                "USD"
        );

        assertThat(equityAllocation.getValue()).isEqualByComparingTo("3000");
        assertThat(equityAllocation.getWeightPercent()).isEqualByComparingTo("100.000000");

        assertThat(technologyAllocation.getValue()).isEqualByComparingTo("3000");
        assertThat(usdAllocation.getValue()).isEqualByComparingTo("3000");

        PortfolioOptimisationResult optimisation = optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        assertThat(optimisation).isNotNull();
        assertThat(optimisation.getAlgorithm()).isEqualTo(PortfolioAlgorithm.MEAN_VARIANCE);
        assertThat(optimisation.getCurrentPortfolio()).isNotNull();
        assertThat(optimisation.getOptimisedPortfolio()).isNotNull();
        assertThat(optimisation.getAssets()).hasSize(2);
        assertThat(optimisation.getEfficientFrontier()).isNotEmpty();

        assertThat(optimisation.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("AAPL", "MSFT");

        BigDecimal targetWeightSum = optimisation.getAssets()
                .stream()
                .map(PortfolioOptimisationAssetResult::getTargetWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(targetWeightSum).isEqualByComparingTo("1.00");
    }

    // Proves that manual assets can appear in holdings/allocation but
    // are excluded from optimisation with a warning.
    @Test
    void workflowWithManualAsset_shouldIncludeManualAssetInAllocationButExcludeFromOptimisation() {
        Activity appleBuy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity microsoftBuy = activity(
                ActivityType.BUY,
                "MSFT",
                "5",
                "200",
                "0",
                "USD",
                "2026-01-02T10:00:00Z"
        );

        Activity propertyBuy = activity(
                ActivityType.BUY,
                "HOUSE1",
                "1",
                "250000",
                "0",
                "GBP",
                "2026-01-03T10:00:00Z"
        );

        AssetProfile appleProfile = profile(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                false,
                true,
                true
        );

        AssetProfile microsoftProfile = profile(
                "MSFT",
                "Microsoft Corporation",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                false,
                true,
                true
        );

        AssetProfile propertyProfile = profile(
                "HOUSE1",
                "Rental Property",
                AssetClass.REAL_ESTATE,
                "Real Estate",
                "Manual",
                "GBP",
                true,
                false,
                false
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(propertyBuy, microsoftBuy, appleBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(appleProfile));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "MSFT"))
                .thenReturn(Optional.of(microsoftProfile));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "HOUSE1"))
                .thenReturn(Optional.of(propertyProfile));

        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of(
                        "AAPL", bd("150"),
                        "MSFT", bd("300")
                ));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121", "133.10"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "210", "220.50", "231.525"));

        AllocationResponse allocation = allocationService.getAllocation(userId);

        AllocationItemResponse realEstateAllocation = findAllocationItem(
                allocation.getByAssetClass(),
                "REAL_ESTATE"
        );

        assertThat(realEstateAllocation.getValue()).isEqualByComparingTo("250000");

        PortfolioOptimisationResult optimisation = optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        assertThat(optimisation.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("AAPL", "MSFT");

        assertThat(optimisation.getWarnings())
                .contains("HOUSE1 was excluded because it is not eligible for optimisation.");
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

    private AssetProfile profile(
            String symbol,
            String displayName,
            AssetClass assetClass,
            String sector,
            String market,
            String currency,
            boolean manualValuationRequired,
            boolean priceDataSupported,
            boolean optimisationSupported
    ) {
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        profile.setSymbol(symbol);
        profile.setDisplayName(displayName);
        profile.setAssetClass(assetClass);
        profile.setSector(sector);
        profile.setMarket(market);
        profile.setCurrency(currency);
        profile.setManualValuationRequired(manualValuationRequired);
        profile.setPriceDataSupported(priceDataSupported);
        profile.setHistoricalPriceSupported(optimisationSupported);
        profile.setOptimisationSupported(optimisationSupported);
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

    private HoldingResponse findHolding(List<HoldingResponse> holdings, String symbol) {
        return holdings.stream()
                .filter(holding -> holding.getSymbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Holding not found: " + symbol));
    }

    private AllocationItemResponse findAllocationItem(
            List<AllocationItemResponse> items,
            String label
    ) {
        return items.stream()
                .filter(item -> item.getLabel().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Allocation item not found: " + label));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}