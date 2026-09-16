package com.tsh11.fypcode.service.assistant.blackbox;

import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.service.assistant.RiskAssessmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAssessmentServiceBoundaryValueAnalysisTest {

    private final RiskAssessmentService service = new RiskAssessmentService();

    // Tests the risk score threshold between `CONSERVATIVE` and `BALANCED`.
    // Score 30 should remain conservative; score 31 should become balanced.
    @Test
    @DisplayName("Boundary 30/31: score 30 is CONSERVATIVE and score 31 is BALANCED")
    void conservativeBalancedBoundaryIsHandledCorrectly() {
        assertThat(service.classify(30)).isEqualTo(RiskProfileType.CONSERVATIVE);
        assertThat(service.classify(31)).isEqualTo(RiskProfileType.BALANCED);
    }

    // Tests the risk score threshold between `BALANCED` and `GROWTH`.
    // Score 55 should remain balanced; score 56 should become growth.
    @Test
    @DisplayName("Boundary 55/56: score 55 is BALANCED and score 56 is GROWTH")
    void balancedGrowthBoundaryIsHandledCorrectly() {
        assertThat(service.classify(55)).isEqualTo(RiskProfileType.BALANCED);
        assertThat(service.classify(56)).isEqualTo(RiskProfileType.GROWTH);
    }

    // Tests the risk score threshold between `GROWTH` and `AGGRESSIVE`.
    // Score 80 should remain growth; score 81 should become aggressive.
    @Test
    @DisplayName("Boundary 80/81: score 80 is GROWTH and score 81 is AGGRESSIVE")
    void growthAggressiveBoundaryIsHandledCorrectly() {
        assertThat(service.classify(80)).isEqualTo(RiskProfileType.GROWTH);
        assertThat(service.classify(81)).isEqualTo(RiskProfileType.AGGRESSIVE);
    }

    //Tests extreme score inputs.
    // A score below zero is classified as conservative, while a score above 114 is classified as aggressive.
    @Test
    @DisplayName("Extreme score boundaries: negative score is conservative and score above 100 is aggressive")
    void classifyHandlesExtremeScores() {
        assertThat(service.classify(-1)).isEqualTo(RiskProfileType.CONSERVATIVE);
        assertThat(service.classify(115)).isEqualTo(RiskProfileType.AGGRESSIVE);
    }
}
