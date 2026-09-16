package com.tsh11.fypcode.service;

import com.tsh11.fypcode.domain.asset.AssetClass;
import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.domain.asset.DataSourceType;
import com.tsh11.fypcode.integration.marketdata.AssetMetadata;
import com.tsh11.fypcode.integration.marketdata.MarketDataProvider;
import com.tsh11.fypcode.repository.AssetProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

//When the user records a BUY/SELL activity for a market asset,
// this service tries to fetch metadata for that symbol from the market-data provider.
@Service
@Transactional
public class AssetMetadataSyncService {

    private final AssetProfileRepository assetProfileRepository;
    private final MarketDataProvider marketDataProvider;

    //constructor
    public AssetMetadataSyncService(AssetProfileRepository assetProfileRepository,
                                    MarketDataProvider marketDataProvider) {
        this.assetProfileRepository = assetProfileRepository;
        this.marketDataProvider = marketDataProvider;
    }

    //main function
    public void ensureMetadataForSymbol(UUID userId, String symbol, String fallbackCurrency) {
        //Ignore invalid input
        if (userId == null || isBlank(symbol)) {
            return;
        }

        //" aapl " → "AAPL"
        String normalizedSymbol = normalizeSymbol(symbol);

        //Try to fetch metadata from provider
        Optional<AssetMetadata> metadataOptional = marketDataProvider.getAssetMetadata(normalizedSymbol);
        //If metadata is missing, create fallback profile if needed
        if (metadataOptional.isEmpty()) {
            createFallbackProfileIfMissing(userId, normalizedSymbol, fallbackCurrency);
            return;
        }

        //Get provider metadata
        AssetMetadata metadata = metadataOptional.get();

        //Find existing profile or create a new one
        AssetProfile profile = assetProfileRepository
                .findByUserIdAndSymbolIgnoreCase(userId, normalizedSymbol)
                .orElseGet(AssetProfile::new);

        //Check whether the profile is new
        //id == null → new profile
        //id != null → existing profile
        boolean newProfile = profile.getId() == null;

        //Initialise new profile
        if (newProfile) {
            profile.setUserId(userId);
            profile.setSymbol(normalizedSymbol);
        }

        //Check manual override
        boolean manualOverride = profile.getDataSource() == DataSourceType.MANUAL;

        //Apply metadata differently depending on manual override
        if (manualOverride) {
            //Only fill missing fields, do not overwrite the user’s manual choices.
            applyMinimalMetadataToManualProfile(profile, metadata, normalizedSymbol, fallbackCurrency);
        } else {
            //Apply full provider metadata and update fields.
            applyProviderMetadata(profile, metadata, normalizedSymbol, fallbackCurrency);
        }

        //Save profile, either create new profile, or update existing one
        assetProfileRepository.save(profile);
    }

    //helper
    //creates a basic asset profile if provider metadata is unavailable.
    private void createFallbackProfileIfMissing(UUID userId, String symbol, String fallbackCurrency) {
        //If the profile already exists, the method returns and does nothing.
        boolean exists = assetProfileRepository.existsByUserIdAndSymbolIgnoreCase(userId, symbol);
        if (exists) {
            return;
        }

        //Create fallback profile
        //Since no provider name is available, it uses the symbol as the display name.
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        profile.setSymbol(symbol);
        profile.setDisplayName(symbol);
        profile.setAssetClass(AssetClass.OTHER);
        profile.setSector(null);
        profile.setMarket(null);
        profile.setCurrency(resolveCurrency(null, fallbackCurrency));
        profile.setManualValuationRequired(false);
        profile.setPriceDataSupported(false);
        profile.setDataSource(DataSourceType.OTHER);

        profile.setProviderAssetType(null);
        profile.setQuoteSupported(false);
        profile.setHistoricalPriceSupported(false);
        profile.setOptimisationSupported(false);

        assetProfileRepository.save(profile);
    }

    //applies full provider metadata to an asset profile.
    private void applyProviderMetadata(AssetProfile profile,
                                       AssetMetadata metadata,
                                       String symbol,
                                       String fallbackCurrency) {
        //Resolve asset class
        //Avoid storing null asset class.
        AssetClass resolvedAssetClass = metadata.getAssetClass() != null
                ? metadata.getAssetClass()
                : AssetClass.OTHER;

        //Set main descriptive fields
        profile.setSymbol(symbol);
        profile.setDisplayName(defaultDisplayName(metadata, symbol));
        profile.setAssetClass(resolvedAssetClass);
        profile.setSector(blankToNull(metadata.getSector()));
        profile.setMarket(blankToNull(metadata.getMarket()));
        profile.setCurrency(resolveCurrency(metadata, fallbackCurrency));

        //Set support and data source fields
        profile.setManualValuationRequired(metadata.isManualValuationRequired());
        profile.setPriceDataSupported(metadata.isPriceDataSupported());
        profile.setDataSource(metadata.getDataSource() != null
                ? metadata.getDataSource()
                : DataSourceType.ALPHA_VANTAGE);

        //Set provider support fields
        profile.setProviderAssetType(blankToNull(metadata.getProviderAssetType()));
        profile.setQuoteSupported(metadata.isQuoteSupported());
        profile.setHistoricalPriceSupported(metadata.isHistoricalPriceSupported());
        profile.setOptimisationSupported(metadata.isOptimisationSupported());
    }

    //applies metadata  to a manual profile.
    private void applyMinimalMetadataToManualProfile(AssetProfile profile,
                                                     AssetMetadata metadata,
                                                     String symbol,
                                                     String fallbackCurrency) {
        //Fill symbol only if missing
        if (isBlank(profile.getSymbol())) {
            profile.setSymbol(symbol);
        }

        //Fill display name only if missing
        if (isBlank(profile.getDisplayName())) {
            profile.setDisplayName(defaultDisplayName(metadata, symbol));
        }

        //Fill asset class only if missing
        if (profile.getAssetClass() == null) {
            profile.setAssetClass(metadata.getAssetClass() != null
                    ? metadata.getAssetClass()
                    : AssetClass.OTHER);
        }

        //Fill currency only if missing
        if (isBlank(profile.getCurrency())) {
            profile.setCurrency(resolveCurrency(metadata, fallbackCurrency));
        }

        //Fill provider type only if missing
        if (isBlank(profile.getProviderAssetType())) {
            profile.setProviderAssetType(blankToNull(metadata.getProviderAssetType()));
        }

    }

    private String defaultDisplayName(AssetMetadata metadata, String symbol) {
        if (metadata != null && !isBlank(metadata.getDisplayName())) {
            return metadata.getDisplayName().trim();
        }
        return symbol;
    }

    private String resolveCurrency(AssetMetadata metadata, String fallbackCurrency) {
        if (metadata != null && !isBlank(metadata.getCurrency())) {
            return metadata.getCurrency().trim().toUpperCase();
        }

        if (!isBlank(fallbackCurrency)) {
            return fallbackCurrency.trim().toUpperCase();
        }

        return "USD";
    }

    private String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}