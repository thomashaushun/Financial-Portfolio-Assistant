package com.tsh11.fypcode.service;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.repository.ActivityRepository;
import com.tsh11.fypcode.repository.AssetProfileRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

//It is the service that turns raw activity history into the user’s current holdings.
// this is central because:
//Activities → Holdings → Overview / Allocation / Assistant / Optimisation
//So if HoldingService is wrong, many later pages will also be wrong.
/*
Main process:
Activity records
    ↓
Group by asset / symbol
    ↓
Process BUY and SELL activities
    ↓
Calculate quantity and cost basis
    ↓
Fetch latest price
    ↓
Calculate market value and unrealised gain/loss
    ↓
Return HoldingResponse list to frontend
 */
@Service
@Transactional(readOnly = true)
public class HoldingService {

    private static final int SCALE = 6; //controls decimal precision when dividing numbers

    private final ActivityRepository activityRepository;
    private final AssetProfileRepository assetProfileRepository;
    private final MarketDataProvider marketDataProvider;

    //constructor
    public HoldingService(ActivityRepository activityRepository,
                          AssetProfileRepository assetProfileRepository,
                          MarketDataProvider marketDataProvider) {
        this.activityRepository = activityRepository;
        this.assetProfileRepository = assetProfileRepository;
        this.marketDataProvider = marketDataProvider;

    }

