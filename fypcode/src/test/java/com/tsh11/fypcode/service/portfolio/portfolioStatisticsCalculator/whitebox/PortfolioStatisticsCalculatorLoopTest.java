package com.tsh11.fypcode.service.portfolio.portfolioStatisticsCalculator.whitebox;

import com.tsh11.fypcode.service.portfolio.PortfolioStatisticsCalculator;
import org.apache.commons.math3.linear.RealMatrix;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioStatisticsCalculatorLoopTest {

    private PortfolioStatisticsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PortfolioStatisticsCalculator();
    }

    // Tests loop over multiple assets when calculating average daily returns
    //Returns correct average for AAPL, MSFT, and VOO
    @Test
    void calculateExpectedDailyReturns_shouldCalculateAverageReturnForEachSymbol() {
        List<String> symbols = List.of("AAPL", "MSFT", "VOO");

        Map<String, List<BigDecimal>> dailyReturns = new LinkedHashMap<>();
        dailyReturns.put("AAPL", List.of(bd("0.01"), bd("0.03"), bd("0.02")));
        dailyReturns.put("MSFT", List.of(bd("0.02"), bd("0.04"), bd("0.06")));
        dailyReturns.put("VOO", List.of(bd("0.005"), bd("0.010"), bd("0.015")));

        Map<String, BigDecimal> result = calculator.calculateExpectedDailyReturns(
                symbols,
                dailyReturns
        );

        /*
         This test verifies the loop over multiple symbols.

         AAPL average = (0.01 + 0.03 + 0.02) / 3 = 0.02
         MSFT average = (0.02 + 0.04 + 0.06) / 3 = 0.04
         VOO average = (0.005 + 0.010 + 0.015) / 3 = 0.010
         */
        assertThat(result).containsKeys("AAPL", "MSFT", "VOO");
        assertThat(result.get("AAPL")).isEqualByComparingTo("0.02");
        assertThat(result.get("MSFT")).isEqualByComparingTo("0.04");
        assertThat(result.get("VOO")).isEqualByComparingTo("0.010");
    }

    // Tests loop over all portfolio weights
    //All assets contribute to the final annualised return
    @Test
    void calculateAnnualisedReturn_shouldIterateOverAllWeights() {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("AAPL", bd("0.50"));
        weights.put("MSFT", bd("0.30"));
        weights.put("VOO", bd("0.20"));

        Map<String, BigDecimal> expectedDailyReturns = new LinkedHashMap<>();
        expectedDailyReturns.put("AAPL", bd("0.001"));
        expectedDailyReturns.put("MSFT", bd("0.002"));
        expectedDailyReturns.put("VOO", bd("0.003"));

        BigDecimal result = calculator.calculateAnnualisedReturn(weights, expectedDailyReturns);

        /*
         This test verifies that all map entries are processed.

         weighted daily return =
             (0.50 * 0.001)
           + (0.30 * 0.002)
           + (0.20 * 0.003)

         = 0.0005 + 0.0006 + 0.0006
         = 0.0017

         annualised return = 0.0017 * 252 = 0.4284
         */
        assertThat(result).isEqualByComparingTo("0.428400");
    }

    // Tests nested loop over all asset pairs in the covariance matrix
    //Produces a full 3×3 covariance matrix with correct values
    @Test
    void calculateCovarianceMatrix_shouldCalculateAllMatrixCellsForMultipleAssets() {
        List<String> symbols = List.of("AAPL", "MSFT", "VOO");

        Map<String, List<BigDecimal>> dailyReturns = new LinkedHashMap<>();
        dailyReturns.put("AAPL", List.of(bd("0.01"), bd("0.02"), bd("0.03")));
        dailyReturns.put("MSFT", List.of(bd("0.02"), bd("0.04"), bd("0.06")));
        dailyReturns.put("VOO", List.of(bd("0.03"), bd("0.06"), bd("0.09")));

        RealMatrix result = calculator.calculateCovarianceMatrix(symbols, dailyReturns);

        /*
         This test verifies the nested loop over all symbol pairs.

         Since MSFT returns are 2x AAPL and VOO returns are 3x AAPL:

         Var(AAPL) = 0.0001
         Cov(AAPL, MSFT) = 0.0002
         Cov(AAPL, VOO) = 0.0003
         Var(MSFT) = 0.0004
         Cov(MSFT, VOO) = 0.0006
         Var(VOO) = 0.0009
         */
        assertThat(result.getRowDimension()).isEqualTo(3);
        assertThat(result.getColumnDimension()).isEqualTo(3);

        assertThat(result.getEntry(0, 0)).isCloseTo(0.0001, withinDouble(0.000000001));
        assertThat(result.getEntry(0, 1)).isCloseTo(0.0002, withinDouble(0.000000001));
        assertThat(result.getEntry(0, 2)).isCloseTo(0.0003, withinDouble(0.000000001));

        assertThat(result.getEntry(1, 0)).isCloseTo(0.0002, withinDouble(0.000000001));
        assertThat(result.getEntry(1, 1)).isCloseTo(0.0004, withinDouble(0.000000001));
        assertThat(result.getEntry(1, 2)).isCloseTo(0.0006, withinDouble(0.000000001));

        assertThat(result.getEntry(2, 0)).isCloseTo(0.0003, withinDouble(0.000000001));
        assertThat(result.getEntry(2, 1)).isCloseTo(0.0006, withinDouble(0.000000001));
        assertThat(result.getEntry(2, 2)).isCloseTo(0.0009, withinDouble(0.000000001));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private org.assertj.core.data.Offset<Double> withinDouble(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}