package com.tsh11.fypcode.service.assistant.blackbox;

import com.tsh11.fypcode.dto.response.ActualAllocationResponse;
import com.tsh11.fypcode.dto.response.AllocationComparisonResponse;
import com.tsh11.fypcode.dto.response.TargetAllocationResponse;
import com.tsh11.fypcode.service.assistant.AssistantAssetClassMapper;
import com.tsh11.fypcode.service.assistant.SuitabilityAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuitabilityAnalysisServiceBoundaryValueAnalysisTest {

    private final SuitabilityAnalysisService service = new SuitabilityAnalysisService(new AssistantAssetClassMapper());

    // Tests the lower allocation drift boundary.
    // A 4.99% difference should still be within the target range.
    @Test
    @DisplayName("Drift below 5% is within target range")
    void driftBelowFivePercentIsWithinTargetRange() {
        AllocationComparisonResponse equity = equityComparison("60", "64.99");

        assertThat(equity.getDifferencePercent()).isEqualByComparingTo(new BigDecimal("4.99"));
        assertThat(equity.getStatus()).isEqualTo("WITHIN_TARGET_RANGE");
    }

    // Tests the 5% boundary.
    // A 5% overweight or underweight difference should become a slight mismatch.
    @Test
    @DisplayName("Drift at 5% is slight overweight or underweight")
    void driftAtFivePercentIsSlightMismatch() {
        AllocationComparisonResponse overweight = equityComparison("60", "65");
        AllocationComparisonResponse underweight = equityComparison("60", "55");

        assertThat(overweight.getStatus()).isEqualTo("SLIGHTLY_OVERWEIGHT");
        assertThat(underweight.getStatus()).isEqualTo("SLIGHTLY_UNDERWEIGHT");
    }

    //  Tests the upper slight-drift boundary.
    //  A 14.99% difference should still be slight mismatch.
    @Test
    @DisplayName("Drift below 15% remains slight mismatch")
    void driftBelowFifteenPercentRemainsSlightMismatch() {
        AllocationComparisonResponse equity = equityComparison("60", "74.99");

        assertThat(equity.getDifferencePercent()).isEqualByComparingTo(new BigDecimal("14.99"));
        assertThat(equity.getStatus()).isEqualTo("SLIGHTLY_OVERWEIGHT");
    }

    // Tests the 15% boundary.
    // A 15% overweight or underweight difference should become a significant mismatch.
    @Test
    @DisplayName("Drift at 15% becomes significant mismatch")
    void driftAtFifteenPercentIsSignificantMismatch() {
        AllocationComparisonResponse overweight = equityComparison("60", "75");
        AllocationComparisonResponse underweight = equityComparison("60", "45");

        assertThat(overweight.getStatus()).isEqualTo("SIGNIFICANTLY_OVERWEIGHT");
        assertThat(underweight.getStatus()).isEqualTo("SIGNIFICANTLY_UNDERWEIGHT");
    }

    private AllocationComparisonResponse equityComparison(String targetEquity, String actualEquity) {
        TargetAllocationResponse target = new TargetAllocationResponse();
        target.setEquityPercent(new BigDecimal(targetEquity));
        target.setBondPercent(BigDecimal.ZERO);
        target.setCashPercent(BigDecimal.ZERO);

        ActualAllocationResponse actual = new ActualAllocationResponse();
        actual.setEquityPercent(new BigDecimal(actualEquity));
        actual.setBondPercent(BigDecimal.ZERO);
        actual.setCashPercent(BigDecimal.ZERO);

        List<AllocationComparisonResponse> comparisons = service.compare(target, actual);
        return comparisons.stream()
                .filter(comparison -> "EQUITY".equals(comparison.getBucket()))
                .findFirst()
                .orElseThrow();
    }
}
