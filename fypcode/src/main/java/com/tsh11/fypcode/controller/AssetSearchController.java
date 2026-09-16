package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.response.AssetSearchResultResponse;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.integration.marketdata.AssetMetadata;
import com.tsh11.fypcode.dto.response.AssetMetadataResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/assets")
public class AssetSearchController {

    private final MarketDataProvider marketDataProvider;

    public AssetSearchController(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }

    @GetMapping("/search")
    public List<AssetSearchResultResponse> search(@RequestParam String query) {
        return marketDataProvider.searchAssets(query);
    }

    @GetMapping("/metadata")
    public AssetMetadataResponse metadata(@RequestParam String symbol) {
        AssetMetadata metadata = marketDataProvider.getAssetMetadata(symbol)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Asset metadata not found for symbol: " + symbol
                ));

        AssetMetadataResponse response = new AssetMetadataResponse();
        response.setSymbol(metadata.getSymbol());
        response.setDisplayName(metadata.getDisplayName());
        response.setAssetClass(metadata.getAssetClass());
        response.setSector(metadata.getSector());
        response.setMarket(metadata.getMarket());
        response.setCurrency(metadata.getCurrency());
        response.setManualValuationRequired(metadata.isManualValuationRequired());
        response.setPriceDataSupported(metadata.isPriceDataSupported());
        response.setDataSource(metadata.getDataSource());

        return response;
    }
}