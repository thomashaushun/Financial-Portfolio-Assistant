package com.tsh11.fypcode.service.portfolio;

import com.tsh11.fypcode.dto.EfficientFrontierPointDto;
import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioMetricsDto;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.PortfolioOptimisationRequest;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import org.apache.commons.math3.linear.RealMatrix;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
main idea: target weight = inverse volatility / total inverse volatility
High volatility asset  → lower target weight
Low volatility asset   → higher target weight
*/

@Component
public class RiskParityOptimisationAlgorithm implements PortfolioOptimisationAlgorithm {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TRADING_DAYS_PER_YEAR = new BigDecimal("252");

    private final PortfolioStatisticsCalculator statisticsCalculator;
    private final RebalanceSuggestionCalculator rebalanceSuggestionCalculator;

    //constructor
    public RiskParityOptimisationAlgorithm(
            PortfolioStatisticsCalculator statisticsCalculator,
            RebalanceSuggestionCalculator rebalanceSuggestionCalculator) {
        this.statisticsCalculator = statisticsCalculator;
        this.rebalanceSuggestionCalculator = rebalanceSuggestionCalculator;
    }

    // used for user drop down choice in analysis.html
    @Override
    public PortfolioAlgorithm getAlgorithmType() {
        return PortfolioAlgorithm.RISK_PARITY;
    }

    //main function
    //runs the main flow
    /*
    1. Validate request.
    2. Extract symbols, current weights, daily returns and risk-free rate.
    3. Calculate expected daily returns.
    4. Calculate covariance matrix.
    5. Calculate risk parity target weights using inverse volatility.
    6. Calculate current portfolio metrics.
    7. Calculate risk parity portfolio metrics.
    8. Build asset-level result rows.
    9. Build suggestions.
    10. Return PortfolioOptimisationResult.
     */
    @Override
    public PortfolioOptimisationResult optimise(PortfolioOptimisationRequest request) {
        //Validation step
        validateRequest(request);

        // fetching data using request.get
        // inputs required for the algorithm
        /*
        example:
        symbols = [AAPL, SPY, VOO]

        currentWeights:
        AAPL = 0.50
        SPY  = 0.30
        VOO  = 0.20

        dailyReturns:
        AAPL = [0.01, -0.02, 0.015, ...]
        SPY  = [0.005, -0.01, 0.007, ...]
        VOO  = [0.004, -0.008, 0.006, ...]
         */
        List<String> symbols = request.getSymbols();
        Map<String, BigDecimal> currentWeights = request.getCurrentWeights();
        Map<String, List<BigDecimal>> dailyReturns = request.getDailyReturns();
        BigDecimal riskFreeRate = request.getRiskFreeRate();

        // calculates average daily return for each asset
        /*
        example:
        AAPL daily returns = [1%, -2%, 1.5%]
        Average daily return = 0.1667%
         */
        // Risk parity does not use expected returns to decide target weights.
        // These expected daily returns are used to calculate portfolio metrics such as expected annual return and Sharpe ratio.
        Map<String, BigDecimal> expectedDailyReturns =
                statisticsCalculator.calculateExpectedDailyReturns(symbols, dailyReturns);

        //Calculate covariance matrix
        //The covariance matrix is used later to calculate portfolio volatility
        RealMatrix covarianceMatrix =
                statisticsCalculator.calculateCovarianceMatrix(symbols, dailyReturns);

        // Core of the algorithm
        Map<String, BigDecimal> targetWeights = calculateInverseVolatilityWeights(symbols, dailyReturns);

        // calculates the current portfolio’s return, volatility and Sharpe ratio.
        PortfolioMetricsDto currentMetrics =
                calculateMetrics(symbols, currentWeights, expectedDailyReturns, covarianceMatrix, riskFreeRate);

        // calculates the same metrics after applying risk parity target weights.
        PortfolioMetricsDto riskParityMetrics =
                calculateMetrics(symbols, targetWeights, expectedDailyReturns, covarianceMatrix, riskFreeRate);

        // Build result
        PortfolioOptimisationResult result = new PortfolioOptimisationResult();
        result.setAlgorithm(getAlgorithmType());
        result.setCurrentPortfolio(currentMetrics);
        result.setOptimisedPortfolio(riskParityMetrics);
        result.setAssets(toAssetResults(symbols, currentWeights, targetWeights));
        result.setEfficientFrontier(List.of(toFrontierPoint(riskParityMetrics, targetWeights)));
        result.setSuggestions(
                rebalanceSuggestionCalculator.calculateSuggestions(currentWeights, targetWeights)
        );

        return result;
    }

