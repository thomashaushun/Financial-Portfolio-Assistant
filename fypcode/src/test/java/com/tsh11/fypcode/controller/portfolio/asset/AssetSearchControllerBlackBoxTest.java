package com.tsh11.fypcode.controller.portfolio.asset;

import com.tsh11.fypcode.controller.AssetSearchController;
import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetType;
import com.tsh11.fypcode.domain.asset.DataSourceType;
import com.tsh11.fypcode.dto.response.AssetSearchResultResponse;
import com.tsh11.fypcode.integration.marketdata.AssetMetadata;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

// Tests these API endpoints:
//GET /api/assets/search
//GET /api/assets/metadata
//
//Verifies that asset search and metadata endpoints expose market-data provider results to the frontend.
@ExtendWith(MockitoExtension.class)
class AssetSearchControllerBlackBoxTest {

    @Mock
    private MarketDataProvider marketDataProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AssetSearchController controller = new AssetSearchController(marketDataProvider);

        mockMvc = standaloneSetup(controller)
                .build();
    }

    // Verifies that asset search returns symbol, display name,
    // asset type, market, currency, and match score.
    @Test
    void search_shouldReturnAssetSearchResultsJson() throws Exception {
        AssetSearchResultResponse apple = new AssetSearchResultResponse();
        apple.setSymbol("AAPL");
        apple.setDisplayName("Apple Inc.");
        apple.setAssetType(AssetType.EQUITY);
        apple.setMarket("NASDAQ");
        apple.setCurrency("USD");
        apple.setMatchScore("0.95");

        when(marketDataProvider.searchAssets("apple"))
                .thenReturn(List.of(apple));

        mockMvc.perform(get("/api/assets/search")
                        .param("query", "apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$[0].displayName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].assetType").value("EQUITY"))
                .andExpect(jsonPath("$[0].market").value("NASDAQ"))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].matchScore").value("0.95"));

        verify(marketDataProvider).searchAssets("apple");
    }

    // Verifies that an empty search result returns an empty JSON array.
    @Test
    void search_whenNoResults_shouldReturnEmptyJsonArray() throws Exception {
        when(marketDataProvider.searchAssets("unknown"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/assets/search")
                        .param("query", "unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(marketDataProvider).searchAssets("unknown");
    }

    // Verifies that metadata for a valid symbol is returned as JSON.
    @Test
    void metadata_shouldReturnMetadataJsonWhenSymbolExists() throws Exception {
        AssetMetadata metadata = new AssetMetadata();
        metadata.setSymbol("AAPL");
        metadata.setDisplayName("Apple Inc.");
        metadata.setAssetClass(AssetClass.EQUITY);
        metadata.setSector("Technology");
        metadata.setMarket("NASDAQ");
        metadata.setCurrency("USD");
        metadata.setManualValuationRequired(false);
        metadata.setPriceDataSupported(true);
        metadata.setDataSource(DataSourceType.ALPHA_VANTAGE);

        when(marketDataProvider.getAssetMetadata("AAPL"))
                .thenReturn(Optional.of(metadata));

        mockMvc.perform(get("/api/assets/metadata")
                        .param("symbol", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.displayName").value("Apple Inc."))
                .andExpect(jsonPath("$.assetClass").value("EQUITY"))
                .andExpect(jsonPath("$.sector").value("Technology"))
                .andExpect(jsonPath("$.market").value("NASDAQ"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.manualValuationRequired").value(false))
                .andExpect(jsonPath("$.priceDataSupported").value(true))
                .andExpect(jsonPath("$.dataSource").value("ALPHA_VANTAGE"));

        verify(marketDataProvider).getAssetMetadata("AAPL");
    }

    // Verifies that missing metadata returns HTTP 404.
    @Test
    void metadata_whenSymbolNotFound_shouldReturnNotFound() throws Exception {
        when(marketDataProvider.getAssetMetadata("UNKNOWN"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/assets/metadata")
                        .param("symbol", "UNKNOWN"))
                .andExpect(status().isNotFound());

        verify(marketDataProvider).getAssetMetadata("UNKNOWN");
    }
}