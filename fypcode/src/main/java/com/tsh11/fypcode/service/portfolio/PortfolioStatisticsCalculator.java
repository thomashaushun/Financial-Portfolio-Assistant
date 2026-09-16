package com.tsh11.fypcode.service.portfolio;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;

// A mathematical helper class used by optimisation algorithms
/*
Calculates:
1. Expected daily returns
2. Covariance matrix
3. Annualised portfolio return
4. Annualised portfolio volatility
5. Sharpe ratio
 */
@Component
public class PortfolioStatisticsCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TRADING_DAYS_PER_YEAR = new BigDecimal("252");

    //calculates the average daily return for each asset
    /*
    Example
    input:
    AAPL returns = [0.01, -0.02, 0.03]
    MSFT returns = [0.02, 0.01, -0.01]

    output:
    AAPL → 0.0067
    MSFT → 0.0067
     */
    //Formula: portfolio return = weighted sum of asset returns × 252
    public Map<String, BigDecimal> calculateExpectedDailyReturns(
            List<String> symbols,
            Map<String, List<BigDecimal>> dailyReturns) {

        java.util.LinkedHashMap<String, BigDecimal> result = new java.util.LinkedHashMap<>();

        //for each symbol
        for (String symbol : symbols) {
            List<BigDecimal> returns = dailyReturns.get(symbol);
            //calculate average
            result.put(symbol, average(returns));
        }

        return result;
    }

    //covariance matrix measures how asset returns move together
    public RealMatrix calculateCovarianceMatrix(
            List<String> symbols,
            Map<String, List<BigDecimal>> dailyReturns) {

        //create a square matrix
        //if there are 3 assets, the matrix is 3x3
        int n = symbols.size();
        double[][] matrix = new double[n][n];

        //loops over every pair of assets:
        //fills the matrix with covariance values
        /*
        Example:
                  AAPL      MSFT      BND
        AAPL   var(AAPL) cov(A,M) cov(A,B)
        MSFT   cov(M,A)  var(MSFT) cov(M,B)
        BND    cov(B,A)  cov(B,M)  var(BND)
         */
        for (int i = 0; i < n; i++) {
            List<BigDecimal> left = dailyReturns.get(symbols.get(i));

            for (int j = 0; j < n; j++) {
                List<BigDecimal> right = dailyReturns.get(symbols.get(j));
                matrix[i][j] = covariance(left, right);
            }
        }

        return new Array2DRowRealMatrix(matrix);
    }

    //calculates expected annual portfolio return.
    public BigDecimal calculateAnnualisedReturn(
            Map<String, BigDecimal> weights,                //asset allocation weights
            Map<String, BigDecimal> expectedDailyReturns) { //average daily return for each asset

        BigDecimal result = BigDecimal.ZERO;

        //loops through each asset
        for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
            BigDecimal weight = entry.getValue();
            BigDecimal dailyReturn = expectedDailyReturns.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            //Formula: portfolio daily return = sum(weight × asset expected daily return)
            result = result.add(weight.multiply(dailyReturn, MC), MC);
        }

        //annualised return = expected daily return × 252
        return result.multiply(TRADING_DAYS_PER_YEAR, MC);
    }

    //calculates portfolio annual volatility/risk
    //portfolio variance = wᵀ × covariance matrix × w
    //annual volatility = sqrt(portfolio variance) × sqrt(252)
    public BigDecimal calculateAnnualisedVolatility(
            Map<String, BigDecimal> weights,
            RealMatrix covarianceMatrix,
            List<String> symbols) {

        //converts weights into an array in the same order as symbols
        double[] weightArray = new double[symbols.size()];
        for (int i = 0; i < symbols.size(); i++) {
            weightArray[i] = weights.getOrDefault(symbols.get(i), BigDecimal.ZERO).doubleValue();
        }

        //creates a vector of asset weights: w
        RealMatrix w = new Array2DRowRealMatrix(weightArray);
        //calculates portfolio variance
        //portfolio variance = wᵀ × covariance matrix × w
        double variance = w.transpose().multiply(covarianceMatrix).multiply(w).getEntry(0, 0);

        if (variance < 0) {
            variance = 0;
        }

        //calculates annualised volatility
        //daily volatility = sqrt(portfolio variance)
        //annual volatility = daily volatility × sqrt(252)
        double annualisedVolatility = Math.sqrt(variance) * Math.sqrt(252.0);
        return BigDecimal.valueOf(annualisedVolatility);
    }

    //calculates the Sharpe ratio.
    //Sharpe ratio = (annual return - risk-free rate) / volatility
    public BigDecimal calculateSharpeRatio(
            BigDecimal annualReturn,
            BigDecimal annualVolatility,
            BigDecimal riskFreeRate) {

        //avoid division by zero, if <= 0, return 0
        if (annualVolatility == null || annualVolatility.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        //calculate excess return.
        //excess return = annual return - risk-free rate
        BigDecimal excessReturn = annualReturn.subtract(
                riskFreeRate == null ? BigDecimal.ZERO : riskFreeRate,
                MC
        );

        // Formula: Sharpe ratio = excess return / volatility
        return excessReturn.divide(annualVolatility, MC);
    }

    //helper
    //average of a list
    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            sum = sum.add(value, MC);
        }

        return sum.divide(BigDecimal.valueOf(values.size()), MC);
    }

    //calculates sample covariance between two return series
    private double covariance(List<BigDecimal> a, List<BigDecimal> b) {
        //If the lists are missing, empty, or different lengths, return 0
        if (a == null || b == null || a.isEmpty() || b.isEmpty() || a.size() != b.size()) {
            return 0.0;
        }

        //calculate both means
        double meanA = a.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);
        double meanB = b.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0.0);

        //sum mean
        double sum = 0.0;
        for (int i = 0; i < a.size(); i++) {
            sum += (a.get(i).doubleValue() - meanA) * (b.get(i).doubleValue() - meanB);
        }

        return a.size() > 1 ? sum / (a.size() - 1) : 0.0;
    }
}