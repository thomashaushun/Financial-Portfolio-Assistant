package com.tsh11.fypcode.service.portfolio.riskparity.blackbox;

import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.PortfolioOptimisationRequest;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import com.tsh11.fypcode.service.portfolio.PortfolioStatisticsCalculator;
import com.tsh11.fypcode.service.portfolio.RebalanceSuggestionCalculator;
import com.tsh11.fypcode.service.portfolio.RiskParityOptimisationAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RiskParityOptimisationAlgorithmEquivalencePartitioningTest {

    private RiskParityOptimisationAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new RiskParityOptimisationAlgorithm(
                new PortfolioStatisticsCalculator(),
                new RebalanceSuggestionCalculator()
        );
    }

    /*
    This test creates two assets:

    HIGH_VOL = unstable/high volatility returns
    LOW_VOL  = stable/low volatility returns

    It then runs the Risk Parity algorithm and checks:

    lowVolTarget > highVolTarget

    Purpose:

    Confirms the core behaviour of Risk Parity: lower-risk assets receive higher allocation, while higher-risk assets receive lower allocation.
     */
    @Test
    void lowerVolatilityAsset_shouldReceiveHigherTargetWeight() {
        PortfolioOptimisationRequest request = validRequest(
                List.of("HIGH_VOL", "LOW_VOL"),
                Map.of(
                        "HIGH_VOL", List.of(
                                bd("0.10"), bd("-0.08"), bd("0.12"), bd("-0.09"), bd("0.11")
                        ),
                        "LOW_VOL", List.of(
                                bd("0.01"), bd("0.011"), bd("0.009"), bd("0.010"), bd("0.012")
                        )
                )
        );

        PortfolioOptimisationResult result = algorithm.optimise(request);

        BigDecimal highVolTarget = targetWeight(result, "HIGH_VOL");
        BigDecimal lowVolTarget = targetWeight(result, "LOW_VOL");

        assertThat(result.getAlgorithm()).isEqualTo(PortfolioAlgorithm.RISK_PARITY);
        assertThat(lowVolTarget).isGreaterThan(highVolTarget);
    }

    /*
     This test creates two assets with the same return pattern, meaning they have the same volatility.

    Expected result:

    ASSET_A target weight ≈ 50%
    ASSET_B target weight ≈ 50%

    Purpose:

    Confirms that when two assets have equal volatility, Risk Parity treats them equally and gives them approximately equal weights.
    This proves the algorithm is not arbitrarily favouring one asset.
     */
    @Test
    void equalVolatilityAssets_shouldReceiveApproximatelyEqualTargetWeights() {
        PortfolioOptimisationRequest request = validRequest(
                List.of("ASSET_A", "ASSET_B"),
                Map.of(
                        "ASSET_A", List.of(
                                bd("0.01"), bd("-0.01"), bd("0.02"), bd("-0.02")
                        ),
                        "ASSET_B", List.of(
                                bd("0.01"), bd("-0.01"), bd("0.02"), bd("-0.02")
                        )
                )
        );

        PortfolioOptimisationResult result = algorithm.optimise(request);

        BigDecimal assetAWeight = targetWeight(result, "ASSET_A");
        BigDecimal assetBWeight = targetWeight(result, "ASSET_B");

        assertThat(assetAWeight).isCloseTo(bd("0.5"), within("0.000001"));
        assertThat(assetBWeight).isCloseTo(bd("0.5"), within("0.000001"));
    }

    /*
    This test creates a three-asset portfolio and runs the Risk Parity algorithm.

    It then adds all target weights together:

    AAPL target weight + SPY target weight + VOO target weight

    Expected result:

    Total target weight ≈ 1.0

    Purpose:

    Confirms that inverse-volatility scores are correctly normalised into valid portfolio weights.
     */
    @Test
    void targetWeights_shouldSumToOne() {
        PortfolioOptimisationRequest request = validRequest(
                List.of("AAPL", "SPY", "VOO"),
                Map.of(
                        "AAPL", List.of(
                                bd("0.05"), bd("-0.04"), bd("0.06"), bd("-0.03"), bd("0.04")
                        ),
                        "SPY", List.of(
                                bd("0.02"), bd("-0.015"), bd("0.025"), bd("-0.01"), bd("0.02")
                        ),
                        "VOO", List.of(
                                bd("0.01"), bd("0.012"), bd("0.009"), bd("0.011"), bd("0.010")
                        )
                )
        );

        PortfolioOptimisationResult result = algorithm.optimise(request);

        BigDecimal totalWeight = result.getAssets().stream()
                .map(PortfolioOptimisationAssetResult::getTargetWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalWeight).isCloseTo(BigDecimal.ONE, within("0.000001"));
    }

    private PortfolioOptimisationRequest validRequest(
            List<String> symbols,
            Map<String, List<BigDecimal>> dailyReturns
    ) {
        Map<String, BigDecimal> currentWeights = new LinkedHashMap<>();
        BigDecimal equalWeight = BigDecimal.ONE.divide(BigDecimal.valueOf(symbols.size()), java.math.MathContext.DECIMAL64);

        for (String symbol : symbols) {
            currentWeights.put(symbol, equalWeight);
        }

        PortfolioOptimisationRequest request = new PortfolioOptimisationRequest();
        request.setAlgorithm(PortfolioAlgorithm.RISK_PARITY);
        request.setFrom(LocalDate.of(2026, 1, 1));
        request.setTo(LocalDate.of(2026, 1, 31));
        request.setRiskFreeRate(bd("0.02"));
        request.setSymbols(symbols);
        request.setCurrentWeights(currentWeights);
        request.setDailyReturns(new LinkedHashMap<>(dailyReturns));
        return request;
    }

    private BigDecimal targetWeight(PortfolioOptimisationResult result, String symbol) {
        return result.getAssets().stream()
                .filter(asset -> asset.getSymbol().equals(symbol))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Asset not found: " + symbol))
                .getTargetWeight();
    }

    private org.assertj.core.data.Offset<BigDecimal> within(String value) {
        return org.assertj.core.data.Offset.offset(new BigDecimal(value));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}