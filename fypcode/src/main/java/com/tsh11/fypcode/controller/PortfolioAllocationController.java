package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.response.AllocationResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.AllocationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioAllocationController {

    private final AllocationService allocationService;

    public PortfolioAllocationController(AllocationService allocationService) {
        this.allocationService = allocationService;
    }

    @GetMapping("/allocation")
    public AllocationResponse getAllocation(@AuthenticationPrincipal AppUserPrincipal principal) {
        return allocationService.getAllocation(principal.getUserId());
    }
}