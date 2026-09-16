package com.tsh11.fypcode.service;

import com.tsh11.fypcode.dto.response.BenchmarkSearchResultResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class BenchmarkSearchService {

    private static final int MAX_RESULTS = 20;

    private final List<BenchmarkSearchResultResponse> catalog = new ArrayList<>();

    @PostConstruct
    public void loadCatalog() {
        try {
            ClassPathResource resource = new ClassPathResource("marketdata/index_catalog.csv");

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                boolean firstLine = true;

                while ((line = reader.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }

                    if (line.isBlank()) {
                        continue;
                    }

                    String[] parts = line.split(",", 2);
                    if (parts.length < 2) {
                        continue;
                    }

                    String symbol = parts[0].trim().toUpperCase(Locale.ROOT);
                    String name = parts[1].trim();

                    if (!symbol.isBlank() && !name.isBlank()) {
                        catalog.add(new BenchmarkSearchResultResponse(symbol, name));
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load Alpha Vantage index catalog.", ex);
        }
    }

    public List<BenchmarkSearchResultResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalizedQuery = query.trim().toUpperCase(Locale.ROOT);

        return catalog.stream()
                .filter(item -> matches(item, normalizedQuery))
                .sorted(Comparator
                        .comparingInt((BenchmarkSearchResultResponse item) -> score(item, normalizedQuery))
                        .thenComparing(BenchmarkSearchResultResponse::getSymbol))
                .limit(MAX_RESULTS)
                .toList();
    }

    private boolean matches(BenchmarkSearchResultResponse item, String query) {
        return item.getSymbol().toUpperCase(Locale.ROOT).contains(query)
                || item.getName().toUpperCase(Locale.ROOT).contains(query);
    }

    private int score(BenchmarkSearchResultResponse item, String query) {
        String symbol = item.getSymbol().toUpperCase(Locale.ROOT);
        String name = item.getName().toUpperCase(Locale.ROOT);

        if (symbol.equals(query)) {
            return 0;
        }

        if (symbol.startsWith(query)) {
            return 1;
        }

        if (name.startsWith(query)) {
            return 2;
        }

        if (symbol.contains(query)) {
            return 3;
        }

        return 4;
    }
}