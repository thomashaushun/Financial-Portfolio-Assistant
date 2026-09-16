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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/*
Main flow:
PortfolioOptimisationService
        ↓
builds PortfolioOptimisationRequest
        ↓
MeanVarianceOptimisationAlgorithm.optimise(request)
        ↓
calculates target weights and metrics
        ↓
returns PortfolioOptimisationResult
 */
/*
The algorithm tries to calculate a portfolio allocation that gives a better trade-off between:
expected return
risk / volatility
risk-adjusted return, usually Sharpe ratio
 */
@Component
public class MeanVarianceOptimisationAlgorithm implements PortfolioOptimisationAlgorithm {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * 5% is a good first-pass search granularity.
     * You can later tighten this to 0.02 or 0.01 for finer search.
     */
    private static final BigDecimal DEFAULT_WEIGHT_STEP = new BigDecimal("0.05");

    /**
     * Avoid duplicate frontier points that are effectively the same risk level.
     */
    private static final BigDecimal FRONTIER_VOL_BUCKET = new BigDecimal("0.005");

    private final PortfolioStatisticsCalculator statisticsCalculator;
    private final RebalanceSuggestionCalculator rebalanceSuggestionCalculator;

    public MeanVarianceOptimisationAlgorithm(
            PortfolioStatisticsCalculator statisticsCalculator,
            RebalanceSuggestionCalculator rebalanceSuggestionCalculator) {
        this.statisticsCalculator = statisticsCalculator;
        this.rebalanceSuggestionCalculator = rebalanceSuggestionCalculator;
    }

    @Override
    public PortfolioAlgorithm getAlgorithmType() {
        return PortfolioAlgorithm.MEAN_VARIANCE;
    }

