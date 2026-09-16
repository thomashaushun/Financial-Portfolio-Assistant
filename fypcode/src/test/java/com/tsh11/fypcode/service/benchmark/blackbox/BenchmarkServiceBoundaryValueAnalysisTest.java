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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BenchmarkServiceBoundaryValueAnalysisTest {

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

    // Ensures the service rejects a missing start date.
    /*
    Input:
    from = null
    to = valid date
     */
    /*
    Expected Output:
    IllegalArgumentException: Invalid date range
     */
    @Test
    void nullFromDate_shouldThrowException() {
        assertThatThrownBy(() -> benchmarkService.compareAgainstBenchmark(
                userId,
                "SPX",
                null,
                LocalDate.of(2026, 1, 31)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid date range");
    }

    // Ensures the service rejects a missing end date.
    /*
    Input:
    from = valid date
    to = null
     */
    /*
    Expected Output:
    IllegalArgumentException: Invalid date range
     */
    @Test
    void nullToDate_shouldThrowException() {
        assertThatThrownBy(() -> benchmarkService.compareAgainstBenchmark(
                userId,
                "SPX",
                LocalDate.of(2026, 1, 1),
                null
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid date range");
    }

    // Ensures the service rejects an invalid reversed date range.
    /*
    Input:
    from = 2026-02-01
    to = 2026-01-01
     */
    /*
    Expected Output:
    IllegalArgumentException: Invalid date range
     */
    @Test
    void fromDateAfterToDate_shouldThrowException() {
        assertThatThrownBy(() -> benchmarkService.compareAgainstBenchmark(
                userId,
                "SPX",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid date range");
    }

    // Ensures an empty portfolio does not crash benchmark comparison and returns a safe empty response.
    /*
    Input:
    nothing
     */
    /*
    Expected Output:
    startingPortfolioValue = 0
    endingPortfolioValue = 0
    portfolioReturnPercent = 0
    points = empty list
     */
    @Test
    void emptyActivities_shouldReturnEmptyBenchmarkComparison() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of());

        BenchmarkComparisonResponse result = benchmarkService.compareAgainstBenchmark(
                userId,
                "SPX",
                from,
                to
        );

        assertThat(result.getBenchmarkSymbol()).isEqualTo("SPX");
        assertThat(result.getFromDate()).isEqualTo(from);
        assertThat(result.getToDate()).isEqualTo(to);
        assertThat(result.getStartingPortfolioValue()).isEqualByComparingTo("0");
        assertThat(result.getEndingPortfolioValue()).isEqualByComparingTo("0");
        assertThat(result.getPortfolioReturnPercent()).isEqualByComparingTo("0");
        assertThat(result.getPoints()).isEmpty();
    }

    // Ensures the service handles unmatched data ranges safely instead of producing misleading comparison results.
    /*
    Input:
    AAPL price dates:
    2026-01-01
    2026-01-02

    Benchmark dates:
    2026-01-04
    2026-01-05
     */
    /*
    Expected Output:
    startingPortfolioValue = 0
    endingPortfolioValue = 0
    portfolioReturnPercent = 0
    points = empty list
     */
    @Test
    void noCommonDatesBetweenPortfolioAndBenchmark_shouldReturnEmptyComparison() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 5);

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
                        price("2026-01-02", "110")
                ));

        when(marketDataProvider.getHistoricalIndexPrices("SPX", from, to))
                .thenReturn(List.of(
                        price("2026-01-04", "1000"),
                        price("2026-01-05", "1100")
                ));

        BenchmarkComparisonResponse result = benchmarkService.compareAgainstBenchmark(
                userId,
                "SPX",
                from,
                to
        );

        assertThat(result.getStartingPortfolioValue()).isEqualByComparingTo("0");
        assertThat(result.getEndingPortfolioValue()).isEqualByComparingTo("0");
        assertThat(result.getPortfolioReturnPercent()).isEqualByComparingTo("0");
        assertThat(result.getPoints()).isEmpty();
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