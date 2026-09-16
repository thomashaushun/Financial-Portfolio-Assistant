package com.tsh11.fypcode.service.holding.regression;

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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldingServiceOptimisationEligibilityRegressionTest {

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

    // Simulates an old EQUITY profile where historicalPriceSupported and optimisationSupported are false.
    // It expects HoldingService to infer that a normal equity can be attempted for optimisation.
    @Test
    void staleEquityProfileWithFalseSupportFlags_shouldStillBeExposedAsOptimisationSupported() {
        Activity adobeBuy = buy("ADBE", "20", "14.50", "USD", "2026-01-01T10:00:00Z");

        AssetProfile staleAdobeProfile = profileWithStaleSupportFlags(
                "ADBE",
                "Adobe Inc",
                AssetClass.EQUITY
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(adobeBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "ADBE"))
                .thenReturn(Optional.of(staleAdobeProfile));

        /*
         Simulates the current quota-safe behaviour where bulk latest quote may not return a price.
         This should not decide optimisation eligibility by itself.
         */
        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of());

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse adobe = result.get(0);

        assertThat(adobe.getSymbol()).isEqualTo("ADBE");
        assertThat(adobe.getAssetClass()).isEqualTo(AssetClass.EQUITY);
        assertThat(adobe.isManualValuationRequired()).isFalse();
        assertThat(adobe.isPriceDataSupported()).isTrue();

        /*
         Regression expectation:
         Old/stale market asset profiles should not permanently block optimisation.
         EQUITY assets can be attempted for historical price optimisation.
         */
        assertThat(adobe.isHistoricalPriceSupported()).isTrue();
        assertThat(adobe.isOptimisationSupported()).isTrue();
    }

    // Same idea for ETF, because ETFs should also be eligible for market-data based analysis.
    @Test
    void staleEtfProfileWithFalseSupportFlags_shouldStillBeExposedAsOptimisationSupported() {
        Activity spyBuy = buy("SPY", "5", "400", "USD", "2026-01-01T10:00:00Z");

        AssetProfile staleSpyProfile = profileWithStaleSupportFlags(
                "SPY",
                "SPDR S&P 500 ETF Trust",
                AssetClass.ETF
        );

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(spyBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "SPY"))
                .thenReturn(Optional.of(staleSpyProfile));

        when(marketDataProvider.getLatestPrices(anyList()))
                .thenReturn(Map.of());

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse spy = result.get(0);

        assertThat(spy.getSymbol()).isEqualTo("SPY");
        assertThat(spy.getAssetClass()).isEqualTo(AssetClass.ETF);
        assertThat(spy.isHistoricalPriceSupported()).isTrue();
        assertThat(spy.isOptimisationSupported()).isTrue();
    }

    // Confirms the inference does not incorrectly make manual real-estate assets eligible.
    @Test
    void manualRealEstateProfile_shouldRemainOptimisationUnsupported() {
        Activity propertyBuy = buy("HOUSE1", "1", "250000", "GBP", "2026-01-01T10:00:00Z");

        AssetProfile manualProperty = profileWithStaleSupportFlags(
                "HOUSE1",
                "Rental Property",
                AssetClass.REAL_ESTATE
        );
        manualProperty.setManualValuationRequired(true);
        manualProperty.setPriceDataSupported(false);

        when(activityRepository.findByUserIdOrderByDateDesc(userId))
                .thenReturn(List.of(propertyBuy));

        when(assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, "HOUSE1"))
                .thenReturn(Optional.of(manualProperty));

        List<HoldingResponse> result = holdingService.getHoldings(userId);

        assertThat(result).hasSize(1);

        HoldingResponse house = result.get(0);

        assertThat(house.getSymbol()).isEqualTo("HOUSE1");
        assertThat(house.isManualValuationRequired()).isTrue();
        assertThat(house.isPriceDataSupported()).isFalse();
        assertThat(house.isHistoricalPriceSupported()).isFalse();
        assertThat(house.isOptimisationSupported()).isFalse();
    }

    private Activity buy(
            String symbol,
            String quantity,
            String unitPrice,
            String currency,
            String date
    ) {
        Activity activity = new Activity();
        activity.setUserId(userId);
        activity.setType(ActivityType.BUY);
        activity.setSymbol(symbol);
        activity.setQuantity(bd(quantity));
        activity.setUnitPrice(bd(unitPrice));
        activity.setFee(BigDecimal.ZERO);
        activity.setCurrency(currency);
        activity.setDate(Instant.parse(date));
        return activity;
    }

    private AssetProfile profileWithStaleSupportFlags(
            String symbol,
            String displayName,
            AssetClass assetClass
    ) {
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        profile.setSymbol(symbol);
        profile.setDisplayName(displayName);
        profile.setAssetClass(assetClass);
        profile.setSector("Technology");
        profile.setMarket("United States");
        profile.setCurrency("USD");
        profile.setManualValuationRequired(false);
        profile.setPriceDataSupported(true);

        /*
         These false flags simulate old asset profiles created before metadata support was reliable.
         */
        profile.setHistoricalPriceSupported(false);
        profile.setOptimisationSupported(false);

        return profile;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}