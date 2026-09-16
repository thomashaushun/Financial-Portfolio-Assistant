package com.tsh11.fypcode.service;

import com.tsh11.fypcode.domain.asset.AssetProfile;
import com.tsh11.fypcode.dto.request.AssetProfileRequest;
import com.tsh11.fypcode.dto.response.AssetProfileResponse;
import com.tsh11.fypcode.repository.AssetProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

//lets the system create, read, update, delete, and look up asset profiles for the current user.
@Service
@Transactional
public class AssetProfileService {

    private final AssetProfileRepository assetProfileRepository;

    //constructor
    public AssetProfileService(AssetProfileRepository assetProfileRepository) {
        this.assetProfileRepository = assetProfileRepository;
    }

    //gets all asset profiles belonging to one user.
    //They are sorted by symbol ascending.
    //The frontend receives DTOs, not database entities.
    @Transactional(readOnly = true)
    public List<AssetProfileResponse> getAllForUser(UUID userId) {
        return assetProfileRepository.findByUserIdOrderBySymbolAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    //gets one asset profile by ID, but only if it belongs to the current user.
    @Transactional(readOnly = true)
    public AssetProfileResponse getByIdForUser(UUID userId, UUID id) {
        AssetProfile profile = assetProfileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Asset profile not found: " + id));
        return toResponse(profile);
    }

    //creates a new asset profile.
    public AssetProfileResponse createForUser(UUID userId, AssetProfileRequest request) {
        //normalises the symbol, " aapl " → "AAPL"
        String normalizedSymbol = normalizeSymbol(request.getSymbol());

        //checks whether the same user already has an asset profile for that symbol
        //A user cannot create duplicate profiles for the same symbol.
        if (assetProfileRepository.existsByUserIdAndSymbolIgnoreCase(userId, normalizedSymbol)) {
            throw new IllegalArgumentException("Asset profile already exists for symbol: " + normalizedSymbol);
        }

        //creates a new entity and assigns it to the current user.
        //then copy request data into the entity using applyRequest
        AssetProfile profile = new AssetProfile();
        profile.setUserId(userId);
        applyRequest(profile, request);

        //saves the profile and returns it as a response DTO
        AssetProfile saved = assetProfileRepository.save(profile);
        return toResponse(saved);
    }

    //updates an existing profile
    public AssetProfileResponse updateForUser(UUID userId, UUID id, AssetProfileRequest request) {
        //try to find profile by id and userID
        //If it does not exist, it throws EntityNotFoundException.
        AssetProfile profile = assetProfileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Asset profile not found: " + id));

        //normalises the symbol, " aapl " → "AAPL"
        String normalizedSymbol = normalizeSymbol(request.getSymbol());
        //checks for duplicate symbol conflicts during update
        /*
        Example:
        Existing profiles:
        Profile 1 = AAPL
        Profile 2 = MSFT

        If editing Profile 2 and changing symbol to AAPL:
        Reject, because another profile already uses AAPL.
         */
        assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, normalizedSymbol)
                .ifPresent(existing -> {
                    if (!existing.getId().equals(id)) {
                        throw new IllegalArgumentException("Another asset profile already uses symbol: " + normalizedSymbol);
                    }
                });

        //Copies request values into the existing entity
        applyRequest(profile, request);

        //Saves the updated profile and returns a response
        AssetProfile saved = assetProfileRepository.save(profile);
        return toResponse(saved);
    }

    //deletes an asset profile
    //A user can only delete their own asset profile
    public void deleteForUser(UUID userId, UUID id) {
        //try to find profile by id and userID
        //If it does not exist, it throws EntityNotFoundException.
        AssetProfile profile = assetProfileRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new EntityNotFoundException("Asset profile not found: " + id));

        assetProfileRepository.delete(profile);
    }

    //helper functions

    //copies data from AssetProfileRequest into an AssetProfile entity
    private void applyRequest(AssetProfile profile, AssetProfileRequest request) {
        profile.setSymbol(normalizeSymbol(request.getSymbol())); //" aapl " → "AAPL"
        profile.setDisplayName(request.getDisplayName().trim()); //remove space, " Apple Inc. " → "Apple Inc."
        profile.setAssetClass(request.getAssetClass());
        profile.setSector(normalizeOptional(request.getSector()));
        profile.setMarket(normalizeOptional(request.getMarket()));
        profile.setCurrency(request.getCurrency().trim().toUpperCase());
        profile.setManualValuationRequired(request.isManualValuationRequired());
        profile.setPriceDataSupported(request.isPriceDataSupported());
        profile.setDataSource(request.getDataSource());
    }

    private AssetProfileResponse toResponse(AssetProfile profile) {
        AssetProfileResponse response = new AssetProfileResponse();
        response.setId(profile.getId());
        response.setSymbol(profile.getSymbol());
        response.setDisplayName(profile.getDisplayName());
        response.setAssetClass(profile.getAssetClass());
        response.setSector(profile.getSector());
        response.setMarket(profile.getMarket());
        response.setCurrency(profile.getCurrency());
        response.setManualValuationRequired(profile.isManualValuationRequired());
        response.setPriceDataSupported(profile.isPriceDataSupported());
        response.setDataSource(profile.getDataSource());
        response.setCreatedAt(profile.getCreatedAt());
        response.setUpdatedAt(profile.getUpdatedAt());
        return response;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? null : symbol.trim().toUpperCase();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @Transactional(readOnly = true)
    public AssetProfile findEntityByUserIdAndSymbol(UUID userId, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        return assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, symbol.trim())
                .orElse(null);
    }

    //marks an asset as not supporting historical prices or optimisation.
    public void markHistoricalUnsupported(UUID userId, String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return;
        }

        assetProfileRepository.findByUserIdAndSymbolIgnoreCase(userId, symbol.trim())
                .ifPresent(profile -> {
                    profile.setHistoricalPriceSupported(false);
                    profile.setOptimisationSupported(false);
                    assetProfileRepository.save(profile);
                });
    }
}