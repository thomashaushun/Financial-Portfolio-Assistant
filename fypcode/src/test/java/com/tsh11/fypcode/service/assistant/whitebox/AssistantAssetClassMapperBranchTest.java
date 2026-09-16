package com.tsh11.fypcode.service.assistant.whitebox;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.investor.AssistantAllocationBucket;
import com.tsh11.fypcode.service.assistant.AssistantAssetClassMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantAssetClassMapperBranchTest {

    private final AssistantAssetClassMapper mapper = new AssistantAssetClassMapper();

    // Covers the explicit null branch in the mapper.
    @Test
    @DisplayName("Null branch maps missing metadata to UNCLASSIFIED")
    void nullBranchMapsToUnclassified() {
        assertThat(mapper.map(null)).isEqualTo(AssistantAllocationBucket.UNCLASSIFIED);
    }

    // Covers the switch branches for traditional assets: equity, ETF, bond, and cash.
    @Test
    @DisplayName("Traditional asset switch branches map to equity, bond, and cash buckets")
    void traditionalSwitchBranchesAreCovered() {
        assertThat(mapper.map(AssetClass.EQUITY)).isEqualTo(AssistantAllocationBucket.EQUITY);
        assertThat(mapper.map(AssetClass.ETF)).isEqualTo(AssistantAllocationBucket.EQUITY);
        assertThat(mapper.map(AssetClass.BOND)).isEqualTo(AssistantAllocationBucket.BOND);
        assertThat(mapper.map(AssetClass.CASH)).isEqualTo(AssistantAllocationBucket.CASH);
    }

    // Covers the switch branches for alternative/other assets: crypto, real estate, and other.
    @Test
    @DisplayName("Alternative asset switch branches map to separate alternative buckets")
    void alternativeSwitchBranchesAreCovered() {
        assertThat(mapper.map(AssetClass.CRYPTO)).isEqualTo(AssistantAllocationBucket.CRYPTO);
        assertThat(mapper.map(AssetClass.REAL_ESTATE)).isEqualTo(AssistantAllocationBucket.REAL_ESTATE);
        assertThat(mapper.map(AssetClass.OTHER)).isEqualTo(AssistantAllocationBucket.OTHER);
    }
}
