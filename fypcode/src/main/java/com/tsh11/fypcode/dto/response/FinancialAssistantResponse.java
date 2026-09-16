package com.tsh11.fypcode.dto.response;

import com.tsh11.fypcode.domain.investor.RiskProfileType;

import java.util.ArrayList;
import java.util.List;

//the main combined response returned by:
//GET /api/portfolio/assistant
//gives the frontend everything it needs to display the Financial Consultant Assistant card on the overview page.

//included informations:
//whether profile exists
//risk profile
//target allocation
//actual allocation
//allocation comparisons
//assistant insights

//example JSON:
/*
{
  "profileExists": true,
  "riskProfile": "BALANCED",
  "targetAllocation": {
    "equityPercent": 60,
    "bondPercent": 30,
    "cashPercent": 10
  },
  "actualAllocation": {
    "equityPercent": 75,
    "bondPercent": 0,
    "cashPercent": 5,
    "cryptoPercent": 20
  },
  "insights": [
    {
      "severity": "WARNING",
      "message": "Your bond allocation is below the target for your profile."
    }
  ]
}
*/

public class FinancialAssistantResponse {
    private boolean profileExists;
    private RiskProfileType riskProfileType;
    private int riskScore;
    private TargetAllocationResponse targetAllocation;
    private ActualAllocationResponse actualAllocation;
    private List<AllocationComparisonResponse> comparisons = new ArrayList<>();
    private List<AssistantInsightResponse> insights = new ArrayList<>();
    private String disclaimer;

    public boolean isProfileExists() {
        return profileExists;
    }

    public void setProfileExists(boolean profileExists) {
        this.profileExists = profileExists;
    }

    public RiskProfileType getRiskProfileType() {
        return riskProfileType;
    }

    public void setRiskProfileType(RiskProfileType riskProfileType) {
        this.riskProfileType = riskProfileType;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public TargetAllocationResponse getTargetAllocation() {
        return targetAllocation;
    }

    public void setTargetAllocation(TargetAllocationResponse targetAllocation) {
        this.targetAllocation = targetAllocation;
    }

    public ActualAllocationResponse getActualAllocation() {
        return actualAllocation;
    }

    public void setActualAllocation(ActualAllocationResponse actualAllocation) {
        this.actualAllocation = actualAllocation;
    }

    public List<AllocationComparisonResponse> getComparisons() {
        return comparisons;
    }

    public void setComparisons(List<AllocationComparisonResponse> comparisons) {
        this.comparisons = comparisons;
    }

    public List<AssistantInsightResponse> getInsights() {
        return insights;
    }

    public void setInsights(List<AssistantInsightResponse> insights) {
        this.insights = insights;
    }

    public String getDisclaimer() {
        return disclaimer;
    }

    public void setDisclaimer(String disclaimer) {
        this.disclaimer = disclaimer;
    }
}
