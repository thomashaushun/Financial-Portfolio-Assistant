package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.HoldingService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioHoldingController {

    private final HoldingService holdingService;

    public PortfolioHoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping("/holdings")
    public List<HoldingResponse> getHoldings(@AuthenticationPrincipal AppUserPrincipal principal) {
        return holdingService.getHoldings(principal.getUserId());
    }
}