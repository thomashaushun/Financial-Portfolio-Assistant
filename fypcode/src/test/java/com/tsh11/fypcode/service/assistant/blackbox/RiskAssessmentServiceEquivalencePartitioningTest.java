package com.tsh11.fypcode.service.assistant.blackbox;

import com.tsh11.fypcode.domain.investor.IncomeStabilityLevel;
import com.tsh11.fypcode.domain.investor.InvestmentExperienceLevel;
import com.tsh11.fypcode.domain.investor.InvestmentGoal;
import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.domain.investor.RiskToleranceLevel;
import com.tsh11.fypcode.dto.request.InvestorProfileRequest;
import com.tsh11.fypcode.service.assistant.RiskAssessmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiskAssessmentServiceEquivalencePartitioningTest {

    private final RiskAssessmentService service = new RiskAssessmentService();

    // Tests the conservative equivalence partition.
    // A cautious user with older age, short horizon, low risk tolerance, beginner experience,
    // low income stability, and capital preservation goal should be classified as CONSERVATIVE.
    @Test
    @DisplayName("Conservative partition: cautious profile is classified as CONSERVATIVE")
    void cautiousProfileIsClassifiedAsConservative() {
        InvestorProfileRequest request = profile(
                65,
                1,
                RiskToleranceLevel.LOW,
                InvestmentExperienceLevel.BEGINNER,
                IncomeStabilityLevel.LOW,
                InvestmentGoal.CAPITAL_PRESERVATION
        );

        RiskProfileType result = service.classify(request);

        assertThat(result).isEqualTo(RiskProfileType.CONSERVATIVE);
    }

    // Tests the balanced equivalence partition.
    // A medium-risk user with moderate horizon, medium tolerance, intermediate experience,
    // medium income stability, and balanced growth goal should be classified as BALANCED.
    @Test
    @DisplayName("Balanced partition: moderate profile is classified as BALANCED")
    void moderateProfileIsClassifiedAsBalanced() {
        InvestorProfileRequest request = profile(
                40,
                5,
                RiskToleranceLevel.MEDIUM,
                InvestmentExperienceLevel.INTERMEDIATE,
                IncomeStabilityLevel.MEDIUM,
                InvestmentGoal.BALANCED_GROWTH
        );

        RiskProfileType result = service.classify(request);

        assertThat(result).isEqualTo(RiskProfileType.BALANCED);
    }

    // Tests the growth equivalence partition.
    // A younger user with longer horizon, high tolerance, intermediate experience,
    // stable income, and long-term growth goal should be classified as GROWTH.
    @Test
    @DisplayName("Growth partition: long-term growth profile is classified as GROWTH")
    void longTermGrowthProfileIsClassifiedAsGrowth() {
        InvestorProfileRequest request = profile(
                28,
                12,
                RiskToleranceLevel.MEDIUM,
                InvestmentExperienceLevel.INTERMEDIATE,
                IncomeStabilityLevel.HIGH,
                InvestmentGoal.LONG_TERM_GROWTH
        );

        RiskProfileType result = service.classify(request);

        assertThat(result).isEqualTo(RiskProfileType.GROWTH);
    }

    // Tests the aggressive equivalence partition.
    // A young user with very long horizon, very high risk tolerance, advanced experience,
    // high income stability, and long-term growth goal should be classified as AGGRESSIVE.
    @Test
    @DisplayName("Aggressive partition: high-risk long-term profile is classified as AGGRESSIVE")
    void highRiskLongTermProfileIsClassifiedAsAggressive() {
        InvestorProfileRequest request = profile(
                22,
                25,
                RiskToleranceLevel.VERY_HIGH,
                InvestmentExperienceLevel.ADVANCED,
                IncomeStabilityLevel.HIGH,
                InvestmentGoal.LONG_TERM_GROWTH
        );

        RiskProfileType result = service.classify(request);

        assertThat(result).isEqualTo(RiskProfileType.AGGRESSIVE);
    }

    private InvestorProfileRequest profile(int age,
                                           int horizon,
                                           RiskToleranceLevel tolerance,
                                           InvestmentExperienceLevel experience,
                                           IncomeStabilityLevel incomeStability,
                                           InvestmentGoal goal) {
        InvestorProfileRequest request = new InvestorProfileRequest();
        request.setAge(age);
        request.setInvestmentHorizonYears(horizon);
        request.setRiskTolerance(tolerance);
        request.setInvestmentExperience(experience);
        request.setIncomeStability(incomeStability);
        request.setInvestmentGoal(goal);
        return request;
    }
}