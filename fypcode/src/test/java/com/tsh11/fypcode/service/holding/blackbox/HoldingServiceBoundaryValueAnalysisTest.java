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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HoldingServiceBoundaryValueAnalysisTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private AssetProfileRepository assetProfileRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    private HoldingService holdingService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        holdingService = new HoldingService(
                activityRepository,
                assetProfileRepository,
                marketDataProvider
        );

        userId = UUID.randomUUID();
    }

    @Test
    void emptyActivityList_shouldReturnEmptyHoldings() {
        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of());

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).isEmpty();

        verify(marketDataProvider, never()).getLatestPrices(anyList());
        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    @Test
    void buyActivityWithZeroQuantity_shouldBeIgnored() {
        Activity zeroQuantityBuy = activity(
                ActivityType.BUY,
                "AAPL",
                "0",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(zeroQuantityBuy));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).isEmpty();

        verify(marketDataProvider, never()).getLatestPrices(anyList());
        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    @Test
    void buyActivityWithNegativeQuantity_shouldBeIgnored() {
        Activity negativeQuantityBuy = activity(
                ActivityType.BUY,
                "AAPL",
                "-1",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(negativeQuantityBuy));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).isEmpty();

        verify(marketDataProvider, never()).getLatestPrices(anyList());
        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    @Test
    void sellingExactlyOwnedQuantity_shouldRemoveHoldingFromResult() {
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
                "10",
                "120",
                "0",
                "USD",
                "2026-01-02T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(sell, buy));


        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).isEmpty();

        verify(marketDataProvider, never()).getLatestPrices(anyList());
        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    @Test
    void sellingMoreThanOwnedQuantity_shouldNotCreateNegativeHolding() {
        Activity buy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity sellMoreThanOwned = activity(
                ActivityType.SELL,
                "AAPL",
                "15",
                "120",
                "0",
                "USD",
                "2026-01-02T10:00:00Z"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(sellMoreThanOwned, buy));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).isEmpty();

        verify(marketDataProvider, never()).getLatestPrices(anyList());
        verify(marketDataProvider, never()).getLatestPrice(anyString());
    }

    @Test
    void sellActivityBeforeAnyBuy_shouldBeIgnoredAndLaterBuyShouldRemain() {
        Activity earlySell = activity(
                ActivityType.SELL,
                "AAPL",
                "5",
                "120",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity laterBuy = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-02T10:00:00Z"
        );

        AssetProfile profile = assetProfile(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD"
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(laterBuy, earlySell));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile));

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(result.get(0).getTotalQuantity()).isEqualByComparingTo("10");
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
            String sector,
            String market,
            String currency
    ) {
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        profile.setSymbol(symbol);
        profile.setDisplayName(displayName);
        profile.setAssetClass(assetClass);
        profile.setSector(sector);
        profile.setMarket(market);
        profile.setCurrency(currency);
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