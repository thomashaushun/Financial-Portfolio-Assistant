package com.tsh11.fypcode.service.assistant;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.investor.AssistantAllocationBucket;
import com.tsh11.fypcode.dto.response.ActualAllocationResponse;
import com.tsh11.fypcode.dto.response.AllocationComparisonResponse;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.dto.response.TargetAllocationResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

//Compares the user’s actual allocation against the target allocation
@Service
public class SuitabilityAnalysisService {

    private static final MathContext MC = MathContext.DECIMAL64; //avoid floating-point inaccuracies
    private static final int DECIMAL_PLACE = 6;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    // If the actual allocation differs from the target by less than 5%, it is considered acceptable.
    // If the difference is between 5% and 15%, it is a minor drift.
    // If it is 15% or more, it is considered significant.
    private static final BigDecimal MINOR_DRIFT_THRESHOLD = BigDecimal.valueOf(5);
    private static final BigDecimal MAJOR_DRIFT_THRESHOLD = BigDecimal.valueOf(15);

    private final AssistantAssetClassMapper assistantAssetClassMapper;

    //constructor
    public SuitabilityAnalysisService(AssistantAssetClassMapper assistantAssetClassMapper) {
        this.assistantAssetClassMapper = assistantAssetClassMapper;
    }

    //calculates the user’s current real portfolio allocation from their holdings
    public ActualAllocationResponse calculateActualAllocation(List<HoldingResponse> holdings) {
        //Initialise allocation buckets to BigDecimal.ZERO
        Map<AssistantAllocationBucket, BigDecimal> values = new EnumMap<>(AssistantAllocationBucket.class);
        for (AssistantAllocationBucket bucket : AssistantAllocationBucket.values()) {
            values.put(bucket, BigDecimal.ZERO);
        }
        //Handle empty holdings
        if (holdings == null || holdings.isEmpty()) {
            ActualAllocationResponse empty = new ActualAllocationResponse();
            empty.setTotalPortfolioValue(BigDecimal.ZERO);
            return empty;
        }

        BigDecimal total = BigDecimal.ZERO;
        boolean etfAssumptionUsed = false;
        //Loop through holdings
        for (HoldingResponse holding : holdings) {
            //Skip invalid holdings
            if (holding == null) {
                continue;
            }
            //Determine the holding value
            BigDecimal value = holdingValue(holding);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            //If the portfolio contains an ETF, the service records that an ETF assumption was used.
            //Will be used to warn the user about system assuming ETF as Equity
            if (holding.getAssetClass() == AssetClass.ETF) {
                etfAssumptionUsed = true;
            }
            //Map asset class to assistant bucket
            AssistantAllocationBucket bucket = assistantAssetClassMapper.map(holding.getAssetClass());
            //Add value to the bucket
            values.put(bucket, values.get(bucket).add(value));
            total = total.add(value);
        }
        //Build the response
        ActualAllocationResponse response = new ActualAllocationResponse();
        response.setTotalPortfolioValue(total);
        response.setEtfAssumptionUsed(etfAssumptionUsed);
        //Convert bucket values into percentages
        response.setEquityPercent(toPercent(values.get(AssistantAllocationBucket.EQUITY), total));
        response.setBondPercent(toPercent(values.get(AssistantAllocationBucket.BOND), total));
        response.setCashPercent(toPercent(values.get(AssistantAllocationBucket.CASH), total));
        response.setCryptoPercent(toPercent(values.get(AssistantAllocationBucket.CRYPTO), total));
        response.setRealEstatePercent(toPercent(values.get(AssistantAllocationBucket.REAL_ESTATE), total));
        response.setOtherPercent(toPercent(values.get(AssistantAllocationBucket.OTHER), total));
        response.setUnclassifiedPercent(toPercent(values.get(AssistantAllocationBucket.UNCLASSIFIED), total));
        return response;
    }

    //compares the recommended target allocation with the user’s actual allocation.
    //compares only:
    //EQUITY
    //BOND
    //CASH
    public List<AllocationComparisonResponse> compare(TargetAllocationResponse target,
                                                       ActualAllocationResponse actual) {
        if (target == null) {
            throw new IllegalArgumentException("Target allocation must not be null.");
        }
        if (actual == null) {
            throw new IllegalArgumentException("Actual allocation must not be null.");
        }
        //Compare equity, bond, and cash
        List<AllocationComparisonResponse> comparisons = new ArrayList<>();
        comparisons.add(compareBucket("EQUITY", target.getEquityPercent(), actual.getEquityPercent()));
        comparisons.add(compareBucket("BOND", target.getBondPercent(), actual.getBondPercent()));
        comparisons.add(compareBucket("CASH", target.getCashPercent(), actual.getCashPercent()));
        return comparisons;
    }

    //helper function
    //compares one allocation bucket
    //example:
    //bucket = "EQUITY"
    //targetPercent = 60
    //actualPercent = 85
    //Difference: +25%
    //Status: SIGNIFICANTLY_OVERWEIGHT
    private AllocationComparisonResponse compareBucket(String bucket,
                                                       BigDecimal targetPercent,
                                                       BigDecimal actualPercent) {
        BigDecimal target = nvl(targetPercent);
        BigDecimal actual = nvl(actualPercent);
        BigDecimal difference = actual.subtract(target, MC);
        //create comparison response
        AllocationComparisonResponse response = new AllocationComparisonResponse();
        response.setBucket(bucket);
        response.setTargetPercent(target);
        response.setActualPercent(actual);
        response.setDifferencePercent(difference);
        response.setStatus(toStatus(difference));
        return response;
    }
    //converts a numerical allocation difference into a human-readable status
    private String toStatus(BigDecimal difference) {

        //first check how large the difference is, ignoring whether it is positive or negative.
        BigDecimal absolute = difference.abs();

        //If the difference is less than 5%, the portfolio is considered close enough to the target.
        if (absolute.compareTo(MINOR_DRIFT_THRESHOLD) < 0) {
            return "WITHIN_TARGET_RANGE";
        }

        //If the difference is at least 5% but less than 15%, it is considered a minor drift.
        if (absolute.compareTo(MAJOR_DRIFT_THRESHOLD) < 0) {
            return difference.signum() > 0 ? "SLIGHTLY_OVERWEIGHT" : "SLIGHTLY_UNDERWEIGHT";
        }

        //If the difference is 15% or more, the allocation is considered significantly different from the target.
        return difference.signum() > 0 ? "SIGNIFICANTLY_OVERWEIGHT" : "SIGNIFICANTLY_UNDERWEIGHT";
    }

    //decides what value should be used for a holding when calculating allocation.
    private BigDecimal holdingValue(HoldingResponse holding) {
        //market value
        if (holding.getMarketValue() != null && holding.getMarketValue().compareTo(BigDecimal.ZERO) > 0) {
            return holding.getMarketValue();
        }
        //falls back to total cost
        return nvl(holding.getTotalCost());
    }

    private BigDecimal toPercent(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return nvl(value)
                .divide(total, DECIMAL_PLACE, RoundingMode.HALF_UP)
                .multiply(HUNDRED);
    }
    //null-safety helper
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
