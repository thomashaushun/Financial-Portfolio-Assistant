package com.tsh11.fypcode.service;

import com.tsh11.fypcode.dto.response.AllocationItemResponse;
import com.tsh11.fypcode.dto.response.AllocationResponse;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/*
AllocationService gets the holdings from HoldingService,
groups them by different categories,
calculates the value of each group,
calculates each group’s percentage weight,
and returns the result to the Allocation page.
 */
@Service
@Transactional(readOnly = true)
public class AllocationService {

    //controls decimal precision when calculating percentage weights
    private static final int SCALE = 6;

    private final HoldingService holdingService;

    //Constructor
    public AllocationService(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    //main functions
    //Build all allocation breakdowns for the authenticated user.
    public AllocationResponse getAllocation(UUID userId) {
        //Get holdings
        List<HoldingResponse> holdings = holdingService.getHoldings(userId);

        //Create response
        AllocationResponse response = new AllocationResponse();
        //Allocation by holding
        response.setByHolding(groupAndConvert(holdings, h -> defaultLabel(h.getDisplayName(), h.getSymbol())));
        //Allocation by asset class
        response.setByAssetClass(groupAndConvert(holdings, h -> h.getAssetClass() != null ? h.getAssetClass().name() : "UNCLASSIFIED"));
        //Allocation by sector
        response.setBySector(groupAndConvert(holdings, h -> defaultLabel(h.getSector(), "UNCLASSIFIED")));
        //Allocation by market
        response.setByMarket(groupAndConvert(holdings, h -> defaultLabel(h.getMarket(), "UNCLASSIFIED")));
        //Allocation by currency
        response.setByCurrency(groupAndConvert(holdings, h -> defaultLabel(h.getCurrency(), "UNKNOWN")));

        return response;
    }

    //helper function
    //Group holdings by a chosen category and convert
    // each group into an allocation item with value and percentage weight.
    private List<AllocationItemResponse> groupAndConvert(List<HoldingResponse> holdings,
                                                         Function<HoldingResponse, String> classifier) {
        //Calculate total portfolio value
        //Formula:
        //total portfolio value = sum of holding values
        BigDecimal total = holdings.stream()
                .map(this::holdingValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        //Group holdings and sum values
        /*
        Example:
        AAPL → EQUITY → £1000
        MSFT → EQUITY → £1000
        BND → BOND → £500
         */
        /*
        becomes:
        EQUITY → £2000
        BOND → £500
         */
        Map<String, BigDecimal> grouped = holdings.stream()
                .collect(Collectors.groupingBy(
                        classifier,
                        Collectors.reducing(BigDecimal.ZERO, this::holdingValue, BigDecimal::add)
                ));

        //Convert groups into response items
        //Convert map entries to stream
        return grouped.entrySet().stream()
                //Sort by value descending, Largest allocation appears first
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                //Convert to AllocationItemResponse
                .map(entry -> toAllocationItem(entry.getKey(), entry.getValue(), total))
                .toList();
    }

    private AllocationItemResponse toAllocationItem(String label, BigDecimal value, BigDecimal total) {
        //creates one allocation item.
        AllocationItemResponse item = new AllocationItemResponse();
        item.setLabel(label);
        item.setValue(value);

        //Calculate weight percentage
        //Formula:
        //weight percentage = group value / total portfolio value × 100
        //"if" used to prevent divide by 0
        BigDecimal weightPercent = BigDecimal.ZERO;
        if (total.compareTo(BigDecimal.ZERO) > 0) {
            weightPercent = value
                    .divide(total, SCALE, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        //Stores the percentage and returns the allocation item
        item.setWeightPercent(weightPercent);
        return item;
    }

    //decides what value to use for allocation.
    private BigDecimal holdingValue(HoldingResponse holding) {
        /*
        Priority:
        1. Use market value if it exists and is greater than zero.
        2. Otherwise use total cost.
        3. Otherwise use zero.
         */
        if (holding.getMarketValue() != null && holding.getMarketValue().compareTo(BigDecimal.ZERO) > 0) {
            return holding.getMarketValue();
        }
        return holding.getTotalCost() != null ? holding.getTotalCost() : BigDecimal.ZERO;
    }

    //If the label is missing, use a fallback
    //Avoid null labels in charts and tables
    private String defaultLabel(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}