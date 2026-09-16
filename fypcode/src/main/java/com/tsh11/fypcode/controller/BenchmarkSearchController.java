package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.response.BenchmarkSearchResultResponse;
import com.tsh11.fypcode.service.BenchmarkSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkSearchController {

    private final BenchmarkSearchService benchmarkSearchService;

    public BenchmarkSearchController(BenchmarkSearchService benchmarkSearchService) {
        this.benchmarkSearchService = benchmarkSearchService;
    }

    @GetMapping("/search")
    public List<BenchmarkSearchResultResponse> search(@RequestParam String query) {
        return benchmarkSearchService.search(query);
    }
}