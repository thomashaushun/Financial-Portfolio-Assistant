package com.tsh11.fypcode.service.portfolio.meanvariance.whitebox;

import com.tsh11.fypcode.dto.EfficientFrontierPointDto;
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

class MeanVarianceOptimisationAlgorithmLoopTest {

    private MeanVarianceOptimisationAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new MeanVarianceOptimisationAlgorithm(
                new PortfolioStatisticsCalculator(),
                new RebalanceSuggestionCalculator()
        );
    }

    // Uses three assets and verifies that every symbol is included in the asset result list.
    @Test
    void threeAssetRequest_shouldProcessEverySymbolInAssetResultLoop() {
        PortfolioOptimisationRequest request = threeAssetRequest();

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result.getAssets()).hasSize(3);

        assertThat(result.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("AAPL", "MSFT", "VOO");

        for (PortfolioOptimisationAssetResult asset : result.getAssets()) {
            assertThat(asset.getCurrentWeight()).isNotNull();
            assertThat(asset.getTargetWeight()).isNotNull();
            assertThat(asset.getWeightDifference()).isNotNull();

            assertThat(asset.getWeightDifference())
                    .isEqualByComparingTo(asset.getTargetWeight().subtract(asset.getCurrentWeight()));
        }

        BigDecimal targetWeightSum = result.getAssets()
                .stream()
                .map(PortfolioOptimisationAssetResult::getTargetWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(targetWeightSum).isEqualByComparingTo("1.00");
    }

    // Verifies that recursively generated target weights respect the minimum and maximum asset constraints.
    @Test
    void recursiveWeightSearch_shouldRespectMinAndMaxConstraintsForEveryAsset() {
        PortfolioOptimisationRequest request = threeAssetRequest();
        request.setConstraints(constraints("0.10", "0.70"));

        PortfolioOptimisationResult result = algorithm.optimise(request);

        for (PortfolioOptimisationAssetResult asset : result.getAssets()) {
            assertThat(asset.getTargetWeight()).isGreaterThanOrEqualTo(bd("0.10"));
            assertThat(asset.getTargetWeight()).isLessThanOrEqualTo(bd("0.70"));
        }

        BigDecimal targetWeightSum = result.getAssets()
                .stream()
                .map(PortfolioOptimisationAssetResult::getTargetWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(targetWeightSum).isEqualByComparingTo("1.00");
    }

    // Checks that efficient frontier points are created and that each point contains weights for all symbols.
    @Test
    void efficientFrontierLoop_shouldCreateFrontierPointsWithWeightsForAllSymbols() {
        PortfolioOptimisationRequest request = threeAssetRequest();

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result.getEfficientFrontier()).isNotEmpty();

        for (EfficientFrontierPointDto point : result.getEfficientFrontier()) {
            assertThat(point.getExpectedAnnualReturn()).isNotNull();
            assertThat(point.getAnnualVolatility()).isNotNull();
            assertThat(point.getSharpeRatio()).isNotNull();

            assertThat(point.getWeights()).containsKeys("AAPL", "MSFT", "VOO");

            BigDecimal weightSum = point.getWeights()
                    .values()
                    .stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(weightSum).isEqualByComparingTo("1.00");
        }
    }

    // Verifies that the efficient frontier is returned in non-decreasing volatility order.
    @Test
    void efficientFrontierLoop_shouldReturnFrontierSortedByNonDecreasingVolatility() {
        PortfolioOptimisationRequest request = threeAssetRequest();

        PortfolioOptimisationResult result = algorithm.optimise(request);

        List<EfficientFrontierPointDto> frontier = result.getEfficientFrontier();

        assertThat(frontier).isNotEmpty();

        for (int i = 1; i < frontier.size(); i++) {
            BigDecimal previousVolatility = frontier.get(i - 1).getAnnualVolatility();
            BigDecimal currentVolatility = frontier.get(i).getAnnualVolatility();

            assertThat(currentVolatility)
                    .isGreaterThanOrEqualTo(previousVolatility);
        }
    }

    private PortfolioOptimisationRequest threeAssetRequest() {
        PortfolioOptimisationRequest request = new PortfolioOptimisationRequest();
        request.setAlgorithm(PortfolioAlgorithm.MEAN_VARIANCE);
        request.setFrom(LocalDate.of(2026, 1, 1));
        request.setTo(LocalDate.of(2026, 1, 31));
        request.setRiskFreeRate(bd("0.02"));
        request.setSymbols(List.of("AAPL", "MSFT", "VOO"));
        request.setCurrentWeights(weights(
                entry("AAPL", "0.40"),
                entry("MSFT", "0.30"),
                entry("VOO", "0.30")
        ));
        request.setDailyReturns(dailyReturns(
                entry("AAPL", List.of("0.010", "0.015", "0.012", "0.011")),
                entry("MSFT", List.of("0.004", "0.006", "0.005", "0.007")),
                entry("VOO", List.of("0.006", "0.008", "0.007", "0.009"))
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