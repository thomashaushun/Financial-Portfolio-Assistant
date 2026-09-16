package com.tsh11.fypcode.service.assistant.whitebox;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskAssessmentServiceBranchTest {

    private final RiskAssessmentService service = new RiskAssessmentService();

    // Covers the null request branch in `calculateRiskScore`.
    // A null request should throw `IllegalArgumentException`.
    @Test
    @DisplayName("Null request branch throws IllegalArgumentException")
    void nullRequestBranchThrowsException() {
        assertThatThrownBy(() -> service.calculateRiskScore(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Investor profile request");
    }

    // Covers all age scoring branches: under 30, 30-44, 45-59, and 60+.
    @Test
    @DisplayName("Age scoring branches decrease score as age increases")
    void ageScoringBranchesAreCovered() {
        int underThirty = service.calculateRiskScore(baseProfileWithAge(29));
        int thirtyToFortyFour = service.calculateRiskScore(baseProfileWithAge(30));
        int fortyFiveToFiftyNine = service.calculateRiskScore(baseProfileWithAge(45));
        int sixtyPlus = service.calculateRiskScore(baseProfileWithAge(60));

        assertThat(underThirty).isGreaterThan(thirtyToFortyFour);
        assertThat(thirtyToFortyFour).isGreaterThan(fortyFiveToFiftyNine);
        assertThat(fortyFiveToFiftyNine).isGreaterThan(sixtyPlus);
    }

    // Covers all investment horizon scoring branches: <=2 years, <=5 years, <=10 years, and >10 years.
    @Test
    @DisplayName("Investment horizon branches increase score as horizon increases")
    void horizonScoringBranchesAreCovered() {
        int shortTerm = service.calculateRiskScore(baseProfileWithHorizon(2));
        int mediumShort = service.calculateRiskScore(baseProfileWithHorizon(5));
        int mediumLong = service.calculateRiskScore(baseProfileWithHorizon(10));
        int longTerm = service.calculateRiskScore(baseProfileWithHorizon(11));

        assertThat(shortTerm).isLessThan(mediumShort);
        assertThat(mediumShort).isLessThan(mediumLong);
        assertThat(mediumLong).isLessThan(longTerm);
    }

    // Covers the null branches for risk tolerance, investment experience, income stability, and investment goal.
    // These should contribute zero rather than crashing.
    @Test
    @DisplayName("Null enum input branches contribute zero instead of crashing")
    void nullEnumBranchesContributeZero() {
        InvestorProfileRequest request = new InvestorProfileRequest();
        request.setAge(30);
        request.setInvestmentHorizonYears(5);
        request.setRiskTolerance(null);
        request.setInvestmentExperience(null);
        request.setIncomeStability(null);
        request.setInvestmentGoal(null);

        int score = service.calculateRiskScore(request);

        assertThat(score).isEqualTo(20); // age 12 + horizon 8 + all nullable enum branches 0
        assertThat(service.classify(score)).isEqualTo(RiskProfileType.CONSERVATIVE);
    }

    private InvestorProfileRequest baseProfileWithAge(int age) {
        InvestorProfileRequest request = baseProfile();
        request.setAge(age);
        return request;
    }

    private InvestorProfileRequest baseProfileWithHorizon(int horizon) {
        InvestorProfileRequest request = baseProfile();
        request.setInvestmentHorizonYears(horizon);
        return request;
    }

    private InvestorProfileRequest baseProfile() {
        InvestorProfileRequest request = new InvestorProfileRequest();
        request.setAge(35);
        request.setInvestmentHorizonYears(6);
        request.setRiskTolerance(RiskToleranceLevel.MEDIUM);
        request.setInvestmentExperience(InvestmentExperienceLevel.INTERMEDIATE);
        request.setIncomeStability(IncomeStabilityLevel.MEDIUM);
        request.setInvestmentGoal(InvestmentGoal.BALANCED_GROWTH);
        return request;
    }
}
