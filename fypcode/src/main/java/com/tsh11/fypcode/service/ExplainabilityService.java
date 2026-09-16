package com.tsh11.fypcode.service;

import com.tsh11.fypcode.dto.ExplanationMode;
import com.tsh11.fypcode.dto.PortfolioMetricsDto;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.request.PortfolioExplanationRequest;
import com.tsh11.fypcode.dto.response.PortfolioExplanationResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

//takes already-calculated optimisation/benchmark data
//builds a beginner-friendly prompt
//sends it through the cached LLM explanation layer
//returns an explanation to the frontend
/*
1. Validates the explanation request.
2. Chooses the explanation title and mode.
3. Builds a prompt using calculated portfolio data.
4. Sends the prompt to the cached LLM service.
5. Returns either an LLM explanation or a fallback explanation.
 */
@Service
public class ExplainabilityService {

    private final CachedLlmExplanationService cachedLlmExplanationService;

    //constructor
    public ExplainabilityService(CachedLlmExplanationService cachedLlmExplanationService) {
        this.cachedLlmExplanationService = cachedLlmExplanationService;
    }

    //main function
    /*
    validate request
    build title
    build prompt
    build cache key
    call CachedLlmExplanationService
    clean returned explanation
    return response
     */
    public PortfolioExplanationResponse explain(PortfolioExplanationRequest request) {
        validateRequest(request);

        String title = buildTitle(request.getMode(), request.getTerm());
        String prompt = buildPrompt(request);

        try {
            String cacheKey = buildCacheKey(request, prompt);
            //CachedLlmExplanationService can reuse previous explanations for the same prompt
            String explanation = cachedLlmExplanationService.explain(cacheKey, prompt);

            PortfolioExplanationResponse response = new PortfolioExplanationResponse();
            response.setMode(request.getMode());
            response.setTitle(title);
            response.setExplanation(cleanExplanation(explanation));
            response.setFallbackUsed(false);
            return response;
        } catch (Exception ex) {
            ex.printStackTrace();

            PortfolioExplanationResponse response = new PortfolioExplanationResponse();
            response.setMode(request.getMode());
            response.setTitle(title);
            response.setExplanation(buildFallbackExplanation(request));
            response.setFallbackUsed(true);
            return response;
        }
    }

    //checks whether the request is valid
    private void validateRequest(PortfolioExplanationRequest request) {
        //reject null request
        if (request == null) {
            throw new IllegalArgumentException("Explanation request must not be null.");
        }

        //reject null explanation mode
        if (request.getMode() == null) {
            throw new IllegalArgumentException("Explanation mode must not be null.");
        }

        //reject TERM mode without a term
        //term explanation mode needs to know which term to explain
        if (request.getMode() == ExplanationMode.TERM) {
            if (request.getTerm() == null || request.getTerm().isBlank()) {
                throw new IllegalArgumentException("A term must be provided for term explanation mode.");
            }
        }
    }

    //controls the user-facing title
    private String buildTitle(ExplanationMode mode, String term) {
        return switch (mode) {
            case SUMMARY -> "Portfolio Explanation";
            case TERM -> "Explanation: " + term;
            case DETAILED -> "Detailed Portfolio Explanation";
            case WARNING -> "Portfolio Warnings and Limitations";
            case PORTFOLIO_BASICS -> "What Is a Finance Portfolio?";
            case ALGORITHM_EXPLANATION -> "How is this portfolio calculated?";
        };
    }

    private String buildPrompt(PortfolioExplanationRequest request) {
        return switch (request.getMode()) {
            case SUMMARY -> buildSummaryPrompt(request);
            case TERM -> buildTermPrompt(request);
            case DETAILED -> buildDetailedPrompt(request);
            case WARNING -> buildWarningPrompt(request);
            case PORTFOLIO_BASICS -> buildPortfolioBasicsPrompt(request);
            case ALGORITHM_EXPLANATION -> buildAlgorithmExplanationPrompt(request);
        };
    }

