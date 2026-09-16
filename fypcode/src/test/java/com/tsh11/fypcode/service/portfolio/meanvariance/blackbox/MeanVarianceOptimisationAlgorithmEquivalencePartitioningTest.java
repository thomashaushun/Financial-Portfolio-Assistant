package com.tsh11.fypcode.service.portfolio.meanvariance.blackbox;

import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.PortfolioOptimisationRequest;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import com.tsh11.fypcode.service.portfolio.MeanVarianceOptimisationAlgorithm;
import com.tsh11.fypcode.service.portfolio.PortfolioConstraintSet;
import com.tsh11.fypcode.service.portfolio.PortfolioStatisticsCalculator;
import com.tsh11.fypcode.service.portfolio.RebalanceSuggestionCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MeanVarianceOptimisationAlgorithmEquivalencePartitioningTest {

    private MeanVarianceOptimisationAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new MeanVarianceOptimisationAlgorithm(
                new PortfolioStatisticsCalculator(),
                new RebalanceSuggestionCalculator()
        );
    }

    // Verifies that this algorithm identifies itself as MEAN_VARIANCE.
    @Test
    void getAlgorithmType_shouldReturnMeanVariance() {
        assertThat(algorithm.getAlgorithmType())
                .isEqualTo(PortfolioAlgorithm.MEAN_VARIANCE);
    }

    // Uses a valid two-asset request and checks that the algorithm returns current metrics,
    // optimised metrics, asset results, efficient frontier points, and target weights summing to 100%.
    @Test
    void validTwoAssetRequest_shouldReturnCompleteOptimisationResult() {
        PortfolioOptimisationRequest request = request(
                List.of("AAPL", "MSFT"),
                weights(
                        entry("AAPL", "0.50"),
                        entry("MSFT", "0.50")
                ),
                dailyReturns(
                        entry("AAPL", List.of("0.010", "0.015", "0.012", "0.011")),
                        entry("MSFT", List.of("0.004", "0.006", "0.005", "0.007"))
                ),
                constraints("0", "1")
        );

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result).isNotNull();
        assertThat(result.getAlgorithm()).isEqualTo(PortfolioAlgorithm.MEAN_VARIANCE);

        assertThat(result.getCurrentPortfolio()).isNotNull();
        assertThat(result.getOptimisedPortfolio()).isNotNull();

        assertThat(result.getCurrentPortfolio().getExpectedAnnualReturn()).isNotNull();
        assertThat(result.getCurrentPortfolio().getAnnualVolatility()).isNotNull();
        assertThat(result.getCurrentPortfolio().getSharpeRatio()).isNotNull();

        assertThat(result.getOptimisedPortfolio().getExpectedAnnualReturn()).isNotNull();
        assertThat(result.getOptimisedPortfolio().getAnnualVolatility()).isNotNull();
        assertThat(result.getOptimisedPortfolio().getSharpeRatio()).isNotNull();

        assertThat(result.getAssets()).hasSize(2);
        assertThat(result.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("AAPL", "MSFT");

        for (PortfolioOptimisationAssetResult asset : result.getAssets()) {
            assertThat(asset.getCurrentWeight()).isNotNull();
            assertThat(asset.getTargetWeight()).isNotNull();
            assertThat(asset.getWeightDifference()).isNotNull();

            assertThat(asset.getWeightDifference())
                    .isEqualByComparingTo(asset.getTargetWeight().subtract(asset.getCurrentWeight()));
        }

        assertThat(result.getEfficientFrontier()).isNotEmpty();

        BigDecimal targetWeightSum = result.getAssets()
                .stream()
                .map(PortfolioOptimisationAssetResult::getTargetWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(targetWeightSum).isEqualByComparingTo("1.00");
    }

    @SafeVarargs
    private final Map<String, BigDecimal> weights(Map.Entry<String, String>... entries) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            result.put(entry.getKey(), bd(entry.getValue()));
        }
        return result;
    }

    @SafeVarargs
    private final Map<String, List<BigDecimal>> dailyReturns(Map.Entry<String, List<String>>... entries) {
        Map<String, List<BigDecimal>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : entries) {
            result.put(
                    entry.getKey(),
                    entry.getValue().stream().map(this::bd).toList()
            );
        }
        return result;
    }

    private PortfolioOptimisationRequest request(
            List<String> symbols,
            Map<String, BigDecimal> currentWeights,
            Map<String, List<BigDecimal>> dailyReturns,
            PortfolioConstraintSet constraints
    ) {
        PortfolioOptimisationRequest request = new PortfolioOptimisationRequest();
        request.setAlgorithm(PortfolioAlgorithm.MEAN_VARIANCE);
        request.setFrom(LocalDate.of(2026, 1, 1));
        request.setTo(LocalDate.of(2026, 1, 31));
        request.setRiskFreeRate(bd("0.02"));
        request.setSymbols(symbols);
        request.setCurrentWeights(currentWeights);
        request.setDailyReturns(dailyReturns);
        request.setConstraints(constraints);
        return request;
    }

    private PortfolioConstraintSet constraints(String min, String max) {
        PortfolioConstraintSet constraints = new PortfolioConstraintSet();
        constraints.setLongOnly(true);
        constraints.setMinWeightPerAsset(bd(min));
        constraints.setMaxWeightPerAsset(bd(max));
        return constraints;
    }

    private Map.Entry<String, String> entry(String key, String value) {
        return Map.entry(key, value);
    }

    private Map.Entry<String, List<String>> entry(String key, List<String> value) {
        return Map.entry(key, value);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}