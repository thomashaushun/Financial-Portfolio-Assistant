package com.tsh11.fypcode.service.assistant.blackbox;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.investor.AssistantAllocationBucket;
import com.tsh11.fypcode.service.assistant.AssistantAssetClassMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantAssetClassMapperEquivalencePartitioningTest {

    private final AssistantAssetClassMapper mapper = new AssistantAssetClassMapper();

    // Parameterised test for valid asset-class partitions.
    // It verifies that `EQUITY`, `ETF`, `BOND`, `CASH`, `CRYPTO`, `REAL_ESTATE`, and `OTHER` map to the correct assistant buckets.
    @ParameterizedTest(name = "{0} maps to {1}")
    @CsvSource({
            "EQUITY, EQUITY",
            "ETF, EQUITY",
            "BOND, BOND",
            "CASH, CASH",
            "CRYPTO, CRYPTO",
            "REAL_ESTATE, REAL_ESTATE",
            "OTHER, OTHER"
    })
    @DisplayName("Valid asset-class partitions map to the correct assistant allocation bucket")
    void validAssetClassesMapToExpectedBuckets(AssetClass assetClass,
                                               AssistantAllocationBucket expectedBucket) {
        assertThat(mapper.map(assetClass)).isEqualTo(expectedBucket);
    }

    // Tests the missing metadata partition.
    // A null asset class should map to `UNCLASSIFIED`.
    @Test
    @DisplayName("Missing asset-class partition maps to UNCLASSIFIED")
    void nullAssetClassMapsToUnclassified() {
        assertThat(mapper.map(null)).isEqualTo(AssistantAllocationBucket.UNCLASSIFIED);
    }
}
