package com.tsh11.fypcode.service;

import com.tsh11.fypcode.domain.activity.Activity;
import com.tsh11.fypcode.domain.activity.ActivityType;
import com.tsh11.fypcode.domain.asset.AssetType;
import com.tsh11.fypcode.dto.request.ActivityRequest;
import com.tsh11.fypcode.dto.response.ActivityResponse;
import com.tsh11.fypcode.repository.ActivityRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/*
handles creating, reading, updating, and deleting user activities.
validates whether an activity is allowed, saves it for the correct user,
normalises values like symbols/currency, and triggers asset metadata syncing when needed.
 */
/*
@Transactional is used because ActivityService performs database operations through ActivityRepository,
and Spring needs to manage those operations as a transaction.
 */
@Service
@Transactional
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final AssetMetadataSyncService assetMetadataSyncService;

    public ActivityService(ActivityRepository activityRepository,
                           AssetMetadataSyncService assetMetadataSyncService) {
        this.activityRepository = activityRepository;
        this.assetMetadataSyncService = assetMetadataSyncService;
    }

    //Getting all activities for a user
    //The method returns DTOs (ActivityResponse), the response needed by the frontend
    //convert a List<Activity> into a stream so each Activity can be transformed into an ActivityResponse
    @Transactional(readOnly = true)
    public List<ActivityResponse> getAllActivitiesForUser(UUID userId) {
        return activityRepository.findByUserIdOrderByDateDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    //Getting one activity for a user
    //using findByIdAndUserId, The activity must match both the activity ID and the current user ID.
    //If the activity does not exist for that user, it throws EntityNotFoundException
    @Transactional(readOnly = true)
    public ActivityResponse getActivityForUser(UUID userId, UUID activityId) {
        Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Activity not found: " + activityId));
        return toResponse(activity);
    }

    // Creating an activity
    public ActivityResponse createActivity(UUID userId, ActivityRequest request) {
        //Validate business rules
        validateBusinessRules(request);

        //Create new Activity entity
        Activity activity = new Activity();
        //Attach authenticated userId
        activity.setUserId(userId);
        //Copy request fields into entity
        applyRequest(activity, request);

        //Save to database
        Activity saved = activityRepository.save(activity);
        //Sync asset metadata if needed
        syncAssetMetadataIfNeeded(userId, saved);
        //Return ActivityResponse
        return toResponse(saved);
    }

    //Updating an activity
    public ActivityResponse updateActivity(UUID userId, UUID activityId, ActivityRequest request) {
        //Validate request
        validateBusinessRules(request);

        //Find activity by activityId AND userId
        Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                //If not found, throw error
                .orElseThrow(() -> new EntityNotFoundException("Activity not found: " + activityId));

        //Apply new request fields
        applyRequest(activity, request);

        //Save updated activity
        Activity saved = activityRepository.save(activity);
        //Sync asset metadata if needed
        syncAssetMetadataIfNeeded(userId, saved);
        //Return response
        return toResponse(saved);
    }

    //Deleting an activity
    public void deleteActivity(UUID userId, UUID activityId) {
        //Find activity by id and userId
        Activity activity = activityRepository.findByIdAndUserId(activityId, userId)
                //If not found, throw error
                .orElseThrow(() -> new EntityNotFoundException("Activity not found: " + activityId));

        //Delete activity
        activityRepository.delete(activity);
    }

    //Applying request data to entity
    //This method copies values from ActivityRequest into the Activity entity.
    private void applyRequest(Activity activity, ActivityRequest request) {
        activity.setType(request.getType());
        activity.setAssetType(request.getAssetType());
        activity.setSymbol(normalizeSymbol(request.getSymbol())); //convert  aapl to AAPL
        activity.setDisplayName(normalizeOptional(request.getDisplayName()));
        activity.setAddress(normalizeOptional(request.getAddress()));
        activity.setDate(request.getDate());
        activity.setQuantity(request.getQuantity());
        activity.setUnitPrice(request.getUnitPrice());
        activity.setFee(request.getFee());
        activity.setCurrency(request.getCurrency().trim().toUpperCase()); //convert usd to USD
        activity.setComment(request.getComment());
    }

    //Converting entity to response
    //converts database entity to API response DTO.
    private ActivityResponse toResponse(Activity activity) {
        ActivityResponse response = new ActivityResponse();
        response.setId(activity.getId());
        response.setType(activity.getType());
        response.setAssetType(activity.getAssetType());
        response.setSymbol(activity.getSymbol());
        response.setDisplayName(activity.getDisplayName());
        response.setAddress(activity.getAddress());
        response.setDate(activity.getDate());
        response.setQuantity(activity.getQuantity());
        response.setUnitPrice(activity.getUnitPrice());
        response.setFee(activity.getFee());
        response.setCurrency(activity.getCurrency());
        response.setComment(activity.getComment());
        return response;
    }

    //Validating business rules
    //This method decides what validation applies based on activity type.
    private void validateBusinessRules(ActivityRequest request) {
        ActivityType type = request.getType();

        switch (type) {
            //BUY and SELL are asset trades, so they need more validation.
            case BUY, SELL -> validateAssetTrade(request);
            //A fee activity must have a positive fee.
            case FEE -> {
                //rejects null, 0, less than 0
                if (request.getFee() == null || request.getFee().signum() <= 0) {
                    throw new IllegalArgumentException("Fee must be greater than 0 for FEE activity");
                }
            }
            //pass without additional custom validation
            case CASH_DEPOSIT, CASH_WITHDRAWAL, MANUAL_ASSET_UPDATE -> {
            }
            //catches unsupported activity types
            default -> throw new IllegalArgumentException("Unsupported activity type");
        }
    }

    //Validating BUY/SELL asset trades
    private void validateAssetTrade(ActivityRequest request) {
        if (request.getAssetType() == null) {
            throw new IllegalArgumentException("Asset type is required for BUY and SELL");
        }

        //checks whether the asset type is a market-search asset.
        /*
        EQUITY
        ETF
        BOND
        CRYPTO
        FOREX
        COMMODITY
         */
        AssetType assetType = request.getAssetType();

        if (isMarketSearchAsset(assetType)) {
            //Market assets need a symbol.
            if (isBlank(request.getSymbol())) {
                throw new IllegalArgumentException("A selected market asset is required.");
            }
            //need a display name.
            if (isBlank(request.getDisplayName())) {
                throw new IllegalArgumentException("Display name is required for market assets.");
            }
            //need quantity.
            if (request.getQuantity() == null) {
                throw new IllegalArgumentException("Quantity is required for market assets.");
            }
            //need unit price.
            if (request.getUnitPrice() == null) {
                throw new IllegalArgumentException("Unit price is required for market assets.");
            }
            //Real estate assets
            //Real estate is handled differently
            // it is not a market-search asset and
            // does not have a stock-style quantity.
        } else if (assetType == AssetType.REAL_ESTATE) {
            //Real estate needs a property name.
            if (isBlank(request.getDisplayName())) {
                throw new IllegalArgumentException("Property name is required for real estate.");
            }
            //needs an address.
            if (isBlank(request.getAddress())) {
                throw new IllegalArgumentException("Address is required for real estate.");
            }
            //needs a purchase value.
            if (request.getUnitPrice() == null) {
                throw new IllegalArgumentException("Purchase value is required for real estate.");
            }
            //should not have quantity.
            if (request.getQuantity() != null) {
                throw new IllegalArgumentException("Quantity must be empty for real estate.");
            }
            //Other manual assets
        } else {
            //Manual assets need a name.
            if (isBlank(request.getDisplayName())) {
                throw new IllegalArgumentException("Name is required for manual assets.");
            }
            //Manual assets need a value.
            if (request.getUnitPrice() == null) {
                throw new IllegalArgumentException("Value is required for manual assets.");
            }
        }
    }

    //Syncing asset metadata
    //This method decides whether asset metadata should be created/updated after saving an activity.
    //When the user buys or sells a market asset,
    // the system ensures an AssetProfile exists for that symbol.
    private void syncAssetMetadataIfNeeded(UUID userId, Activity activity) {
        //Only BUY and SELL activities trigger metadata sync.
        if (activity.getType() != ActivityType.BUY && activity.getType() != ActivityType.SELL) {
            return;
        }

        //Only market-search assets trigger metadata sync.
        if (!isMarketSearchAsset(activity.getAssetType())) {
            return;
        }

        //If there is no symbol, metadata cannot be synced.
        if (activity.getSymbol() == null || activity.getSymbol().isBlank()) {
            return;
        }

        //calls AssetMetadataSyncService.
        //ensure an asset profile/metadata record exists for this user and symbol
        assetMetadataSyncService.ensureMetadataForSymbol(
                userId,
                activity.getSymbol(),
                activity.getCurrency()
        );
    }

    //Market-search asset helper
    //This defines which asset types are treated as searchable market assets.
    //Manual assets are not included
    private boolean isMarketSearchAsset(AssetType assetType) {
        return assetType == AssetType.EQUITY
                || assetType == AssetType.ETF
                || assetType == AssetType.BOND
                || assetType == AssetType.CRYPTO
                || assetType == AssetType.FOREX
                || assetType == AssetType.COMMODITY;
    }

    //Symbol normalisation
    //" aapl " → "AAPL"
    //"" → null
    //null → null
    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return symbol.trim().toUpperCase();
    }

    //Optional string normalisation
    //used for display name and address
    //" Apple Inc. " → "Apple Inc."
    //"" → null
    //"   " → null
    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    //Blank string helper
    //This checks whether a string is missing or only whitespace.
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}