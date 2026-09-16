package com.tsh11.fypcode.service.portfolio.meanvariance.whitebox;

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

class MeanVarianceOptimisationAlgorithmBranchTest {

    private MeanVarianceOptimisationAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new MeanVarianceOptimisationAlgorithm(
                new PortfolioStatisticsCalculator(),
                new RebalanceSuggestionCalculator()
        );
    }

    // Creates impossible constraints so no feasible candidate can be created.
    // The test verifies that the algorithm falls back to the current portfolio.
    @Test
    void impossibleConstraints_shouldUseCurrentPortfolioAsFallbackCandidate() {
        PortfolioOptimisationRequest request = validRequest();

        /*
         With two assets, min = 0.60 and max = 0.60 means each asset would need 60%.
         The total would be 120%, so no feasible candidate can exist.
         The algorithm should fall back to the current portfolio.
         */
        request.setConstraints(constraints("0.60", "0.60"));

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result.getAssets()).hasSize(2);

        PortfolioOptimisationAssetResult apple = findAsset(result, "AAPL");
        PortfolioOptimisationAssetResult microsoft = findAsset(result, "MSFT");

        assertThat(apple.getCurrentWeight()).isEqualByComparingTo("0.50");
        assertThat(apple.getTargetWeight()).isEqualByComparingTo("0.50");
        assertThat(apple.getWeightDifference()).isEqualByComparingTo("0");

        assertThat(microsoft.getCurrentWeight()).isEqualByComparingTo("0.50");
        assertThat(microsoft.getTargetWeight()).isEqualByComparingTo("0.50");
        assertThat(microsoft.getWeightDifference()).isEqualByComparingTo("0");

        assertThat(result.getSuggestions()).isEmpty();
        assertThat(result.getEfficientFrontier()).isEmpty();
    }

    // Runs a feasible optimisation and verifies that any material target-weight difference produces increase/reduce suggestions.
    @Test
    void feasibleOptimisation_shouldCreateIncreaseOrReduceSuggestionWhenTargetDiffersMaterially() {
        PortfolioOptimisationRequest request = validRequest();

        PortfolioOptimisationResult result = algorithm.optimise(request);

        boolean atLeastOneMaterialChange = result.getAssets()
                .stream()
                .anyMatch(asset -> asset.getWeightDifference().abs().compareTo(new BigDecimal("0.01")) >= 0);

        if (atLeastOneMaterialChange) {
            assertThat(result.getSuggestions()).isNotEmpty();
        }

        for (String suggestion : result.getSuggestions()) {
            assertThat(suggestion)
                    .satisfiesAnyOf(
                            text -> assertThat(text).startsWith("Increase allocation to "),
                            text -> assertThat(text).startsWith("Reduce allocation to ")
                    );
        }
    }

    // Forces the only feasible portfolio to match the current weights, verifying the zero-difference branch and no rebalance suggestions.
    @Test
    void targetWeightEqualToCurrentWeight_shouldCreateZeroWeightDifferenceBranch() {
        PortfolioOptimisationRequest request = validRequest();
        request.setConstraints(constraints("0.50", "0.50"));

        /*
         With two assets and min=max=0.50, the only feasible candidate is 50/50.
         Since the current portfolio is also 50/50, both weight differences should be zero.
         */
        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result.getAssets()).hasSize(2);

        for (PortfolioOptimisationAssetResult asset : result.getAssets()) {
            assertThat(asset.getCurrentWeight()).isEqualByComparingTo("0.50");
            assertThat(asset.getTargetWeight()).isEqualByComparingTo("0.50");
            assertThat(asset.getWeightDifference()).isEqualByComparingTo("0");
        }

        assertThat(result.getSuggestions()).isEmpty();
    }

    private PortfolioOptimisationRequest validRequest() {
        PortfolioOptimisationRequest request = new PortfolioOptimisationRequest();
        request.setAlgorithm(PortfolioAlgorithm.MEAN_VARIANCE);
        request.setFrom(LocalDate.of(2026, 1, 1));
        request.setTo(LocalDate.of(2026, 1, 31));
        request.setRiskFreeRate(bd("0.02"));
        request.setSymbols(List.of("AAPL", "MSFT"));
        request.setCurrentWeights(weights(
                entry("AAPL", "0.50"),
                entry("MSFT", "0.50")
        ));
        request.setDailyReturns(dailyReturns(
                entry("AAPL", List.of("0.010", "0.015", "0.012", "0.011")),
                entry("MSFT", List.of("0.004", "0.006", "0.005", "0.007"))
        ));
        request.setConstraints(constraints("0", "1"));
        return request;
    }

    private PortfolioOptimisationAssetResult findAsset(PortfolioOptimisationResult result, String symbol) {
        return result.getAssets()
                .stream()
                .filter(asset -> asset.getSymbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Asset not found: " + symbol));
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