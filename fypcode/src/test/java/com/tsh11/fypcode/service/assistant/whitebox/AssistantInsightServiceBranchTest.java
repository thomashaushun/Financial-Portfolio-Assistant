package com.tsh11.fypcode.service.assistant.whitebox;

import com.tsh11.fypcode.domain.investor.InsightSeverity;
import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.dto.response.ActualAllocationResponse;
import com.tsh11.fypcode.dto.response.AllocationComparisonResponse;
import com.tsh11.fypcode.dto.response.AssistantInsightResponse;
import com.tsh11.fypcode.service.assistant.AssistantInsightService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantInsightServiceBranchTest {

    private final AssistantInsightService service = new AssistantInsightService();

    // Covers the early return branch when no portfolio value is available.
    // The user should receive setup guidance.
    @Test
    @DisplayName("No portfolio value branch returns setup guidance and stops analysis")
    void noPortfolioValueBranchReturnsSetupGuidance() {
        ActualAllocationResponse actual = new ActualAllocationResponse();
        actual.setTotalPortfolioValue(BigDecimal.ZERO);

        List<AssistantInsightResponse> insights = service.buildInsights(RiskProfileType.BALANCED, List.of(), actual);

        assertThat(insights).hasSize(1);
        assertThat(insights.get(0).getSeverity()).isEqualTo(InsightSeverity.INFO);
        assertThat(insights.get(0).getTitle()).contains("No portfolio value");
    }

    // Covers the branch that converts significant allocation drift into a warning insight.
    @Test
    @DisplayName("Traditional mismatch branch creates warning for significant drift")
    void significantTraditionalMismatchCreatesWarning() {
        ActualAllocationResponse actual = actualWithValue("1000");
        AllocationComparisonResponse comparison = comparison("EQUITY", "60", "85", "25", "SIGNIFICANTLY_OVERWEIGHT");

        List<AssistantInsightResponse> insights = service.buildInsights(RiskProfileType.BALANCED, List.of(comparison), actual);

        assertThat(insights)
                .anySatisfy(insight -> {
                    assertThat(insight.getSeverity()).isEqualTo(InsightSeverity.WARNING);
                    assertThat(insight.getTitle()).contains("Equity allocation drift");
                    assertThat(insight.getMessage()).contains("25.00% above");
                });
    }

    // Covers crypto, real estate, other, unclassified, and ETF-note branches.
    @Test
    @DisplayName("Alternative asset branches create crypto, real estate, other, unclassified, and ETF notes")
    void alternativeAssetInsightBranchesAreCovered() {
        ActualAllocationResponse actual = actualWithValue("1000");
        actual.setCryptoPercent(new BigDecimal("10"));
        actual.setRealEstatePercent(new BigDecimal("10"));
        actual.setOtherPercent(new BigDecimal("10"));
        actual.setUnclassifiedPercent(new BigDecimal("5"));
        actual.setEtfAssumptionUsed(true);

        List<AssistantInsightResponse> insights = service.buildInsights(RiskProfileType.GROWTH, List.of(), actual);

        assertThat(insights).extracting(AssistantInsightResponse::getTitle)
                .contains(
                        "Crypto exposure detected",
                        "Real estate exposure detected",
                        "Other asset exposure detected",
                        "Unclassified assets detected",
                        "ETF classification note"
                );
    }

    // Covers the branch where no significant mismatch exists and the assistant creates a positive profile-match insight, plus the educational disclaimer.
    @Test
    @DisplayName("No mismatch branch creates positive profile-match insight plus disclaimer")
    void noMismatchBranchCreatesPositiveInsight() {
        ActualAllocationResponse actual = actualWithValue("1000");
        AllocationComparisonResponse comparison = comparison("EQUITY", "60", "62", "2", "WITHIN_TARGET_RANGE");

        List<AssistantInsightResponse> insights = service.buildInsights(RiskProfileType.BALANCED, List.of(comparison), actual);

        assertThat(insights).extracting(AssistantInsightResponse::getTitle)
                .contains("Portfolio broadly matches profile", "Educational guidance only");
    }

    private ActualAllocationResponse actualWithValue(String totalValue) {
        ActualAllocationResponse actual = new ActualAllocationResponse();
        actual.setTotalPortfolioValue(new BigDecimal(totalValue));
        return actual;
    }

    private AllocationComparisonResponse comparison(String bucket,
                                                    String target,
                                                    String actual,
                                                    String difference,
                                                    String status) {
        AllocationComparisonResponse comparison = new AllocationComparisonResponse();
        comparison.setBucket(bucket);
        comparison.setTargetPercent(new BigDecimal(target));
        comparison.setActualPercent(new BigDecimal(actual));
        comparison.setDifferencePercent(new BigDecimal(difference));
        comparison.setStatus(status);
        return comparison;
    }
}
