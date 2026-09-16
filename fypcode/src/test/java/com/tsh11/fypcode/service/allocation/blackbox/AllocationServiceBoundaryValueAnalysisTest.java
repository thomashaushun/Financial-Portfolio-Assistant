package com.tsh11.fypcode.service.allocation.blackbox;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.dto.response.AllocationItemResponse;
import com.tsh11.fypcode.dto.response.AllocationResponse;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.service.AllocationService;
import com.tsh11.fypcode.service.HoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllocationServiceBoundaryValueAnalysisTest {

    @Mock
    private HoldingService holdingService;

    private AllocationService allocationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        allocationService = new AllocationService(holdingService);
        userId = UUID.randomUUID();
    }

    //Test empty holdings list
    //All allocation groups are returned as empty lists
    @Test
    void emptyHoldingsList_shouldReturnEmptyAllocationGroups() {
        when(holdingService.getHoldings(userId))
                .thenReturn(List.of());

        AllocationResponse result = allocationService.getAllocation(userId);

        assertThat(result.getByHolding()).isEmpty();
        assertThat(result.getByAssetClass()).isEmpty();
        assertThat(result.getBySector()).isEmpty();
        assertThat(result.getByMarket()).isEmpty();
        assertThat(result.getByCurrency()).isEmpty();
    }

    //Test zero market value and zero total cost
    //Allocation value and weight percentage are both zero
    @Test
    void zeroMarketValueAndZeroTotalCost_shouldReturnZeroWeightPercent() {
        HoldingResponse holding = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "0",
                "0"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        assertThat(result.getByHolding()).hasSize(1);

        AllocationItemResponse item = result.getByHolding().get(0);

        assertThat(item.getLabel()).isEqualTo("Apple Inc.");
        assertThat(item.getValue()).isEqualByComparingTo("0");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("0");
    }

    //Test null market value and null total cost
    //Allocation value and weight percentage are both zero
    @Test
    void nullMarketValueAndNullTotalCost_shouldReturnZeroValueAndZeroWeightPercent() {
        HoldingResponse holding = new HoldingResponse();
        holding.setSymbol("AAPL");
        holding.setDisplayName("Apple Inc.");
        holding.setAssetClass(AssetClass.EQUITY);
        holding.setSector("Technology");
        holding.setMarket("NASDAQ");
        holding.setCurrency("USD");
        holding.setMarketValue(null);
        holding.setTotalCost(null);

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        AllocationItemResponse item = result.getByHolding().get(0);

        assertThat(item.getLabel()).isEqualTo("Apple Inc.");
        assertThat(item.getValue()).isEqualByComparingTo("0");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("0");
    }

    //Test zero market value with positive total cost.
    //Total cost is used as fallback value.
    @Test
    void zeroMarketValueWithPositiveTotalCost_shouldUseTotalCostAsFallbackValue() {
        HoldingResponse holding = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "0",
                "1000"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        AllocationItemResponse item = result.getByHolding().get(0);

        assertThat(item.getLabel()).isEqualTo("Apple Inc.");
        assertThat(item.getValue()).isEqualByComparingTo("1000");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("100.000000");
    }

    //Test negative market value with positive total cost.
    //Total cost is used as fallback value.
    @Test
    void negativeMarketValueWithPositiveTotalCost_shouldUseTotalCostAsFallbackValue() {
        HoldingResponse holding = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "-100",
                "1000"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        AllocationItemResponse item = result.getByHolding().get(0);

        assertThat(item.getLabel()).isEqualTo("Apple Inc.");
        assertThat(item.getValue()).isEqualByComparingTo("1000");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("100.000000");
    }

    private HoldingResponse holding(
            String symbol,
            String displayName,
            AssetClass assetClass,
            String sector,
            String market,
            String currency,
            String marketValue,
            String totalCost
    ) {
        HoldingResponse holding = new HoldingResponse();
        holding.setSymbol(symbol);
        holding.setDisplayName(displayName);
        holding.setAssetClass(assetClass);
        holding.setSector(sector);
        holding.setMarket(market);
        holding.setCurrency(currency);
        holding.setMarketValue(new BigDecimal(marketValue));
        holding.setTotalCost(new BigDecimal(totalCost));
        return holding;
    }
}