    //defines the rules given to the LLM
    private String baseInstructions() {
        return """
                You are helping a beginner investor understand calculated portfolio results.

                Write for a finance layman.
                Use simple everyday language.
                Target reading level: age 13-15.
                Use short sentences.
                Avoid technical finance wording unless it is necessary.
                If a finance term appears, explain it immediately in brackets.
                Do not sound like a financial report.
                Do not give personal financial advice.
                Do not tell the user what they should buy or sell.
                Do not describe calculated returns as guaranteed growth.
                Say "the system suggests" or "the calculation suggests", not "you should".
                Do not invent numbers.
                Only use the values given below.
                Do not mention algorithms unless directly useful.
                Do not use markdown.
                Do not use long bullet lists.
                Use at most 4 short sections.
                End with this sentence: "This is educational information, not financial advice."
                """;
    }

    //asks the LLM to explain the overall optimisation result
    private String buildSummaryPrompt(PortfolioExplanationRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(baseInstructions()).append("\n");
        prompt.append("""
                Task:
                Explain the overall portfolio optimisation result in plain language.

                Required output format:
                1. Start with a one-sentence takeaway.
                2. Explain current portfolio vs calculated portfolio in simple words.
                3. Explain the main suggested change.
                4. Explain any warnings only if warnings are provided.

                Avoid:
                - "optimised portfolio" without explaining it
                - "volatility" without explaining it
                - "Sharpe ratio" without explaining it
                - percentages without saying what they mean
                """).append("\n\n");

        appendInputData(prompt, request);

        return prompt.toString();
    }

    //explain finance term
    private String buildTermPrompt(PortfolioExplanationRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(baseInstructions()).append("\n");
        prompt.append("""
                Task:
                Explain one finance term in very simple language.

                Required output format:
                1. One simple definition.
                2. One everyday analogy.
                3. How it relates to this portfolio, if the data is relevant.

                Keep it under 120 words.
                """).append("\n\n");

        prompt.append("Term to explain: ").append(request.getTerm()).append("\n\n");
        appendInputData(prompt, request);

        return prompt.toString();
    }

    //asks for detailed explanation but still beginner-friendly
    private String buildDetailedPrompt(PortfolioExplanationRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(baseInstructions()).append("\n");
        prompt.append("""
                Task:
                Give a detailed explanation, but still suitable for a beginner.

                Required output format:
                1. Main takeaway
                2. What the numbers mean
                3. What changed in the asset mix
                4. Limitations or warnings

                Keep the answer under 350 words.
                """).append("\n\n");

        appendInputData(prompt, request);

        return prompt.toString();
    }

    //explains warnings or excluded assets
    private String buildWarningPrompt(PortfolioExplanationRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(baseInstructions()).append("\n");
        prompt.append("""
                Task:
                Explain warnings or excluded assets in simple language.

                Required output format:
                1. What was excluded or limited
                2. Why that matters
                3. What the user should understand from it

                Do not blame the user.
                Do not use technical API language unless necessary.
                """).append("\n\n");

        appendInputData(prompt, request);

        return prompt.toString();
    }

    //explains what a finance portfolio is to a beginner
    private String buildPortfolioBasicsPrompt(PortfolioExplanationRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(baseInstructions()).append("\n");
        prompt.append("""
            Task:
            Explain what a finance portfolio is to a complete beginner.

            Required output:
            1. Define a portfolio in one simple sentence.
            2. Explain why people hold more than one asset.
            3. Explain what allocation means.
            4. Explain diversification using an everyday example.

            Keep it under 300 words.
            """).append("\n\n");

        appendInputData(prompt, request);

        return prompt.toString();
    }

