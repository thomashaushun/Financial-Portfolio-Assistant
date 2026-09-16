package com.tsh11.fypcode.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortfolioPageController {

    @GetMapping("/portfolio/holdings")
    public String portfolioHoldingsPage() {
        return "portfolio/holdings";
    }

    @GetMapping("/portfolio/overview")
    public String portfolioOverviewPage() {
        return "portfolio/overview";
    }

    @GetMapping("/portfolio/activities")
    public String portfolioActivitiesPage() {
        return "portfolio/activities";
    }

    @GetMapping("/portfolio/asset-profiles")
    public String portfolioAssetProfilesPage() {
        return "portfolio/asset-profiles";
    }

    @GetMapping("/portfolio/allocation")
    public String portfolioAllocationPage() {
        return "portfolio/allocation";
    }

    @GetMapping("/portfolio/analysis")
    public String portfolioAnalysisPage() {
        return "portfolio/analysis";
    }

    @GetMapping("/portfolio/investor-profile")
    public String portfolioInvestorProfilePage() {
        return "portfolio/investor-profile";
    }
}