package com.tsh11.fypcode.service.assistant.blackbox;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.dto.response.ActualAllocationResponse;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.service.assistant.AssistantAssetClassMapper;
import com.tsh11.fypcode.service.assistant.SuitabilityAnalysisService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SuitabilityAnalysisServiceEquivalencePartitioningTest {

    private final SuitabilityAnalysisService service = new SuitabilityAnalysisService(new AssistantAssetClassMapper());

    // Tests the traditional asset partitions.
    // Equity, bond, and cash holdings should calculate the expected actual allocation percentages.
    @Test
    @DisplayName("Traditional asset partitions calculate equity, bond, and cash percentages")
    void traditionalAssetsCalculateTraditionalAllocationPercentages() {
        ActualAllocationResponse actual = service.calculateActualAllocation(List.of(
                holding(AssetClass.EQUITY, "600"),
                holding(AssetClass.BOND, "300"),
                holding(AssetClass.CASH, "100")
        ));

        assertThat(actual.getEquityPercent()).isEqualByComparingTo(new BigDecimal("60.000000"));
        assertThat(actual.getBondPercent()).isEqualByComparingTo(new BigDecimal("30.000000"));
        assertThat(actual.getCashPercent()).isEqualByComparingTo(new BigDecimal("10.000000"));
    }

    // Tests alternative asset partitions.
    // Crypto, real estate, and other assets should be reported separately rather than counted as equity, bond, or cash.
    @Test
    @DisplayName("Alternative asset partitions are reported separately from equity/bond/cash")
    void alternativeAssetsAreReportedSeparately() {
        ActualAllocationResponse actual = service.calculateActualAllocation(List.of(
                holding(AssetClass.CRYPTO, "200"),
                holding(AssetClass.REAL_ESTATE, "300"),
                holding(AssetClass.OTHER, "500")
        ));

        assertThat(actual.getCryptoPercent()).isEqualByComparingTo(new BigDecimal("20.000000"));
        assertThat(actual.getRealEstatePercent()).isEqualByComparingTo(new BigDecimal("30.000000"));
        assertThat(actual.getOtherPercent()).isEqualByComparingTo(new BigDecimal("50.000000"));
        assertThat(actual.getEquityPercent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Tests the ETF partition.
    // ETFs are counted as equity in the current implementation and the ETF assumption flag should be set.
    @Test
    @DisplayName("ETF partition is counted as equity and records ETF assumption flag")
    void etfIsCountedAsEquityAndFlagged() {
        ActualAllocationResponse actual = service.calculateActualAllocation(List.of(
                holding(AssetClass.ETF, "1000")
        ));

        assertThat(actual.getEquityPercent()).isEqualByComparingTo(new BigDecimal("100.000000"));
        assertThat(actual.isEtfAssumptionUsed()).isTrue();
    }

    // Tests the unknown asset partition.
    // A holding with null asset class should be reported as 100% unclassified.
    @Test
    @DisplayName("Unknown asset-class partition is reported as unclassified")
    void nullAssetClassIsReportedAsUnclassified() {
        ActualAllocationResponse actual = service.calculateActualAllocation(List.of(
                holding(null, "250")
        ));

        assertThat(actual.getUnclassifiedPercent()).isEqualByComparingTo(new BigDecimal("100.000000"));
    }

    private HoldingResponse holding(AssetClass assetClass, String value) {
        HoldingResponse holding = new HoldingResponse();
        holding.setAssetClass(assetClass);
        holding.setMarketValue(new BigDecimal(value));
        return holding;
    }
}
