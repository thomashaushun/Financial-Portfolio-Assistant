package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import com.tsh11.fypcode.dto.response.BenchmarkComparisonResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.BenchmarkService;
import com.tsh11.fypcode.service.PortfolioOptimisationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/portfolio/analysis")
public class PortfolioAnalysisController {

    private final BenchmarkService benchmarkService;
    private final PortfolioOptimisationService portfolioOptimisationService;

    public PortfolioAnalysisController(
            BenchmarkService benchmarkService,
            PortfolioOptimisationService portfolioOptimisationService) {
        this.benchmarkService = benchmarkService;
        this.portfolioOptimisationService = portfolioOptimisationService;
    }

    @GetMapping("/benchmark")
    public BenchmarkComparisonResponse benchmark(
            @RequestParam String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        return benchmarkService.compareAgainstBenchmark(
                principal.getUserId(),
                symbol,
                from,
                to
        );
    }

    @GetMapping("/optimise")
    public PortfolioOptimisationResult optimise(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "MEAN_VARIANCE") PortfolioAlgorithm algorithm,
            @AuthenticationPrincipal AppUserPrincipal principal) {

        return portfolioOptimisationService.optimise(
                principal.getUserId(),
                from,
                to,
                algorithm
        );
    }
}