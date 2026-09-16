package com.tsh11.fypcode.integration.marketdata;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AlphaVantageBulkQuote {

    private final RestClient restClient;
    private final String apiKey;

    public AlphaVantageBulkQuote(
            RestClient.Builder builder,
            @Value("${marketdata.alphavantage.base-url}") String baseUrl,
            @Value("${marketdata.alphavantage.api-key}") String apiKey
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    @Cacheable(value = "latestPriceBatch", key = "#cacheKey", sync = true)
    public Map<String, BigDecimal> getLatestPricesForBatch(String cacheKey, List<String> symbols) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "REALTIME_BULK_QUOTES")
                        .queryParam("symbol", String.join(",", symbols))
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        validateAlphaVantageResponse(root);

        Map<String, BigDecimal> result = new LinkedHashMap<>();

        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                putParsedBulkQuote(result, item);
            }
        } else if (root.isArray()) {
            for (JsonNode item : root) {
                putParsedBulkQuote(result, item);
            }
        }

        //temp for debug
        System.out.println("BULK QUOTE REQUEST: " + symbols);
        //end temp
        return result;
    }

    private void putParsedBulkQuote(Map<String, BigDecimal> result, JsonNode item) {
        String symbol = firstNonBlank(
                item.path("symbol").asText(null),
                item.path("ticker").asText(null),
                item.path("01. symbol").asText(null)
        );

        String priceText = firstNonBlank(
                item.path("price").asText(null),
                item.path("05. price").asText(null),
                item.path("last_price").asText(null),
                item.path("close").asText(null)
        );

        if (symbol == null || symbol.isBlank()) {
            return;
        }

        BigDecimal price = parseBigDecimalOrZero(priceText);
        if (price.compareTo(BigDecimal.ZERO) > 0) {
            result.put(symbol.trim().toUpperCase(), price);
        }
    }

    private void validateAlphaVantageResponse(JsonNode root) {
        if (root == null) {
            throw new IllegalStateException("Alpha Vantage returned an empty response");
        }
        if (root.has("Error Message")) {
            throw new IllegalStateException(root.path("Error Message").asText("Alpha Vantage returned an error"));
        }
        if (root.has("Note")) {
            throw new IllegalStateException(root.path("Note").asText("Alpha Vantage request limit reached"));
        }
        if (root.has("Information")) {
            throw new IllegalStateException(root.path("Information").asText("Alpha Vantage informational response"));
        }

        /*
         * REALTIME_BULK_QUOTES can return an artificial sample response when the API key
         * does not have the required realtime US market data entitlement.
         * That response contains a "message" field and should not be treated as real quote data.
         */
        if (root.has("message")) {
            throw new IllegalStateException(root.path("message").asText("Alpha Vantage bulk quote response is not real market data"));
        }
    }

    private BigDecimal parseBigDecimalOrZero(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}