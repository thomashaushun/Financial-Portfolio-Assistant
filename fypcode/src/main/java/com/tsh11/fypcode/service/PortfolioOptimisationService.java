package com.tsh11.fypcode.service;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.dto.PortfolioAlgorithm;
import com.tsh11.fypcode.dto.PortfolioOptimisationAssetResult;
import com.tsh11.fypcode.dto.PortfolioOptimisationRequest;
import com.tsh11.fypcode.dto.PortfolioOptimisationResult;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.integration.marketdata.HistoricalPricePoint;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.service.portfolio.PortfolioConstraintSet;
import com.tsh11.fypcode.service.portfolio.PortfolioOptimisationAlgorithm;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

//orchestration service for portfolio optimisation
//Given the user’s current eligible holdings and historical price data,
// what target allocation does the selected optimisation algorithm calculate?
@Service
public class PortfolioOptimisationService {

    //controls precision for decimal calculations
    private static final MathContext MC = MathContext.DECIMAL64;
    //risk-free rate: the return an investor could theoretically earn from an investment with almost no risk
    private static final BigDecimal DEFAULT_RISK_FREE_RATE = new BigDecimal("0.02");

    private final HoldingService holdingService;
    private final MarketDataProvider marketDataProvider;
    private final List<PortfolioOptimisationAlgorithm> algorithms;
    private final AssetProfileService assetProfileService;

    //constructor
    public PortfolioOptimisationService(
            HoldingService holdingService,
            MarketDataProvider marketDataProvider,
            List<PortfolioOptimisationAlgorithm> algorithms,
            AssetProfileService assetProfileService) {
        this.holdingService = holdingService;
        this.marketDataProvider = marketDataProvider;
        this.algorithms = algorithms;
        this.assetProfileService = assetProfileService;
    }

    //main function
    //to run the full portfolio optimisation workflow for user
    //takes the user ID, date range, and selected algorithm,
    // then prepares all the required data, runs the chosen optimisation algorithm,
    // returns the final optimisation result for the frontend to display.
    public PortfolioOptimisationResult optimise(
            UUID userId,
            LocalDate from,
            LocalDate to,
            PortfolioAlgorithm algorithmType) {

        //Validate inputs, no null, from is not after to
        validateInputs(userId, from, to, algorithmType);

        //Load all holdings
        List<HoldingResponse> allHoldings = holdingService.getHoldings(userId);
        //Filter eligible holdings
        List<HoldingResponse> eligibleHoldings = filterEligibleHoldings(allHoldings);

        //logging start
        System.out.println("Eligible holdings:");
        for (HoldingResponse holding : eligibleHoldings) {
            System.out.println(
                    holding.getSymbol()
                            + " | class=" + holding.getAssetClass()
                            + " | manual=" + holding.isManualValuationRequired()
                            + " | priceSupported=" + holding.isPriceDataSupported()
                            + " | marketValue=" + holding.getMarketValue()
            );
        }
        //loggin end

        //Reject no eligible assets
        if (eligibleHoldings.isEmpty()) {
            throw new IllegalArgumentException("No eligible assets available for portfolio optimisation.");
        }

        //Calculate current weights
        /*
        Example:
        AAPL value = £600
        MSFT value = £400
        Total = £1000

        AAPL weight = 0.60
        MSFT weight = 0.40
         */
        Map<String, BigDecimal> currentWeights = calculateCurrentWeights(eligibleHoldings);
        //Build daily returns
        /*
        Example:
        price series: 100, 105, 103
        daily returns: 5%, -1.90%
         */
        Map<String, List<BigDecimal>> dailyReturns = buildDailyReturns(userId, eligibleHoldings, from, to);
        //Get final symbols with return data
        List<String> symbols = new ArrayList<>(dailyReturns.keySet());

        //Require at least 2 eligible assets
        if (symbols.size() < 2) {
            throw new IllegalArgumentException("At least two eligible assets with historical data are required.");
        }

        //Build PortfolioOptimisationRequest
        PortfolioOptimisationRequest request = new PortfolioOptimisationRequest();
        request.setAlgorithm(algorithmType);
        request.setFrom(from);
        request.setTo(to);
        request.setRiskFreeRate(DEFAULT_RISK_FREE_RATE);
        request.setSymbols(symbols);
        request.setCurrentWeights(retainMatchingWeights(currentWeights, symbols));
        request.setDailyReturns(dailyReturns);
        request.setConstraints(defaultConstraints());

        //Resolve selected algorithm
        /* MEAN_VARIANCE → MeanVarianceOptimisationAlgorithm
           RISK_PARITY → RiskParityOptimisationAlgorithm */
        PortfolioOptimisationAlgorithm algorithm = resolveAlgorithm(algorithmType);
        //Run algorithm
        PortfolioOptimisationResult result = algorithm.optimise(request);

        //Add suggested changes
        /*
        Fields included:
        current value
        target value
        value difference
        latest price
        suggested unit change
        suggested action: BUY / REDUCE / HOLD
         */
        addSuggestedChanges(result, eligibleHoldings);
        //Add warnings for excluded assets
        /*
        Causes:
        Not eligible for optimisation
        defined as eligible, but insufficient historical data
         */
        addWarningsForExcludedAssets(result, allHoldings, eligibleHoldings, symbols);

        return result;
    }