    //explains how the portfolio calculation works
    private String buildAlgorithmExplanationPrompt(PortfolioExplanationRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append(baseInstructions()).append("\n");

        if (request.getAlgorithm() != null && request.getAlgorithm().name().equals("MEAN_VARIANCE")) {
            prompt.append("""
                Task:
                Explain how the Mean Variance portfolio calculation works to a complete beginner.

                Required output:
                1. Start with one simple sentence explaining what the calculation tries to do.
                2. Explain "return" as possible gain.
                3. Explain "risk" or "volatility" as how much the value may move up and down.
                4. Explain "efficient frontier" as the set of mixes that aim to get the best return for a chosen level of ups and downs.
                5. Explain why the calculated portfolio mix may differ from the current mix.
                6. Mention that it uses past price data and that past data cannot guarantee the future.

                Keep it under 350 words.
                """).append("\n\n");
        } else {
            prompt.append("""
                Task:
                Explain how this portfolio calculation works to a complete beginner.

                Required output:
                1. Explain what the calculation is trying to compare.
                2. Explain the key terms in simple language.
                3. Explain why the calculated portfolio mix may differ from the current mix.
                4. Mention that calculations use available data and cannot guarantee the future.

                Keep it under 350 words.
                """).append("\n\n");
        }

        appendInputData(prompt, request);

        return prompt.toString();
    }

    //main function
    //It adds the calculated data into the prompt.
    private void appendInputData(StringBuilder prompt, PortfolioExplanationRequest request) {
        prompt.append("Calculated data to explain:\n\n");

        appendMetrics(prompt, "Current portfolio", request.getCurrentPortfolio());
        appendMetrics(prompt, "Calculated portfolio", request.getOptimisedPortfolio());

        BigDecimal returnDifference = difference(
                request.getOptimisedPortfolio() != null ? request.getOptimisedPortfolio().getExpectedAnnualReturn() : null,
                request.getCurrentPortfolio() != null ? request.getCurrentPortfolio().getExpectedAnnualReturn() : null
        );

        BigDecimal volatilityDifference = difference(
                request.getOptimisedPortfolio() != null ? request.getOptimisedPortfolio().getAnnualVolatility() : null,
                request.getCurrentPortfolio() != null ? request.getCurrentPortfolio().getAnnualVolatility() : null
        );

        BigDecimal sharpeDifference = difference(
                request.getOptimisedPortfolio() != null ? request.getOptimisedPortfolio().getSharpeRatio() : null,
                request.getCurrentPortfolio() != null ? request.getCurrentPortfolio().getSharpeRatio() : null
        );

        prompt.append("Simple comparison:\n");
        prompt.append("- return change: ").append(formatSignedPercent(returnDifference)).append("\n");
        prompt.append("- volatility change: ").append(formatSignedPercent(volatilityDifference)).append("\n");
        prompt.append("- Sharpe ratio change: ").append(formatSignedDecimal(sharpeDifference)).append("\n\n");

        prompt.append("Asset weight changes:\n");
        prompt.append(formatAssets(request.getAssets())).append("\n\n");

        prompt.append("System suggestions:\n");
        prompt.append(formatLines(request.getSuggestions())).append("\n\n");

        prompt.append("Warnings:\n");
        prompt.append(formatLines(request.getWarnings())).append("\n\n");

        if (request.getBenchmarkSymbol() != null && !request.getBenchmarkSymbol().isBlank()) {
            prompt.append("Benchmark symbol: ").append(request.getBenchmarkSymbol()).append("\n");
        }

        if (request.getBenchmarkSummary() != null && !request.getBenchmarkSummary().isBlank()) {
            prompt.append("Benchmark summary: ").append(request.getBenchmarkSummary()).append("\n");
        }

        if (request.getUserQuestion() != null && !request.getUserQuestion().isBlank()) {
            prompt.append("User question: ").append(request.getUserQuestion()).append("\n");
        }
    }

    //adds portfolio metric values to the prompt
    /*
    expected yearly return
    volatility
    Sharpe ratio
     */
    private void appendMetrics(StringBuilder prompt, String label, PortfolioMetricsDto metrics) {
        prompt.append(label).append(":\n");

        if (metrics == null) {
            prompt.append("- no data\n\n");
            return;
        }

        prompt.append("- expected yearly return: ")
                .append(formatPercent(metrics.getExpectedAnnualReturn()))
                .append(" (possible yearly gain estimated by the calculation)\n");

        prompt.append("- volatility: ")
                .append(formatPercent(metrics.getAnnualVolatility()))
                .append(" (how much the value may move up and down)\n");

        prompt.append("- Sharpe ratio: ")
                .append(formatDecimal(metrics.getSharpeRatio()))
                .append(" (higher usually means more return for the amount of ups and downs)\n\n");
    }

