package com.tsh11.fypcode.service.assistant;

import com.tsh11.fypcode.domain.investor.InsightSeverity;
import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.dto.response.ActualAllocationResponse;
import com.tsh11.fypcode.dto.response.AllocationComparisonResponse;
import com.tsh11.fypcode.dto.response.AssistantInsightResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
//Turns suitability analysis results into human-readable insights.
//input:
//Bond target = 30%
//Bond actual = 0%
//Difference = -30%
//output:
//Your portfolio has no bond exposure, which may make it less defensive than the target allocation for your profile.
@Service
public class AssistantInsightService {

    private static final BigDecimal ALTERNATIVE_NOTICE_THRESHOLD = BigDecimal.valueOf(10);
    private static final BigDecimal UNCLASSIFIED_NOTICE_THRESHOLD = BigDecimal.valueOf(5);

    public List<AssistantInsightResponse> buildInsights(RiskProfileType riskProfileType,
                                                         List<AllocationComparisonResponse> comparisons,
                                                         ActualAllocationResponse actualAllocation) {
        List<AssistantInsightResponse> insights = new ArrayList<>();

        if (actualAllocation == null || actualAllocation.getTotalPortfolioValue().compareTo(BigDecimal.ZERO) <= 0) {
            insights.add(new AssistantInsightResponse(
                    InsightSeverity.INFO,
                    "No portfolio value available",
                    "Add activities and asset profiles so the assistant can compare your portfolio with your investor profile."
            ));
            return insights;
        }

        boolean hasMajorTraditionalMismatch = false;

        if (comparisons != null) {
            for (AllocationComparisonResponse comparison : comparisons) {
                if (comparison == null || "WITHIN_TARGET_RANGE".equals(comparison.getStatus())) {
                    continue;
                }

                InsightSeverity severity = comparison.getStatus().startsWith("SIGNIFICANTLY")
                        ? InsightSeverity.WARNING
                        : InsightSeverity.INFO;

                if (severity == InsightSeverity.WARNING) {
                    hasMajorTraditionalMismatch = true;
                }

                insights.add(new AssistantInsightResponse(
                        severity,
                        formatBucket(comparison.getBucket()) + " allocation drift",
                        buildTraditionalAllocationMessage(riskProfileType, comparison)
                ));
            }
        }

        addAlternativeAssetInsights(insights, actualAllocation);

        if (!hasMajorTraditionalMismatch && insights.isEmpty()) {
            insights.add(new AssistantInsightResponse(
                    InsightSeverity.INFO,
                    "Portfolio broadly matches profile",
                    "Your equity, bond, and cash allocation is within the assistant's tolerance range for a "
                            + formatProfile(riskProfileType) + " profile."
            ));
        }

        /*
        insights.add(new AssistantInsightResponse(
                InsightSeverity.INFO,
                "Educational guidance only",
                "These insights are rule-based educational guidance, not regulated financial advice."
        ));
         */

        return insights;
    }

    private String buildTraditionalAllocationMessage(RiskProfileType riskProfileType,
                                                     AllocationComparisonResponse comparison) {
        String bucket = formatBucket(comparison.getBucket());
        String profile = formatProfile(riskProfileType);
        String direction = comparison.getDifferencePercent().signum() > 0 ? "above" : "below";

        return bucket + " exposure is " + comparison.getDifferencePercent().abs().setScale(2, java.math.RoundingMode.HALF_UP)
                + "% " + direction + " the target for a " + profile
                + " profile. Consider reviewing whether this allocation still matches the selected risk profile.";
    }

    private void addAlternativeAssetInsights(List<AssistantInsightResponse> insights,
                                             ActualAllocationResponse actualAllocation) {
        if (actualAllocation.getCryptoPercent().compareTo(ALTERNATIVE_NOTICE_THRESHOLD) >= 0) {
            insights.add(new AssistantInsightResponse(
                    InsightSeverity.WARNING,
                    "Crypto exposure detected",
                    "Crypto represents " + displayPercent(actualAllocation.getCryptoPercent())
                            + " of the portfolio. The assistant treats crypto as alternative high-risk exposure rather than equity."
            ));
        }

        if (actualAllocation.getRealEstatePercent().compareTo(ALTERNATIVE_NOTICE_THRESHOLD) >= 0) {
            insights.add(new AssistantInsightResponse(
                    InsightSeverity.INFO,
                    "Real estate exposure detected",
                    "Real estate represents " + displayPercent(actualAllocation.getRealEstatePercent())
                            + " of the portfolio and is shown separately from the equity-bond-cash target model."
            ));
        }

        if (actualAllocation.getOtherPercent().compareTo(ALTERNATIVE_NOTICE_THRESHOLD) >= 0) {
            insights.add(new AssistantInsightResponse(
                    InsightSeverity.INFO,
                    "Other asset exposure detected",
                    "Assets classified as Other represent " + displayPercent(actualAllocation.getOtherPercent())
                            + " of the portfolio. Check asset profiles if these should be classified more precisely."
            ));
        }

        if (actualAllocation.getUnclassifiedPercent().compareTo(UNCLASSIFIED_NOTICE_THRESHOLD) >= 0) {
            insights.add(new AssistantInsightResponse(
                    InsightSeverity.WARNING,
                    "Unclassified assets detected",
                    "Unclassified assets represent " + displayPercent(actualAllocation.getUnclassifiedPercent())
                            + " of the portfolio. Complete asset profiles to improve assistant accuracy."
            ));
        }

    }

    private String formatBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return "Unknown";
        }
        return bucket.charAt(0) + bucket.substring(1).toLowerCase();
    }

    private String formatProfile(RiskProfileType riskProfileType) {
        return riskProfileType == null ? "selected" : riskProfileType.name().toLowerCase().replace('_', ' ');
    }

    private String displayPercent(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP) + "%";
    }
}
