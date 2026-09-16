package com.tsh11.fypcode.service.holding.whitebox;

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

@ExtendWith(MockitoExtension.class)
class HoldingServiceLoopTest {

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
    void multipleActivitiesForSameSymbol_shouldBeProcessedInDateOrder() {
        Activity buyFirst = activity(
                ActivityType.BUY,
                "AAPL",
                "10",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity buySecond = activity(
                ActivityType.BUY,
                "AAPL",
                "5",
                "200",
                "0",
                "USD",
                "2026-01-02T10:00:00Z"
        );

        Activity sellThird = activity(
                ActivityType.SELL,
                "AAPL",
                "6",
                "150",
                "0",
                "USD",
                "2026-01-03T10:00:00Z"
        );

        Activity buyFourth = activity(
                ActivityType.BUY,
                "AAPL",
                "1",
                "300",
                "0",
                "USD",
                "2026-01-04T10:00:00Z"
        );

        AssetProfile profile = assetProfile(
                "AAPL",
                "Apple Inc.",
                AssetClass.EQUITY,
                "Technology",
                "NASDAQ",
                "USD"
        );

        /*
         Repository method name returns activities by date descending.
         The service should internally sort them ascending before applying buy/sell calculations.
         */
        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(buyFourth, sellThird, buySecond, buyFirst));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile));

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("250")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        /*
         Manual calculation:
         Buy 10 @ 100 = cost 1000, quantity 10
         Buy 5 @ 200 = cost 1000, quantity 15, total cost 2000
         Average cost before sell = 2000 / 15 = 133.333333
         Sell 6 removes 6 * 133.333333 = 799.999998
         Remaining quantity = 9
         Remaining cost = 1200.000002
         Buy 1 @ 300 = cost +300, quantity 10
         Final cost = 1500.000002
         Final average cost = 150.000000 after rounding to scale 6
         */
        assertThat(holding.getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(holding.getTotalCost()).isEqualByComparingTo("1500.000002");
        assertThat(holding.getAverageCost()).isEqualByComparingTo("150.000000");

        assertThat(holding.getLatestPrice()).isEqualByComparingTo("250");
        assertThat(holding.getMarketValue()).isEqualByComparingTo("2500");
        assertThat(holding.getUnrealizedGainLoss()).isEqualByComparingTo("999.999998");
    }

    @Test
    void repeatedSameSymbolActivitiesWithDifferentSymbolCase_shouldBeGroupedTogether() {
        Activity upperCaseBuy = activity(
                ActivityType.BUY,
                "AAPL",
                "5",
                "100",
                "0",
                "USD",
                "2026-01-01T10:00:00Z"
        );

        Activity lowerCaseBuy = activity(
                ActivityType.BUY,
                "aapl",
                "5",
                "120",
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
                .thenReturn(List.of(lowerCaseBuy, upperCaseBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "AAPL"))
                .thenReturn(Optional.of(profile));

        when(marketDataProvider.getLatestPrices(List.of("AAPL")))
                .thenReturn(Map.of("AAPL", bd("150")));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse holding = result.get(0);

        assertThat(holding.getSymbol()).isEqualTo("AAPL");
        assertThat(holding.getTotalQuantity()).isEqualByComparingTo("10");
        assertThat(holding.getTotalCost()).isEqualByComparingTo("1100");
        assertThat(holding.getAverageCost()).isEqualByComparingTo("110.000000");
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