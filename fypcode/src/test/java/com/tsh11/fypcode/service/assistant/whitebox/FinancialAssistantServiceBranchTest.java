package com.tsh11.fypcode.service.assistant.whitebox;

import com.tsh11.fypcode.domain.investor.IncomeStabilityLevel;
import com.tsh11.fypcode.domain.investor.InvestmentExperienceLevel;
import com.tsh11.fypcode.domain.investor.InvestmentGoal;
import com.tsh11.fypcode.domain.investor.InvestorProfile;
import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.domain.investor.RiskToleranceLevel;
import com.tsh11.fypcode.dto.response.ActualAllocationResponse;
import com.tsh11.fypcode.dto.response.AllocationComparisonResponse;
import com.tsh11.fypcode.dto.response.AssistantInsightResponse;
import com.tsh11.fypcode.dto.response.FinancialAssistantResponse;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.dto.response.TargetAllocationResponse;
import com.tsh11.fypcode.service.assistant.AssistantInsightService;
import com.tsh11.fypcode.service.assistant.FinancialAssistantService;
import com.tsh11.fypcode.service.HoldingService;
import com.tsh11.fypcode.service.assistant.InvestorProfileService;
import com.tsh11.fypcode.service.assistant.SuitabilityAnalysisService;
import com.tsh11.fypcode.service.assistant.TargetAllocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAssistantServiceBranchTest {

    @Mock
    private InvestorProfileService investorProfileService;

    @Mock
    private HoldingService holdingService;

    @Mock
    private TargetAllocationService targetAllocationService;

    @Mock
    private SuitabilityAnalysisService suitabilityAnalysisService;

    @Mock
    private AssistantInsightService assistantInsightService;

    @InjectMocks
    private FinancialAssistantService service;

    // Covers the null user ID validation branch.
    // The service should reject the request before calling dependencies.
    @Test
    @DisplayName("Null user ID branch rejects request before dependencies are called")
    void nullUserIdBranchRejectsRequest() {
        assertThatThrownBy(() -> service.getAssistantSummary(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User ID");

        verifyNoInteractions(investorProfileService, holdingService, targetAllocationService,
                suitabilityAnalysisService, assistantInsightService);
    }

    // Covers the branch where the user has not created an investor profile.
    // The response should ask the user to create one and should not run portfolio analysis.
    @Test
    @DisplayName("Missing profile branch returns create-profile insight and skips portfolio analysis")
    void missingProfileBranchReturnsCreateProfileInsight() {
        UUID userId = UUID.randomUUID();
        when(investorProfileService.findEntityForUser(userId)).thenReturn(Optional.empty());

        FinancialAssistantResponse response = service.getAssistantSummary(userId);

        assertThat(response.isProfileExists()).isFalse();
        assertThat(response.getInsights()).hasSize(1);
        assertThat(response.getInsights().get(0).getTitle()).contains("Create an investor profile");
        assertThat(response.getDisclaimer()).contains("educational");
        verifyNoInteractions(holdingService, targetAllocationService, suitabilityAnalysisService, assistantInsightService);
    }

    // Covers the normal orchestration branch.
    // The service should load the profile, holdings, target allocation, actual allocation, comparisons, and insights, then return a complete assistant response.
    @Test
    @DisplayName("Existing profile branch orchestrates holdings, target allocation, suitability comparison, and insights")
    void existingProfileBranchBuildsCompleteAssistantResponse() {
        UUID userId = UUID.randomUUID();
        InvestorProfile profile = profile(userId, RiskProfileType.BALANCED, 50);
        List<HoldingResponse> holdings = List.of(new HoldingResponse());
        TargetAllocationResponse target = new TargetAllocationResponse();
        ActualAllocationResponse actual = new ActualAllocationResponse();
        actual.setTotalPortfolioValue(new BigDecimal("1000"));
        List<AllocationComparisonResponse> comparisons = List.of(new AllocationComparisonResponse());
        List<AssistantInsightResponse> insights = List.of(new AssistantInsightResponse());

        when(investorProfileService.findEntityForUser(userId)).thenReturn(Optional.of(profile));
        when(holdingService.getHoldings(userId)).thenReturn(holdings);
        when(targetAllocationService.getTargetAllocation(RiskProfileType.BALANCED)).thenReturn(target);
        when(suitabilityAnalysisService.calculateActualAllocation(holdings)).thenReturn(actual);
        when(suitabilityAnalysisService.compare(target, actual)).thenReturn(comparisons);
        when(assistantInsightService.buildInsights(RiskProfileType.BALANCED, comparisons, actual)).thenReturn(insights);

        FinancialAssistantResponse response = service.getAssistantSummary(userId);

        assertThat(response.isProfileExists()).isTrue();
        assertThat(response.getRiskProfileType()).isEqualTo(RiskProfileType.BALANCED);
        assertThat(response.getRiskScore()).isEqualTo(50);
        assertThat(response.getTargetAllocation()).isSameAs(target);
        assertThat(response.getActualAllocation()).isSameAs(actual);
        assertThat(response.getComparisons()).isSameAs(comparisons);
        assertThat(response.getInsights()).isSameAs(insights);
        assertThat(response.getDisclaimer()).contains("regulated financial advice");

        verify(holdingService).getHoldings(userId);
        verify(targetAllocationService).getTargetAllocation(RiskProfileType.BALANCED);
        verify(suitabilityAnalysisService).calculateActualAllocation(holdings);
        verify(suitabilityAnalysisService).compare(target, actual);
        verify(assistantInsightService).buildInsights(RiskProfileType.BALANCED, comparisons, actual);
    }

    private InvestorProfile profile(UUID userId, RiskProfileType riskProfileType, int riskScore) {
        InvestorProfile profile = new InvestorProfile();
        profile.setUserId(userId);
        profile.setAge(35);
        profile.setInvestmentHorizonYears(8);
        profile.setRiskTolerance(RiskToleranceLevel.MEDIUM);
        profile.setInvestmentExperience(InvestmentExperienceLevel.INTERMEDIATE);
        profile.setIncomeStability(IncomeStabilityLevel.MEDIUM);
        profile.setInvestmentGoal(InvestmentGoal.BALANCED_GROWTH);
        profile.setRiskProfileType(riskProfileType);
        profile.setRiskScore(riskScore);
        return profile;
    }
}
