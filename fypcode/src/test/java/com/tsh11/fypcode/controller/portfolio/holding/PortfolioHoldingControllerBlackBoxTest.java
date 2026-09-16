package com.tsh11.fypcode.controller.portfolio.holding;

import com.tsh11.fypcode.controller.PortfolioHoldingController;
import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.HoldingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

//Tests the API endpoint:
//GET /api/portfolio/holdings
// Verifies that the holdings endpoint returns JSON holding data for the authenticated user.
@ExtendWith(MockitoExtension.class)
class PortfolioHoldingControllerBlackBoxTest {

    @Mock
    private HoldingService holdingService;

    private MockMvc mockMvc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        PortfolioHoldingController controller = new PortfolioHoldingController(holdingService);

        mockMvc = standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Verifies that the endpoint returns holding fields such as symbol,
    // display name, asset class, market value, and currency.
    @Test
    void getHoldings_shouldReturnHoldingsJsonForAuthenticatedUser() throws Exception {
        HoldingResponse apple = new HoldingResponse();
        apple.setSymbol("AAPL");
        apple.setDisplayName("Apple Inc.");
        apple.setAssetClass(AssetClass.EQUITY);
        apple.setSector("Technology");
        apple.setMarket("NASDAQ");
        apple.setCurrency("USD");
        apple.setTotalQuantity(new BigDecimal("10"));
        apple.setTotalCost(new BigDecimal("1000"));
        apple.setLatestPrice(new BigDecimal("150"));
        apple.setMarketValue(new BigDecimal("1500"));
        apple.setUnrealizedGainLoss(new BigDecimal("500"));

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple));

        mockMvc.perform(get("/api/portfolio/holdings")
                        .with(authenticatedPrincipal(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].displayName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].assetClass").value("EQUITY"))
                .andExpect(jsonPath("$[0].sector").value("Technology"))
                .andExpect(jsonPath("$[0].market").value("NASDAQ"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].totalQuantity").value(10))
                .andExpect(jsonPath("$[0].marketValue").value(1500));

        verify(holdingService).getHoldings(userId);
    }

    // Verifies that an empty portfolio returns an empty JSON array rather than an error.
    @Test
    void getHoldings_whenNoHoldings_shouldReturnEmptyJsonArray() throws Exception {
        when(holdingService.getHoldings(userId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/portfolio/holdings")
                        .with(authenticatedPrincipal(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(holdingService).getHoldings(userId);
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