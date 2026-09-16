package com.tsh11.fypcode.controller.portfolio.allocation;

import com.tsh11.fypcode.controller.PortfolioAllocationController;
import com.tsh11.fypcode.dto.response.AllocationItemResponse;
import com.tsh11.fypcode.dto.response.AllocationResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.AllocationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// Tests the API endpoint:
//GET /api/portfolio/allocation
// Verifies that the allocation endpoint returns grouped allocation data for the authenticated user.
@ExtendWith(MockitoExtension.class)
class PortfolioAllocationControllerBlackBoxTest {

    @Mock
    private AllocationService allocationService;

    private MockMvc mockMvc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        PortfolioAllocationController controller = new PortfolioAllocationController(allocationService);

        mockMvc = standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Verifies that allocation groups such as holding,
    // asset class, sector, market, and currency are returned as JSON.
    @Test
    void getAllocation_shouldReturnAllocationJsonForAuthenticatedUser() throws Exception {
        AllocationResponse response = new AllocationResponse();
        response.setByHolding(List.of(item("Apple Inc.", "1500", "50")));
        response.setByAssetClass(List.of(item("EQUITY", "3000", "100")));
        response.setBySector(List.of(item("Technology", "3000", "100")));
        response.setByMarket(List.of(item("NASDAQ", "3000", "100")));
        response.setByCurrency(List.of(item("USD", "3000", "100")));

        when(allocationService.getAllocation(userId))
                .thenReturn(response);

        mockMvc.perform(get("/api/portfolio/allocation")
                        .with(authenticatedPrincipal(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byHolding[0].label").value("Apple Inc."))
                .andExpect(jsonPath("$.byHolding[0].value").value(1500))
                .andExpect(jsonPath("$.byHolding[0].weightPercent").value(50))
                .andExpect(jsonPath("$.byAssetClass[0].label").value("EQUITY"))
                .andExpect(jsonPath("$.bySector[0].label").value("Technology"))
                .andExpect(jsonPath("$.byMarket[0].label").value("NASDAQ"))
                .andExpect(jsonPath("$.byCurrency[0].label").value("USD"));

        verify(allocationService).getAllocation(userId);
    }

    // Verifies that empty allocation groups are returned safely for an empty portfolio.
    @Test
    void getAllocation_whenPortfolioIsEmpty_shouldReturnEmptyAllocationGroups() throws Exception {
        AllocationResponse response = new AllocationResponse();
        response.setByHolding(List.of());
        response.setByAssetClass(List.of());
        response.setBySector(List.of());
        response.setByMarket(List.of());
        response.setByCurrency(List.of());

        when(allocationService.getAllocation(userId))
                .thenReturn(response);

        mockMvc.perform(get("/api/portfolio/allocation")
                        .with(authenticatedPrincipal(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byHolding").isEmpty())
                .andExpect(jsonPath("$.byAssetClass").isEmpty())
                .andExpect(jsonPath("$.bySector").isEmpty())
                .andExpect(jsonPath("$.byMarket").isEmpty())
                .andExpect(jsonPath("$.byCurrency").isEmpty());

        verify(allocationService).getAllocation(userId);
    }

    private AllocationItemResponse item(String label, String value, String weightPercent) {
        AllocationItemResponse item = new AllocationItemResponse();
        item.setLabel(label);
        item.setValue(new BigDecimal(value));
        item.setWeightPercent(new BigDecimal(weightPercent));
        return item;
    }

    private RequestPostProcessor authenticatedPrincipal(UUID userId) {
        return request -> {
            AppUserPrincipal principal = mock(AppUserPrincipal.class);
            when(principal.getUserId()).thenReturn(userId);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setUserPrincipal(authentication);

            return request;
        };
    }
}