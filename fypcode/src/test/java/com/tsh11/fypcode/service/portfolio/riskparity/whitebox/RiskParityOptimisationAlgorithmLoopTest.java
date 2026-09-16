package com.tsh11.fypcode.service.portfolio.riskparity.whitebox;

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

//This file tests loops over:
//
//multiple assets
//multiple return observations
class RiskParityOptimisationAlgorithmLoopTest {

    private RiskParityOptimisationAlgorithm algorithm;

    @BeforeEach
    void setUp() {
        algorithm = new RiskParityOptimisationAlgorithm(
                new PortfolioStatisticsCalculator(),
                new RebalanceSuggestionCalculator()
        );
    }

    //This creates a two-asset portfolio:
    //AAPL
    //SPY
    //
    //Expected result:
    //result has 2 asset rows
    //asset order is AAPL, SPY
    //
    //Purpose:
    //Confirms the algorithm loops through and processes both assets.
    @Test
    void twoAssetPortfolio_shouldProcessBothAssets() {
        PortfolioOptimisationRequest request = requestForSymbols(
                List.of("AAPL", "SPY"),
                Map.of(
                        "AAPL", List.of(bd("0.05"), bd("-0.04"), bd("0.06"), bd("-0.03")),
                        "SPY", List.of(bd("0.01"), bd("-0.008"), bd("0.012"), bd("-0.006"))
                )
        );

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result.getAssets()).hasSize(2);
        assertThat(result.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("AAPL", "SPY");
    }

    //This creates a three-asset portfolio:
    //AAPL
    //SPY
    //VOO
    //
    //Expected result:
    //result has 3 asset rows
    //asset order is AAPL, SPY, VOO
    //target weights sum to 1
    //
    //Purpose:
    //Confirms the algorithm can process more than two assets and still normalises all weights correctly.
    @Test
    void threeAssetPortfolio_shouldProcessEveryAssetAndNormaliseWeights() {
        PortfolioOptimisationRequest request = requestForSymbols(
                List.of("AAPL", "SPY", "VOO"),
                Map.of(
                        "AAPL", List.of(bd("0.06"), bd("-0.05"), bd("0.07"), bd("-0.04"), bd("0.05")),
                        "SPY", List.of(bd("0.03"), bd("-0.02"), bd("0.025"), bd("-0.015"), bd("0.02")),
                        "VOO", List.of(bd("0.01"), bd("-0.005"), bd("0.012"), bd("-0.004"), bd("0.011"))
                )
        );

        PortfolioOptimisationResult result = algorithm.optimise(request);

        assertThat(result.getAssets()).hasSize(3);
        assertThat(result.getAssets())
                .extracting(PortfolioOptimisationAssetResult::getSymbol)
                .containsExactly("AAPL", "SPY", "VOO");

        BigDecimal totalTargetWeight = result.getAssets().stream()
                .map(PortfolioOptimisationAssetResult::getTargetWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalTargetWeight).isCloseTo(BigDecimal.ONE, within("0.000001"));
    }

    //This compares two requests:
    //short return series
    //longer return series
    //
    //Both requests contain AAPL and SPY, where SPY is less volatile than AAPL.
    //
    //Expected result:
    //SPY target weight > AAPL target weight
    //
    //for both short and longer return series.
    //
    //Purpose:
    //Confirms the algorithm processes return series through its volatility calculation loop and consistently gives higher weight to the lower-volatility asset.
    @Test
    void multipleReturnPoints_shouldUseFullReturnSeriesInVolatilityCalculation() {
        PortfolioOptimisationRequest shortSeriesRequest = requestForSymbols(
                List.of("AAPL", "SPY"),
                Map.of(
                        "AAPL", List.of(bd("0.05"), bd("-0.05"), bd("0.05")),
                        "SPY", List.of(bd("0.01"), bd("-0.01"), bd("0.01"))
                )
        );

        PortfolioOptimisationRequest longerSeriesRequest = requestForSymbols(
                List.of("AAPL", "SPY"),
                Map.of(
                        "AAPL", List.of(
                                bd("0.05"), bd("-0.05"), bd("0.05"), bd("-0.05"), bd("0.05"), bd("-0.05")
                        ),
                        "SPY", List.of(
                                bd("0.01"), bd("-0.01"), bd("0.01"), bd("-0.01"), bd("0.01"), bd("-0.01")
                        )
                )
        );

        PortfolioOptimisationResult shortResult = algorithm.optimise(shortSeriesRequest);
        PortfolioOptimisationResult longerResult = algorithm.optimise(longerSeriesRequest);

        assertThat(shortResult.getAssets()).hasSize(2);
        assertThat(longerResult.getAssets()).hasSize(2);

        assertThat(targetWeight(shortResult, "SPY"))
                .isGreaterThan(targetWeight(shortResult, "AAPL"));

        assertThat(targetWeight(longerResult, "SPY"))
                .isGreaterThan(targetWeight(longerResult, "AAPL"));
    }

    private PortfolioOptimisationRequest requestForSymbols(
            List<String> symbols,
            Map<String, List<BigDecimal>> dailyReturns
    ) {
        PortfolioOptimisationRequest request = new PortfolioOptimisationRequest();
        request.setAlgorithm(PortfolioAlgorithm.RISK_PARITY);
        request.setFrom(LocalDate.of(2026, 1, 1));
        request.setTo(LocalDate.of(2026, 1, 31));
        request.setRiskFreeRate(bd("0.02"));
        request.setSymbols(symbols);

        Map<String, BigDecimal> currentWeights = new LinkedHashMap<>();
        BigDecimal equalWeight = BigDecimal.ONE.divide(
                BigDecimal.valueOf(symbols.size()),
                java.math.MathContext.DECIMAL64
        );

        for (String symbol : symbols) {
            currentWeights.put(symbol, equalWeight);
        }

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