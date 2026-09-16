package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.request.PortfolioExplanationRequest;
import com.tsh11.fypcode.dto.response.PortfolioExplanationResponse;
import com.tsh11.fypcode.service.ExplainabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/portfolio/analysis")
public class PortfolioExplanationController {

    private final ExplainabilityService explainabilityService;

    public PortfolioExplanationController(ExplainabilityService explainabilityService) {
        this.explainabilityService = explainabilityService;
    }

    @PostMapping("/explain")
    public ResponseEntity<PortfolioExplanationResponse> explain(
            @RequestBody PortfolioExplanationRequest request) {

        PortfolioExplanationResponse response = explainabilityService.explain(request);
        return ResponseEntity.ok(response);
    }
}