    //main function
    /*
    Core calculation:
    if BUY:
        quantity += buyQuantity
        costBasis += buyQuantity * unitPrice + fee

    if SELL:
        averageCost = costBasis / quantity
        removedCost = averageCost * sellQuantity
        quantity -= sellQuantity
        costBasis -= removedCost
     */
    //If the same user asks for holdings repeatedly,
    // Spring can return cached holdings instead of recalculating and fetching prices every time
    //solution to Alpha Vantage quota/burst issue
    @Cacheable(value = "holdingsByUser", key = "#userId", sync = true)
    public List<HoldingResponse> getHoldings(UUID userId) {
        //loads all activities for the current user.
        List<Activity> activities = activityRepository.findByUserIdOrderByDateDesc(userId);

        //Filter and group activities
        Map<String, List<Activity>> grouped = activities.stream()
                //Keep only activities with symbols
                .filter(activity -> activity.getSymbol() != null && !activity.getSymbol().isBlank())
                //Keep only BUY and SELL
                .filter(activity -> activity.getType() == ActivityType.BUY || activity.getType() == ActivityType.SELL)
                //Group by normalised symbol
                .collect(Collectors.groupingBy(activity -> normalizeSymbol(activity.getSymbol())));

        //Temporary structures
        //storing symbol ,quantity ,cost basis ,average cost ,asset profile, currency ,price support flags
        List<HoldingComputation> computedHoldings = new ArrayList<>();
        //stores the symbols that need latest prices
        LinkedHashSet<String> symbolsForPriceLookup = new LinkedHashSet<>();

        //Loop through each symbol group
        /*
        Example:
        AAPL → all AAPL BUY/SELL activities
        MSFT → all MSFT BUY/SELL activities
         */
        for (Map.Entry<String, List<Activity>> entry : grouped.entrySet()) {
            String symbol = entry.getKey();

            //Sort activities by date ascending
            //If SELL was processed before earlier BUY, cost basis calculation would be wrong.
            List<Activity> symbolActivities = entry.getValue().stream()
                    .sorted(Comparator.comparing(Activity::getDate))
                    .toList();

            //Initialise quantity and cost basis
            BigDecimal quantity = BigDecimal.ZERO;
            BigDecimal costBasis = BigDecimal.ZERO;
            String activityCurrency = null;

            //Process each activity
            for (Activity activity : symbolActivities) {
                activityCurrency = activity.getCurrency();

                //BUY branch
                //nvl() turns null values into zero.
                if (activity.getType() == ActivityType.BUY) {
                    BigDecimal buyQuantity = nvl(activity.getQuantity());
                    BigDecimal unitPrice = nvl(activity.getUnitPrice());
                    BigDecimal fee = nvl(activity.getFee());

                    //If the BUY quantity is zero or negative, ignore it.
                    if (buyQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    //calculates total cost of the BUY
                    //buy cost = quantity × unit price + fee
                    BigDecimal buyCost = buyQuantity.multiply(unitPrice).add(fee);
                    quantity = quantity.add(buyQuantity);
                    costBasis = costBasis.add(buyCost);
                } else if (activity.getType() == ActivityType.SELL) { //SELL branch
                    //nvl() turns null values into zero.
                    BigDecimal sellQuantity = nvl(activity.getQuantity());

                    //If current quantity is zero, ignore the sell because there is nothing to sell.
                    if (sellQuantity.compareTo(BigDecimal.ZERO) <= 0 || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    //prevents selling more than currently held.
                    BigDecimal effectiveSellQuantity = sellQuantity.min(quantity);
                    //calculates average cost before the sale.
                    //average cost = current cost basis ÷ current quantity
                    BigDecimal avgCost = costBasis.divide(quantity, SCALE, RoundingMode.HALF_UP);
                    //calculates how much cost basis should be removed by the sale.
                    //removed cost = average cost × quantity sold
                    BigDecimal removedCost = avgCost.multiply(effectiveSellQuantity);

                    //sell reduces quantity and cost basis
                    quantity = quantity.subtract(effectiveSellQuantity);
                    costBasis = costBasis.subtract(removedCost);

                    //if selling removes all holdings, reset quantity and cost basis to zero
                    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                        quantity = BigDecimal.ZERO;
                        costBasis = BigDecimal.ZERO;
                    }
                }
            }

            //Skip zero holdings
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            //Load asset profile
            //return AssetProfile or null
            AssetProfile assetProfile = assetProfileRepository
                    .findByUserIdAndSymbolIgnoreCase(userId, symbol)
                    .orElse(null);

            //Calculate average cost
            //average cost = remaining cost basis / remaining quantity
            BigDecimal averageCost = BigDecimal.ZERO;
            if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                averageCost = costBasis.divide(quantity, SCALE, RoundingMode.HALF_UP);
            }

            //Resolve currency
            /*
            Priority:
            1. AssetProfile currency
            2. Activity currency
            Asset profile metadata is preferred
             */
            String resolvedCurrency = (assetProfile != null && assetProfile.getCurrency() != null
                    && !assetProfile.getCurrency().isBlank())
                    ? assetProfile.getCurrency()
                    : activityCurrency;

            //Determine valuation flags
            //true only if the asset profile exists and says manual valuation is required
            boolean manualValuationRequired = assetProfile != null && assetProfile.isManualValuationRequired();
            //If no profile exists → assume price data supported
            //If profile exists → use its priceDataSupported flag
            boolean priceDataSupported = assetProfile == null || assetProfile.isPriceDataSupported();

            //decides whether the service should fetch latest market price
            //condition:
            //No profile exists
            //OR
            //Profile exists, manual valuation is not required, and price data is supported
            boolean shouldFetchPrice = assetProfile == null
                    || (!assetProfile.isManualValuationRequired() && assetProfile.isPriceDataSupported());

            //If the asset should have a market price, add its symbol to the batch lookup set
            if (shouldFetchPrice) {
                symbolsForPriceLookup.add(symbol);
            }

            //Store intermediate computation in internal object
            HoldingComputation computation = new HoldingComputation();
            computation.symbol = symbol;
            computation.assetProfile = assetProfile;
            computation.totalQuantity = quantity;
            computation.totalCost = costBasis;
            computation.averageCost = averageCost;
            computation.currency = resolvedCurrency;
            computation.manualValuationRequired = manualValuationRequired;
            computation.priceDataSupported = priceDataSupported;
            computation.shouldFetchPrice = shouldFetchPrice;

            computedHoldings.add(computation);
        }

        // Fetch latest prices in batch
        Map<String, BigDecimal> latestPrices = symbolsForPriceLookup.isEmpty()
                //If there are no symbols requiring price lookup, no market data call
                ? Map.of()
                //if there are symbols, fetch them in bulk
                //avoids calling the provider separately for every holding
                : marketDataProvider.getLatestPrices(new ArrayList<>(symbolsForPriceLookup));

        //Build final HoldingResponse objects
        List<HoldingResponse> holdings = new ArrayList<>();

        for (HoldingComputation computation : computedHoldings) {

            //Latest price
            //If the asset should fetch price, get it from the batch result.
            BigDecimal latestPrice = BigDecimal.ZERO;
            if (computation.shouldFetchPrice) {
                latestPrice = latestPrices.getOrDefault(computation.symbol, BigDecimal.ZERO);
            }

            //calls the helper
            //Use latest price if available and positive.
            //Otherwise use average cost if available and positive.
            //Otherwise use zero.
            BigDecimal valuationPrice = valuationPrice(latestPrice, computation.averageCost);

            //market value = quantity × valuation price
            BigDecimal marketValue = computation.totalQuantity.multiply(valuationPrice);
            //unrealised gain/loss = market value - total cost
            BigDecimal unrealizedGainLoss = marketValue.subtract(computation.totalCost);

            //Unrealised gain/loss percentage
            //unrealised gain/loss % = unrealised gain/loss ÷ total cost × 100
            BigDecimal unrealizedGainLossPercent = BigDecimal.ZERO;
            if (computation.totalCost.compareTo(BigDecimal.ZERO) > 0) {
                unrealizedGainLossPercent = unrealizedGainLoss
                        .divide(computation.totalCost, SCALE, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }

            //Fill HoldingResponse with metadata
            HoldingResponse response = new HoldingResponse();
            response.setSymbol(computation.symbol);
            response.setDisplayName(computation.assetProfile != null ? computation.assetProfile.getDisplayName() : computation.symbol);
            response.setAssetClass(computation.assetProfile != null ? computation.assetProfile.getAssetClass() : null);
            response.setSector(computation.assetProfile != null ? computation.assetProfile.getSector() : null);
            response.setMarket(computation.assetProfile != null ? computation.assetProfile.getMarket() : null);
            response.setManualValuationRequired(computation.manualValuationRequired);
            response.setPriceDataSupported(computation.priceDataSupported);

            // Bug fix on 14 May 2026, fixing eligible asset for optimization not getting optimized.
            // Even if the stored flags say historical/optimization support is false
            // still optimize EQUITY, ETF, or BOND
            // start
            boolean inferredHistoricalSupport = computation.assetProfile != null
                    && supportsHistoricalPrices(computation.assetProfile.getAssetClass())
                    && !computation.manualValuationRequired
                    && computation.priceDataSupported;

            response.setHistoricalPriceSupported(
                    computation.assetProfile != null
                            && (computation.assetProfile.isHistoricalPriceSupported() || inferredHistoricalSupport)
            );

            response.setOptimisationSupported(
                    computation.assetProfile != null
                            && (computation.assetProfile.isOptimisationSupported() || inferredHistoricalSupport)
            );
            //BUg fix end

            response.setTotalQuantity(computation.totalQuantity);
            response.setTotalCost(computation.totalCost);
            response.setAverageCost(computation.averageCost);
            response.setCurrency(computation.currency);

            response.setLatestPrice(valuationPrice);
            response.setMarketValue(marketValue);
            response.setUnrealizedGainLoss(unrealizedGainLoss);
            response.setUnrealizedGainLossPercent(unrealizedGainLossPercent);

            holdings.add(response);
        }

        //Sorts holdings alphabetically by symbol.
        //Then returns the list.
        holdings.sort(Comparator.comparing(HoldingResponse::getSymbol));
        return holdings;
    }

    //helper functions
    //decides which price to use for valuation.
    private BigDecimal valuationPrice(BigDecimal latestPrice, BigDecimal averageCost) {
        /*
        Priority:
        1. Positive latest market price
        2. Positive average cost
        3. Zero
         */
        if (latestPrice != null && latestPrice.compareTo(BigDecimal.ZERO) > 0) {
            return latestPrice;
        }

        if (averageCost != null && averageCost.compareTo(BigDecimal.ZERO) > 0) {
            return averageCost;
        }

        return BigDecimal.ZERO;
    }

    //“null value” helper
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase();
    }

    // Helper for bug fix 14 May 2026
    //define which asset classes supports historical prices
    private boolean supportsHistoricalPrices(AssetClass assetClass) {
        return assetClass == AssetClass.EQUITY
                || assetClass == AssetClass.ETF
                || assetClass == AssetClass.BOND;
    }

    //Store intermediate holding calculation results before latest prices are fetched and final HoldingResponse objects are created.
    private static class HoldingComputation {
        private String symbol;
        private AssetProfile assetProfile;
        private BigDecimal totalQuantity;
        private BigDecimal averageCost;
        private BigDecimal totalCost;
        private String currency;
        private boolean manualValuationRequired;
        private boolean priceDataSupported;
        private boolean shouldFetchPrice;
    }
}