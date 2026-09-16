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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioOptimisationServiceBranchTest {

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

    //Ineligible holdings
    //Excluded with warnings
    @Test
    void ineligibleHoldings_shouldBeExcludedAndAddedToWarnings() {
        HoldingResponse apple = eligibleHolding("AAPL", "1000", "100");
        HoldingResponse microsoft = eligibleHolding("MSFT", "1000", "100");

        HoldingResponse manualAsset = eligibleHolding("HOUSE1", "250000", "0");
        manualAsset.setManualValuationRequired(true);

        HoldingResponse priceUnsupportedAsset = eligibleHolding("PRIVATE1", "1000", "0");
        priceUnsupportedAsset.setPriceDataSupported(false);

        HoldingResponse optimisationUnsupportedAsset = eligibleHolding("CASH1", "500", "1");
        optimisationUnsupportedAsset.setOptimisationSupported(false);

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(
                        apple,
                        microsoft,
                        manualAsset,
                        priceUnsupportedAsset,
                        optimisationUnsupportedAsset
                ));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "220", "242"));

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

        assertThat(result.getWarnings())
                .contains(
                        "HOUSE1 was excluded because it is not eligible for optimisation.",
                        "PRIVATE1 was excluded because it is not eligible for optimisation.",
                        "CASH1 was excluded because it is not eligible for optimisation."
                );
    }

    //Insufficient historical data
    //Asset excluded with warning
    @Test
    void eligibleAssetWithInsufficientHistoricalData_shouldBeExcludedWithHistoricalDataWarning() {
        HoldingResponse apple = eligibleHolding("AAPL", "1000", "100");
        HoldingResponse microsoft = eligibleHolding("MSFT", "1000", "100");
        HoldingResponse tesla = eligibleHolding("TSLA", "1000", "100");

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft, tesla));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "220", "242"));

        // Only one price point means no daily returns can be calculated.
        when(marketDataProvider.getHistoricalPrices("TSLA", from, to))
                .thenReturn(prices("300"));

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

        assertThat(result.getWarnings())
                .contains("TSLA was excluded because sufficient historical data was not available.");
    }

    //Historical API failure
    //Asset marked unsupported and excluded
    @Test
    void historicalPriceFetchFailure_shouldMarkHistoricalUnsupportedAndContinueWithRemainingAssets() {
        HoldingResponse apple = eligibleHolding("AAPL", "1000", "100");
        HoldingResponse microsoft = eligibleHolding("MSFT", "1000", "100");
        HoldingResponse tesla = eligibleHolding("TSLA", "1000", "100");

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft, tesla));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "220", "242"));

        when(marketDataProvider.getHistoricalPrices("TSLA", from, to))
                .thenThrow(new RuntimeException("API failure"));

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

        verify(assetProfileService).markHistoricalUnsupported(userId, "TSLA");

        assertThat(result.getWarnings())
                .contains("TSLA was excluded because sufficient historical data was not available.");
    }

    //Unsupported algorithm
    //Exception thrown
    @Test
    void unsupportedAlgorithm_shouldThrowException() {
        HoldingResponse apple = eligibleHolding("AAPL", "1000", "100");
        HoldingResponse microsoft = eligibleHolding("MSFT", "1000", "100");

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200", "220", "242"));

        when(algorithm.getAlgorithmType())
                .thenReturn(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> optimisationService.optimise(
                        userId,
                        from,
                        to,
                        PortfolioAlgorithm.MEAN_VARIANCE
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported algorithm: MEAN_VARIANCE");
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

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}