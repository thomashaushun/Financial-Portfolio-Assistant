package com.tsh11.fypcode.service.portfolio;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RebalanceSuggestionCalculator {

    private static final BigDecimal MATERIALITY_THRESHOLD = new BigDecimal("0.01");

    public List<String> calculateSuggestions(
            Map<String, BigDecimal> currentWeights,
            Map<String, BigDecimal> targetWeights) {

        List<String> suggestions = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : targetWeights.entrySet()) {
            String symbol = entry.getKey();
            BigDecimal target = entry.getValue();
            BigDecimal current = currentWeights.getOrDefault(symbol, BigDecimal.ZERO);
            BigDecimal difference = target.subtract(current);

            if (difference.abs().compareTo(MATERIALITY_THRESHOLD) < 0) {
                continue;
            }

            if (difference.compareTo(BigDecimal.ZERO) > 0) {
                suggestions.add("Increase allocation to " + symbol);
            } else {
                suggestions.add("Reduce allocation to " + symbol);
            }
        }

        return suggestions;
    }
}