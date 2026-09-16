package com.tsh11.fypcode.service.assistant.whitebox;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.dto.response.ActualAllocationResponse;
import com.tsh11.fypcode.dto.response.AllocationComparisonResponse;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.dto.response.TargetAllocationResponse;
import com.tsh11.fypcode.service.assistant.AssistantAssetClassMapper;
import com.tsh11.fypcode.service.assistant.SuitabilityAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuitabilityAnalysisServiceBranchTest {

    private final SuitabilityAnalysisService service = new SuitabilityAnalysisService(new AssistantAssetClassMapper());

    // Covers the branch where holdings are null or empty.
    // The service should return zero portfolio value.
    @Test
    @DisplayName("Null or empty holdings branch returns zero portfolio value")
    void nullOrEmptyHoldingsBranchReturnsZeroAllocation() {
        assertThat(service.calculateActualAllocation(null).getTotalPortfolioValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(service.calculateActualAllocation(List.of()).getTotalPortfolioValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Covers loop branches that skip null holdings, zero values, and negative values.
    @Test
    @DisplayName("Holding loop skips null holdings and non-positive values")
    void holdingLoopSkipsNullAndNonPositiveValues() {
        ActualAllocationResponse actual = service.calculateActualAllocation(Arrays.asList(
                null,
                holding(AssetClass.EQUITY, "0", null),
                holding(AssetClass.BOND, "-10", null),
                holding(AssetClass.CASH, "100", null)
        ));

        assertThat(actual.getTotalPortfolioValue()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(actual.getCashPercent()).isEqualByComparingTo(new BigDecimal("100.000000"));
        assertThat(actual.getEquityPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(actual.getBondPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Covers the holding value branch where market value is missing and total cost is used instead.
    @Test
    @DisplayName("Holding value branch falls back to total cost when market value is missing")
    void holdingValueFallsBackToTotalCost() {
        ActualAllocationResponse actual = service.calculateActualAllocation(List.of(
                holding(AssetClass.EQUITY, null, "250")
        ));

        assertThat(actual.getTotalPortfolioValue()).isEqualByComparingTo(new BigDecimal("250"));
        assertThat(actual.getEquityPercent()).isEqualByComparingTo(new BigDecimal("100.000000"));
    }

    // Covers validation branches in `compare`.
    // Null target or actual allocation should throw `IllegalArgumentException`.
    @Test
    @DisplayName("Compare branch rejects null target or actual allocation")
    void compareRejectsNullInputs() {
        ActualAllocationResponse actual = new ActualAllocationResponse();
        TargetAllocationResponse target = target("60", "30", "10");

        assertThatThrownBy(() -> service.compare(null, actual))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target allocation");

        assertThatThrownBy(() -> service.compare(target, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Actual allocation");
    }

    // Covers the three status branches: within target range, slight mismatch, and significant mismatch.
    @Test
    @DisplayName("Comparison status branches cover within, slight, and significant drift")
    void comparisonStatusBranchesAreCovered() {
        ActualAllocationResponse actual = new ActualAllocationResponse();
        actual.setEquityPercent(new BigDecimal("63"));
        actual.setBondPercent(new BigDecimal("20"));
        actual.setCashPercent(new BigDecimal("30"));

        List<AllocationComparisonResponse> comparisons = service.compare(target("60", "30", "10"), actual);

        assertThat(statusFor(comparisons, "EQUITY")).isEqualTo("WITHIN_TARGET_RANGE");
        assertThat(statusFor(comparisons, "BOND")).isEqualTo("SLIGHTLY_UNDERWEIGHT");
        assertThat(statusFor(comparisons, "CASH")).isEqualTo("SIGNIFICANTLY_OVERWEIGHT");
    }

    private TargetAllocationResponse target(String equity, String bond, String cash) {
        TargetAllocationResponse target = new TargetAllocationResponse();
        target.setEquityPercent(new BigDecimal(equity));
        target.setBondPercent(new BigDecimal(bond));
        target.setCashPercent(new BigDecimal(cash));
        return target;
    }

    private String statusFor(List<AllocationComparisonResponse> comparisons, String bucket) {
        return comparisons.stream()
                .filter(comparison -> bucket.equals(comparison.getBucket()))
                .findFirst()
                .orElseThrow()
                .getStatus();
    }

    private HoldingResponse holding(AssetClass assetClass, String marketValue, String totalCost) {
        HoldingResponse holding = new HoldingResponse();
        holding.setAssetClass(assetClass);
        if (marketValue != null) {
            holding.setMarketValue(new BigDecimal(marketValue));
        }
        if (totalCost != null) {
            holding.setTotalCost(new BigDecimal(totalCost));
        }
        return holding;
    }
}
