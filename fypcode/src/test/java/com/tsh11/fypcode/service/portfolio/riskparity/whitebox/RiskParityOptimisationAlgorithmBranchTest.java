package com.tsh11.fypcode.service.portfolio.riskparity.whitebox;

import com.tsh11.fypcode.dto.PortfolioAlgorithm;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskParityOptimisationAlgorithmBranchTest {

    private RiskParityOptimisationAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new RiskParityOptimisationAlgorithm(
                new PortfolioStatisticsCalculator(),
                new RebalanceSuggestionCalculator()
        );
    }

    /*
    This gives the algorithm a valid request.

    Expected result:

    result is not null
    algorithm = RISK_PARITY
    current portfolio metrics exist
    optimised/risk-parity metrics exist
    asset result rows exist

    Purpose:

    Confirms the normal successful branch of the algorithm works.
     */
    @Test
    void validRequestBranch_shouldReturnResult() {
        PortfolioOptimisationRequest request = validRequest();
        request.setRiskFreeRate(bd("0.02"));

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result).isNotNull();
        assertThat(result.getAlgorithm()).isEqualTo(PortfolioAlgorithm.RISK_PARITY);
        assertThat(result.getCurrentPortfolio()).isNotNull();
        assertThat(result.getOptimisedPortfolio()).isNotNull();
        assertThat(result.getAssets()).hasSize(2);
    }

    /*
    This creates a valid request but sets:

    riskFreeRate = null

    Expected result:

    algorithm still returns a result
    Sharpe ratio is calculated safely

    Purpose:

    Confirms the algorithm can still calculate metrics when the risk-free rate is missing.
     */
    @Test
    void nullRiskFreeRateBranch_shouldStillReturnResult() {
        PortfolioOptimisationRequest request = validRequest();
        request.setRiskFreeRate(null);

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result).isNotNull();
        assertThat(result.getOptimisedPortfolio()).isNotNull();
        assertThat(result.getOptimisedPortfolio().getSharpeRatio()).isNotNull();
    }

    /*
    This creates a request where one asset has zero volatility.

    Expected result:

    IllegalArgumentException:
    Risk parity cannot be calculated because AAPL has zero or invalid volatility.

    Purpose:

    Confirms the zero-volatility error branch is executed and gives a clear message.
     */
    @Test
    void zeroVolatilityBranch_shouldThrowClearException() {
        PortfolioOptimisationRequest request = validRequest();
        request.setDailyReturns(Map.of(
                "AAPL", List.of(bd("0.01"), bd("0.01"), bd("0.01")),
                "SPY", List.of(bd("0.02"), bd("-0.015"), bd("0.025"))
        ));

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Risk parity cannot be calculated because AAPL has zero or invalid volatility.");
    }

    /*
    This uses current weights that are deliberately far from the Risk Parity target weights.

    Example:

    Current:
    AAPL = 80%
    SPY  = 20%

    The algorithm calculates Risk Parity target weights, then calls the suggestion calculator.

    Expected result:

    suggestions list is not empty
    suggestions mention allocation changes

    Purpose:

    Confirms the branch where material allocation differences create rebalance suggestions.
     */
    @Test
    void materialWeightDifferenceBranch_shouldCreateSuggestions() {
        PortfolioOptimisationRequest request = validRequest();

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result.getSuggestions()).isNotEmpty();
        assertThat(result.getSuggestions())
                .anySatisfy(suggestion -> assertThat(suggestion)
                        .contains("allocation"));
    }

    private PortfolioOptimisationRequest validRequest() {
        PortfolioOptimisationRequest request = new PortfolioOptimisationRequest();
        request.setAlgorithm(PortfolioAlgorithm.RISK_PARITY);
        request.setFrom(LocalDate.of(2026, 1, 1));
        request.setTo(LocalDate.of(2026, 1, 31));
        request.setRiskFreeRate(bd("0.02"));
        request.setSymbols(List.of("AAPL", "SPY"));

        Map<String, BigDecimal> currentWeights = new LinkedHashMap<>();
        currentWeights.put("AAPL", bd("0.8"));
        currentWeights.put("SPY", bd("0.2"));
        request.setCurrentWeights(currentWeights);

        Map<String, List<BigDecimal>> dailyReturns = new LinkedHashMap<>();
        dailyReturns.put("AAPL", List.of(
                bd("0.08"), bd("-0.06"), bd("0.07"), bd("-0.05"), bd("0.06")
        ));
        dailyReturns.put("SPY", List.of(
                bd("0.01"), bd("-0.008"), bd("0.012"), bd("-0.006"), bd("0.011")
        ));
        request.setDailyReturns(dailyReturns);

        return request;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}