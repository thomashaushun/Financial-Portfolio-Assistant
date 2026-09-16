package com.tsh11.fypcode.controller;

import com.tsh11.fypcode.dto.request.ActivityRequest;
import com.tsh11.fypcode.dto.response.ActivityResponse;
import com.tsh11.fypcode.security.AppUserPrincipal;
import com.tsh11.fypcode.service.ActivityService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/portfolio/activities")
public class PortfolioActivityController {

    private final ActivityService activityService;

    public PortfolioActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public List<ActivityResponse> getAllActivities(@AuthenticationPrincipal AppUserPrincipal principal) {
        return activityService.getAllActivitiesForUser(principal.getUserId());
    }

    @GetMapping("/{id}")
    public ActivityResponse getActivity(@PathVariable UUID id,
                                        @AuthenticationPrincipal AppUserPrincipal principal) {
        return activityService.getActivityForUser(principal.getUserId(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse createActivity(@Valid @RequestBody ActivityRequest request,
                                           @AuthenticationPrincipal AppUserPrincipal principal) {
        return activityService.createActivity(principal.getUserId(), request);
    }

    @PutMapping("/{id}")
    public ActivityResponse updateActivity(@PathVariable UUID id,
                                           @Valid @RequestBody ActivityRequest request,
                                           @AuthenticationPrincipal AppUserPrincipal principal) {
        return activityService.updateActivity(principal.getUserId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActivity(@PathVariable UUID id,
                               @AuthenticationPrincipal AppUserPrincipal principal) {
        activityService.deleteActivity(principal.getUserId(), id);
    }
}