package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.response.OverviewResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.OverviewService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioOverviewController {

    private final OverviewService overviewService;

    public PortfolioOverviewController(OverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @GetMapping("/overview")
    public OverviewResponse getOverview(@AuthenticationPrincipal AppUserPrincipal principal) {
        return overviewService.getOverview(principal.getUserId());
    }
}