    //main method
    /*order of execution:
    1. Validate request.
    2. Extract symbols, current weights, daily returns and risk-free rate.
    3. Calculate expected daily returns.
    4. Calculate covariance matrix.
    5. Evaluate the current portfolio.
    6. Generate feasible candidate portfolios.
    7. Select the candidate with the best Sharpe ratio.
    8. Calculate efficient frontier points.
    9. Build PortfolioOptimisationResult.
    10. Return result.
     */
    @Override
    public PortfolioOptimisationResult optimise(PortfolioOptimisationRequest request) {
        //Validate request
        validateRequest(request);

        //Extract input data
        List<String> symbols = request.getSymbols();
        Map<String, BigDecimal> currentWeights = request.getCurrentWeights();
        Map<String, List<BigDecimal>> dailyReturns = request.getDailyReturns();
        BigDecimal riskFreeRate = request.getRiskFreeRate();

        //Calculate expected daily returns
        Map<String, BigDecimal> expectedDailyReturns =
                statisticsCalculator.calculateExpectedDailyReturns(symbols, dailyReturns);

        //Calculate covariance matrix
        RealMatrix covarianceMatrix =
                statisticsCalculator.calculateCovarianceMatrix(symbols, dailyReturns);

        //Evaluate the current portfolio
        PortfolioCandidate currentCandidate =
                evaluateCandidate(symbols, currentWeights, expectedDailyReturns, covarianceMatrix, riskFreeRate);

        //Generate feasible candidate portfolios
        List<PortfolioCandidate> feasibleCandidates =
                computeFeasibleCandidates(symbols, expectedDailyReturns, covarianceMatrix, request);

        //Select best Sharpe ratio candidate
        PortfolioCandidate optimalCandidate =
                selectBestSharpeCandidate(feasibleCandidates, currentCandidate);

        //Calculate efficient frontier
        List<EfficientFrontierPointDto> efficientFrontier =
                computeEfficientFrontier(feasibleCandidates);

        //Build final result
        PortfolioOptimisationResult result = new PortfolioOptimisationResult();
        result.setAlgorithm(getAlgorithmType());
        result.setCurrentPortfolio(toMetricsDto(currentCandidate));
        result.setOptimisedPortfolio(toMetricsDto(optimalCandidate));
        result.setAssets(toAssetResults(symbols, currentWeights, optimalCandidate.getWeights()));
        result.setEfficientFrontier(efficientFrontier);
        result.setSuggestions(
                rebalanceSuggestionCalculator.calculateSuggestions(currentWeights, optimalCandidate.getWeights())
        );

        return result;
    }

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
        if (request.getConstraints() == null) {
            throw new IllegalArgumentException("Portfolio constraints are required.");
        }
    }

    private PortfolioCandidate evaluateCandidate(
            List<String> symbols,
            Map<String, BigDecimal> weights,
            Map<String, BigDecimal> expectedDailyReturns,
            RealMatrix covarianceMatrix,
            BigDecimal riskFreeRate) {

        PortfolioCandidate candidate = new PortfolioCandidate();
        candidate.setWeights(new LinkedHashMap<>(weights));

        //calculates expected annual return
        BigDecimal annualReturn =
                statisticsCalculator.calculateAnnualisedReturn(weights, expectedDailyReturns);

        //calculates annual volatility/risk
        BigDecimal annualVolatility =
                statisticsCalculator.calculateAnnualisedVolatility(weights, covarianceMatrix, symbols);

        //calculates the Sharpe ratio
        BigDecimal sharpeRatio =
                statisticsCalculator.calculateSharpeRatio(annualReturn, annualVolatility, riskFreeRate);

        candidate.setExpectedAnnualReturn(annualReturn);
        candidate.setAnnualVolatility(annualVolatility);
        candidate.setSharpeRatio(sharpeRatio);

        return candidate;
    }

    //prepares the grid-search limits
    private List<PortfolioCandidate> computeFeasibleCandidates(
            List<String> symbols,
            Map<String, BigDecimal> expectedDailyReturns,
            RealMatrix covarianceMatrix,
            PortfolioOptimisationRequest request) {

        BigDecimal step = DEFAULT_WEIGHT_STEP; // 0.05
        PortfolioConstraintSet constraints = request.getConstraints();
        BigDecimal riskFreeRate = request.getRiskFreeRate();

        int totalUnits = toUnits(ONE, step);
        int minUnits = toUnits(
                constraints.getMinWeightPerAsset() == null ? ZERO : constraints.getMinWeightPerAsset(),
                step
        );
        int maxUnits = toUnits(
                constraints.getMaxWeightPerAsset() == null ? ONE : constraints.getMaxWeightPerAsset(),
                step
        );

        List<PortfolioCandidate> candidates = new ArrayList<>();
        int[] units = new int[symbols.size()];

        searchWeightsRecursively(
                0,
                totalUnits,
                symbols,
                units,
                minUnits,
                maxUnits,
                step,
                expectedDailyReturns,
                covarianceMatrix,
                riskFreeRate,
                candidates
        );

        return candidates;
    }

    //Try all valid combinations of asset weights that sum to 100% and respect min/max constraints.
    private void searchWeightsRecursively(
            int assetIndex,
            int remainingUnits,
            List<String> symbols,
            int[] units,
            int minUnits,
            int maxUnits,
            BigDecimal step,
            Map<String, BigDecimal> expectedDailyReturns,
            RealMatrix covarianceMatrix,
            BigDecimal riskFreeRate,
            List<PortfolioCandidate> candidates) {

        int assetCount = symbols.size();

        if (assetIndex == assetCount - 1) {
            int lastUnits = remainingUnits;

            if (lastUnits < minUnits || lastUnits > maxUnits) {
                return;
            }

            units[assetIndex] = lastUnits;

            Map<String, BigDecimal> weights = toWeightMap(symbols, units, step);
            if (!isWeightSumValid(weights)) {
                return;
            }

            PortfolioCandidate candidate = evaluateCandidate(
                    symbols,
                    weights,
                    expectedDailyReturns,
                    covarianceMatrix,
                    riskFreeRate
            );

            candidates.add(candidate);
            return;
        }

        int remainingAssetsAfterCurrent = assetCount - assetIndex - 1;

        int lowerBound = Math.max(
                minUnits,
                remainingUnits - remainingAssetsAfterCurrent * maxUnits
        );

        int upperBound = Math.min(
                maxUnits,
                remainingUnits - remainingAssetsAfterCurrent * minUnits
        );

        for (int currentUnits = lowerBound; currentUnits <= upperBound; currentUnits++) {
            units[assetIndex] = currentUnits;

            searchWeightsRecursively(
                    assetIndex + 1,
                    remainingUnits - currentUnits,
                    symbols,
                    units,
                    minUnits,
                    maxUnits,
                    step,
                    expectedDailyReturns,
                    covarianceMatrix,
                    riskFreeRate,
                    candidates
            );
        }
    }

    private PortfolioCandidate selectBestSharpeCandidate(
            List<PortfolioCandidate> candidates,
            PortfolioCandidate fallbackCandidate) {

        if (candidates == null || candidates.isEmpty()) {
            return fallbackCandidate;
        }

        return candidates.stream()
                .max(Comparator.comparing(PortfolioCandidate::getSharpeRatio))
                .orElse(fallbackCandidate);
    }

    private List<EfficientFrontierPointDto> computeEfficientFrontier(List<PortfolioCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<PortfolioCandidate> sortedByRisk = candidates.stream()
                .sorted(Comparator.comparing(PortfolioCandidate::getAnnualVolatility)
                        .thenComparing(PortfolioCandidate::getExpectedAnnualReturn))
                .toList();

        List<PortfolioCandidate> frontierCandidates = new ArrayList<>();
        BigDecimal bestReturnSoFar = null;

        for (PortfolioCandidate candidate : sortedByRisk) {
            BigDecimal risk = safe(candidate.getAnnualVolatility());
            BigDecimal expectedReturn = safe(candidate.getExpectedAnnualReturn());

            if (bestReturnSoFar == null || expectedReturn.compareTo(bestReturnSoFar) > 0) {
                frontierCandidates.add(candidate);
                bestReturnSoFar = expectedReturn;
            }
        }

        List<PortfolioCandidate> deduplicated = deduplicateFrontierCandidates(frontierCandidates);

        List<EfficientFrontierPointDto> frontier = new ArrayList<>();
        for (PortfolioCandidate candidate : deduplicated) {
            EfficientFrontierPointDto point = new EfficientFrontierPointDto();
            point.setExpectedAnnualReturn(candidate.getExpectedAnnualReturn());
            point.setAnnualVolatility(candidate.getAnnualVolatility());
            point.setSharpeRatio(candidate.getSharpeRatio());
            point.setWeights(new LinkedHashMap<>(candidate.getWeights()));
            frontier.add(point);
        }

        return frontier;
    }

    private Map<String, BigDecimal> toWeightMap(
            List<String> symbols,
            int[] units,
            BigDecimal step) {

        Map<String, BigDecimal> weights = new LinkedHashMap<>();

        for (int i = 0; i < symbols.size(); i++) {
            BigDecimal weight = BigDecimal.valueOf(units[i]).multiply(step, MC);
            weights.put(symbols.get(i), weight);
        }

        return weights;
    }

    private boolean isWeightSumValid(Map<String, BigDecimal> weights) {
        BigDecimal sum = ZERO;

        for (BigDecimal weight : weights.values()) {
            sum = sum.add(weight, MC);
        }

        return sum.subtract(ONE, MC).abs().compareTo(new BigDecimal("0.0000001")) <= 0;
    }

    private int toUnits(BigDecimal weight, BigDecimal step) {
        return weight.divide(step, 0, BigDecimal.ROUND_HALF_UP).intValueExact();
    }

    private String volatilityBucket(BigDecimal volatility) {
        BigDecimal safeVolatility = volatility == null ? ZERO : volatility;
        BigDecimal bucket = safeVolatility.divide(FRONTIER_VOL_BUCKET, 0, BigDecimal.ROUND_HALF_UP)
                .multiply(FRONTIER_VOL_BUCKET);
        return bucket.toPlainString();
    }

    private PortfolioMetricsDto toMetricsDto(PortfolioCandidate candidate) {
        PortfolioMetricsDto dto = new PortfolioMetricsDto();
        dto.setExpectedAnnualReturn(candidate.getExpectedAnnualReturn());
        dto.setAnnualVolatility(candidate.getAnnualVolatility());
        dto.setSharpeRatio(candidate.getSharpeRatio());
        return dto;
    }

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

    private List<PortfolioCandidate> deduplicateFrontierCandidates(List<PortfolioCandidate> candidates) {
        List<PortfolioCandidate> deduplicated = new ArrayList<>();
        LinkedHashSet<String> seenSignatures = new LinkedHashSet<>();

        for (PortfolioCandidate candidate : candidates) {
            String signature = candidateWeightSignature(candidate);

            if (seenSignatures.contains(signature)) {
                continue;
            }

            seenSignatures.add(signature);
            deduplicated.add(candidate);
        }

        return deduplicated;
    }

    private String candidateWeightSignature(PortfolioCandidate candidate) {
        StringBuilder builder = new StringBuilder();

        candidate.getWeights().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder
                        .append(entry.getKey())
                        .append('=')
                        .append(entry.getValue().stripTrailingZeros().toPlainString())
                        .append(';'));

        return builder.toString();
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}