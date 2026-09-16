package com.tsh11.fypcode.controller.portfolio.analysis;

import com.tsh11.fypcode.controller.PortfolioAnalysisController;
import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioMetricsDto;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.BenchmarkService;
import com.tsh11.fypcode.service.PortfolioOptimisationService;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// Tests these API endpoints:
//GET /api/portfolio/analysis/optimise
//GET /api/portfolio/analysis/benchmark
//
//Verifies that the analysis controller exposes optimisation and benchmark comparison functionality through HTTP endpoints.
@ExtendWith(MockitoExtension.class)
class PortfolioAnalysisControllerBlackBoxTest {

    @Mock
    private BenchmarkService benchmarkService;

    @Mock
    private PortfolioOptimisationService portfolioOptimisationService;

    private MockMvc mockMvc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        PortfolioAnalysisController controller = new PortfolioAnalysisController(
                benchmarkService,
                portfolioOptimisationService
        );

        mockMvc = standaloneSetup(controller)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // Verifies that a valid optimisation request returns algorithm, portfolio metrics, assets, suggestions, and warnings.
    @Test
    void optimise_shouldReturnOptimisationJsonForValidRequest() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        PortfolioOptimisationResult response = optimisationResult();

        when(portfolioOptimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        ))
                .thenReturn(response);

        mockMvc.perform(get("/api/portfolio/analysis/optimise")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .param("algorithm", "MEAN_VARIANCE")
                        .with(authenticatedPrincipal(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("MEAN_VARIANCE"))
                .andExpect(jsonPath("$.currentPortfolio.expectedAnnualReturn").value(0.08))
                .andExpect(jsonPath("$.optimisedPortfolio.expectedAnnualReturn").value(0.12))
                .andExpect(jsonPath("$.assets[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.assets[0].currentWeight").value(0.4))
                .andExpect(jsonPath("$.assets[0].targetWeight").value(0.6))
                .andExpect(jsonPath("$.warnings[0]").value("MSFT was excluded because sufficient historical data was not available."));

        verify(portfolioOptimisationService).optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );
    }

    // Verifies that the default algorithm is MEAN_VARIANCE when the request does not provide an algorithm.
    @Test
    void optimise_withoutAlgorithmParameter_shouldUseDefaultMeanVarianceAlgorithm() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        PortfolioOptimisationResult response = optimisationResult();

        when(portfolioOptimisationService.optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        ))
                .thenReturn(response);

        mockMvc.perform(get("/api/portfolio/analysis/optimise")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .with(authenticatedPrincipal(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm").value("MEAN_VARIANCE"));

        verify(portfolioOptimisationService).optimise(
                userId,
                from,
                to,
                PortfolioAlgorithm.MEAN_VARIANCE
        );
    }

    // Verifies that missing required date parameters result in HTTP 400.
    @Test
    void optimise_withMissingDateParameter_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/portfolio/analysis/optimise")
                        .param("to", "2026-01-31")
                        .with(authenticatedPrincipal(userId)))
                .andExpect(status().isBadRequest());
    }

    // Verifies that the benchmark endpoint accepts symbol and date parameters and delegates to BenchmarkService.
    @Test
    void benchmark_shouldDelegateToBenchmarkServiceForValidRequest() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);

        /*
         The response DTO fields are not tested here because BenchmarkService has its own logic.
         This controller test only verifies that the API endpoint accepts parameters and delegates correctly.
         */
        when(benchmarkService.compareAgainstBenchmark(userId, "SPY", from, to))
                .thenReturn(null);

        mockMvc.perform(get("/api/portfolio/analysis/benchmark")
                        .param("symbol", "SPY")
                        .param("from", "2026-01-01")
                        .param("to", "2026-01-31")
                        .with(authenticatedPrincipal(userId)))
                .andExpect(status().isOk());

        verify(benchmarkService).compareAgainstBenchmark(userId, "SPY", from, to);
    }

    private PortfolioOptimisationResult optimisationResult() {
        PortfolioMetricsDto current = new PortfolioMetricsDto();
        current.setExpectedAnnualReturn(new BigDecimal("0.08"));
        current.setAnnualVolatility(new BigDecimal("0.20"));
        current.setSharpeRatio(new BigDecimal("0.30"));

        PortfolioMetricsDto optimised = new PortfolioMetricsDto();
        optimised.setExpectedAnnualReturn(new BigDecimal("0.12"));
        optimised.setAnnualVolatility(new BigDecimal("0.18"));
        optimised.setSharpeRatio(new BigDecimal("0.55"));

        PortfolioOptimisationAssetResult asset = new PortfolioOptimisationAssetResult();
        asset.setSymbol("AAPL");
        asset.setCurrentWeight(new BigDecimal("0.40"));
        asset.setTargetWeight(new BigDecimal("0.60"));
        asset.setWeightDifference(new BigDecimal("0.20"));
        asset.setSuggestedAction("BUY");

        PortfolioOptimisationResult result = new PortfolioOptimisationResult();
        result.setAlgorithm(PortfolioAlgorithm.MEAN_VARIANCE);
        result.setCurrentPortfolio(current);
        result.setOptimisedPortfolio(optimised);
        result.setAssets(List.of(asset));
        result.setSuggestions(List.of("Increase allocation to AAPL"));
        result.setWarnings(List.of("MSFT was excluded because sufficient historical data was not available."));
        return result;
    }

    private RequestPostProcessor authenticatedPrincipal(UUID userId) {
        return request -> {
            AppUserPrincipal principal = mock(AppUserPrincipal.class);
            org.mockito.Mockito.lenient()
                    .when(principal.getUserId())
                    .thenReturn(userId);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setUserPrincipal(authentication);

            return request;
        };
    }
}