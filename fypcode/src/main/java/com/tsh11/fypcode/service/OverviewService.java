package com.tsh11.fypcode.service;

import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.dto.response.OverviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;


/*
uses HoldingService to get the current holdings,
then adds them together to produce the high-level numbers shown on the Overview page.
 */
/*
Calculates:
total holdings count
total quantity
total cost basis
total market value
total unrealised gain/loss
total unrealised gain/loss percentage
base currency
 */
@Service
@Transactional(readOnly = true)
public class OverviewService {

    //controls decimal precision when calculating percentages
    //used when calculating unrealised gain/loss percentage
    private static final int SCALE = 6;

    private final HoldingService holdingService;

    //constructor
    public OverviewService(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    //main function
    //Build the portfolio overview summary for the authenticated user
    public OverviewResponse getOverview(UUID userId) {
        //call HoldingService to receive holding results
        List<HoldingResponse> holdings = holdingService.getHoldings(userId);

        //Initialise totals to 0
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalCostBasis = BigDecimal.ZERO;
        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalUnrealizedGainLoss = BigDecimal.ZERO;
        String baseCurrency = null;

        //loop through holdings
        for (HoldingResponse holding : holdings) {
            //Sum total quantity
            totalQuantity = totalQuantity.add(nvl(holding.getTotalQuantity()));
            //Sum total cost basis
            totalCostBasis = totalCostBasis.add(nvl(holding.getTotalCost()));
            //Sum total market value
            totalMarketValue = totalMarketValue.add(nvl(holding.getMarketValue()));
            //Sum unrealised gain/loss
            totalUnrealizedGainLoss = totalUnrealizedGainLoss.add(nvl(holding.getUnrealizedGainLoss()));

            //Choose base currency
            //to the first non-empty currency found in the holdings list
            if (baseCurrency == null && holding.getCurrency() != null && !holding.getCurrency().isBlank()) {
                baseCurrency = holding.getCurrency();
            }
        }

        //Create response object
        OverviewResponse response = new OverviewResponse();
        //Set total holdings count
        response.setTotalHoldingsCount(holdings.size());
        //Set total values
        response.setTotalQuantity(totalQuantity);
        response.setTotalCostBasis(totalCostBasis);
        response.setTotalMarketValue(totalMarketValue);
        response.setTotalUnrealizedGainLoss(totalUnrealizedGainLoss);
        //Set base currency, set to USD if none found
        response.setBaseCurrency(baseCurrency == null ? "USD" : baseCurrency);

        //Calculate gain/loss percentage
        //Only calculate percentage if total cost basis is greater than zero
        //avoid divide by 0
        if (totalCostBasis.compareTo(BigDecimal.ZERO) > 0) {
            /*
            Formula:
            total unrealised gain/loss % =
            total unrealised gain/loss / total cost basis × 100
             */
            //Example:
            /*
            total cost basis = £1000
            market value = £1200
            gain = £200

            gain/loss % = (1200 - 1000) / 1000 × 100 = 20%
             */
            response.setTotalUnrealizedGainLossPercent(
                    totalUnrealizedGainLoss
                            .divide(totalCostBasis, SCALE, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
            );
        } else {
            //If no cost basis, set percentage to zero
            response.setTotalUnrealizedGainLossPercent(BigDecimal.ZERO);
        }

        return response;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}