   /*
   Validation step, rejects the request if:
   request is null
   symbols are missing
   current weights are missing
   daily returns are missing
   */
    private void validateRequest(PortfolioOptimisationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Portfolio optimisation request must not be null.");
        }

        if (request.getSymbols() == null || request.getSymbols().isEmpty()) {
            throw new IllegalArgumentException("At least one asset is required.");
        }

        if (request.getCurrentWeights() == null || request.getCurrentWeights().isEmpty()) {
            throw new IllegalArgumentException("Current weights are required.");
        }

        if (request.getDailyReturns() == null || request.getDailyReturns().isEmpty()) {
            throw new IllegalArgumentException("Historical return series are required.");
        }
    }

    // Core of the algorithm
    //lower-risk asset gets more weight
    /*
    For each asset:
    1. Get daily returns.
    2. Calculate annualised volatility.
    3. Calculate inverse volatility = 1 / volatility.
    4. Add inverse volatility to total.

    Then:
    5. Divide each inverse volatility by the total inverse volatility.
    6. Return target weights.
     */
    private Map<String, BigDecimal> calculateInverseVolatilityWeights(
            List<String> symbols,
            Map<String, List<BigDecimal>> dailyReturns) {

        Map<String, BigDecimal> inverseVolatilities = new LinkedHashMap<>();
        BigDecimal totalInverseVolatility = ZERO;

        //loop through assets
        for (String symbol : symbols) {
            //Get the list of daily return values for the current asset symbol from the dailyReturns map
            List<BigDecimal> returns = dailyReturns.get(symbol);

            BigDecimal annualisedVolatility = calculateAnnualisedVolatility(returns);

            //reject invalid volatility
            if (annualisedVolatility.compareTo(ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "Risk parity cannot be calculated because " + symbol + " has zero or invalid volatility."
                );
            }

            //calculate inverse volatility, 1 / annualisedVolatility
            BigDecimal inverseVolatility = ONE.divide(annualisedVolatility, MC);
            //store and sum inverse volatility
            inverseVolatilities.put(symbol, inverseVolatility);
            totalInverseVolatility = totalInverseVolatility.add(inverseVolatility, MC);
        }

        //reject invalid total
        if (totalInverseVolatility.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException("Risk parity cannot be calculated because total inverse volatility is zero.");
        }

        //convert inverse volatility into weights
        //Formula: weight = asset inverse volatility / total inverse volatility
        /*Example:
        Asset A inverse volatility = 5
        Asset B inverse volatility = 10
        Total inverse volatility = 15

        Asset A weight = 5 / 15 = 33.33%
        Asset B weight = 10 / 15 = 66.67%
         */
        Map<String, BigDecimal> weights = new LinkedHashMap<>();

        for (String symbol : symbols) {
            BigDecimal weight = inverseVolatilities.get(symbol).divide(totalInverseVolatility, MC);
            weights.put(symbol, weight);
        }

        return weights;
    }

    /*
    calculates an asset’s annualised volatility from its daily returns.

    1. If fewer than two return values exist, return zero.
    2. Calculate average daily return.
    3. For each daily return:
       - subtract the average
       - square the difference
       - add it to total
    4. Divide by n - 1 to calculate sample variance.
    5. Take square root to get daily standard deviation.
    6. Multiply by sqrt(252) to annualise.
     */
    /*
    Formula:

    Daily variance:
    variance = Σ(return - mean)² / (n - 1)

    Daily volatility:
    daily volatility = √variance

    Annualised volatility:
    annualised volatility = daily volatility × √252
     */
    private BigDecimal calculateAnnualisedVolatility(List<BigDecimal> returns) {
        //At least two return values are needed to calculate volatility
        if (returns == null || returns.size() < 2) {
            return ZERO;
        }

        //calculate mean daily return
        BigDecimal mean = average(returns);
        //calculate squared deviations
        BigDecimal sumSquaredDifferences = ZERO;

        //difference = return - mean
        //squared difference = difference²
        for (BigDecimal value : returns) {
            BigDecimal difference = value.subtract(mean, MC);
            sumSquaredDifferences = sumSquaredDifferences.add(difference.multiply(difference, MC), MC);
        }

        //calculate sample variance
        //variance = sumSquaredDifferences / (size of return - 1)
        BigDecimal variance = sumSquaredDifferences.divide(
                BigDecimal.valueOf(returns.size() - 1L),
                MC
        );

        //daily volatility = √variance
        //annualised volatility = daily volatility × √252
        double annualisedVolatility = Math.sqrt(variance.doubleValue()) * Math.sqrt(TRADING_DAYS_PER_YEAR.doubleValue());

        return BigDecimal.valueOf(annualisedVolatility);
    }

    // calculates the arithmetic average of a list of BigDecimal values.
    //used by calculateAnnualisedVolatility()
    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return ZERO;
        }

        BigDecimal total = ZERO;

        for (BigDecimal value : values) {
            total = total.add(value, MC);
        }

        return total.divide(BigDecimal.valueOf(values.size()), MC);
    }

    //calculates portfolio-level metrics for a given weight allocation.
    /*
    used twice:
    1. For current weights.
    2. For risk parity target weights.
     */
    /*
    calculates:
    expected annual return
    annual volatility
    Sharpe ratio.
     */
    // Sharpe ratio = (annual return - risk free rate) / annual volatility
    // Sharpe ratio is used to evaluate portfolio performance after considering risk
    private PortfolioMetricsDto calculateMetrics(
            List<String> symbols,
            Map<String, BigDecimal> weights,
            Map<String, BigDecimal> expectedDailyReturns,
            RealMatrix covarianceMatrix,
            BigDecimal riskFreeRate) {

        PortfolioMetricsDto dto = new PortfolioMetricsDto();

        BigDecimal annualReturn =
                statisticsCalculator.calculateAnnualisedReturn(weights, expectedDailyReturns);

        BigDecimal annualVolatility =
                statisticsCalculator.calculateAnnualisedVolatility(weights, covarianceMatrix, symbols);

        BigDecimal sharpeRatio =
                statisticsCalculator.calculateSharpeRatio(annualReturn, annualVolatility, riskFreeRate);

        dto.setExpectedAnnualReturn(annualReturn);
        dto.setAnnualVolatility(annualVolatility);
        dto.setSharpeRatio(sharpeRatio);

        return dto;
    }

    /*
    creates one result row per asset.

    For each symbol, it stores:
    symbol
    current weight
    target weight
    difference

    Example:
    AAPL current = 50%
    AAPL target = 18%
    difference = -32%

    The frontend uses this for the allocation comparison table.
     */
    private List<PortfolioOptimisationAssetResult> toAssetResults(
            List<String> symbols,
            Map<String, BigDecimal> currentWeights,
            Map<String, BigDecimal> targetWeights) {

        List<PortfolioOptimisationAssetResult> results = new ArrayList<>();

        for (String symbol : symbols) {
            BigDecimal current = currentWeights.getOrDefault(symbol, ZERO);
            BigDecimal target = targetWeights.getOrDefault(symbol, ZERO);

            PortfolioOptimisationAssetResult item = new PortfolioOptimisationAssetResult();
            item.setSymbol(symbol);
            item.setCurrentWeight(current);
            item.setTargetWeight(target);
            item.setWeightDifference(target.subtract(current, MC));

            results.add(item);
        }

        return results;
    }

    /*
    This converts the risk parity portfolio into an EfficientFrontierPointDto.

    However, risk parity does not calculate a full efficient frontier.

    It produces one target allocation, so this function creates one point representing that allocation.
     */
    private EfficientFrontierPointDto toFrontierPoint(
            PortfolioMetricsDto metrics,
            Map<String, BigDecimal> weights) {

        EfficientFrontierPointDto point = new EfficientFrontierPointDto();
        point.setExpectedAnnualReturn(metrics.getExpectedAnnualReturn());
        point.setAnnualVolatility(metrics.getAnnualVolatility());
        point.setSharpeRatio(metrics.getSharpeRatio());
        point.setWeights(new LinkedHashMap<>(weights));

        return point;
    }
}