package com.tsh11.fypcode.integration.marketdata;

import com.tsh11.fypcode.dto.response.AssetSearchResultResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MockMarketDataProvider implements MarketDataProvider {

    private static final Map<String, BigDecimal> MOCK_PRICES = Map.of(
            "AAPL", new BigDecimal("210.50"),
            "VTI", new BigDecimal("275.20"),
            "BND", new BigDecimal("72.10"),
            "SPX", new BigDecimal("5100.00"),
            "SPY", new BigDecimal("510.00"),
            "IBM", new BigDecimal("185.25"),
            "GOLD", new BigDecimal("44.00")
    );

    @Override
    public BigDecimal getLatestPrice(String symbol) {
        return MOCK_PRICES.getOrDefault(normalize(symbol), BigDecimal.ZERO);
    }

    @Override
    public List<HistoricalPricePoint> getHistoricalPrices(String symbol, LocalDate from, LocalDate to) {
        return buildMockHistory(symbol, from, to, getLatestPrice(symbol));
    }

    @Override
    public List<HistoricalPricePoint> getHistoricalIndexPrices(String symbol, LocalDate from, LocalDate to) {
        return buildMockHistory(symbol, from, to, getLatestPrice(symbol));
    }

    @Override
    public Optional<AssetMetadata> getAssetMetadata(String symbol) {
        return Optional.empty();
    }

    @Override
    public List<AssetSearchResultResponse> searchAssets(String query) {
        return List.of();
    }

    private List<HistoricalPricePoint> buildMockHistory(String symbol,
                                                        LocalDate from,
                                                        LocalDate to,
                                                        BigDecimal latestPrice) {
        if (from == null || to == null || from.isAfter(to)) {
            return List.of();
        }

        BigDecimal basePrice = latestPrice.compareTo(BigDecimal.ZERO) > 0
                ? latestPrice
                : new BigDecimal("100.00");

        List<HistoricalPricePoint> points = new ArrayList<>();

        LocalDate current = from;
        int dayIndex = 0;

        while (!current.isAfter(to)) {
            // Simple deterministic mock variation:
            // creates a small repeating pattern around the base price
            BigDecimal adjustment = BigDecimal.valueOf((dayIndex % 10) - 5L)
                    .multiply(new BigDecimal("0.50"));

            BigDecimal close = basePrice.add(adjustment);
            if (close.compareTo(BigDecimal.ZERO) < 0) {
                close = BigDecimal.ZERO;
            }

            points.add(new HistoricalPricePoint(current, close));

            current = current.plusDays(1);
            dayIndex++;
        }

        return points;
    }

    private String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase();
    }
}