    private String formatAssets(List<PortfolioOptimisationAssetResult> assets) {
        if (assets == null || assets.isEmpty()) {
            return "- none";
        }

        return assets.stream()
                .map(asset -> String.format(
                        "- %s: current %s, calculated target %s, change %s",
                        safe(asset.getSymbol()),
                        formatPercent(asset.getCurrentWeight()),
                        formatPercent(asset.getTargetWeight()),
                        formatSignedPercent(asset.getWeightDifference())
                ))
                .collect(Collectors.joining("\n"));
    }

    private String formatLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "- none";
        }

        return lines.stream()
                .map(line -> "- " + safe(line))
                .collect(Collectors.joining("\n"));
    }

    //fallback
    private String buildFallbackExplanation(PortfolioExplanationRequest request) {
        return switch (request.getMode()) {
            case SUMMARY -> buildSimpleFallbackSummary(request);
            case TERM -> "This term is part of how the system explains your portfolio calculation. This is an explanation of the calculation, not financial advice.";
            case DETAILED -> buildSimpleFallbackSummary(request);
            case WARNING -> "Some assets or data may not have been included in the calculation. This can happen when the system cannot get enough reliable price history. ";
            case PORTFOLIO_BASICS -> "A finance portfolio is the group of things you invest in, such as shares, funds, cash, or property. Holding different assets can help avoid relying too much on one investment.";
            case ALGORITHM_EXPLANATION -> "This calculation compares different portfolio mixes. It looks at possible return and how much the value may move up and down, then looks for a mix that gives a better balance.";
        };
    }

    private String buildSimpleFallbackSummary(PortfolioExplanationRequest request) {
        String currentReturn = request.getCurrentPortfolio() == null
                ? "-"
                : formatPercent(request.getCurrentPortfolio().getExpectedAnnualReturn());

        String optimisedReturn = request.getOptimisedPortfolio() == null
                ? "-"
                : formatPercent(request.getOptimisedPortfolio().getExpectedAnnualReturn());

        String currentVolatility = request.getCurrentPortfolio() == null
                ? "-"
                : formatPercent(request.getCurrentPortfolio().getAnnualVolatility());

        String optimisedVolatility = request.getOptimisedPortfolio() == null
                ? "-"
                : formatPercent(request.getOptimisedPortfolio().getAnnualVolatility());

        return "The calculation compares your current portfolio with a calculated alternative mix. "
                + "Your current expected yearly return is " + currentReturn
                + ", while the calculated mix is " + optimisedReturn + ". "
                + "Your current volatility, meaning how much the value may move up and down, is "
                + currentVolatility + ", compared with " + optimisedVolatility + " for the calculated mix. "
                + "This is an explanation of the calculation, not financial advice.";
    }

    private BigDecimal difference(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return left.subtract(right);
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }

        BigDecimal percent = value.multiply(BigDecimal.valueOf(100));
        return percent.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    private String formatSignedPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }

        BigDecimal percent = value.multiply(BigDecimal.valueOf(100));
        String text = percent.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";

        if (percent.compareTo(BigDecimal.ZERO) > 0) {
            return "+" + text;
        }

        return text;
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String formatSignedDecimal(BigDecimal value) {
        if (value == null) {
            return "-";
        }

        String text = value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        if (value.compareTo(BigDecimal.ZERO) > 0) {
            return "+" + text;
        }

        return text;
    }

    private String cleanExplanation(String explanation) {
        if (explanation == null || explanation.isBlank()) {
            return "The explanation is unavailable right now. This is an explanation of the calculation, not financial advice.";
        }

        return explanation
                .replace("optimised portfolio", "calculated portfolio mix")
                .replace("Optimised portfolio", "Calculated portfolio mix")
                .trim();
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    private String buildCacheKey(PortfolioExplanationRequest request, String prompt) {
        String raw = request.getMode()
                + "|" + safe(request.getTerm())
                + "|" + safe(request.getUserQuestion())
                + "|" + (request.getAlgorithm() == null ? "-" : request.getAlgorithm().name())
                + "|" + prompt;

        return sha256(raw);
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build LLM explanation cache key.", ex);
        }
    }
}