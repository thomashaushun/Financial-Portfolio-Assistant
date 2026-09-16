package com.tsh11.fypcode.service.optimisation.blackbox;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.PortfolioOptimisationRequest;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.integration.marketdata.HistoricalPricePoint;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioOptimisationServiceEquivalencePartitioningTest {

    @Mock
    private HoldingService holdingService;

    @Mock
    private MarketDataProvider marketDataProvider;

    @Mock
    private PortfolioOptimisationAlgorithm algorithm;

    @Mock
    private AssetProfileService assetProfileService;

    private PortfolioOptimisationService optimisationService;

    private UUID userId;
    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        optimisationService = new PortfolioOptimisationService(
                holdingService,
                marketDataProvider,
                List.of(algorithm),
                assetProfileService
        );

        userId = UUID.randomUUID();
        from = LocalDate.of(2026, 1, 1);
        to = LocalDate.of(2026, 1, 5);
    }

    //Valid two-asset portfolio
    //Optimisation request is built and result returned
    @Test
    void validTwoAssetPortfolio_shouldReturnOptimisationResultAndSuggestedChanges() {
        HoldingResponse apple = eligibleHolding(
                "AAPL",
                "Apple Inc.",
                "1000",
                "100",
                AssetClass.EQUITY
        );

        HoldingResponse microsoft = eligibleHolding(
                "MSFT",
                "Microsoft Corporation",
                "3000",
                "150",
                AssetClass.EQUITY
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "220", "242"));

        when(algorithm.getAlgorithmType())
                .thenReturn(PortfolioAlgorithm.MEAN_VARIANCE);

        PortfolioOptimisationResult algorithmResult = resultWithAssets(
                assetResult("AAPL", "0.50"),
                assetResult("MSFT", "0.50")
        );

        when(algorithm.optimise(any(PortfolioOptimisationRequest.class)))
                .thenReturn(algorithmResult);

        PortfolioOptimisationResult result = optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        assertThat(result).isSameAs(algorithmResult);
        assertThat(result.getAssets()).hasSize(2);

        PortfolioOptimisationAssetResult appleResult = findAsset(result, "AAPL");
        PortfolioOptimisationAssetResult microsoftResult = findAsset(result, "MSFT");

        assertThat(appleResult.getCurrentValue()).isEqualByComparingTo("1000");
        assertThat(appleResult.getTargetValue()).isEqualByComparingTo("2000.00");
        assertThat(appleResult.getValueDifference()).isEqualByComparingTo("1000.00");
        assertThat(appleResult.getLatestPrice()).isEqualByComparingTo("100");
        assertThat(appleResult.getSuggestedUnitChange()).isEqualByComparingTo("10.00");
        assertThat(appleResult.getSuggestedAction()).isEqualTo("BUY");

        assertThat(microsoftResult.getCurrentValue()).isEqualByComparingTo("3000");
        assertThat(microsoftResult.getTargetValue()).isEqualByComparingTo("2000.00");
        assertThat(microsoftResult.getValueDifference()).isEqualByComparingTo("-1000.00");
        assertThat(microsoftResult.getLatestPrice()).isEqualByComparingTo("150");
        assertThat(microsoftResult.getSuggestedAction()).isEqualTo("REDUCE");

        ArgumentCaptor<PortfolioOptimisationRequest> requestCaptor =
                ArgumentCaptor.forClass(PortfolioOptimisationRequest.class);

        org.mockito.Mockito.verify(algorithm).optimise(requestCaptor.capture());

        PortfolioOptimisationRequest request = requestCaptor.getValue();

        assertThat(request.getAlgorithm()).isEqualTo(PortfolioAlgorithm.MEAN_VARIANCE);
        assertThat(request.getFrom()).isEqualTo(from);
        assertThat(request.getTo()).isEqualTo(to);
        assertThat(request.getRiskFreeRate()).isEqualByComparingTo("0.02");
        assertThat(request.getSymbols()).containsExactly("AAPL", "MSFT");

        assertThat(request.getCurrentWeights().get("AAPL")).isEqualByComparingTo("0.25");
        assertThat(request.getCurrentWeights().get("MSFT")).isEqualByComparingTo("0.75");

        assertThat(request.getDailyReturns()).containsKeys("AAPL", "MSFT");
        assertThat(request.getDailyReturns().get("AAPL")).hasSize(2);
        assertThat(request.getDailyReturns().get("MSFT")).hasSize(2);

        assertThat(request.getConstraints().isLongOnly()).isTrue();
        assertThat(request.getConstraints().getMinWeightPerAsset()).isEqualByComparingTo("0");
        assertThat(request.getConstraints().getMaxWeightPerAsset()).isEqualByComparingTo("0.60");
    }

    //Lowercase/whitespace symbols
    //Symbols are normalised before processing
    @Test
    void lowercaseAndWhitespaceSymbols_shouldBeNormalisedBeforeOptimisation() {
        HoldingResponse apple = eligibleHolding(
                "  aapl  ",
                "Apple Inc.",
                "1000",
                "100",
                AssetClass.EQUITY
        );

        HoldingResponse microsoft = eligibleHolding(
                "msft",
                "Microsoft Corporation",
                "1000",
                "100",
                AssetClass.EQUITY
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "210", "220"));

        when(algorithm.getAlgorithmType())
                .thenReturn(PortfolioAlgorithm.MEAN_VARIANCE);

        when(algorithm.optimise(any(PortfolioOptimisationRequest.class)))
                .thenReturn(resultWithAssets(
                        assetResult("AAPL", "0.50"),
                        assetResult("MSFT", "0.50")
                ));

        PortfolioOptimisationResult result = optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        assertThat(result.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("AAPL", "MSFT");

        org.mockito.Mockito.verify(marketDataProvider)
                .getHistoricalPrices("AAPL", from, to);

        org.mockito.Mockito.verify(marketDataProvider)
                .getHistoricalPrices("MSFT", from, to);
    }

    private HoldingResponse eligibleHolding(
            String symbol,
            String displayName,
            String marketValue,
            String latestPrice,
            AssetClass assetClass
    ) {
        HoldingResponse holding = new HoldingResponse();
        holding.setSymbol(symbol);
        holding.setDisplayName(displayName);
        holding.setAssetClass(assetClass);
        holding.setMarketValue(bd(marketValue));
        holding.setLatestPrice(bd(latestPrice));
        holding.setManualValuationRequired(false);
        holding.setPriceDataSupported(true);
        holding.setOptimisationSupported(true);
        return holding;
    }

    private List<HistoricalPricePoint> prices(String... closes) {
        LocalDate start = LocalDate.of(2026, 1, 1);

        java.util.ArrayList<HistoricalPricePoint> result = new java.util.ArrayList<>();

        for (int i = 0; i < closes.length; i++) {
            result.add(new HistoricalPricePoint(start.plusDays(i), bd(closes[i])));
        }

        return result;
    }

    private PortfolioOptimisationAssetResult assetResult(String symbol, String targetWeight) {
        PortfolioOptimisationAssetResult asset = new PortfolioOptimisationAssetResult();
        asset.setSymbol(symbol);
        asset.setTargetWeight(bd(targetWeight));
        return asset;
    }

    private PortfolioOptimisationResult resultWithAssets(PortfolioOptimisationAssetResult... assets) {
        PortfolioOptimisationResult result = new PortfolioOptimisationResult();
        result.setAlgorithm(PortfolioAlgorithm.MEAN_VARIANCE);
        result.setAssets(List.of(assets));
        return result;
    }

    private PortfolioOptimisationAssetResult findAsset(PortfolioOptimisationResult result, String symbol) {
        return result.getAssets()
                .stream()
                .filter(asset -> asset.getSymbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Asset not found: " + symbol));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}