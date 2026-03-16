package com.recon.api.controller;

import com.recon.api.domain.ActivityFeedResponse;
import com.recon.api.domain.ApiResponse;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.ActivityFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityFeedController {

    private final ActivityFeedService activityFeedService;

    @GetMapping
    public ApiResponse<ActivityFeedResponse> getActivity(
            @AuthenticationPrincipal ReconUserPrincipal principal,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Integer limit
    ) {
        requireAuditView(principal);
        return ApiResponse.ok(activityFeedService.getActivity(
                principal.getTenantId(),
                moduleKey,
                sourceType,
                actor,
                fromDate,
                toDate,
                limit
        ));
    }

    private void requireAuditView(ReconUserPrincipal principal) {
        if (principal == null || !principal.getPermissions().contains("AUDIT_VIEW")) {
            throw new AccessDeniedException("Missing permission: AUDIT_VIEW");
        }
    }
}
