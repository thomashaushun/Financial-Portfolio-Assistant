package com.tsh11.fypcode.service.allocation.whitebox;

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
class AllocationServiceBranchTest {

    @Mock
    private HoldingService holdingService;

    private AllocationService allocationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        allocationService = new AllocationService(holdingService);
        userId = UUID.randomUUID();
    }

    //Test null asset class
    //Asset class label becomes UNCLASSIFIED
    @Test
    void nullAssetClass_shouldUseUnclassifiedBranch() {
        HoldingResponse holding = holding(
                "PRIVATE1",
                "Private Asset",
                null,
                "Other",
                "Manual",
                "GBP",
                "1000",
                "1000"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        AllocationItemResponse item = result.getByAssetClass().get(0);

        assertThat(item.getLabel()).isEqualTo("UNCLASSIFIED");
        assertThat(item.getValue()).isEqualByComparingTo("1000");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("100.000000");
    }

    //Test blank sector.
    //Sector label becomes UNCLASSIFIED
    @Test
    void blankSector_shouldUseUnclassifiedFallbackBranch() {
        HoldingResponse holding = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "   ",
                "NASDAQ",
                "USD",
                "1000",
                "800"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        AllocationItemResponse item = result.getBySector().get(0);

        assertThat(item.getLabel()).isEqualTo("UNCLASSIFIED");
        assertThat(item.getValue()).isEqualByComparingTo("1000");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("100.000000");
    }

    //Test null market
    //Market label becomes UNCLASSIFIED
    @Test
    void nullMarket_shouldUseUnclassifiedFallbackBranch() {
        HoldingResponse holding = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                null,
                "USD",
                "1000",
                "800"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        AllocationItemResponse item = result.getByMarket().get(0);

        assertThat(item.getLabel()).isEqualTo("UNCLASSIFIED");
        assertThat(item.getValue()).isEqualByComparingTo("1000");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("100.000000");
    }

    //Test blank currency.
    //Currency label becomes UNKNOWN
    @Test
    void blankCurrency_shouldUseUnknownFallbackBranch() {
        HoldingResponse holding = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "   ",
                "1000",
                "800"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        AllocationItemResponse item = result.getByCurrency().get(0);

        assertThat(item.getLabel()).isEqualTo("UNKNOWN");
        assertThat(item.getValue()).isEqualByComparingTo("1000");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("100.000000");
    }

    //Test null display name.
    //Holding label falls back to the asset symbol
    @Test
    void nullDisplayName_shouldUseSymbolAsHoldingLabelFallbackBranch() {
        HoldingResponse holding = holding(
                "AAPL",
                null,
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "1000",
                "800"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        AllocationItemResponse item = result.getByHolding().get(0);

        assertThat(item.getLabel()).isEqualTo("AAPL");
        assertThat(item.getValue()).isEqualByComparingTo("1000");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("100.000000");
    }

    //Test positive market value branch
    //Market value is used before total cost
    @Test
    void positiveMarketValue_shouldTakePriorityOverTotalCostBranch() {
        HoldingResponse holding = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "1500",
                "1000"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(holding));

        AllocationResponse result = allocationService.getAllocation(userId);

        AllocationItemResponse item = result.getByHolding().get(0);

        assertThat(item.getValue()).isEqualByComparingTo("1500");
        assertThat(item.getWeightPercent()).isEqualByComparingTo("100.000000");
    }

    //Test sorting branch
    //Allocation items are sorted by value in descending order
    @Test
    void allocationItems_shouldBeSortedByValueDescendingBranch() {
        HoldingResponse smallHolding = holding(
                "SMALL",
                "Small Holding",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "500",
                "500"
        );

        HoldingResponse largeHolding = holding(
                "LARGE",
                "Large Holding",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "1500",
                "1500"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(smallHolding, largeHolding));

        AllocationResponse result = allocationService.getAllocation(userId);

        assertThat(result.getByHolding()).hasSize(2);
        assertThat(result.getByHolding().get(0).getLabel()).isEqualTo("Large Holding");
        assertThat(result.getByHolding().get(0).getValue()).isEqualByComparingTo("1500");

        assertThat(result.getByHolding().get(1).getLabel()).isEqualTo("Small Holding");
        assertThat(result.getByHolding().get(1).getValue()).isEqualByComparingTo("500");
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