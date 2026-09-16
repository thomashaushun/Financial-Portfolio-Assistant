package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.repository.ActivityRepository;
import com.tsh11.fypcode.repository.InvestorProfileRepository;
import com.tsh11.fypcode.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

@ControllerAdvice(assignableTypes = PortfolioPageController.class)
public class PortfolioModelAdvice {

    private final ActivityRepository activityRepository;
    private final InvestorProfileRepository investorProfileRepository;

    public PortfolioModelAdvice(ActivityRepository activityRepository,
                                InvestorProfileRepository investorProfileRepository) {
        this.activityRepository = activityRepository;
        this.investorProfileRepository = investorProfileRepository;
    }

    @ModelAttribute("portfolioSetup")
    public Map<String, Object> portfolioSetup(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return Map.of(
                    "displayName", "User",
                    "hasInvestorProfile", false,
                    "hasActivities", false,
                    "setupComplete", false
            );
        }

        boolean hasInvestorProfile = investorProfileRepository.existsByUserId(principal.getUserId());
        boolean hasActivities = activityRepository.existsByUserId(principal.getUserId());

        String displayName = principal.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = principal.getUsername();
        }

        return Map.of(
                "displayName", displayName,
                "hasInvestorProfile", hasInvestorProfile,
                "hasActivities", hasActivities,
                "setupComplete", hasInvestorProfile && hasActivities
        );
    }
}
