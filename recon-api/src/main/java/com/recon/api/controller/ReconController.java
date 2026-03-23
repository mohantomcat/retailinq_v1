package com.recon.api.controller;

import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.DashboardAnalyticsResponse;
import com.recon.api.domain.DashboardStats;
import com.recon.api.domain.PagedResult;
import com.recon.api.domain.ReconSearchRequest;
import com.recon.api.domain.ReconSummary;
import com.recon.api.domain.ScorecardsResponse;
import com.recon.api.domain.TenantConfig;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.AccessScopeService;
import com.recon.api.service.ReconQueryService;
import com.recon.api.service.DashboardAnalyticsService;
import com.recon.api.service.ScorecardService;
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
    private final ScorecardService scorecardService;
    private final TenantService tenantService;
    private final AccessScopeService accessScopeService;

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
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            AccessScopeService.StoreScopeFilter storeScope = accessScopeService.resolveStoreScope(principal, storeIds);
            TenantConfig tenant = tenantService.getTenant(principal.getTenantId());
            if (storeScope.denyAll()) {
                return ResponseEntity.ok(ApiResponse.ok(DashboardStats.builder()
                        .asOf(com.recon.api.util.TimezoneConverter.toDisplay(java.time.Instant.now().toString(), tenant))
                        .build()));
            }
            DashboardStats stats = queryService.getDashboardStats(
                    storeScope.storeIds(), fromBusinessDate, toBusinessDate, reconView, tenant);
            return ResponseEntity.ok(ApiResponse.ok(stats));
        } catch (Exception e) {
            log.error("Dashboard error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/dashboard/analytics")
    public ResponseEntity<ApiResponse<DashboardAnalyticsResponse>> getDashboardAnalytics(
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "wkstnIds", required = false) List<String> wkstnIds,
            @RequestParam(name = "transactionTypes", required = false) List<String> transactionTypes,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            AccessScopeService.StoreScopeFilter storeScope = accessScopeService.resolveStoreScope(principal, storeIds);
            if (storeScope.denyAll()) {
                return ResponseEntity.ok(ApiResponse.ok(DashboardAnalyticsResponse.builder()
                        .last7Days(List.of())
                        .last30Days(List.of())
                        .topFailingStores(List.of())
                        .topFailingRegisters(List.of())
                        .exceptionAging(List.of())
                        .build()));
            }
            DashboardAnalyticsResponse analytics = analyticsService.getAnalytics(
                    principal.getTenantId(), storeScope.storeIds(), wkstnIds, transactionTypes, reconView);
            return ResponseEntity.ok(ApiResponse.ok(analytics));
        } catch (Exception e) {
            log.error("Dashboard analytics error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/scorecards")
    public ResponseEntity<ApiResponse<ScorecardsResponse>> getScorecards(
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReportsAccess(principal);
            requireReconAccess(principal, reconView);
            AccessScopeService.StoreScopeFilter storeScope = accessScopeService.resolveStoreScope(principal, storeIds);
            if (storeScope.denyAll()) {
                return ResponseEntity.ok(ApiResponse.ok(ScorecardsResponse.builder()
                        .moduleScorecards(List.of())
                        .storeScorecards(List.of())
                        .build()));
            }
            ScorecardsResponse response = scorecardService.getScorecards(
                    principal.getTenantId(),
                    storeScope.storeIds(),
                    fromBusinessDate,
                    toBusinessDate,
                    reconView,
                    allowedReconViews(principal)
            );
            return ResponseEntity.ok(ApiResponse.ok(response));
        } catch (Exception e) {
            log.error("Scorecards error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PagedResult<ReconSummary>>> getTransactions(
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "wkstnIds", required = false) List<String> wkstnIds,
            @RequestParam(name = "transactionTypes", required = false) List<String> transactionTypes,
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
            AccessScopeService.StoreScopeFilter storeScope = accessScopeService.resolveStoreScope(principal, storeIds);
            TenantConfig tenant = tenantService.getTenant(principal.getTenantId());
            if (storeScope.denyAll()) {
                return ResponseEntity.ok(ApiResponse.ok(PagedResult.<ReconSummary>builder()
                        .content(List.of())
                        .page(page)
                        .size(Math.min(size, 100))
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build()));
            }
            ReconSearchRequest req = ReconSearchRequest.builder()
                    .storeIds(storeScope.storeIds())
                    .wkstnIds(wkstnIds)
                    .transactionTypes(transactionTypes)
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
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireGenericReconAccess(principal);
            TenantConfig tenant = tenantService.getTenant(principal.getTenantId());
            ReconSummary summary = queryService.getByTransactionKey(transactionKey, tenant);
            if (summary == null) {
                return ResponseEntity.notFound().build();
            }
            if (!accessScopeService.canAccessStore(principal, summary.getStoreId())) {
                throw new AccessDeniedException("Missing access to store: " + summary.getStoreId());
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
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireGenericReconAccess(principal);
            AccessScopeService.StoreScopeFilter storeScope = accessScopeService.resolveStoreScope(principal, storeIds);
            TenantConfig tenant = tenantService.getTenant(principal.getTenantId());
            if (storeScope.denyAll()) {
                return ResponseEntity.ok(ApiResponse.ok(List.of()));
            }
            List<ReconSummary> results = queryService.getMismatches(
                    storeScope.storeIds(), fromBusinessDate, toBusinessDate, page, size, tenant);
            return ResponseEntity.ok(ApiResponse.ok(results));
        } catch (Exception e) {
            log.error("Mismatches error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/missing")
    public ResponseEntity<ApiResponse<PagedResult<ReconSummary>>> getMissing(
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            AccessScopeService.StoreScopeFilter storeScope = accessScopeService.resolveStoreScope(principal, storeIds);
            TenantConfig tenant = tenantService.getTenant(principal.getTenantId());
            if (storeScope.denyAll()) {
                return ResponseEntity.ok(ApiResponse.ok(PagedResult.<ReconSummary>builder()
                        .content(List.of())
                        .page(page)
                        .size(size)
                        .totalElements(0)
                        .totalPages(0)
                        .last(true)
                        .build()));
            }
            ReconSearchRequest req = ReconSearchRequest.builder()
                    .storeIds(storeScope.storeIds())
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
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            AccessScopeService.StoreScopeFilter storeScope = accessScopeService.resolveStoreScope(principal, List.of());
            if (storeScope.denyAll()) {
                return ResponseEntity.ok(ApiResponse.ok(List.of()));
            }
            List<String> stores = storeScope.storeIds();
            return ResponseEntity.ok(ApiResponse.ok(stores));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/registers")
    public ResponseEntity<ApiResponse<List<String>>> getRegisters(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            AccessScopeService.StoreScopeFilter storeScope = accessScopeService.resolveStoreScope(principal, storeIds);
            if (storeScope.denyAll()) {
                return ResponseEntity.ok(ApiResponse.ok(List.of()));
            }
            List<String> registers = queryService.getRegisters(storeScope.storeIds(), reconView);
            return ResponseEntity.ok(ApiResponse.ok(registers));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/transaction-types")
    public ResponseEntity<ApiResponse<List<String>>> getTransactionTypes(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "storeIds", required = false) List<String> storeIds,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReconAccess(principal, reconView);
            AccessScopeService.StoreScopeFilter storeScope = accessScopeService.resolveStoreScope(principal, storeIds);
            if (storeScope.denyAll()) {
                return ResponseEntity.ok(ApiResponse.ok(List.of()));
            }
            List<String> transactionTypes = queryService.getTransactionTypes(storeScope.storeIds(), reconView);
            return ResponseEntity.ok(ApiResponse.ok(transactionTypes));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    private String resolveMissingStatus(String reconView) {
        if ("XSTORE_SIOCS".equalsIgnoreCase(reconView)) {
            return "MISSING_IN_SIOCS";
        }
        if ("SIOCS_MFCS".equalsIgnoreCase(reconView)) {
            return "MISSING_IN_MFCS";
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

    private void requireReportsAccess(ReconUserPrincipal principal) {
        if (!principal.hasPermission("REPORTS_VIEW")) {
            throw new AccessDeniedException("Missing permission: REPORTS_VIEW");
        }
    }

    private List<String> allowedReconViews(ReconUserPrincipal principal) {
        java.util.ArrayList<String> allowed = new java.util.ArrayList<>();
        if (principal.hasPermission("RECON_XSTORE_SIM")) {
            allowed.add("XSTORE_SIM");
        }
        if (principal.hasPermission("RECON_XSTORE_SIOCS")) {
            allowed.add("XSTORE_SIOCS");
        }
        if (principal.hasPermission("RECON_XSTORE_XOCS")) {
            allowed.add("XSTORE_XOCS");
        }
        if (principal.hasPermission("RECON_SIOCS_MFCS")) {
            allowed.add("SIOCS_MFCS");
        }
        return allowed;
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
            case "SIOCS_MFCS" -> "RECON_SIOCS_MFCS";
            default -> null;
        };

        if (requiredPermission != null && !principal.hasPermission(requiredPermission)) {
            throw new AccessDeniedException("Missing permission: " + requiredPermission);
        }
    }
}
