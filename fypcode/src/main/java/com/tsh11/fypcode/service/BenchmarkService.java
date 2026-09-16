package com.tsh11.fypcode.service;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.dto.response.BenchmarkComparisonResponse;
import com.tsh11.fypcode.dto.response.BenchmarkPointResponse;
import com.tsh11.fypcode.integration.marketdata.HistoricalPricePoint;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class BenchmarkService {

    private static final int SCALE = 6;

    private final ActivityRepository activityRepository;
    private final MarketDataProvider marketDataProvider;

    public BenchmarkService(ActivityRepository activityRepository,
                            MarketDataProvider marketDataProvider) {
        this.activityRepository = activityRepository;
        this.marketDataProvider = marketDataProvider;
    }

    //main function
    public BenchmarkComparisonResponse compareAgainstBenchmark(UUID userId,
                                                               String benchmarkSymbol,
                                                               LocalDate from,
                                                               LocalDate to) {
        //date validation
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid date range");
        }

        //normalise the benchmark symbol
        //" spx " → "SPX"
        String normalisedBenchmarkSymbol = benchmarkSymbol == null
                ? ""
                : benchmarkSymbol.trim().toUpperCase();

        //rejects missing benchmark symbols.
        if (normalisedBenchmarkSymbol.isBlank()) {
            throw new IllegalArgumentException("Benchmark symbol must not be blank");
        }

        //Load relevant activities
        /*
        Only:
        BUY activities
        SELL activities
        activities with a valid symbol
         */
        List<Activity> activities = activityRepository.findByUserIdOrderByDateDesc(userId).stream()
                .filter(a -> a.getType() == ActivityType.BUY || a.getType() == ActivityType.SELL)
                .filter(a -> a.getSymbol() != null && !a.getSymbol().isBlank())
                .toList();

        //If no activities, return empty response
        if (activities.isEmpty()) {
            BenchmarkComparisonResponse empty = new BenchmarkComparisonResponse();
            empty.setBenchmarkSymbol(benchmarkSymbol);
            empty.setFromDate(from);
            empty.setToDate(to);
            empty.setStartingPortfolioValue(BigDecimal.ZERO);
            empty.setEndingPortfolioValue(BigDecimal.ZERO);
            empty.setPortfolioReturnPercent(BigDecimal.ZERO);
            empty.setStartingBenchmarkValue(BigDecimal.ZERO);
            empty.setEndingBenchmarkValue(BigDecimal.ZERO);
            empty.setBenchmarkReturnPercent(BigDecimal.ZERO);
            empty.setExcessReturnPercent(BigDecimal.ZERO);
            empty.setPoints(List.of());
            return empty;
        }

        //Group activities by symbol
        //e.g.
        //AAPL → all AAPL BUY/SELL activities
        //MSFT → all MSFT BUY/SELL activities
        Map<String, List<Activity>> activitiesBySymbol = activities.stream()
                .collect(Collectors.groupingBy(a -> a.getSymbol().trim().toUpperCase()));

        //Fetch historical prices for each portfolio asset
        //e.g. AAPL historical prices,MSFT historical prices
        Map<String, List<HistoricalPricePoint>> priceHistoryBySymbol = new HashMap<>();
        for (String symbol : activitiesBySymbol.keySet()) {
            priceHistoryBySymbol.put(symbol, marketDataProvider.getHistoricalPrices(symbol, from, to));
        }

        //Fetch benchmark historical prices
        // e.g. SPX historical index prices
        List<HistoricalPricePoint> benchmarkHistory =
                marketDataProvider.getHistoricalIndexPrices(normalisedBenchmarkSymbol, from, to);

        //Convert benchmark history into a map
        /*
        Create:
        date → benchmark close price
         */
        /*
        Example:
        2026-05-01 → 5200
        2026-05-02 → 5225
         */
        Map<LocalDate, BigDecimal> benchmarkMap = benchmarkHistory.stream()
                .collect(Collectors.toMap(HistoricalPricePoint::getDate, HistoricalPricePoint::getClose));

        //Find common dates in:
        //benchmark history
        //AND every portfolio asset’s historical price history
        //The chart should compare portfolio and benchmark values on the SAME dates.
        Set<LocalDate> commonDates = new TreeSet<>(benchmarkMap.keySet());
        for (List<HistoricalPricePoint> history : priceHistoryBySymbol.values()) {
            Set<LocalDate> symbolDates = history.stream()
                    .map(HistoricalPricePoint::getDate)
                    .collect(Collectors.toSet());
            commonDates.retainAll(symbolDates);
        }

        // Build raw comparison points before indexed values are calculated.
        List<BenchmarkPointResponse> points = new ArrayList<>();

        //For each common date:
        for (LocalDate date : commonDates) {
            //Calculate portfolio value on each date
            //start with 0
            BigDecimal portfolioValue = BigDecimal.ZERO;

            //for each asset symbol, calculates how many units of that asset were held on that date
            /*
            example
            On 2026-05-10:
            AAPL quantity = 10
            MSFT quantity = 5
             */
            for (Map.Entry<String, List<Activity>> entry : activitiesBySymbol.entrySet()) {
                String symbol = entry.getKey();
                BigDecimal quantityHeld = quantityHeldOnDate(entry.getValue(), date);

                //If the user held none of that asset on that date, skip it
                if (quantityHeld.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                //gets the asset closing price for that date and adds its value to portfolio value
                /*
                Formula:
                asset value on date = quantity held × closing price
                portfolio value = sum of all asset values
                 */
                /*
                Example:
                AAPL: 10 × 180 = 1800
                MSFT: 5 × 300 = 1500
                portfolio value = 3300
                 */
                BigDecimal close = closeForDate(priceHistoryBySymbol.get(symbol), date);
                portfolioValue = portfolioValue.add(quantityHeld.multiply(close));
            }

            //Create one chart point
            BenchmarkPointResponse point = new BenchmarkPointResponse();
            //date
            point.setDate(date);
            //portfolio value
            point.setPortfolioValue(portfolioValue);
            //benchmark value
            point.setBenchmarkValue(benchmarkMap.getOrDefault(date, BigDecimal.ZERO));

            points.add(point);
        }

        //If no points, return empty response
        if (points.isEmpty()) {
            BenchmarkComparisonResponse empty = new BenchmarkComparisonResponse();
            empty.setBenchmarkSymbol(normalisedBenchmarkSymbol);
            empty.setFromDate(from);
            empty.setToDate(to);
            empty.setStartingPortfolioValue(BigDecimal.ZERO);
            empty.setEndingPortfolioValue(BigDecimal.ZERO);
            empty.setPortfolioReturnPercent(BigDecimal.ZERO);
            empty.setStartingBenchmarkValue(BigDecimal.ZERO);
            empty.setEndingBenchmarkValue(BigDecimal.ZERO);
            empty.setBenchmarkReturnPercent(BigDecimal.ZERO);
            empty.setExcessReturnPercent(BigDecimal.ZERO);
            empty.setPoints(List.of());
            return empty;
        }

        // Ignore dates before the portfolio had positive value.
        // This avoids calculating returns from a zero starting value.
        //keep only points where portfolio value is positive
        List<BenchmarkPointResponse> positivePortfolioPoints = points.stream()
                .filter(point -> point.getPortfolioValue() != null)
                .filter(point -> point.getPortfolioValue().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        //If there are no positive-value portfolio points, return empty response.
        if (positivePortfolioPoints.isEmpty()) {
            BenchmarkComparisonResponse empty = new BenchmarkComparisonResponse();
            empty.setBenchmarkSymbol(benchmarkSymbol.trim().toUpperCase());
            empty.setFromDate(from);
            empty.setToDate(to);
            empty.setStartingPortfolioValue(BigDecimal.ZERO);
            empty.setEndingPortfolioValue(BigDecimal.ZERO);
            empty.setPortfolioReturnPercent(BigDecimal.ZERO);
            empty.setStartingBenchmarkValue(BigDecimal.ZERO);
            empty.setEndingBenchmarkValue(BigDecimal.ZERO);
            empty.setBenchmarkReturnPercent(BigDecimal.ZERO);
            empty.setExcessReturnPercent(BigDecimal.ZERO);
            empty.setPoints(List.of());
            return empty;
        }

        points = positivePortfolioPoints;

        //Identify start and end values
        //The first point becomes the starting value
        //The last point becomes the ending value
        BigDecimal startPortfolio = points.get(0).getPortfolioValue();
        BigDecimal endPortfolio = points.get(points.size() - 1).getPortfolioValue();
        BigDecimal startBenchmark = points.get(0).getBenchmarkValue();
        BigDecimal endBenchmark = points.get(points.size() - 1).getBenchmarkValue();

        //Index both series to 100
        //Raw portfolio and benchmark values may be different,
        // e.g portfolio = 10000, benchmark = 5000
        //So they are converted to a common baseline
        /*
        Formula:
        indexed value = current value / starting value × 100
         */
        /*
        Example:
        Portfolio:
        start 10,000 → 100
        end 11,000 → 110
        Benchmark:
        start 5,000 → 100
        end 5,250 → 105
         */
        for (BenchmarkPointResponse point : points) {
            point.setPortfolioIndexed(indexTo100(point.getPortfolioValue(), startPortfolio));
            point.setBenchmarkIndexed(indexTo100(point.getBenchmarkValue(), startBenchmark));
        }

        //Build final response
        BenchmarkComparisonResponse response = new BenchmarkComparisonResponse();
        response.setBenchmarkSymbol(normalisedBenchmarkSymbol);
        response.setFromDate(points.get(0).getDate());
        response.setToDate(points.get(points.size() - 1).getDate());

        response.setStartingPortfolioValue(startPortfolio);
        response.setEndingPortfolioValue(endPortfolio);
        response.setPortfolioReturnPercent(returnPercent(startPortfolio, endPortfolio));

        response.setStartingBenchmarkValue(startBenchmark);
        response.setEndingBenchmarkValue(endBenchmark);
        response.setBenchmarkReturnPercent(returnPercent(startBenchmark, endBenchmark));

        //excess return
        //Formula:
        //excess return = portfolio return - benchmark return
        /*
        Example:
        portfolio return = 10%
        benchmark return = 6%
        excess return = 4%
         */
        response.setExcessReturnPercent(
                response.getPortfolioReturnPercent().subtract(response.getBenchmarkReturnPercent())
        );
        response.setPoints(points);

        return response;
    }

    //helper
    //calculates how many units of one asset the user held on a specific date
    private BigDecimal quantityHeldOnDate(List<Activity> activities, LocalDate date) {
        //Start with zero
        BigDecimal quantity = BigDecimal.ZERO;

        //Loop through activities for that symbol
        for (Activity activity : activities) {
            //If the activity happened after the date being evaluated, ignore it
            LocalDate activityDate = activity.getDate().atZone(ZoneOffset.UTC).toLocalDate();
            if (activityDate.isAfter(date)) {
                continue;
            }

            //BUY increases quantity
            //SELL decreases quantity
            if (activity.getType() == ActivityType.BUY) {
                quantity = quantity.add(nvl(activity.getQuantity()));
            } else if (activity.getType() == ActivityType.SELL) {
                quantity = quantity.subtract(nvl(activity.getQuantity()));
            }
        }

        //If the result would be negative, return zero
        return quantity.max(BigDecimal.ZERO);
    }

    //find the closing price for a symbol on a specific date
    private BigDecimal closeForDate(List<HistoricalPricePoint> history, LocalDate date) {
        for (HistoricalPricePoint point : history) {
            if (point.getDate().equals(date)) {
                return point.getClose();
            }
        }
        return BigDecimal.ZERO;
    }

    //converts a raw value into an indexed value with the start equal to 100
    //Formula:
    //indexed value = value / start × 100
    private BigDecimal indexTo100(BigDecimal value, BigDecimal start) {
        if (start == null || start.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(start, SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    //calculates return percentage
    //Formula:
    //return % = (end - start) / start × 100
    private BigDecimal returnPercent(BigDecimal start, BigDecimal end) {
        if (start == null || start.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return end.subtract(start)
                .divide(start, SCALE, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    //null value to 0
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}