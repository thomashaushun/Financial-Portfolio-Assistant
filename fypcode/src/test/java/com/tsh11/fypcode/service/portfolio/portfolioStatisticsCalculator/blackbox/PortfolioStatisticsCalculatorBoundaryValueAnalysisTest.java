package com.tsh11.fypcode.service.portfolio.portfolioStatisticsCalculator.blackbox;

import com.tsh11.fypcode.service.portfolio.PortfolioStatisticsCalculator;
import org.apache.commons.math3.linear.RealMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioStatisticsCalculatorBoundaryValueAnalysisTest {

    private PortfolioStatisticsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PortfolioStatisticsCalculator();
    }

    // Tests annualised return when one asset has a weight but no expected daily return entry
    // Missing return is treated as 0, so only the available return contributes
    @Test
    void calculateAnnualisedReturn_shouldUseZeroWhenSymbolHasNoExpectedDailyReturn() {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("AAPL", bd("0.50"));
        weights.put("MSFT", bd("0.50"));

        Map<String, BigDecimal> expectedDailyReturns = new LinkedHashMap<>();
        expectedDailyReturns.put("AAPL", bd("0.002"));

        BigDecimal result = calculator.calculateAnnualisedReturn(weights, expectedDailyReturns);

        /*
         Boundary case:
         MSFT has no expected daily return entry, so the method should treat it as 0.

         weighted daily return = (0.50 * 0.002) + (0.50 * 0)
                              = 0.001

         annualised return = 0.001 * 252
                           = 0.252
         */
        assertThat(result).isEqualByComparingTo("0.252000");
    }

    // Tests Sharpe ratio when volatility is exactly zero
    // Returns 0 to avoid division by zero
    @Test
    void calculateSharpeRatio_withZeroVolatility_shouldReturnZero() {
        BigDecimal result = calculator.calculateSharpeRatio(
                bd("0.12"),
                BigDecimal.ZERO,
                bd("0.02")
        );

        assertThat(result).isEqualByComparingTo("0");
    }

    // Tests Sharpe ratio when volatility is missing
    // Returns 0 safely
    @Test
    void calculateSharpeRatio_withNullVolatility_shouldReturnZero() {
        BigDecimal result = calculator.calculateSharpeRatio(
                bd("0.12"),
                null,
                bd("0.02")
        );

        assertThat(result).isEqualByComparingTo("0");
    }

    // Tests Sharpe ratio when risk-free rate is missing
    // Treats risk-free rate as 0
    @Test
    void calculateSharpeRatio_withNullRiskFreeRate_shouldTreatRiskFreeRateAsZero() {
        BigDecimal annualReturn = bd("0.12");
        BigDecimal annualVolatility = bd("0.20");

        BigDecimal result = calculator.calculateSharpeRatio(
                annualReturn,
                annualVolatility,
                null
        );

        /*
         Boundary case:
         riskFreeRate is null, so it should be treated as 0.

         Sharpe ratio = (0.12 - 0) / 0.20
                      = 0.6
         */
        assertThat(result).isEqualByComparingTo("0.6");
    }

    // Tests expected return when one asset has an empty return list
    // Average return for that asset is 0
    @Test
    void calculateExpectedDailyReturns_withEmptyReturnList_shouldReturnZeroAverage() {
        List<String> symbols = List.of("AAPL", "MSFT");

        Map<String, List<BigDecimal>> dailyReturns = new LinkedHashMap<>();
        dailyReturns.put("AAPL", List.of(bd("0.01"), bd("0.03")));
        dailyReturns.put("MSFT", List.of());

        Map<String, BigDecimal> result = calculator.calculateExpectedDailyReturns(
                symbols,
                dailyReturns
        );

        assertThat(result.get("AAPL")).isEqualByComparingTo("0.02");
        assertThat(result.get("MSFT")).isEqualByComparingTo("0");
    }

    // Tests expected return when an asset is listed but has no return data in the map
    // Average return for that asset is 0
    @Test
    void calculateExpectedDailyReturns_withMissingSymbolReturnList_shouldReturnZeroAverage() {
        List<String> symbols = List.of("AAPL", "MSFT");

        Map<String, List<BigDecimal>> dailyReturns = new LinkedHashMap<>();
        dailyReturns.put("AAPL", List.of(bd("0.01"), bd("0.03")));

        Map<String, BigDecimal> result = calculator.calculateExpectedDailyReturns(
                symbols,
                dailyReturns
        );

        assertThat(result.get("AAPL")).isEqualByComparingTo("0.02");
        assertThat(result.get("MSFT")).isEqualByComparingTo("0");
    }

    // Tests covariance calculation when two assets have return lists of different lengths
    // Covariance between those two assets is 0
    @Test
    void calculateCovarianceMatrix_withDifferentLengthReturnLists_shouldReturnZeroCovariance() {
        List<String> symbols = List.of("AAPL", "MSFT");

        Map<String, List<BigDecimal>> dailyReturns = new LinkedHashMap<>();
        dailyReturns.put("AAPL", List.of(bd("0.01"), bd("0.02"), bd("0.03")));
        dailyReturns.put("MSFT", List.of(bd("0.02"), bd("0.04")));

        RealMatrix result = calculator.calculateCovarianceMatrix(symbols, dailyReturns);

        assertThat(result.getEntry(0, 1)).isEqualTo(0.0);
        assertThat(result.getEntry(1, 0)).isEqualTo(0.0);
    }

    // Tests volatility when an asset exists in the covariance matrix but has no portfolio weight
    // Missing weight is treated as 0
    @Test
    void calculateAnnualisedVolatility_withMissingWeight_shouldTreatMissingWeightAsZero() {
        List<String> symbols = List.of("AAPL", "MSFT");

        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("AAPL", bd("1.00"));

        Map<String, List<BigDecimal>> dailyReturns = new LinkedHashMap<>();
        dailyReturns.put("AAPL", List.of(bd("0.01"), bd("0.02"), bd("0.03")));
        dailyReturns.put("MSFT", List.of(bd("0.02"), bd("0.04"), bd("0.06")));

        RealMatrix covarianceMatrix = calculator.calculateCovarianceMatrix(symbols, dailyReturns);

        BigDecimal result = calculator.calculateAnnualisedVolatility(
                weights,
                covarianceMatrix,
                symbols
        );

        /*
         Boundary case:
         MSFT weight is missing, so it is treated as 0.

         Portfolio uses only AAPL.

         Daily variance = 0.0001
         Annualised volatility = sqrt(0.0001) * sqrt(252)
                               = 0.01 * sqrt(252)
                               ≈ 0.15874507866387544
         */
        assertThat(result.doubleValue())
                .isCloseTo(0.15874507866387544, withinDouble(0.000000001));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private org.assertj.core.data.Offset<Double> withinDouble(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}