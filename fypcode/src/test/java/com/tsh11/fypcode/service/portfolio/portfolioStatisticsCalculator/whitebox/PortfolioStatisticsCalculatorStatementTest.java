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

class PortfolioStatisticsCalculatorStatementTest {

    private PortfolioStatisticsCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new PortfolioStatisticsCalculator();
    }

    // Tests the weighted annual return formula
    // Weighted daily return is multiplied by 252 trading days
    @Test
    void calculateAnnualisedReturn_shouldCalculateWeightedAnnualReturn() {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("AAPL", bd("0.60"));
        weights.put("MSFT", bd("0.40"));

        Map<String, BigDecimal> expectedDailyReturns = new LinkedHashMap<>();
        expectedDailyReturns.put("AAPL", bd("0.001"));
        expectedDailyReturns.put("MSFT", bd("0.002"));

        BigDecimal result = calculator.calculateAnnualisedReturn(weights, expectedDailyReturns);

        /*
         Manual calculation:
         weighted daily return = (0.60 * 0.001) + (0.40 * 0.002)
                              = 0.0006 + 0.0008
                              = 0.0014

         annualised return = 0.0014 * 252
                           = 0.3528
         */
        assertThat(result).isEqualByComparingTo("0.352800");
    }

    // Tests the Sharpe ratio formula
    //(annual return - risk-free rate) / volatility
    @Test
    void calculateSharpeRatio_shouldCalculateExcessReturnDividedByVolatility() {
        BigDecimal annualReturn = bd("0.12");
        BigDecimal annualVolatility = bd("0.20");
        BigDecimal riskFreeRate = bd("0.02");

        BigDecimal result = calculator.calculateSharpeRatio(
                annualReturn,
                annualVolatility,
                riskFreeRate
        );

        /*
         Manual calculation:
         Sharpe ratio = (0.12 - 0.02) / 0.20
                      = 0.10 / 0.20
                      = 0.5
         */
        assertThat(result).isEqualByComparingTo("0.5");
    }

    // Tests covariance and variance values for two assets
    //Matrix contains correct variance and covariance values
    @Test
    void calculateCovarianceMatrix_shouldCalculateCovarianceBetweenAssets() {
        List<String> symbols = List.of("AAPL", "MSFT");

        Map<String, List<BigDecimal>> dailyReturns = new LinkedHashMap<>();
        dailyReturns.put("AAPL", List.of(bd("0.01"), bd("0.02"), bd("0.03")));
        dailyReturns.put("MSFT", List.of(bd("0.02"), bd("0.04"), bd("0.06")));

        RealMatrix result = calculator.calculateCovarianceMatrix(symbols, dailyReturns);

        /*
         AAPL returns: 0.01, 0.02, 0.03
         Mean AAPL = 0.02

         MSFT returns: 0.02, 0.04, 0.06
         Mean MSFT = 0.04

         Sample variance AAPL:
         ((-0.01)^2 + 0^2 + 0.01^2) / (3 - 1)
         = 0.0002 / 2
         = 0.0001

         Sample covariance AAPL/MSFT:
         ((-0.01 * -0.02) + (0 * 0) + (0.01 * 0.02)) / 2
         = 0.0004 / 2
         = 0.0002

         Sample variance MSFT:
         ((-0.02)^2 + 0^2 + 0.02^2) / 2
         = 0.0008 / 2
         = 0.0004
         */
        assertThat(result.getRowDimension()).isEqualTo(2);
        assertThat(result.getColumnDimension()).isEqualTo(2);

        assertThat(result.getEntry(0, 0)).isCloseTo(0.0001, withinDouble(0.000000001));
        assertThat(result.getEntry(0, 1)).isCloseTo(0.0002, withinDouble(0.000000001));
        assertThat(result.getEntry(1, 0)).isCloseTo(0.0002, withinDouble(0.000000001));
        assertThat(result.getEntry(1, 1)).isCloseTo(0.0004, withinDouble(0.000000001));
    }

    // Tests portfolio volatility formula using weights and covariance matrix
    //Calculates sqrt(wᵀΣw) × sqrt(252)
    @Test
    void calculateAnnualisedVolatility_shouldCalculatePortfolioVolatility() {
        List<String> symbols = List.of("AAPL", "MSFT");

        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("AAPL", bd("0.50"));
        weights.put("MSFT", bd("0.50"));

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
         Covariance matrix:
         [0.0001, 0.0002]
         [0.0002, 0.0004]

         Weights:
         [0.5, 0.5]

         Daily portfolio variance:
         wT * covariance * w
         = 0.000225

         Daily volatility:
         sqrt(0.000225) = 0.015

         Annualised volatility:
         0.015 * sqrt(252)
         ≈ 0.23811761799581313
         */
        assertThat(result.doubleValue())
                .isCloseTo(0.23811761799581313, withinDouble(0.000000001));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private org.assertj.core.data.Offset<Double> withinDouble(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}