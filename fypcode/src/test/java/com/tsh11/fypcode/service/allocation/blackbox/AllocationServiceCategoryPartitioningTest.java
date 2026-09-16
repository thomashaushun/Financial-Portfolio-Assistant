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
class AllocationServiceCategoryPartitioningTest {

    @Mock
    private HoldingService holdingService;

    private AllocationService allocationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        allocationService = new AllocationService(holdingService);
        userId = UUID.randomUUID();
    }

    //Verify allocation by individual holding
    //Each holding appears as a separate allocation item with correct value and percentage
    @Test
    void getAllocation_shouldGroupHoldingsByIndividualHolding() {
        HoldingResponse apple = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "1500",
                "1000"
        );

        HoldingResponse microsoft = holding(
                "MSFT",
                "Microsoft Corporation",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "1500",
                "1000"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft));

        AllocationResponse result = allocationService.getAllocation(userId);

        assertThat(result.getByHolding()).hasSize(2);

        AllocationItemResponse appleAllocation = findByLabel(result.getByHolding(), "Apple Inc.");
        AllocationItemResponse microsoftAllocation = findByLabel(result.getByHolding(), "Microsoft Corporation");

        assertThat(appleAllocation.getValue()).isEqualByComparingTo("1500");
        assertThat(appleAllocation.getWeightPercent()).isEqualByComparingTo("50.000000");

        assertThat(microsoftAllocation.getValue()).isEqualByComparingTo("1500");
        assertThat(microsoftAllocation.getWeightPercent()).isEqualByComparingTo("50.000000");
    }

    //Verify allocation by asset class
    //Holdings are grouped by asset class such as EQUITY and ETF
    @Test
    void getAllocation_shouldGroupHoldingsByAssetClass() {
        HoldingResponse equity = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "2000",
                "1000"
        );

        HoldingResponse etf = holding(
                "VOO",
                "Vanguard S&P 500 ETF",
                AssetClass.ETF,
                "Diversified",
                "NYSE",
                "USD",
                "1000",
                "800"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(equity, etf));

        AllocationResponse result = allocationService.getAllocation(userId);

        assertThat(result.getByAssetClass()).hasSize(2);

        AllocationItemResponse equityAllocation = findByLabel(result.getByAssetClass(), "EQUITY");
        AllocationItemResponse etfAllocation = findByLabel(result.getByAssetClass(), "ETF");

        assertThat(equityAllocation.getValue()).isEqualByComparingTo("2000");
        assertThat(equityAllocation.getWeightPercent()).isEqualByComparingTo("66.666700");

        assertThat(etfAllocation.getValue()).isEqualByComparingTo("1000");
        assertThat(etfAllocation.getWeightPercent()).isEqualByComparingTo("33.333300");
    }

    //Verify allocation by sector
    //Holdings with the same sector are aggregated together
    @Test
    void getAllocation_shouldGroupHoldingsBySector() {
        HoldingResponse apple = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "1500",
                "1000"
        );

        HoldingResponse microsoft = holding(
                "MSFT",
                "Microsoft Corporation",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "1500",
                "1000"
        );

        HoldingResponse jpmorgan = holding(
                "JPM",
                "JPMorgan Chase",
                AssetClass.EQUITY,
                "Financial Services",
                "NYSE",
                "USD",
                "1000",
                "900"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, microsoft, jpmorgan));

        AllocationResponse result = allocationService.getAllocation(userId);

        assertThat(result.getBySector()).hasSize(2);

        AllocationItemResponse technology = findByLabel(result.getBySector(), "Technology");
        AllocationItemResponse financialServices = findByLabel(result.getBySector(), "Financial Services");

        assertThat(technology.getValue()).isEqualByComparingTo("3000");
        assertThat(technology.getWeightPercent()).isEqualByComparingTo("75.000000");

        assertThat(financialServices.getValue()).isEqualByComparingTo("1000");
        assertThat(financialServices.getWeightPercent()).isEqualByComparingTo("25.000000");
    }

    //Verify allocation by market
    //Holdings are grouped by market such as NASDAQ and LSE
    @Test
    void getAllocation_shouldGroupHoldingsByMarket() {
        HoldingResponse apple = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "2000",
                "1000"
        );

        HoldingResponse vanguard = holding(
                "VUAG.L",
                "Vanguard S&P 500 UCITS ETF",
                AssetClass.ETF,
                "Diversified",
                "LSE",
                "GBP",
                "1000",
                "800"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(apple, vanguard));

        AllocationResponse result = allocationService.getAllocation(userId);

        assertThat(result.getByMarket()).hasSize(2);

        AllocationItemResponse nasdaq = findByLabel(result.getByMarket(), "NASDAQ");
        AllocationItemResponse lse = findByLabel(result.getByMarket(), "LSE");

        assertThat(nasdaq.getValue()).isEqualByComparingTo("2000");
        assertThat(nasdaq.getWeightPercent()).isEqualByComparingTo("66.666700");

        assertThat(lse.getValue()).isEqualByComparingTo("1000");
        assertThat(lse.getWeightPercent()).isEqualByComparingTo("33.333300");
    }

    //Verify allocation by currency
    //Holdings are grouped by currency such as USD and GBP
    @Test
    void getAllocation_shouldGroupHoldingsByCurrency() {
        HoldingResponse usdHolding = holding(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD",
                "2000",
                "1000"
        );

        HoldingResponse gbpHolding = holding(
                "VUAG.L",
                "Vanguard S&P 500 UCITS ETF",
                AssetClass.ETF,
                "Diversified",
                "LSE",
                "GBP",
                "1000",
                "800"
        );

        when(holdingService.getHoldings(userId))
                .thenReturn(List.of(usdHolding, gbpHolding));

        AllocationResponse result = allocationService.getAllocation(userId);

        assertThat(result.getByCurrency()).hasSize(2);

        AllocationItemResponse usd = findByLabel(result.getByCurrency(), "USD");
        AllocationItemResponse gbp = findByLabel(result.getByCurrency(), "GBP");

        assertThat(usd.getValue()).isEqualByComparingTo("2000");
        assertThat(usd.getWeightPercent()).isEqualByComparingTo("66.666700");

        assertThat(gbp.getValue()).isEqualByComparingTo("1000");
        assertThat(gbp.getWeightPercent()).isEqualByComparingTo("33.333300");
    }

    private AllocationItemResponse findByLabel(List<AllocationItemResponse> items, String label) {
        return items.stream()
                .filter(item -> item.getLabel().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Allocation item not found: " + label));
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