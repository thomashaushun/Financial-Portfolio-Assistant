package com.tsh11.fypcode.service.portfolio.riskparity.blackbox;

import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioOptimisationRequest;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskParityOptimisationAlgorithmBoundaryValueAnalysisTest {

    private RiskParityOptimisationAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new RiskParityOptimisationAlgorithm(
                new PortfolioStatisticsCalculator(),
                new RebalanceSuggestionCalculator()
        );
    }

    /*
    This file tests edge cases and invalid inputs.

    nullRequest_shouldThrowException

    This passes:

    algorithm.optimise(null)

    Expected result:

    IllegalArgumentException:
    Portfolio optimisation request must not be null.

    Purpose:

    Confirms the algorithm rejects a completely missing request instead of causing a null pointer error.
     */
    @Test
    void nullRequest_shouldThrowException() {
        assertThatThrownBy(() -> algorithm.optimise(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Portfolio optimisation request must not be null.");
    }

    /*
    This creates a request where:

    symbols = empty list

    Expected result:

    IllegalArgumentException:
    At least one asset is required.

    Purpose:

    Confirms the algorithm does not try to optimise a portfolio with no assets.
     */
    @Test
    void emptySymbols_shouldThrowException() {
        PortfolioOptimisationRequest request = baseRequest();
        request.setSymbols(List.of());

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one asset is required.");
    }

    /*
    This creates a request where:

    currentWeights = null

    Expected result:

    IllegalArgumentException:
    Current weights are required.

    Purpose:

    Confirms current portfolio weights are required before comparing current allocation against Risk Parity target allocation.
     */
    @Test
    void missingCurrentWeights_shouldThrowException() {
        PortfolioOptimisationRequest request = baseRequest();
        request.setCurrentWeights(null);

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current weights are required.");
    }

    /*
    This creates a request where:

    currentWeights = empty map

    Expected result:

    IllegalArgumentException:
    Current weights are required.

    Purpose:

    Confirms an empty current-weight map is treated as invalid.
     */
    @Test
    void emptyCurrentWeights_shouldThrowException() {
        PortfolioOptimisationRequest request = baseRequest();
        request.setCurrentWeights(Map.of());

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current weights are required.");
    }

    /*
    This creates a request where:

    dailyReturns = null

    Expected result:

    IllegalArgumentException:
    Historical return series are required.

    Purpose:

    Confirms Risk Parity cannot run without historical return data because volatility is calculated from daily returns.
     */
    @Test
    void missingDailyReturns_shouldThrowException() {
        PortfolioOptimisationRequest request = baseRequest();
        request.setDailyReturns(null);

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Historical return series are required.");
    }

    /*
    This creates a request where:

    dailyReturns = empty map

    Expected result:

    IllegalArgumentException:
    Historical return series are required.

    Purpose:

    Confirms an empty historical return dataset is invalid.
     */
    @Test
    void emptyDailyReturns_shouldThrowException() {
        PortfolioOptimisationRequest request = baseRequest();
        request.setDailyReturns(Map.of());

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Historical return series are required.");
    }

    /*
    This creates an asset with identical returns:

    AAPL returns = [0.01, 0.01, 0.01]

    Because all values are the same, volatility is zero.

    Expected result:

    IllegalArgumentException:
    Risk parity cannot be calculated because AAPL has zero or invalid volatility.

    Purpose:

    Confirms the algorithm safely handles zero volatility.
    This matters because Risk Parity calculates 1 / volatility, and division by zero is invalid.
     */
    @Test
    void zeroVolatilityAsset_shouldThrowException() {
        PortfolioOptimisationRequest request = baseRequest();
        request.setSymbols(List.of("AAPL", "SPY"));
        request.setCurrentWeights(Map.of(
                "AAPL", bd("0.5"),
                "SPY", bd("0.5")
        ));
        request.setDailyReturns(Map.of(
                "AAPL", List.of(bd("0.01"), bd("0.01"), bd("0.01")),
                "SPY", List.of(bd("0.01"), bd("-0.01"), bd("0.02"))
        ));

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Risk parity cannot be calculated because AAPL has zero or invalid volatility.");
    }

    /*
    This creates an asset with only one return value:

    AAPL returns = [0.01]

    Volatility cannot be calculated meaningfully from one data point.

    Expected result:

    IllegalArgumentException:
    Risk parity cannot be calculated because AAPL has zero or invalid volatility.

    Purpose:

    Confirms the algorithm rejects insufficient return history.
     */
    @Test
    void singleReturnPoint_shouldThrowExceptionBecauseVolatilityCannotBeCalculated() {
        PortfolioOptimisationRequest request = baseRequest();
        request.setSymbols(List.of("AAPL", "SPY"));
        request.setCurrentWeights(Map.of(
                "AAPL", bd("0.5"),
                "SPY", bd("0.5")
        ));
        request.setDailyReturns(Map.of(
                "AAPL", List.of(bd("0.01")),
                "SPY", List.of(bd("0.01"), bd("-0.01"), bd("0.02"))
        ));

        assertThatThrownBy(() -> algorithm.optimise(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Risk parity cannot be calculated because AAPL has zero or invalid volatility.");
    }

    private PortfolioOptimisationRequest baseRequest() {
        PortfolioOptimisationRequest request = new PortfolioOptimisationRequest();
        request.setAlgorithm(PortfolioAlgorithm.RISK_PARITY);
        request.setFrom(LocalDate.of(2026, 1, 1));
        request.setTo(LocalDate.of(2026, 1, 31));
        request.setRiskFreeRate(bd("0.02"));
        request.setSymbols(List.of("AAPL", "SPY"));

        Map<String, BigDecimal> currentWeights = new LinkedHashMap<>();
        currentWeights.put("AAPL", bd("0.5"));
        currentWeights.put("SPY", bd("0.5"));
        request.setCurrentWeights(currentWeights);

        Map<String, List<BigDecimal>> dailyReturns = new LinkedHashMap<>();
        dailyReturns.put("AAPL", List.of(bd("0.01"), bd("-0.01"), bd("0.02")));
        dailyReturns.put("SPY", List.of(bd("0.02"), bd("-0.015"), bd("0.025")));
        request.setDailyReturns(dailyReturns);

        return request;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}