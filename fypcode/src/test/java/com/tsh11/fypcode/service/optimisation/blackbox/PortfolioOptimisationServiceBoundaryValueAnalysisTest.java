package com.tsh11.fypcode.service.optimisation.blackbox;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.dto.PortfolioAlgorithm;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioOptimisationServiceBoundaryValueAnalysisTest {

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

    //Null user ID
    //Exception thrown
    @Test
    void nullUserId_shouldThrowException() {
        assertThatThrownBy(() -> optimisationService.optimise(
                null,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("User ID must not be null.");
    }

    //Null from date
    //Exception thrown
    @Test
    void nullFromDate_shouldThrowException() {
        assertThatThrownBy(() -> optimisationService.optimise(
                userId,
                null,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("From date must not be null.");
    }

    //Null to date
    //Exception thrown
    @Test
    void nullToDate_shouldThrowException() {
        assertThatThrownBy(() -> optimisationService.optimise(
                userId,
                from,
                null,
                PortfolioAlgorithm.MEAN_VARIANCE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("To date must not be null.");
    }

    //From date after to date
    //Exception thrown
    @Test
    void fromDateAfterToDate_shouldThrowException() {
        assertThatThrownBy(() -> optimisationService.optimise(
                userId,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1),
                PortfolioAlgorithm.MEAN_VARIANCE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("From date must be on or before to date.");
    }

    //Null algorithm
    //Exception thrown
    @Test
    void nullAlgorithm_shouldThrowException() {
        assertThatThrownBy(() -> optimisationService.optimise(
                userId,
                from,
                to,
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Portfolio algorithm must not be null.");
    }

    //Empty holdings list
    //Exception thrown
    @Test
    void emptyHoldingsList_shouldThrowNoEligibleAssetsException() {
        when(holdingService.getHoldings(userId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No eligible assets available for portfolio optimisation.");
    }

    //Only one asset with historical data
    //Exception thrown
    @Test
    void onlyOneAssetWithHistoricalData_shouldThrowMinimumAssetsException() {
        HoldingResponse apple = eligibleHolding("AAPL", "1000", "100");

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121"));

        assertThatThrownBy(() -> optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least two eligible assets with historical data are required.");
    }

    //Only one asset has enough historical data
    //Exception thrown
    @Test
    void twoEligibleAssetsButOnlyOneHasEnoughPricePoints_shouldThrowMinimumAssetsException() {
        HoldingResponse apple = eligibleHolding("AAPL", "1000", "100");
        HoldingResponse microsoft = eligibleHolding("MSFT", "1000", "100");

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(prices("100", "110", "121"));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(prices("200"));

        assertThatThrownBy(() -> optimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least two eligible assets with historical data are required.");
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

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}