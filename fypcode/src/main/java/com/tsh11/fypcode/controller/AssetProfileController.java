package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.request.AssetProfileRequest;
import com.tsh11.fypcode.dto.response.AssetProfileResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.AssetProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/asset-profiles")
public class AssetProfileController {

    private final AssetProfileService assetProfileService;

    public AssetProfileController(AssetProfileService assetProfileService) {
        this.assetProfileService = assetProfileService;
    }

    @GetMapping
    public List<AssetProfileResponse> getAll(@AuthenticationPrincipal AppUserPrincipal principal) {
        return assetProfileService.getAllForUser(principal.getUserId());
    }

    @GetMapping("/{id}")
    public AssetProfileResponse getById(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        return assetProfileService.getByIdForUser(principal.getUserId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetProfileResponse create(@Valid @RequestBody AssetProfileRequest request,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        return assetProfileService.createForUser(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    public AssetProfileResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody AssetProfileRequest request,
                                       @AuthenticationPrincipal AppUserPrincipal principal) {
        return assetProfileService.updateForUser(principal.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id,
                       @AuthenticationPrincipal AppUserPrincipal principal) {
        assetProfileService.deleteForUser(principal.getUserId(), id);
    }
}