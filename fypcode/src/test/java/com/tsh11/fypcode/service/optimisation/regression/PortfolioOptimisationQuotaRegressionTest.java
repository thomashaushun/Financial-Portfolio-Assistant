package com.tsh11.fypcode.service.optimisation.regression;

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

// Prevents optimisation from excluding valid assets just because live quote prices are missing
@ExtendWith(MockitoExtension.class)
class PortfolioOptimisationQuotaRegressionTest {

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
        to = LocalDate.of(2026, 1, 31);
    }

    @Test
    void optimisation_shouldUseTotalCostFallbackWhenLatestPriceAndMarketValueAreMissing() {
        HoldingResponse spy = supportedHoldingWithMissingLivePrice(
                "SPY",
                "SPDR S&P 500 ETF Trust",
                "2000"
        );

        HoldingResponse voo = supportedHoldingWithMissingLivePrice(
                "VOO",
                "Vanguard 500 Index Fund ETF Shares",
                "3000"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(spy, voo));

        when(marketDataProvider.getHistoricalPrices("SPY", from, to))
                .thenReturn(prices("400", "420", "441"));

        when(marketDataProvider.getHistoricalPrices("VOO", from, to))
                .thenReturn(prices("500", "525", "551.25"));

        when(algorithm.getAlgorithmType())
                .thenReturn(PortfolioAlgorithm.MEAN_VARIANCE);

        when(algorithm.optimise(any(PortfolioOptimisationRequest.class)))
                .thenReturn(resultWithAssets(
                        assetResult("SPY", "0.50"),
                        assetResult("VOO", "0.50")
                ));

        PortfolioOptimisationResult result = optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        assertThat(result).isNotNull();
        assertThat(result.getAssets()).hasSize(2);
        assertThat(result.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("SPY", "VOO");

        ArgumentCaptor<PortfolioOptimisationRequest> requestCaptor =
                ArgumentCaptor.forClass(PortfolioOptimisationRequest.class);

        org.mockito.Mockito.verify(algorithm).optimise(requestCaptor.capture());

        PortfolioOptimisationRequest request = requestCaptor.getValue();

        /*
         Expected current weights use totalCost fallback:
         SPY = 2000 / 5000 = 0.4
         VOO = 3000 / 5000 = 0.6
         */
        assertThat(request.getCurrentWeights().get("SPY")).isEqualByComparingTo("0.4");
        assertThat(request.getCurrentWeights().get("VOO")).isEqualByComparingTo("0.6");
    }

    @Test
    void optimisation_shouldNotThrowNoEligibleAssetsWhenSupportedAssetsHavePositiveTotalCost() {
        HoldingResponse qqq = supportedHoldingWithMissingLivePrice(
                "QQQ",
                "Invesco QQQ Trust Series 1",
                "400005"
        );

        HoldingResponse spy = supportedHoldingWithMissingLivePrice(
                "SPY",
                "SPDR S&P 500 ETF Trust",
                "2000"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(qqq, spy));

        when(marketDataProvider.getHistoricalPrices("QQQ", from, to))
                .thenReturn(prices("300", "330", "363"));

        when(marketDataProvider.getHistoricalPrices("SPY", from, to))
                .thenReturn(prices("400", "420", "441"));

        when(algorithm.getAlgorithmType())
                .thenReturn(PortfolioAlgorithm.MEAN_VARIANCE);

        when(algorithm.optimise(any(PortfolioOptimisationRequest.class)))
                .thenReturn(resultWithAssets(
                        assetResult("QQQ", "0.50"),
                        assetResult("SPY", "0.50")
                ));

        PortfolioOptimisationResult result = optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );

        assertThat(result).isNotNull();
        assertThat(result.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("QQQ", "SPY");
    }

    private HoldingResponse supportedHoldingWithMissingLivePrice(
            String symbol,
            String displayName,
            String totalCost
    ) {
        HoldingResponse holding = new HoldingResponse();
        holding.setSymbol(symbol);
        holding.setDisplayName(displayName);
        holding.setAssetClass(AssetClass.ETF);
        holding.setCurrency("USD");
        holding.setManualValuationRequired(false);
        holding.setPriceDataSupported(true);
        holding.setHistoricalPriceSupported(true);
        holding.setOptimisationSupported(true);
        holding.setLatestPrice(BigDecimal.ZERO);
        holding.setMarketValue(BigDecimal.ZERO);
        holding.setTotalCost(bd(totalCost));
        holding.setTotalQuantity(bd("10"));
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