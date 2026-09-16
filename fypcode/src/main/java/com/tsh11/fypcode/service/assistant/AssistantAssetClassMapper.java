package com.tsh11.fypcode.service.assistant;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.investor.AssistantAllocationBucket;
import org.springframework.stereotype.Component;

//Maps existing AssetClass values into assistant allocation buckets
@Component
public class AssistantAssetClassMapper {

    public AssistantAllocationBucket map(AssetClass assetClass) {
        if (assetClass == null) {
            return AssistantAllocationBucket.UNCLASSIFIED;
        }

        return switch (assetClass) {
            case CASH -> AssistantAllocationBucket.CASH;
            case BOND -> AssistantAllocationBucket.BOND;
            case EQUITY, ETF -> AssistantAllocationBucket.EQUITY; //consider ETF as Equity (stocks) for now
            case CRYPTO -> AssistantAllocationBucket.CRYPTO;
            case REAL_ESTATE -> AssistantAllocationBucket.REAL_ESTATE;
            case OTHER -> AssistantAllocationBucket.OTHER;
        };
    }
}
