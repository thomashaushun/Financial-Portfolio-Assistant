package com.tsh11.fypcode.controller.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsh11.fypcode.controller.GlobalExceptionHandler;
import com.tsh11.fypcode.controller.InvestorProfileController;
import com.tsh11.fypcode.domain.investor.IncomeStabilityLevel;
import com.tsh11.fypcode.domain.investor.InvestmentExperienceLevel;
import com.tsh11.fypcode.domain.investor.InvestmentGoal;
import com.tsh11.fypcode.domain.investor.RiskProfileType;
import com.tsh11.fypcode.domain.investor.RiskToleranceLevel;
import com.tsh11.fypcode.dto.request.InvestorProfileRequest;
import com.tsh11.fypcode.dto.response.InvestorProfileResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.assistant.InvestorProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//Test endpoints:
//GET  /api/portfolio/investor-profile
//POST /api/portfolio/investor-profile
//PUT  /api/portfolio/investor-profile
@ExtendWith(MockitoExtension.class)
class InvestorProfileControllerBlackBoxTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private InvestorProfileService investorProfileService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new InvestorProfileController(investorProfileService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .setCustomArgumentResolvers(authenticatedPrincipalResolver())
                .build();

        objectMapper = new ObjectMapper();
    }

    // Checks that the API returns the saved investor profile.
    @Test
    @DisplayName("GET investor profile returns 200 and the saved profile when one exists")
    void getProfile_whenProfileExists_returnsProfile() throws Exception {
        // Tests the successful black-box API path for retrieving a saved investor profile.
        when(investorProfileService.getForUser(USER_ID)).thenReturn(Optional.of(profileResponse()));

        mockMvc.perform(get("/api/portfolio/investor-profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.age").value(30))
                .andExpect(jsonPath("$.investmentHorizonYears").value(10))
                .andExpect(jsonPath("$.riskTolerance").value("MEDIUM"))
                .andExpect(jsonPath("$.investmentExperience").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.incomeStability").value("MEDIUM"))
                .andExpect(jsonPath("$.investmentGoal").value("BALANCED_GROWTH"))
                .andExpect(jsonPath("$.riskProfileType").value("BALANCED"))
                .andExpect(jsonPath("$.riskScore").value(55));

        verify(investorProfileService).getForUser(USER_ID);
    }

    // Checks that missing profile is handled gracefully.
    @Test
    @DisplayName("GET investor profile returns 404 when the user has not created a profile")
    void getProfile_whenProfileDoesNotExist_returnsNotFound() throws Exception {
        // Tests the missing-profile API state so the frontend can handle a user without a profile.
        when(investorProfileService.getForUser(USER_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/portfolio/investor-profile"))
                .andExpect(status().isNotFound());

        verify(investorProfileService).getForUser(USER_ID);
    }

    // Checks that valid profile input is accepted.
    @Test
    @DisplayName("POST investor profile accepts a valid request and returns the calculated profile")
    void createProfile_withValidRequest_returnsSavedProfile() throws Exception {
        // Tests the successful black-box API path for creating or replacing an investor profile.
        InvestorProfileRequest request = validRequest();
        when(investorProfileService.createOrUpdate(eq(USER_ID), any(InvestorProfileRequest.class)))
                .thenReturn(profileResponse());

        mockMvc.perform(post("/api/portfolio/investor-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskProfileType").value("BALANCED"))
                .andExpect(jsonPath("$.riskScore").value(55));

        verify(investorProfileService).createOrUpdate(eq(USER_ID), any(InvestorProfileRequest.class));
    }

    // Checks that update works.
    @Test
    @DisplayName("PUT investor profile accepts a valid request and returns the updated profile")
    void updateProfile_withValidRequest_returnsUpdatedProfile() throws Exception {
        // Tests the successful black-box API path for updating an existing profile.
        InvestorProfileRequest request = validRequest();
        InvestorProfileResponse updated = profileResponse();
        updated.setRiskProfileType(RiskProfileType.GROWTH);
        updated.setRiskScore(70);

        when(investorProfileService.update(eq(USER_ID), any(InvestorProfileRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/portfolio/investor-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskProfileType").value("GROWTH"))
                .andExpect(jsonPath("$.riskScore").value(70));

        verify(investorProfileService).update(eq(USER_ID), any(InvestorProfileRequest.class));
    }

    // API failure handling / validation.
    @Test
    @DisplayName("POST investor profile rejects age below the minimum boundary")
    void createProfile_withAgeBelowMinimum_returnsBadRequest() throws Exception {
        // Boundary value test for age: 17 is just below the allowed minimum of 18.
        InvestorProfileRequest request = validRequest();
        request.setAge(17);

        mockMvc.perform(post("/api/portfolio/investor-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // Boundary validation.
    @Test
    @DisplayName("POST investor profile rejects investment horizon below the minimum boundary")
    void createProfile_withInvestmentHorizonBelowMinimum_returnsBadRequest() throws Exception {
        // Boundary value test for investment horizon: 0 is below the allowed minimum of 1 year.
        InvestorProfileRequest request = validRequest();
        request.setInvestmentHorizonYears(0);

        mockMvc.perform(post("/api/portfolio/investor-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // Required-field validation.
    @Test
    @DisplayName("POST investor profile rejects a missing required enum field")
    void createProfile_withMissingRiskTolerance_returnsBadRequest() throws Exception {
        // API validation test for a required field marked with @NotNull.
        InvestorProfileRequest request = validRequest();
        request.setRiskTolerance(null);

        mockMvc.perform(post("/api/portfolio/investor-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    // API failure handling for malformed input.
    @Test
    @DisplayName("POST investor profile rejects an invalid enum value")
    void createProfile_withInvalidEnumValue_returnsBadRequest() throws Exception {
        // API failure-handling test for malformed JSON enum input from the client.
        String invalidJson = """
                {
                  "age": 30,
                  "investmentHorizonYears": 10,
                  "riskTolerance": "IMPOSSIBLE_RISK_LEVEL",
                  "investmentExperience": "INTERMEDIATE",
                  "incomeStability": "MEDIUM",
                  "investmentGoal": "BALANCED_GROWTH"
                }
                """;

        mockMvc.perform(post("/api/portfolio/investor-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    private InvestorProfileRequest validRequest() {
        InvestorProfileRequest request = new InvestorProfileRequest();
        request.setAge(30);
        request.setInvestmentHorizonYears(10);
        request.setRiskTolerance(RiskToleranceLevel.MEDIUM);
        request.setInvestmentExperience(InvestmentExperienceLevel.INTERMEDIATE);
        request.setIncomeStability(IncomeStabilityLevel.MEDIUM);
        request.setInvestmentGoal(InvestmentGoal.BALANCED_GROWTH);
        return request;
    }

    private InvestorProfileResponse profileResponse() {
        InvestorProfileResponse response = new InvestorProfileResponse();
        response.setId(PROFILE_ID);
        response.setAge(30);
        response.setInvestmentHorizonYears(10);
        response.setRiskTolerance(RiskToleranceLevel.MEDIUM);
        response.setInvestmentExperience(InvestmentExperienceLevel.INTERMEDIATE);
        response.setIncomeStability(IncomeStabilityLevel.MEDIUM);
        response.setInvestmentGoal(InvestmentGoal.BALANCED_GROWTH);
        response.setRiskProfileType(RiskProfileType.BALANCED);
        response.setRiskScore(55);
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
