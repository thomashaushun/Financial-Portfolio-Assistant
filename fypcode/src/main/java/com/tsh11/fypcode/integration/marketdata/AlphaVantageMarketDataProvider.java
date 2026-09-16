package com.tsh11.fypcode.integration.marketdata;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetType;
import com.tsh11.fypcode.domain.asset.DataSourceType;
import com.tsh11.fypcode.dto.response.AssetSearchResultResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.cache.annotation.Cacheable;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Primary
public class AlphaVantageMarketDataProvider implements MarketDataProvider {

    private static final int MAX_BULK_SYMBOLS = 100;

    private final RestClient restClient;
    private final String apiKey;
    private final AlphaVantageBulkQuote alphaVantageBulkQuote;

    public AlphaVantageMarketDataProvider(
            RestClient.Builder builder,
            @Value("${marketdata.alphavantage.base-url}") String baseUrl,
            @Value("${marketdata.alphavantage.api-key}") String apiKey,
            AlphaVantageBulkQuote alphaVantageBulkQuote
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.alphaVantageBulkQuote = alphaVantageBulkQuote;
    }

    @Override
    @Cacheable(
            value = "latestPrice",
            key = "(#symbol == null ? '' : #symbol.trim().toUpperCase())",
            sync = true
    )
    public BigDecimal getLatestPrice(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "GLOBAL_QUOTE")
                        .queryParam("symbol", normalizedSymbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        validateAlphaVantageResponse(root);

        JsonNode globalQuote = root.path("Global Quote");
        if (globalQuote.isMissingNode() || globalQuote.isEmpty()) {
            return BigDecimal.ZERO;
        }

        System.out.println("SINGLE QUOTE REQUEST: " + normalizedSymbol);
        return parseBigDecimalOrZero(globalQuote.path("05. price").asText(null));
    }

    @Override
    public Map<String, BigDecimal> getLatestPrices(List<String> symbols) {
        List<String> normalizedSymbols = normalizeSymbols(symbols);
        if (normalizedSymbols.isEmpty()) {
            return Map.of();
        }

        Map<String, BigDecimal> result = new LinkedHashMap<>();

        for (int start = 0; start < normalizedSymbols.size(); start += MAX_BULK_SYMBOLS) {
            int end = Math.min(start + MAX_BULK_SYMBOLS, normalizedSymbols.size());
            List<String> batch = normalizedSymbols.subList(start, end);

            try {
                Map<String, BigDecimal> batchPrices =
                        alphaVantageBulkQuote.getLatestPricesForBatch(buildBatchCacheKey(batch), batch);

                for (Map.Entry<String, BigDecimal> entry : batchPrices.entrySet()) {
                    String symbol = normalizeSymbol(entry.getKey());
                    BigDecimal price = entry.getValue();

                    if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                        result.put(symbol, price);
                    }
                }
            } catch (Exception ex) {
                System.out.println("BULK QUOTE FAILED: " + ex.getMessage());
            }
        }

        /*
         * Controlled fallback:
         * If the bulk endpoint returns only partial data or artificial/sample data,
         * fetch missing symbols one by one using GLOBAL_QUOTE.
         *
         * This is deliberately throttled to avoid reintroducing Alpha Vantage burst errors.
         */
        for (String symbol : normalizedSymbols) {
            if (result.containsKey(symbol)) {
                continue;
            }

            try {
                sleepBeforeSingleQuoteFallback();

                BigDecimal fallbackPrice = getLatestPrice(symbol);

                if (fallbackPrice != null && fallbackPrice.compareTo(BigDecimal.ZERO) > 0) {
                    result.put(symbol, fallbackPrice);
                } else {
                    result.put(symbol, BigDecimal.ZERO);
                }
            } catch (Exception ex) {
                System.out.println("SINGLE QUOTE FALLBACK FAILED for " + symbol + ": " + ex.getMessage());
                result.put(symbol, BigDecimal.ZERO);
            }
        }

