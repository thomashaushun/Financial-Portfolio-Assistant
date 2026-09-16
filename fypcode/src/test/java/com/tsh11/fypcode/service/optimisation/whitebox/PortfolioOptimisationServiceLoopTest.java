package com.tsh11.fypcode.service.optimisation.whitebox;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioOptimisationServiceLoopTest {

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
        to = LocalDate.of(2026, 1, 6);
    }

    //Different return series lengths
    //Returns aligned to shortest series
    @Test
    void differentLengthHistoricalReturnSeries_shouldBeAlignedToShortestSeries() {
        HoldingResponse apple = eligibleHolding("AAPL", "1000", "100");
        HoldingResponse microsoft = eligibleHolding("MSFT", "1000", "100");

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft));

        /*
         AAPL has 4 price points, therefore 3 daily returns.
         MSFT has 3 price points, therefore 2 daily returns.
         The service should align both return lists to length 2.
         */
        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121", "133.10"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "210", "220.50"));

        when(algorithm.getAlgorithmType())
                .thenReturn(PortfolioAlgorithm.MEAN_VARIANCE);

        when(algorithm.optimise(any(PortfolioOptimisationRequest.class)))
                .thenReturn(resultWithAssets(
                        assetResult("AAPL", "0.50"),
                        assetResult("MSFT", "0.50")
                ));

        optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        ArgumentCaptor<PortfolioOptimisationRequest> requestCaptor =
                ArgumentCaptor.forClass(PortfolioOptimisationRequest.class);

        org.mockito.Mockito.verify(algorithm).optimise(requestCaptor.capture());

        PortfolioOptimisationRequest request = requestCaptor.getValue();

        assertThat(request.getDailyReturns().get("AAPL")).hasSize(2);
        assertThat(request.getDailyReturns().get("MSFT")).hasSize(2);

        // AAPL returns are 10%, 10%, 10%; after alignment, the last two remain.
        assertThat(request.getDailyReturns().get("AAPL").get(0)).isEqualByComparingTo("0.1");
        assertThat(request.getDailyReturns().get("AAPL").get(1)).isEqualByComparingTo("0.1");

        // MSFT returns are 5%, 5%.
        assertThat(request.getDailyReturns().get("MSFT").get(0)).isEqualByComparingTo("0.05");
        assertThat(request.getDailyReturns().get("MSFT").get(1)).isEqualByComparingTo("0.05");
    }

    //Suggested change processing
    //BUY and REDUCE actions calculated
    @Test
    void suggestedChangeLoop_shouldAssignBuyAndReduceActions() {
        HoldingResponse apple = eligibleHolding("AAPL", "1000", "100");
        HoldingResponse microsoft = eligibleHolding("MSFT", "1000", "100");

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "220", "242"));

        when(algorithm.getAlgorithmType())
                .thenReturn(PortfolioAlgorithm.MEAN_VARIANCE);

    /*
     Total portfolio value = 2000.
     AAPL target 75% = 1500, current 1000, BUY.
     MSFT target 25% = 500, current 1000, REDUCE.
     */
        when(algorithm.optimise(any(PortfolioOptimisationRequest.class)))
                .thenReturn(resultWithAssets(
                        assetResult("AAPL", "0.75"),
                        assetResult("MSFT", "0.25")
                ));

        PortfolioOptimisationResult result = optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        PortfolioOptimisationAssetResult appleResult = findAsset(result, "AAPL");
        PortfolioOptimisationAssetResult microsoftResult = findAsset(result, "MSFT");

        assertThat(appleResult.getSuggestedAction()).isEqualTo("BUY");
        assertThat(appleResult.getCurrentValue()).isEqualByComparingTo("1000");
        assertThat(appleResult.getTargetValue()).isEqualByComparingTo("1500.00");
        assertThat(appleResult.getValueDifference()).isEqualByComparingTo("500.00");
        assertThat(appleResult.getSuggestedUnitChange()).isEqualByComparingTo("5.00");

        assertThat(microsoftResult.getSuggestedAction()).isEqualTo("REDUCE");
        assertThat(microsoftResult.getCurrentValue()).isEqualByComparingTo("1000");
        assertThat(microsoftResult.getTargetValue()).isEqualByComparingTo("500.00");
        assertThat(microsoftResult.getValueDifference()).isEqualByComparingTo("-500.00");
        assertThat(microsoftResult.getSuggestedUnitChange()).isEqualByComparingTo("-5.00");
    }

    private HoldingResponse eligibleHolding(String symbol, String marketValue, String latestPrice) {
        HoldingResponse holding = new HoldingResponse();
        holding.setSymbol(symbol);
        holding.setDisplayName(symbol);
        holding.setAssetClass(AssetClass.EQUITY);
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