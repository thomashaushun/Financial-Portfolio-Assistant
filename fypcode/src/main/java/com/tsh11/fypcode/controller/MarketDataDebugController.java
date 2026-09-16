package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
public class MarketDataDebugController {

    private final MarketDataProvider marketDataProvider;

    public MarketDataDebugController(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @GetMapping("/api/debug/quote")
    public Map<String, Object> getQuote(@RequestParam String symbol) {
        BigDecimal price = marketDataProvider.getLatestPrice(symbol);
        return Map.of(
                "symbol", symbol,
                "price", price
        );
    }
}