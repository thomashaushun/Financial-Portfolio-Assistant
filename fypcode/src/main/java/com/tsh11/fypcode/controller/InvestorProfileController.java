package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.request.InvestorProfileRequest;
import com.tsh11.fypcode.dto.response.InvestorProfileResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.assistant.InvestorProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
//API endpoints for the investor profile.
/*
Endpoints:
GET  /api/portfolio/investor-profile
POST /api/portfolio/investor-profile
PUT  /api/portfolio/investor-profile
 */
@RestController
@RequestMapping("/api/portfolio/investor-profile")
public class InvestorProfileController {

    private final InvestorProfileService investorProfileService;

    public InvestorProfileController(InvestorProfileService investorProfileService) {
        this.investorProfileService = investorProfileService;
    }

    @GetMapping
    public ResponseEntity<InvestorProfileResponse> getProfile(@AuthenticationPrincipal AppUserPrincipal principal) {
        return investorProfileService.getForUser(principal.getUserId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public InvestorProfileResponse createOrUpdateProfile(@Valid @RequestBody InvestorProfileRequest request,
                                                         @AuthenticationPrincipal AppUserPrincipal principal) {
        return investorProfileService.createOrUpdate(principal.getUserId(), request);
    }

    @PutMapping
    public InvestorProfileResponse updateProfile(@Valid @RequestBody InvestorProfileRequest request,
                                                 @AuthenticationPrincipal AppUserPrincipal principal) {
        return investorProfileService.update(principal.getUserId(), request);
    }
}
