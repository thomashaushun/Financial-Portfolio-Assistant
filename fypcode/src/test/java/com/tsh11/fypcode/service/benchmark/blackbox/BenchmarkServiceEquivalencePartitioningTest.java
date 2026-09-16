package com.tsh11.fypcode.service.benchmark.blackbox;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.dto.response.BenchmarkComparisonResponse;
import com.tsh11.fypcode.integration.marketdata.HistoricalPricePoint;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.repository.ActivityRepository;
import com.tsh11.fypcode.service.BenchmarkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceEquivalencePartitioningTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    private BenchmarkService benchmarkService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        benchmarkService = new BenchmarkService(activityRepository, marketDataProvider);
        userId = UUID.randomUUID();
    }

    // Confirms the benchmark comparison works correctly for a standard valid portfolio.
    /*
    normal benchmark:
    one asset
    valid buy activity
    valid asset historical prices
    valid benchmark historical prices
     */
    /*
    Expected calculation:
    AAPL quantity = 10

    AAPL price:
    2026-01-01 = 100
    2026-01-03 = 120

    Portfolio start = 10 × 100 = 1000
    Portfolio end = 10 × 120 = 1200
    Portfolio return = 20%

    Benchmark:
    Start = 1000
    End = 1100
    Benchmark return = 10%

    Excess return = 20% - 10% = 10%
     */
    /*
    Check indexing:
    Portfolio indexed start = 100
    Portfolio indexed end = 120
    Benchmark indexed end = 110
     */
    @Test
    void validPortfolioAndBenchmark_shouldCalculatePortfolioBenchmarkAndExcessReturns() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 3);

        Activity buy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "2025-12-31T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(List.of(
                        price("2026-01-01", "100"),
                        price("2026-01-02", "110"),
                        price("2026-01-03", "120")
                ));

        when(marketDataProvider.getHistoricalIndexPrices("SPX", from, to))
                .thenReturn(List.of(
                        price("2026-01-01", "1000"),
                        price("2026-01-02", "1050"),
                        price("2026-01-03", "1100")
                ));

        BenchmarkComparisonResponse result = benchmarkService.compareAgainstBenchmark(
                userId,
                "Spx",
                from,
                to
        );

        assertThat(result.getBenchmarkSymbol()).isEqualTo("SPX");
        assertThat(result.getFromDate()).isEqualTo(from);
        assertThat(result.getToDate()).isEqualTo(to);

        assertThat(result.getStartingPortfolioValue()).isEqualByComparingTo("1000");
        assertThat(result.getEndingPortfolioValue()).isEqualByComparingTo("1200");
        assertThat(result.getPortfolioReturnPercent()).isEqualByComparingTo("20.000000");

        assertThat(result.getStartingBenchmarkValue()).isEqualByComparingTo("1000");
        assertThat(result.getEndingBenchmarkValue()).isEqualByComparingTo("1100");
        assertThat(result.getBenchmarkReturnPercent()).isEqualByComparingTo("10.000000");

        assertThat(result.getExcessReturnPercent()).isEqualByComparingTo("10.000000");

        assertThat(result.getPoints()).hasSize(3);
        assertThat(result.getPoints().get(0).getPortfolioIndexed()).isEqualByComparingTo("100.000000");
        assertThat(result.getPoints().get(2).getPortfolioIndexed()).isEqualByComparingTo("120.000000");
        assertThat(result.getPoints().get(2).getBenchmarkIndexed()).isEqualByComparingTo("110.000000");
    }

    // This test checks that the benchmark service correctly handles a portfolio with more than one asset.
    /*
    Expected Calculation:
    Start:
    AAPL = 10 × 100 = 1000
    MSFT = 5 × 200 = 1000
    Total portfolio value = 2000

    End:
    AAPL = 10 × 120 = 1200
    MSFT = 5 × 220 = 1100
    Total portfolio value = 2300

    Portfolio return = (2300 - 2000) / 2000 × 100
                     = 15%
     */
    @Test
    void multipleAssets_shouldCalculateCombinedPortfolioValuePerDate() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 2);

        Activity appleBuy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "2025-12-31T10:00:00Z"
        );

        Activity microsoftBuy = activity(
                ActivityType.BUY,
                "MSFT",
                "5",
                "200",
                "2025-12-31T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(microsoftBuy, appleBuy));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(List.of(
                        price("2026-01-01", "100"),
                        price("2026-01-02", "120")
                ));

        when(marketDataProvider.getHistoricalPrices("MSFT", from, to))
                .thenReturn(List.of(
                        price("2026-01-01", "200"),
                        price("2026-01-02", "220")
                ));

        when(marketDataProvider.getHistoricalIndexPrices("SPX", from, to))
                .thenReturn(List.of(
                        price("2026-01-01", "1000"),
                        price("2026-01-02", "1100")
                ));

        BenchmarkComparisonResponse result = benchmarkService.compareAgainstBenchmark(
                userId,
                "SPX",
                from,
                to
        );

        /*
         Start:
         AAPL = 10 * 100 = 1000
         MSFT = 5 * 200 = 1000
         Total = 2000

         End:
         AAPL = 10 * 120 = 1200
         MSFT = 5 * 220 = 1100
         Total = 2300

         Portfolio return = 15%
         */
        assertThat(result.getStartingPortfolioValue()).isEqualByComparingTo("2000");
        assertThat(result.getEndingPortfolioValue()).isEqualByComparingTo("2300");
        assertThat(result.getPortfolioReturnPercent()).isEqualByComparingTo("15.000000");
    }

    private Activity activity(
            ActivityType type,
            String symbol,
            String quantity,
            String unitPrice,
            String date
    ) {
        Activity activity = new Activity();
        activity.setUserId(userId);
        activity.setType(type);
        activity.setSymbol(symbol);
        activity.setQuantity(bd(quantity));
        activity.setUnitPrice(bd(unitPrice));
        activity.setFee(BigDecimal.ZERO);
        activity.setCurrency("USD");
        activity.setDate(Instant.parse(date));
        return activity;
    }

    private HistoricalPricePoint price(String date, String close) {
        return new HistoricalPricePoint(LocalDate.parse(date), bd(close));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}