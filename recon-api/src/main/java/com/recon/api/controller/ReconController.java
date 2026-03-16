package com.recon.api.controller;

import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.DashboardAnalyticsResponse;
import com.recon.api.domain.DashboardStats;
import com.recon.api.domain.PagedResult;
import com.recon.api.domain.ReconSearchRequest;
import com.recon.api.domain.ReconSummary;
import com.recon.api.domain.TenantConfig;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.ReconQueryService;
import com.recon.api.service.DashboardAnalyticsService;
import com.recon.api.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recon")
@RequiredArgsConstructor
@Slf4j
public class ReconController {

    private final ReconQueryService queryService;
    private final DashboardAnalyticsService analyticsService;
    private final TenantService tenantService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(
                ApiResponse.ok("Recon API is running"));
    }

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<List<TenantConfig>>> getTenants() {
        return ResponseEntity.ok(
                ApiResponse.ok(tenantService.getAllTenants()));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardStats>> getDashboard(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId,
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            TenantConfig tenant = tenantService.getTenant(tenantId);
            DashboardStats stats = queryService.getDashboardStats(
                    storeIds, fromBusinessDate, toBusinessDate, reconView, tenant);
            return ResponseEntity.ok(ApiResponse.ok(stats));
        } catch (Exception e) {
            log.error("Dashboard error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/dashboard/analytics")
    public ResponseEntity<ApiResponse<DashboardAnalyticsResponse>> getDashboardAnalytics(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId,
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "wkstnIds", required = false) List<String> wkstnIds,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            DashboardAnalyticsResponse analytics = analyticsService.getAnalytics(
                    storeIds, wkstnIds, reconView);
            return ResponseEntity.ok(ApiResponse.ok(analytics));
        } catch (Exception e) {
            log.error("Dashboard analytics error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PagedResult<ReconSummary>>> getTransactions(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId,
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "wkstnIds", required = false) List<String> wkstnIds,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @RequestParam(name = "reconStatus", required = false) String reconStatus,
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "transactionKey", required = false) String transactionKey,
            @RequestParam(name = "fromDate", required = false) String fromDate,
            @RequestParam(name = "toDate", required = false) String toDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            TenantConfig tenant = tenantService.getTenant(tenantId);
            ReconSearchRequest req = ReconSearchRequest.builder()
                    .storeIds(storeIds)
                    .wkstnIds(wkstnIds)
                    .fromBusinessDate(fromBusinessDate)
                    .toBusinessDate(toBusinessDate)
                    .reconStatus(reconStatus)
                    .reconView(reconView)
                    .transactionKey(transactionKey)
                    .fromDate(fromDate)
                    .toDate(toDate)
                    .page(page)
                    .size(Math.min(size, 100))
                    .build();
            PagedResult<ReconSummary> result = queryService.search(req, tenant);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Search error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/transactions/{transactionKey}")
    public ResponseEntity<ApiResponse<ReconSummary>> getTransaction(
            @PathVariable("transactionKey") String transactionKey,
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireGenericReconAccess(principal);
            TenantConfig tenant = tenantService.getTenant(tenantId);
            ReconSummary summary = queryService.getByTransactionKey(transactionKey, tenant);
            if (summary == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(ApiResponse.ok(summary));
        } catch (Exception e) {
            log.error("Get transaction error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/mismatches")
    public ResponseEntity<ApiResponse<List<ReconSummary>>> getMismatches(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId,
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireGenericReconAccess(principal);
            TenantConfig tenant = tenantService.getTenant(tenantId);
            List<ReconSummary> results = queryService.getMismatches(
                    storeIds, fromBusinessDate, toBusinessDate, page, size, tenant);
            return ResponseEntity.ok(ApiResponse.ok(results));
        } catch (Exception e) {
            log.error("Mismatches error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/missing")
    public ResponseEntity<ApiResponse<PagedResult<ReconSummary>>> getMissing(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId,
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            TenantConfig tenant = tenantService.getTenant(tenantId);
            ReconSearchRequest req = ReconSearchRequest.builder()
                    .storeIds(storeIds)
                    .fromBusinessDate(fromBusinessDate)
                    .toBusinessDate(toBusinessDate)
                    .reconView(reconView)
                    .reconStatus(resolveMissingStatus(reconView))
                    .page(page)
                    .size(size)
                    .build();
            PagedResult<ReconSummary> result = queryService.search(req, tenant);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (Exception e) {
            log.error("Missing error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/stores")
    public ResponseEntity<ApiResponse<List<String>>> getStores(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            List<String> stores = queryService.getStores(reconView);
            return ResponseEntity.ok(ApiResponse.ok(stores));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/registers")
    public ResponseEntity<ApiResponse<List<String>>> getRegisters(
            @RequestParam(name = "tenantId", defaultValue = "tenant-india") String tenantId,
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            List<String> registers = queryService.getRegisters(storeIds, reconView);
            return ResponseEntity.ok(ApiResponse.ok(registers));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    private String resolveMissingStatus(String reconView) {
        if ("XSTORE_SIOCS".equalsIgnoreCase(reconView)) {
            return "MISSING_IN_SIOCS";
        }
        if ("XSTORE_XOCS".equalsIgnoreCase(reconView)) {
            return "MISSING_IN_XOCS";
        }
        return "MISSING_IN_SIM";
    }

    private void requireGenericReconAccess(ReconUserPrincipal principal) {
        if (!principal.hasPermission("RECON_VIEW")) {
            throw new AccessDeniedException("Missing permission: RECON_VIEW");
        }
    }

    private void requireReconAccess(ReconUserPrincipal principal, String reconView) {
        requireGenericReconAccess(principal);
        if (reconView == null || reconView.isBlank()) {
            return;
        }

        String requiredPermission = switch (reconView.toUpperCase()) {
            case "XSTORE_SIOCS" -> "RECON_XSTORE_SIOCS";
            case "XSTORE_XOCS" -> "RECON_XSTORE_XOCS";
            case "XSTORE_SIM" -> "RECON_XSTORE_SIM";
            default -> null;
        };

        if (requiredPermission != null && !principal.hasPermission(requiredPermission)) {
            throw new AccessDeniedException("Missing permission: " + requiredPermission);
        }
    }
}