    //helper

    //validates the request
    /*
    userId is null
    from is null
    to is null
    from is after to
    algorithmType is null
     */
    private void validateInputs(
            UUID userId,
            LocalDate from,
            LocalDate to,
            PortfolioAlgorithm algorithmType) {

        if (userId == null) {
            throw new IllegalArgumentException("User ID must not be null.");
        }
        if (from == null) {
            throw new IllegalArgumentException("From date must not be null.");
        }
        if (to == null) {
            throw new IllegalArgumentException("To date must not be null.");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("From date must be on or before to date.");
        }
        if (algorithmType == null) {
            throw new IllegalArgumentException("Portfolio algorithm must not be null.");
        }
    }

    //filters the current holdings to assets that can be used in optimisation
    private List<HoldingResponse> filterEligibleHoldings(List<HoldingResponse> holdings) {
        List<HoldingResponse> eligible = new ArrayList<>();

        for (HoldingResponse holding : holdings) {
            //Skips null holding
            if (holding == null) {
                continue;
            }

            //Skips holdings without symbol
            String symbol = normaliseSymbol(holding.getSymbol());
            if (symbol.isBlank()) {
                continue;
            }

            //Skips manual valuation assets, e.g. Real estate, Other
            //they have no historic data to run optimization algorithm on
            if (holding.isManualValuationRequired()) {
                continue;
            }

            //Skips assets without market price support
            //e.g. Asset not recognised by Alpha Vantage, Fallback asset profile created after metadata lookup failed
            if (!holding.isPriceDataSupported()) {
                continue;
            }

            //Skips assets not marked as optimisation-supported
            //e.g. Assets with failed historical price fetch, Assets with no historical data
            if (!holding.isOptimisationSupported()) {
                continue;
            }

            // Changed to fix bug 13 May 2026
            //Skips assets with no positive market value or cost fallback.
            //prevents invalid assets
            if (optimisationValue(holding).compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            eligible.add(holding);
        }

        return eligible;
    }

    // Changed to fix bug 13 May 2026
    //calculates the current allocation weights of eligible holdings
    private Map<String, BigDecimal> calculateCurrentWeights(List<HoldingResponse> holdings) {
        Map<String, BigDecimal> weights = new LinkedHashMap<>();

        //calculates the total value of eligible assets
        BigDecimal totalPortfolioValue = holdings.stream()
                //call optimisationValue(holding)
                .map(this::optimisationValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //Reject zero or negative total value, prevent divide by 0
        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total portfolio value must be positive.");
        }

        //for each holding
        for (HoldingResponse holding : holdings) {
            //Normalise symbol
            String symbol = normaliseSymbol(holding.getSymbol());
            //Get the holding value
            BigDecimal value = optimisationValue(holding);
            //Calculate current weight
            //Formula: current weight = holding value / total eligible portfolio value
            /*
            Example:
            AAPL value = £600
            MSFT value = £400
            Total value = £1000

            AAPL weight = 600 / 1000 = 0.60
            MSFT weight = 400 / 1000 = 0.40
             */
            BigDecimal weight = value.divide(totalPortfolioValue, MC);
            weights.put(symbol, weight);
        }

        return weights;
    }

    //builds the historical daily return series for eligible assets.
    private Map<String, List<BigDecimal>> buildDailyReturns(
            UUID userId,
            List<HoldingResponse> holdings,
            LocalDate from,
            LocalDate to) {

        Map<String, List<BigDecimal>> rawReturns = new LinkedHashMap<>();

        //for each holding
        for (HoldingResponse holding : holdings) {
            String symbol = normaliseSymbol(holding.getSymbol());

            List<HistoricalPricePoint> prices;
            try {
                //fetches historical prices from the market data provider
                prices = marketDataProvider.getHistoricalPrices(symbol, from, to);
                System.out.println(symbol + " historical prices fetched: " + (prices == null ? 0 : prices.size()));
            } catch (Exception ex) {
                System.out.println(symbol + " historical price fetch failed: " + ex.getMessage());
                assetProfileService.markHistoricalUnsupported(userId, symbol);
                continue;
            }

            //converts prices into daily returns
            List<BigDecimal> returns = toDailyReturns(prices);

            if (!returns.isEmpty()) {
                rawReturns.put(symbol, returns);
            }
        }

        //aligns return series so all assets have the same length
        return alignReturnSeries(rawReturns);
    }

    //converts historical prices into daily returns
    private List<BigDecimal> toDailyReturns(List<HistoricalPricePoint> prices) {
        List<BigDecimal> returns = new ArrayList<>();

        //at least two prices are needed to calculate one return, reject null or less than 2
        if (prices == null || prices.size() < 2) {
            return returns;
        }

        //Sorts prices chronologically
        prices.sort((left, right) -> left.getDate().compareTo(right.getDate()));

        //for each pair
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal previous = prices.get(i - 1).getClose();
            BigDecimal current = prices.get(i).getClose();

            if (previous == null || current == null || previous.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            //Formula: daily return = (current close - previous close) / previous close
            /*
            Example:
            previous = 100
            current = 105
            return = (105 - 100) / 100 = 0.05
             */
            BigDecimal dailyReturn = current.subtract(previous, MC).divide(previous, MC);
            returns.add(dailyReturn);
        }

        return returns;
    }

    //make all return series the same length
    //Optimisation algorithms need aligned return vectors of equal length
    /*
    Example:
    AAPL returns length = 100
    MSFT returns length = 90

    Aligned length = 90
    AAPL uses latest 90 returns
    MSFT uses latest 90 returns
     */
    private Map<String, List<BigDecimal>> alignReturnSeries(Map<String, List<BigDecimal>> rawReturns) {
        //find the shortest return series
        int minSize = rawReturns.values().stream()
                .mapToInt(List::size)
                .min()
                .orElse(0);

        Map<String, List<BigDecimal>> aligned = new LinkedHashMap<>();

        if (minSize <= 0) {
            return aligned;
        }

        for (Map.Entry<String, List<BigDecimal>> entry : rawReturns.entrySet()) {
            List<BigDecimal> values = entry.getValue();

            if (values.size() >= minSize) {
                //each asset’s returns are trimmed to the most recent minSize values
                aligned.put(entry.getKey(), values.subList(values.size() - minSize, values.size()));
            }
        }

        return aligned;
    }

    //filters current weights to only symbols that have usable daily returns
    private Map<String, BigDecimal> retainMatchingWeights(
            Map<String, BigDecimal> currentWeights,
            List<String> symbols) {

        Map<String, BigDecimal> filtered = new LinkedHashMap<>();

        for (String symbol : symbols) {
            filtered.put(symbol, currentWeights.getOrDefault(symbol, BigDecimal.ZERO));
        }

        return filtered;
    }

    //creates the default optimisation constraints
    /*
    Long-only = no short selling
    Minimum weight = 0%
    Maximum weight per asset = 60%
     */
    private PortfolioConstraintSet defaultConstraints() {
        PortfolioConstraintSet constraints = new PortfolioConstraintSet();
        constraints.setLongOnly(true);
        constraints.setMinWeightPerAsset(BigDecimal.ZERO);
        constraints.setMaxWeightPerAsset(new BigDecimal("0.60"));
        return constraints;
    }

    //selects the algorithm implementation
    private PortfolioOptimisationAlgorithm resolveAlgorithm(PortfolioAlgorithm algorithmType) {
        //search through the injected algorithm list:
        return algorithms.stream()
                .filter(algorithm -> algorithm.getAlgorithmType() == algorithmType)
                .findFirst()
                //If no matching algorithm exists, throw exception
                .orElseThrow(() -> new IllegalArgumentException("Unsupported algorithm: " + algorithmType));
    }

    //adds warnings explaining exclusions
    private void addWarningsForExcludedAssets(
            PortfolioOptimisationResult result,
            List<HoldingResponse> allHoldings,
            List<HoldingResponse> eligibleHoldings,
            List<String> finalSymbols) {

        //builds a list of initially eligible symbols
        List<String> eligibleSymbols = eligibleHoldings.stream()
                .map(HoldingResponse::getSymbol)
                .map(this::normaliseSymbol)
                .toList();

        //loop through all holdings
        for (HoldingResponse holding : allHoldings) {
            String symbol = normaliseSymbol(holding.getSymbol());

            if (symbol.isBlank()) {
                continue;
            }

            //If a symbol was never eligible, throw this warning
            if (!eligibleSymbols.contains(symbol)) {
                result.getWarnings().add(symbol + " was excluded because it is not eligible for optimisation.");
                continue;
            }

            //If a symbol is eligible but not in final symbols, throw this warning
            if (!finalSymbols.contains(symbol)) {
                result.getWarnings().add(symbol + " was excluded because sufficient historical data was not available.");
            }
        }
    }

    private String normaliseSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase();
    }

