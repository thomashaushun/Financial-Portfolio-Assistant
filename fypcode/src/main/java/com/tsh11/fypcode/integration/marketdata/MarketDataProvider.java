package com.tsh11.fypcode.integration.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.tsh11.fypcode.dto.response.AssetSearchResultResponse;

public interface MarketDataProvider {
    BigDecimal getLatestPrice(String symbol);

    List<HistoricalPricePoint> getHistoricalPrices(String symbol, LocalDate from, LocalDate to);

    List<HistoricalPricePoint> getHistoricalIndexPrices(String symbol, LocalDate from, LocalDate to);

    Optional<AssetMetadata> getAssetMetadata(String symbol);

    //gets latest prices for multiple symbols
    //The interface provides a basic implementation that classes can use unless they override it.
    default Map<String, BigDecimal> getLatestPrices(List<String> symbols) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();

        //Handle null or empty input
        if (symbols == null || symbols.isEmpty()) {
            return result;
        }

        //Loop through symbols
        for (String symbol : symbols) {
            //Skip invalid symbols
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            //Normalise symbol and fetch price
            result.put(symbol.trim().toUpperCase(), getLatestPrice(symbol));
        }

        return result;
    }

    List<AssetSearchResultResponse> searchAssets(String query);
}