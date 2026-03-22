package com.recon.api.controller;

import com.recon.api.domain.ActivityFeedResponse;
import com.recon.api.domain.AuditArchiveExecutionResponse;
import com.recon.api.domain.AuditRetentionCenterResponse;
import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.RunAuditArchiveRequest;
import com.recon.api.domain.SaveAuditRetentionPolicyRequest;
import com.recon.api.domain.SoxAuditReportResponse;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.AuditComplianceService;
import com.recon.api.service.ActivityFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/activity")
@RequiredArgsConstructor
public class ActivityFeedController {

    private final ActivityFeedService activityFeedService;
    private final AuditComplianceService auditComplianceService;

    @GetMapping
    public ApiResponse<ActivityFeedResponse> getActivity(
            @AuthenticationPrincipal ReconUserPrincipal principal,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "true") boolean includeArchived,
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
                includeArchived,
                limit
        ));
    }

    @GetMapping("/retention-center")
    public ApiResponse<AuditRetentionCenterResponse> getRetentionCenter(
            @AuthenticationPrincipal ReconUserPrincipal principal
    ) {
        requireAuditView(principal);
        return ApiResponse.ok(auditComplianceService.getRetentionCenter(principal.getTenantId()));
    }

    @PutMapping("/retention-policy")
    public ApiResponse<com.recon.api.domain.AuditRetentionPolicyDto> saveRetentionPolicy(
            @AuthenticationPrincipal ReconUserPrincipal principal,
            @RequestBody SaveAuditRetentionPolicyRequest request
    ) {
        requireAuditManage(principal);
        return ApiResponse.ok(auditComplianceService.saveRetentionPolicy(
                principal.getTenantId(),
                request,
                principal.getUsername()
        ));
    }

    @PostMapping("/archive")
    public ApiResponse<AuditArchiveExecutionResponse> runArchive(
            @AuthenticationPrincipal ReconUserPrincipal principal,
            @RequestBody(required = false) RunAuditArchiveRequest request
    ) {
        requireAuditManage(principal);
        return ApiResponse.ok(auditComplianceService.runArchive(
                principal.getTenantId(),
                request,
                principal.getUsername()
        ));
    }

    @GetMapping("/sox-report")
    public ApiResponse<SoxAuditReportResponse> getSoxReport(
            @AuthenticationPrincipal ReconUserPrincipal principal,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        requireAuditView(principal);
        return ApiResponse.ok(auditComplianceService.getSoxReport(
                principal.getTenantId(),
                moduleKey,
                fromDate,
                toDate
        ));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAudit(
            @AuthenticationPrincipal ReconUserPrincipal principal,
            @RequestParam(required = false) String moduleKey,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false, defaultValue = "true") boolean includeArchived,
            @RequestParam(required = false, defaultValue = "CSV") String format
    ) {
        requireAuditExport(principal);
        AuditComplianceService.ExportBundle bundle = auditComplianceService.exportEntries(
                principal.getTenantId(),
                moduleKey,
                sourceType,
                actor,
                fromDate,
                toDate,
                format,
                includeArchived,
                principal.getUsername()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bundle.filename() + "\"")
                .contentType(MediaType.parseMediaType(bundle.contentType()))
                .body(bundle.payload());
    }

    private void requireAuditView(ReconUserPrincipal principal) {
        if (principal == null || !principal.getPermissions().contains("AUDIT_VIEW")) {
            throw new AccessDeniedException("Missing permission: AUDIT_VIEW");
        }
    }

    private void requireAuditExport(ReconUserPrincipal principal) {
        requireAuditView(principal);
        if (!principal.getPermissions().contains("AUDIT_EXPORT")) {
            throw new AccessDeniedException("Missing permission: AUDIT_EXPORT");
        }
    }

    private void requireAuditManage(ReconUserPrincipal principal) {
        requireAuditView(principal);
        if (!principal.getPermissions().contains("AUDIT_MANAGE")) {
            throw new AccessDeniedException("Missing permission: AUDIT_MANAGE");
        }
    }
}
