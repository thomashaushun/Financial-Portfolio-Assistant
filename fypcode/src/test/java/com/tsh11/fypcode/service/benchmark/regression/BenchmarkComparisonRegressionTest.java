package com.tsh11.fypcode.service.benchmark.regression;

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
class BenchmarkComparisonRegressionTest {

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

    // Creates a date range starting before the user first buys the asset.
    // The asset is bought on 2026-01-03, while the selected range starts on 2026-01-01.
    // The test expects the portfolio comparison to start from the first date where the portfolio actually has value.
    @Test
    void benchmarkComparison_whenSelectedFromDateIsBeforeFirstBuy_shouldNotReportZeroPortfolioReturn() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 5);

        Activity buy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "2026-01-03T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(marketDataProvider.getHistoricalPrices("AAPL", from, to))
                .thenReturn(List.of(
                        price("2026-01-01", "90"),
                        price("2026-01-02", "95"),
                        price("2026-01-03", "100"),
                        price("2026-01-04", "110"),
                        price("2026-01-05", "120")
                ));

        when(marketDataProvider.getHistoricalIndexPrices("SPX", from, to))
                .thenReturn(List.of(
                        price("2026-01-01", "1000"),
                        price("2026-01-02", "1010"),
                        price("2026-01-03", "1020"),
                        price("2026-01-04", "1030"),
                        price("2026-01-05", "1040")
                ));

        BenchmarkComparisonResponse result = benchmarkService.compareAgainstBenchmark(
                userId,
                "SPX",
                from,
                to
        );

        /*
         The selected from date is 2026-01-01, but the user only buys AAPL on 2026-01-03.
         The benchmark comparison should start the portfolio return calculation from the first
         date where the portfolio actually has positive value.

         Expected:
         Start portfolio value = 10 * 100 = 1000 on 2026-01-03
         End portfolio value = 10 * 120 = 1200 on 2026-01-05
         Portfolio return = (1200 - 1000) / 1000 * 100 = 20%
         */
        assertThat(result.getStartingPortfolioValue()).isEqualByComparingTo("1000");
        assertThat(result.getEndingPortfolioValue()).isEqualByComparingTo("1200");
        assertThat(result.getPortfolioReturnPercent()).isEqualByComparingTo("20.000000");
        assertThat(result.getFromDate()).isEqualTo(LocalDate.of(2026, 1, 3));

        assertThat(result.getPoints())
                .allSatisfy(point -> assertThat(point.getPortfolioValue())
                        .isGreaterThan(BigDecimal.ZERO));
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