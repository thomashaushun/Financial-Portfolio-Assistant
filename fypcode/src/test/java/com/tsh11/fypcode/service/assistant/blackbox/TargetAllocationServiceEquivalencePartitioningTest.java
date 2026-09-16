package com.tsh11.fypcode.service.assistant.blackbox;

import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.dto.response.TargetAllocationResponse;
import com.tsh11.fypcode.service.assistant.TargetAllocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetAllocationServiceEquivalencePartitioningTest {

    private final TargetAllocationService service = new TargetAllocationService();

    // Tests the conservative risk profile partition.
    // It should return 40% equity, 50% bond, and 10% cash.
    @Test
    @DisplayName("Conservative partition returns defensive target allocation")
    void conservativeProfileReturnsDefensiveAllocation() {
        TargetAllocationResponse target = service.getTargetAllocation(RiskProfileType.CONSERVATIVE);

        assertTarget(target, RiskProfileType.CONSERVATIVE, "40", "50", "10");
    }

    // Tests the balanced risk profile partition.
    // It should return 60% equity, 30% bond, and 10% cash.
    @Test
    @DisplayName("Balanced partition returns 60/30/10 target allocation")
    void balancedProfileReturnsBalancedAllocation() {
        TargetAllocationResponse target = service.getTargetAllocation(RiskProfileType.BALANCED);

        assertTarget(target, RiskProfileType.BALANCED, "60", "30", "10");
    }

    // Tests the growth risk profile partition.
    // It should return 75% equity, 20% bond, and 5% cash.
    @Test
    @DisplayName("Growth partition returns growth-oriented target allocation")
    void growthProfileReturnsGrowthAllocation() {
        TargetAllocationResponse target = service.getTargetAllocation(RiskProfileType.GROWTH);

        assertTarget(target, RiskProfileType.GROWTH, "75", "20", "5");
    }

    // Tests the aggressive risk profile partition.
    // It should return 90% equity, 10% bond, and 0% cash.
    @Test
    @DisplayName("Aggressive partition returns highest equity target allocation")
    void aggressiveProfileReturnsAggressiveAllocation() {
        TargetAllocationResponse target = service.getTargetAllocation(RiskProfileType.AGGRESSIVE);

        assertTarget(target, RiskProfileType.AGGRESSIVE, "90", "10", "0");
    }

    // Tests the invalid input partition.
    // A null risk profile should be rejected with `IllegalArgumentException`.
    @Test
    @DisplayName("Invalid partition: null risk profile is rejected")
    void nullRiskProfileIsRejected() {
        assertThatThrownBy(() -> service.getTargetAllocation(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Risk profile type");
    }

    private void assertTarget(TargetAllocationResponse target,
                              RiskProfileType expectedType,
                              String expectedEquity,
                              String expectedBond,
                              String expectedCash) {
        assertThat(target.getRiskProfileType()).isEqualTo(expectedType);
        assertThat(target.getEquityPercent()).isEqualByComparingTo(new BigDecimal(expectedEquity));
        assertThat(target.getBondPercent()).isEqualByComparingTo(new BigDecimal(expectedBond));
        assertThat(target.getCashPercent()).isEqualByComparingTo(new BigDecimal(expectedCash));
    }
}
