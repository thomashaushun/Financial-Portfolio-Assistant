package com.tsh11.fypcode.service.portfolio.meanvariance.blackbox;

import com.tsh11.fypcode.dto.PortfolioAlgorithm;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeanVarianceOptimisationAlgorithmBoundaryValueAnalysisTest {

    private MeanVarianceOptimisationAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new MeanVarianceOptimisationAlgorithm(
                new PortfolioStatisticsCalculator(),
                new RebalanceSuggestionCalculator()
        );
    }

    // Checks that a null optimisation request is rejected.
    @Test
    void nullRequest_shouldThrowException() {
        assertThatThrownBy(() -> algorithm.optimise(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Portfolio optimisation request must not be null.");
    }

    // Checks that the algorithm rejects a request with no symbols.
    @Test
    void emptySymbols_shouldThrowException() {
        PortfolioOptimisationRequest request = validRequest();
        request.setSymbols(List.of());

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one asset is required.");
    }

    // Checks that the algorithm rejects a request with no current portfolio weights.
    @Test
    void emptyCurrentWeights_shouldThrowException() {
        PortfolioOptimisationRequest request = validRequest();
        request.setCurrentWeights(Map.of());

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current weights are required.");
    }

    //Checks that the algorithm rejects a request with no historical return series.
    @Test
    void emptyDailyReturns_shouldThrowException() {
        PortfolioOptimisationRequest request = validRequest();
        request.setDailyReturns(Map.of());

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Historical return series are required.");
    }

    // Checks that portfolio constraints are required.
    @Test
    void nullConstraints_shouldThrowException() {
        PortfolioOptimisationRequest request = validRequest();
        request.setConstraints(null);

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Portfolio constraints are required.");
    }

    // Checks that a missing risk-free rate does not crash the algorithm,
    // so that the statistics calculator can treat it safely.
    @Test
    void nullRiskFreeRate_shouldStillReturnResult() {
        PortfolioOptimisationRequest request = validRequest();
        request.setRiskFreeRate(null);

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result).isNotNull();
        assertThat(result.getCurrentPortfolio()).isNotNull();
        assertThat(result.getOptimisedPortfolio()).isNotNull();
        assertThat(result.getAssets()).hasSize(2);
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