    //Enrich output with practical change suggestions
    private void addSuggestedChanges(
            PortfolioOptimisationResult result,
            List<HoldingResponse> eligibleHoldings) {

        if (result == null || result.getAssets() == null || result.getAssets().isEmpty()) {
            return;
        }

        //map holdings by symbol
        Map<String, HoldingResponse> holdingsBySymbol = eligibleHoldings.stream()
                .collect(Collectors.toMap(
                        holding -> normaliseSymbol(holding.getSymbol()),
                        holding -> holding,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        // Changed to fix bug 13 May 2026
        //calculates total portfolio value using optimisationValue
        BigDecimal totalPortfolioValue = eligibleHoldings.stream()
                .map(this::optimisationValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //Stop if total value is not positive
        if (totalPortfolioValue.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        //for each asset result:
        for (PortfolioOptimisationAssetResult asset : result.getAssets()) {
            //Match optimisation asset to current holding
            String symbol = normaliseSymbol(asset.getSymbol());
            HoldingResponse holding = holdingsBySymbol.get(symbol);

            //If no matching holding is found, skip this asset
            if (holding == null) {
                continue;
            }

            BigDecimal currentValue = optimisationValue(holding);
            BigDecimal unitPriceForSuggestion = suggestedUnitPrice(holding);
            BigDecimal targetWeight = nvl(asset.getTargetWeight());

            //Formula: target value = total eligible portfolio value × target weight
            BigDecimal targetValue = totalPortfolioValue.multiply(targetWeight, MC);
            //Formula: value difference = target value - current value
            BigDecimal valueDifference = targetValue.subtract(currentValue, MC);

            //Formula: suggested unit change = value difference / unit price
            BigDecimal suggestedUnitChange = BigDecimal.ZERO;
            if (unitPriceForSuggestion.compareTo(BigDecimal.ZERO) > 0) {
                suggestedUnitChange = valueDifference.divide(unitPriceForSuggestion, MC);
            }

            //Save calculated values into the asset result
            asset.setCurrentValue(currentValue);
            asset.setTargetValue(targetValue);
            asset.setValueDifference(valueDifference);
            asset.setLatestPrice(unitPriceForSuggestion);
            asset.setSuggestedUnitChange(suggestedUnitChange);
            //toSuggestedAction
            asset.setSuggestedAction(toSuggestedAction(valueDifference));
        }
    }

    //null safety helper
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /*
    Logic:
    valueDifference > 0 → BUY
    valueDifference < 0 → REDUCE
    valueDifference = 0 → HOLD
    null → HOLD
     */
    private String toSuggestedAction(BigDecimal valueDifference) {
        if (valueDifference == null) {
            return "HOLD";
        }

        int comparison = valueDifference.compareTo(BigDecimal.ZERO);

        if (comparison > 0) {
            return "BUY";
        }

        if (comparison < 0) {
            return "REDUCE";
        }

        return "HOLD";
    }

    //decides what value to use for optimisation
    /*
    Priority
    1. Market value
    2. Total cost
    3. Zero
     */
    private BigDecimal optimisationValue(HoldingResponse holding) {
        if (holding == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal marketValue = holding.getMarketValue();
        if (marketValue != null && marketValue.compareTo(BigDecimal.ZERO) > 0) {
            return marketValue;
        }

        BigDecimal totalCost = holding.getTotalCost();
        if (totalCost != null && totalCost.compareTo(BigDecimal.ZERO) > 0) {
            return totalCost;
        }

        return BigDecimal.ZERO;
    }

    //decides what unit price to use when calculating suggested unit changes
    /*
    1. Latest price
    2. Average cost
    3. Zero
     */
    private BigDecimal suggestedUnitPrice(HoldingResponse holding) {
        if (holding == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal latestPrice = holding.getLatestPrice();
        if (latestPrice != null && latestPrice.compareTo(BigDecimal.ZERO) > 0) {
            return latestPrice;
        }

        BigDecimal averageCost = holding.getAverageCost();
        if (averageCost != null && averageCost.compareTo(BigDecimal.ZERO) > 0) {
            return averageCost;
        }

        return BigDecimal.ZERO;
    }
}