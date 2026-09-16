package com.tsh11.fypcode.service.holding.blackbox;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.dto.response.HoldingResponse;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.repository.ActivityRepository;
import com.tsh11.fypcode.repository.AssetProfileRepository;
import com.tsh11.fypcode.service.HoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class HoldingServiceEquivalencePartitioningTest {

    private ActivityRepository activityRepository;
    private AssetProfileRepository assetProfileRepository;
    private MarketDataProvider marketDataProvider;
    private HoldingService holdingService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        activityRepository = mock(ActivityRepository.class);
        assetProfileRepository = mock(AssetProfileRepository.class);
        marketDataProvider = mock(MarketDataProvider.class);

        holdingService = new HoldingService(
                activityRepository,
                assetProfileRepository,
                marketDataProvider
        );

        userId = UUID.randomUUID();

        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of());
    }

    @Test
    void validBuyActivity_shouldCreateHoldingWithCorrectQuantity() {
        Activity buy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        AssetProfile profile = assetProfile("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD");

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile));

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        assertThat(holding.getSymbol()).isEqualTo("AAPL");
        assertThat(holding.getDisplayName()).isEqualTo("Apple Inc.");
        assertThat(holding.getAssetClass()).isEqualTo(AssetClass.EQUITY);
        assertThat(holding.getCurrency()).isEqualTo("USD");

        assertThat(holding.getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(holding.getTotalCost()).isEqualByComparingTo("1000");
        assertThat(holding.getAverageCost()).isEqualByComparingTo("100");
        assertThat(holding.getLatestPrice()).isEqualByComparingTo("150");
        assertThat(holding.getMarketValue()).isEqualByComparingTo("1500");
        assertThat(holding.getUnrealizedGainLoss()).isEqualByComparingTo("500");
        assertThat(holding.getUnrealizedGainLossPercent()).isEqualByComparingTo("50");
    }

    @Test
    void validBuyAndSellActivities_shouldReturnNetHolding() {
        Activity buy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity sell = activity(
                ActivityType.SELL,
                "AAPL",
                "4",
                "120",
                "0",
                "USD",
                "2026-01-02T10:00:00Z"
        );

        AssetProfile profile = assetProfile("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD");

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(sell, buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile));

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        assertThat(holding.getSymbol()).isEqualTo("AAPL");
        assertThat(holding.getTotalQuantity()).isEqualByComparingTo("6");
        assertThat(holding.getTotalCost()).isEqualByComparingTo("600");
        assertThat(holding.getAverageCost()).isEqualByComparingTo("100");
        assertThat(holding.getMarketValue()).isEqualByComparingTo("900");
        assertThat(holding.getUnrealizedGainLoss()).isEqualByComparingTo("300");
        assertThat(holding.getUnrealizedGainLossPercent()).isEqualByComparingTo("50");
    }

    @Test
    void multipleValidAssets_shouldCreateSeparateHoldings() {
        Activity appleBuy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity microsoftBuy = activity(
                ActivityType.BUY,
                "MSFT",
                "5",
                "200",
                "0",
                "USD",
                "2026-01-01T11:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(microsoftBuy, appleBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(assetProfile("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD")));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "MSFT"))
                .thenReturn(Optional.of(assetProfile("MSFT", "Microsoft Corporation", AssetClass.EQUITY, "USD")));

        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of(
                        "AAPL", bd("150"),
                        "MSFT", bd("300")
                ));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(2);

        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(result.get(0).getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(result.get(0).getMarketValue()).isEqualByComparingTo("1500");

        assertThat(result.get(1).getSymbol()).isEqualTo("MSFT");
        assertThat(result.get(1).getTotalQuantity()).isEqualByComparingTo("5");
        assertThat(result.get(1).getMarketValue()).isEqualByComparingTo("1500");
    }

    @Test
    void lowercaseAndWhitespaceSymbol_shouldBeNormalisedIntoSameHolding() {
        Activity firstBuy = activity(
                ActivityType.BUY,
                " aapl ",
                "5",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity secondBuy = activity(
                ActivityType.BUY,
                "AAPL",
                "5",
                "120",
                "0",
                "USD",
                "2026-01-02T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(secondBuy, firstBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(assetProfile("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD")));

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        assertThat(holding.getSymbol()).isEqualTo("AAPL");
        assertThat(holding.getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(holding.getTotalCost()).isEqualByComparingTo("1100");
        assertThat(holding.getAverageCost()).isEqualByComparingTo("110");
    }

    @Test
    void nonBuySellActivity_shouldBeIgnored() {
        Activity buy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity fee = activity(
                ActivityType.FEE,
                "AAPL",
                "999",
                "999",
                "0",
                "USD",
                "2026-01-02T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(fee, buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(assetProfile("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD")));

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(result.get(0).getTotalCost()).isEqualByComparingTo("1000");
    }

    @Test
    void validHoldingWithoutAssetProfile_shouldStillReturnHoldingUsingSymbolAndActivityCurrency() {
        Activity buy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.empty());

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        assertThat(holding.getSymbol()).isEqualTo("AAPL");
        assertThat(holding.getDisplayName()).isEqualTo("AAPL");
        assertThat(holding.getAssetClass()).isNull();
        assertThat(holding.getCurrency()).isEqualTo("USD");
        assertThat(holding.getMarketValue()).isEqualByComparingTo("1500");
    }

    @Test
    void validMarketAsset_shouldRequestLatestPriceForNormalisedSymbol() {
        Activity buy = activity(
                ActivityType.BUY,
                " aapl ",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(assetProfile("AAPL", "Apple Inc.", AssetClass.EQUITY, "USD")));

        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of("AAPL", bd("150")));

        holdingService.getHoldings(userId);

        ArgumentCaptor<List<String>> symbolsCaptor = ArgumentCaptor.forClass(List.class);

        verify(marketDataProvider).getLatestPrices(symbolsCaptor.capture());

        assertThat(symbolsCaptor.getValue()).containsExactly("AAPL");
    }

    private Activity activity(
            ActivityType type,
            String symbol,
            String quantity,
            String unitPrice,
            String fee,
            String currency,
            String date
    ) {
        Activity activity = new Activity();
        activity.setUserId(userId);
        activity.setType(type);
        activity.setSymbol(symbol);
        activity.setQuantity(bd(quantity));
        activity.setUnitPrice(bd(unitPrice));
        activity.setFee(bd(fee));
        activity.setCurrency(currency);
        activity.setDate(Instant.parse(date));
        return activity;
    }

    private AssetProfile assetProfile(
            String symbol,
            String displayName,
            AssetClass assetClass,
            String currency
    ) {
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        profile.setSymbol(symbol);
        profile.setDisplayName(displayName);
        profile.setAssetClass(assetClass);
        profile.setCurrency(currency);
        profile.setSector("Technology");
        profile.setMarket("NASDAQ");
        profile.setManualValuationRequired(false);
        profile.setPriceDataSupported(true);
        profile.setHistoricalPriceSupported(true);
        profile.setOptimisationSupported(true);
        return profile;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}