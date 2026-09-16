package com.tsh11.fypcode.controller.assistant;

import com.tsh11.fypcode.controller.FinancialAssistantController;
import com.tsh11.fypcode.controller.GlobalExceptionHandler;
import com.tsh11.fypcode.domain.investor.InsightSeverity;
import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.dto.response.ActualAllocationResponse;
import com.tsh11.fypcode.dto.response.AllocationComparisonResponse;
import com.tsh11.fypcode.dto.response.AssistantInsightResponse;
import com.tsh11.fypcode.dto.response.FinancialAssistantResponse;
import com.tsh11.fypcode.dto.response.TargetAllocationResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.assistant.FinancialAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Endpoint:
//GET /api/portfolio/assistant
@ExtendWith(MockitoExtension.class)
class FinancialAssistantControllerBlackBoxTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private FinancialAssistantService financialAssistantService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new FinancialAssistantController(financialAssistantService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authenticatedPrincipalResolver())
                .build();
    }

    // Confirms the endpoint handles a new user with no profile.
    @Test
    @DisplayName("GET assistant returns a create-profile prompt when no investor profile exists")
    void getAssistant_whenProfileMissing_returnsCreateProfilePrompt() throws Exception {
        // Tests the valid new-user state where the assistant cannot personalise analysis yet.
        when(financialAssistantService.getAssistantSummary(USER_ID)).thenReturn(missingProfileResponse());

        mockMvc.perform(get("/api/portfolio/assistant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileExists").value(false))
                .andExpect(jsonPath("$.insights[0].severity").value("INFO"))
                .andExpect(jsonPath("$.insights[0].title").value("Create an investor profile"));

        verify(financialAssistantService).getAssistantSummary(USER_ID);
    }

    // Confirms the assistant returns profile, target allocation, actual allocation, and insights.
    @Test
    @DisplayName("GET assistant returns target allocation, actual allocation, comparisons, and insights")
    void getAssistant_whenProfileExists_returnsAssistantResponse() throws Exception {
        // Tests the successful black-box API path once an investor profile and portfolio analysis are available.
        when(financialAssistantService.getAssistantSummary(USER_ID)).thenReturn(completeAssistantResponse());

        mockMvc.perform(get("/api/portfolio/assistant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileExists").value(true))
                .andExpect(jsonPath("$.riskProfileType").value("BALANCED"))
                .andExpect(jsonPath("$.riskScore").value(55))
                .andExpect(jsonPath("$.targetAllocation.equityPercent").value(60))
                .andExpect(jsonPath("$.targetAllocation.bondPercent").value(30))
                .andExpect(jsonPath("$.targetAllocation.cashPercent").value(10))
                .andExpect(jsonPath("$.actualAllocation.equityPercent").value(85))
                .andExpect(jsonPath("$.actualAllocation.bondPercent").value(0))
                .andExpect(jsonPath("$.actualAllocation.cashPercent").value(10))
                .andExpect(jsonPath("$.actualAllocation.cryptoPercent").value(5))
                .andExpect(jsonPath("$.comparisons[0].bucket").value("EQUITY"))
                .andExpect(jsonPath("$.comparisons[0].status").value("SIGNIFICANTLY_OVERWEIGHT"))
                .andExpect(jsonPath("$.insights[0].severity").value("WARNING"));

        verify(financialAssistantService).getAssistantSummary(USER_ID);
    }

    // Confirms empty portfolio does not crash.
    @Test
    @DisplayName("GET assistant handles an empty portfolio without returning a server error")
    void getAssistant_whenNoHoldings_returnsNoHoldingsInsight() throws Exception {
        // Tests the empty-portfolio state: the endpoint should still return 200 with a helpful insight.
        when(financialAssistantService.getAssistantSummary(USER_ID)).thenReturn(emptyPortfolioResponse());

        mockMvc.perform(get("/api/portfolio/assistant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileExists").value(true))
                .andExpect(jsonPath("$.actualAllocation.totalPortfolioValue").value(0))
                .andExpect(jsonPath("$.insights[0].severity").value("INFO"))
                .andExpect(jsonPath("$.insights[0].title").value("No holdings to analyse"));

        verify(financialAssistantService).getAssistantSummary(USER_ID);
    }

    // Confirms API failure handling through GlobalExceptionHandler.
    @Test
    @DisplayName("GET assistant returns 400 when the service rejects the request")
    void getAssistant_whenServiceThrowsIllegalArgument_returnsBadRequest() throws Exception {
        // API failure-handling test for IllegalArgumentException mapped by GlobalExceptionHandler.
        when(financialAssistantService.getAssistantSummary(USER_ID))
                .thenThrow(new IllegalArgumentException("User ID must not be null."));

        mockMvc.perform(get("/api/portfolio/assistant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("User ID must not be null."));

        verify(financialAssistantService).getAssistantSummary(USER_ID);
    }

    private FinancialAssistantResponse missingProfileResponse() {
        FinancialAssistantResponse response = new FinancialAssistantResponse();
        response.setProfileExists(false);
        response.setDisclaimer("Educational guidance only.");
        response.getInsights().add(new AssistantInsightResponse(
                InsightSeverity.INFO,
                "Create an investor profile",
                "Complete the investor profile questionnaire to receive personalised suitability insights."
        ));
        return response;
    }

    private FinancialAssistantResponse completeAssistantResponse() {
        FinancialAssistantResponse response = new FinancialAssistantResponse();
        response.setProfileExists(true);
        response.setRiskProfileType(RiskProfileType.BALANCED);
        response.setRiskScore(55);
        response.setDisclaimer("Educational guidance only.");

        TargetAllocationResponse target = new TargetAllocationResponse();
        target.setRiskProfileType(RiskProfileType.BALANCED);
        target.setEquityPercent(new BigDecimal("60"));
        target.setBondPercent(new BigDecimal("30"));
        target.setCashPercent(new BigDecimal("10"));
        response.setTargetAllocation(target);

        ActualAllocationResponse actual = new ActualAllocationResponse();
        actual.setEquityPercent(new BigDecimal("85"));
        actual.setBondPercent(BigDecimal.ZERO);
        actual.setCashPercent(new BigDecimal("10"));
        actual.setCryptoPercent(new BigDecimal("5"));
        actual.setTotalPortfolioValue(new BigDecimal("10000"));
        response.setActualAllocation(actual);

        AllocationComparisonResponse comparison = new AllocationComparisonResponse();
        comparison.setBucket("EQUITY");
        comparison.setTargetPercent(new BigDecimal("60"));
        comparison.setActualPercent(new BigDecimal("85"));
        comparison.setDifferencePercent(new BigDecimal("25"));
        comparison.setStatus("SIGNIFICANTLY_OVERWEIGHT");
        response.setComparisons(List.of(comparison));

        response.setInsights(List.of(new AssistantInsightResponse(
                InsightSeverity.WARNING,
                "Equity allocation is above target",
                "Your equity exposure is significantly above the target for a Balanced investor."
        )));

        return response;
    }

    private FinancialAssistantResponse emptyPortfolioResponse() {
        FinancialAssistantResponse response = new FinancialAssistantResponse();
        response.setProfileExists(true);
        response.setRiskProfileType(RiskProfileType.BALANCED);
        response.setRiskScore(55);
        response.setActualAllocation(new ActualAllocationResponse());
        response.setInsights(List.of(new AssistantInsightResponse(
                InsightSeverity.INFO,
                "No holdings to analyse",
                "Add investment activities before reviewing portfolio suitability."
        )));
        return response;
    }

    private HandlerMethodArgumentResolver authenticatedPrincipalResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(AppUserPrincipal.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter,
                                          ModelAndViewContainer mavContainer,
                                          NativeWebRequest webRequest,
                                          WebDataBinderFactory binderFactory) {
                return new AppUserPrincipal(
                        USER_ID,
                        "Test User",
                        "test@example.com",
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
            }
        };
    }
}