        System.out.println("LATEST PRICE MAP -> " + result);

        return result;
    }

    @Override
    @Cacheable(
            value = "historicalPrice",
            key = "(#symbol == null ? '' : #symbol.trim().toUpperCase()) + '_' + #from + '_' + #to"
    )
    public List<HistoricalPricePoint> getHistoricalPrices(String symbol, LocalDate from, LocalDate to) {
        String normalizedSymbol = normalizeSymbol(symbol);

        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "TIME_SERIES_DAILY_ADJUSTED")
                        .queryParam("symbol", normalizedSymbol)
                        .queryParam("outputsize", "full")
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        validateAlphaVantageResponse(root);

        JsonNode series = root.path("Time Series (Daily)");
        if (series.isMissingNode() || !series.isObject()) {
            return List.of();
        }

        List<HistoricalPricePoint> result = new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry : series.properties()) {
            LocalDate date = LocalDate.parse(entry.getKey());
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }

            JsonNode dailyNode = entry.getValue();
            BigDecimal adjustedClose = parseBigDecimalOrZero(dailyNode.path("5. adjusted close").asText(null));
            result.add(new HistoricalPricePoint(date, adjustedClose));
        }

        result.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        return result;
    }

    @Override
    @Cacheable(
            value = "historicalIndexPrice",
            key = "(#symbol == null ? '' : #symbol.trim().toUpperCase()) + '_' + #from + '_' + #to"
    )
    public List<HistoricalPricePoint> getHistoricalIndexPrices(String symbol, LocalDate from, LocalDate to) {
        String normalizedSymbol = normalizeSymbol(symbol);

        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "INDEX_DATA")
                        .queryParam("symbol", normalizedSymbol)
                        .queryParam("interval", "daily")
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        validateAlphaVantageResponse(root);

        JsonNode data = root.path("data");
        if (data.isMissingNode() || !data.isArray()) {
            return List.of();
        }

        List<HistoricalPricePoint> result = new ArrayList<>();

        for (JsonNode node : data) {
            LocalDate date = LocalDate.parse(node.path("date").asText());
            if (date.isBefore(from) || date.isAfter(to)) {
                continue;
            }

            BigDecimal close = parseBigDecimalOrZero(node.path("close").asText(null));
            result.add(new HistoricalPricePoint(date, close));
        }

        result.sort((a, b) -> a.getDate().compareTo(b.getDate()));
        return result;
    }

    @Override
    @Cacheable(
            value = "assetSearch",
            key = "(#query == null ? '' : #query.trim().toUpperCase())"
    )
    public List<AssetSearchResultResponse> searchAssets(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "SYMBOL_SEARCH")
                        .queryParam("keywords", query.trim())
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        validateAlphaVantageResponse(root);

        JsonNode matches = root.path("bestMatches");
        if (matches.isMissingNode() || !matches.isArray()) {
            return List.of();
        }

        List<AssetSearchResultResponse> results = new ArrayList<>();

        for (JsonNode match : matches) {
            String symbol = safeText(match.path("1. symbol").asText(null));
            String displayName = safeText(match.path("2. name").asText(null));
            String providerType = safeText(match.path("3. type").asText(null));

            if (symbol == null || displayName == null) {
                continue;
            }

            AssetSearchResultResponse item = new AssetSearchResultResponse();
            item.setSymbol(symbol);
            item.setDisplayName(displayName);
            item.setAssetType(mapSearchAssetType(providerType, symbol));
            item.setMarket(safeText(match.path("4. region").asText(null)));
            item.setCurrency(safeText(match.path("8. currency").asText(null)));
            item.setMatchScore(safeText(match.path("9. matchScore").asText(null)));

            results.add(item);
        }

        return results;
    }

    @Override
    @Cacheable(
            value = "assetMetadata",
            key = "(#symbol == null ? '' : #symbol.trim().toUpperCase())"
    )
    public Optional<AssetMetadata> getAssetMetadata(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);

        JsonNode searchRoot = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "SYMBOL_SEARCH")
                        .queryParam("keywords", normalizedSymbol)
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        validateAlphaVantageResponse(searchRoot);

        JsonNode matches = searchRoot.path("bestMatches");
        if (matches.isMissingNode() || !matches.isArray() || matches.isEmpty()) {
            return Optional.empty();
        }

        JsonNode selectedMatch = findBestMatch(matches, normalizedSymbol);

        String matchedSymbol = normalizeSymbol(selectedMatch.path("1. symbol").asText(null));
        String providerType = safeText(selectedMatch.path("3. type").asText(null));
        String region = safeText(selectedMatch.path("4. region").asText(null));
        String currency = safeText(selectedMatch.path("8. currency").asText(null));
        String displayName = safeText(selectedMatch.path("2. name").asText(null));

        AssetMetadata metadata = new AssetMetadata();
        metadata.setSymbol(matchedSymbol);
        metadata.setDisplayName(displayName != null ? displayName : matchedSymbol);
        metadata.setProviderAssetType(providerType);
        metadata.setAssetClass(mapAssetClass(providerType, matchedSymbol));
        metadata.setMarket(region);
        metadata.setCurrency(currency);
        metadata.setDataSource(DataSourceType.ALPHA_VANTAGE);

        applyInitialCapabilities(metadata);

        if (metadata.getAssetClass() == AssetClass.ETF) {
            enrichFromEtfProfile(metadata);
        } else if (metadata.getAssetClass() == AssetClass.EQUITY
                || metadata.getAssetClass() == AssetClass.BOND
                || metadata.getAssetClass() == AssetClass.OTHER) {
            enrichFromOverview(metadata);
        }

        if (metadata.getAssetClass() == null) {
            metadata.setAssetClass(AssetClass.OTHER);
        }

        applyInitialCapabilities(metadata);

        return Optional.of(metadata);
    }

    private JsonNode findBestMatch(JsonNode matches, String normalizedSymbol) {
        for (JsonNode match : matches) {
            String matchSymbol = safeText(match.path("1. symbol").asText(null));
            if (normalizedSymbol.equalsIgnoreCase(matchSymbol)) {
                return match;
            }
        }

        return matches.get(0);
    }

    private void enrichFromOverview(AssetMetadata metadata) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "OVERVIEW")
                        .queryParam("symbol", metadata.getSymbol())
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        validateAlphaVantageResponse(root);

        String overviewAssetType = safeText(root.path("AssetType").asText(null));

        if (isBlank(metadata.getProviderAssetType()) && !isBlank(overviewAssetType)) {
            metadata.setProviderAssetType(overviewAssetType);
        }

        if (isBlank(metadata.getDisplayName())) {
            metadata.setDisplayName(safeText(root.path("Name").asText(null)));
        }

        if (metadata.getAssetClass() == AssetClass.OTHER && !isBlank(overviewAssetType)) {
            metadata.setAssetClass(mapAssetClass(overviewAssetType, metadata.getSymbol()));
        }

        if (isBlank(metadata.getSector())) {
            metadata.setSector(safeText(root.path("Sector").asText(null)));
        }

        if (isBlank(metadata.getMarket())) {
            metadata.setMarket(safeText(root.path("Exchange").asText(null)));
        }

        if (isBlank(metadata.getCurrency())) {
            metadata.setCurrency(safeText(root.path("Currency").asText(null)));
        }
    }

    private void enrichFromEtfProfile(AssetMetadata metadata) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "ETF_PROFILE")
                        .queryParam("symbol", metadata.getSymbol())
                        .queryParam("apikey", apiKey)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        validateAlphaVantageResponse(root);

        if (isBlank(metadata.getDisplayName())) {
            metadata.setDisplayName(safeText(root.path("name").asText(null)));
        }

        if (isBlank(metadata.getCurrency())) {
            metadata.setCurrency(safeText(root.path("currency").asText(null)));
        }

        if (isBlank(metadata.getMarket())) {
            metadata.setMarket(safeText(root.path("exchange").asText(null)));
        }

        if (isBlank(metadata.getSector())) {
            metadata.setSector("UNCLASSIFIED");
        }

        metadata.setProviderAssetType(
                isBlank(metadata.getProviderAssetType()) ? "ETF" : metadata.getProviderAssetType()
        );
        metadata.setAssetClass(AssetClass.ETF);
    }

    private void applyInitialCapabilities(AssetMetadata metadata) {
        AssetClass assetClass = metadata.getAssetClass();

        boolean externallyPriced = assetClass == AssetClass.EQUITY
                || assetClass == AssetClass.ETF
                || assetClass == AssetClass.BOND
                || assetClass == AssetClass.CRYPTO;

        boolean historicalCandidate = assetClass == AssetClass.EQUITY
                || assetClass == AssetClass.ETF
                || assetClass == AssetClass.BOND;

        metadata.setManualValuationRequired(!externallyPriced);
        metadata.setPriceDataSupported(externallyPriced);
        metadata.setQuoteSupported(externallyPriced);
        metadata.setHistoricalPriceSupported(historicalCandidate);
        metadata.setOptimisationSupported(historicalCandidate);
    }

    private AssetClass mapAssetClass(String providerType, String symbol) {
        String normalized = providerType == null ? "" : providerType.trim().toUpperCase();

        if (normalized.contains("ETF")) {
            return AssetClass.ETF;
        }

        if (normalized.contains("FUND")) {
            return AssetClass.ETF;
        }

        if (normalized.contains("BOND")) {
            return AssetClass.BOND;
        }

        if (normalized.contains("EQUITY")
                || normalized.contains("STOCK")
                || normalized.contains("SHARE")) {
            return AssetClass.EQUITY;
        }

        if (looksLikeMarketSymbol(symbol)) {
            return AssetClass.EQUITY;
        }

        return AssetClass.OTHER;
    }

    private AssetType mapSearchAssetType(String providerType, String symbol) {
        String normalized = providerType == null ? "" : providerType.trim().toUpperCase();

        if (normalized.contains("ETF")) {
            return AssetType.ETF;
        }

        if (normalized.contains("FUND")) {
            return AssetType.ETF;
        }

        if (normalized.contains("BOND")) {
            return AssetType.BOND;
        }

        if (normalized.contains("EQUITY")
                || normalized.contains("STOCK")
                || normalized.contains("SHARE")) {
            return AssetType.EQUITY;
        }

        if (looksLikeMarketSymbol(symbol)) {
            return AssetType.EQUITY;
        }

        return AssetType.OTHER_MANUAL;
    }

    private boolean looksLikeMarketSymbol(String symbol) {
        return symbol != null
                && !symbol.isBlank()
                && symbol.trim().toUpperCase().matches("^[A-Z0-9.\\-]+$");
    }

    private List<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null) {
            return List.of();
        }

        return symbols.stream()
                .filter(symbol -> symbol != null && !symbol.isBlank())
                .map(this::normalizeSymbol)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new
                ));
    }

    private String buildBatchCacheKey(List<String> symbols) {
        return symbols.stream()
                .sorted()
                .collect(Collectors.joining(","));
    }

    public String latestPriceBatchCacheKey(List<String> symbols) {
        List<String> normalizedSymbols = normalizeSymbols(symbols);

        if (normalizedSymbols.isEmpty()) {
            return "EMPTY";
        }

        return buildBatchCacheKey(normalizedSymbols);
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

    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Symbol must not be blank");
        }
        return symbol.trim().toUpperCase();
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void sleepBeforeSingleQuoteFallback() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}