package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.response.FinancialAssistantResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.assistant.FinancialAssistantService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
//Provides the main assistant API endpoint.
/*
Endpoint:
GET /api/portfolio/assistant
 */
@RestController
@RequestMapping("/api/portfolio")
public class FinancialAssistantController {

    private final FinancialAssistantService financialAssistantService;

    public FinancialAssistantController(FinancialAssistantService financialAssistantService) {
        this.financialAssistantService = financialAssistantService;
    }

    @GetMapping("/assistant")
    public FinancialAssistantResponse getAssistantSummary(@AuthenticationPrincipal AppUserPrincipal principal) {
        return financialAssistantService.getAssistantSummary(principal.getUserId());
    }
}
