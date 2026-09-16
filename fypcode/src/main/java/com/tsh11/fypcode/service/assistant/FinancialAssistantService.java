package com.tsh11.fypcode.service.assistant;

import com.tsh11.fypcode.domain.investor.InsightSeverity;
import com.tsh11.fypcode.domain.investor.InvestorProfile;
import com.tsh11.fypcode.dto.response.ActualAllocationResponse;
import com.tsh11.fypcode.dto.response.AllocationComparisonResponse;
import com.tsh11.fypcode.dto.response.AssistantInsightResponse;
import com.tsh11.fypcode.dto.response.FinancialAssistantResponse;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.dto.response.TargetAllocationResponse;
import com.tsh11.fypcode.service.HoldingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
//Coordinates the whole assistant workflow.
/*
Get investor profile
↓
If missing, return profileExists = false
↓
Get target allocation from RiskProfileType
↓
Get holdings
↓
Calculate actual allocation
↓
Compare target vs actual
↓
Generate assistant insights
↓
Return FinancialAssistantResponse
 */
@Service
@Transactional(readOnly = true)
public class FinancialAssistantService {

    private static final String DISCLAIMER = "This assistant provides rule-based educational portfolio guidance only. It does not provide regulated financial advice.";

    //service dependencies
    private final InvestorProfileService investorProfileService;
    private final HoldingService holdingService;
    private final TargetAllocationService targetAllocationService;
    private final SuitabilityAnalysisService suitabilityAnalysisService;
    private final AssistantInsightService assistantInsightService;
    //constructor
    public FinancialAssistantService(InvestorProfileService investorProfileService,
                                     HoldingService holdingService,
                                     TargetAllocationService targetAllocationService,
                                     SuitabilityAnalysisService suitabilityAnalysisService,
                                     AssistantInsightService assistantInsightService) {
        this.investorProfileService = investorProfileService;
        this.holdingService = holdingService;
        this.targetAllocationService = targetAllocationService;
        this.suitabilityAnalysisService = suitabilityAnalysisService;
        this.assistantInsightService = assistantInsightService;
    }
    //main method, Build the full assistant response for the logged-in user.
    public FinancialAssistantResponse getAssistantSummary(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null.");
        }
        //create response object
        FinancialAssistantResponse response = new FinancialAssistantResponse();
        response.setDisclaimer(DISCLAIMER);
        //Load investor profile
        //If the profile exists, assign it to profile. If not, assign null.
        InvestorProfile profile = investorProfileService.findEntityForUser(userId).orElse(null);
        //Missing profile branch
        if (profile == null) {
            response.setProfileExists(false);
            response.getInsights().add(new AssistantInsightResponse(
                    InsightSeverity.INFO,
                    "Create an investor profile",
                    "Complete the investor profile questionnaire to receive personalised suitability insights."
            ));
            return response;
        }
        //if profile exists:
        //loads the user’s current holdings,target allocation, actual allocation, allocation comparison
        List<HoldingResponse> holdings = holdingService.getHoldings(userId);
        TargetAllocationResponse targetAllocation = targetAllocationService.getTargetAllocation(profile.getRiskProfileType());
        ActualAllocationResponse actualAllocation = suitabilityAnalysisService.calculateActualAllocation(holdings);
        List<AllocationComparisonResponse> comparisons = suitabilityAnalysisService.compare(targetAllocation, actualAllocation);
        //fill the response
        response.setProfileExists(true);
        response.setRiskProfileType(profile.getRiskProfileType());
        response.setRiskScore(profile.getRiskScore());
        response.setTargetAllocation(targetAllocation);
        response.setActualAllocation(actualAllocation);
        response.setComparisons(comparisons);
        response.setInsights(assistantInsightService.buildInsights(
                profile.getRiskProfileType(),
                comparisons,
                actualAllocation
        ));

        return response;
    }
}
