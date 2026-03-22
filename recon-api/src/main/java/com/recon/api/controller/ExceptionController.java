package com.recon.api.controller;

import com.recon.api.domain.AddExceptionCommentRequest;
import com.recon.api.domain.ApiResponse;
import com.recon.api.domain.BulkUpdateExceptionCasesRequest;
import com.recon.api.domain.BulkUpdateExceptionCasesResponse;
import com.recon.api.domain.DecideExceptionApprovalRequest;
import com.recon.api.domain.CreateExceptionExternalTicketRequest;
import com.recon.api.domain.ExceptionApprovalCenterResponse;
import com.recon.api.domain.ExceptionApprovalRequestDto;
import com.recon.api.domain.ExceptionAutomationCenterResponse;
import com.recon.api.domain.ExceptionAssignmentOptionsDto;
import com.recon.api.domain.ExceptionCaseDto;
import com.recon.api.domain.ExceptionClosurePolicyDto;
import com.recon.api.domain.ExceptionEscalationPolicyCenterResponse;
import com.recon.api.domain.ExceptionEscalationPolicyDto;
import com.recon.api.domain.ExceptionPlaybookDto;
import com.recon.api.domain.ExceptionPlaybookStepExecutionResponseDto;
import com.recon.api.domain.ExceptionQueueResponse;
import com.recon.api.domain.ExceptionSuppressionRuleDto;
import com.recon.api.domain.ExceptionRoutingRuleDto;
import com.recon.api.domain.ExceptionExternalTicketDto;
import com.recon.api.domain.ExceptionIntegrationChannelDto;
import com.recon.api.domain.ExceptionOutboundCommunicationDto;
import com.recon.api.domain.ExceptionTicketingCenterResponse;
import com.recon.api.domain.KnownIssueCatalogResponse;
import com.recon.api.domain.KnownIssueDto;
import com.recon.api.domain.KnownIssueFeedbackResponse;
import com.recon.api.domain.OperationsCommandCenterResponse;
import com.recon.api.domain.RecurrenceAnalyticsResponse;
import com.recon.api.domain.RegionalIncidentBoardResponse;
import com.recon.api.domain.RootCauseAnalyticsResponse;
import com.recon.api.domain.SaveKnownIssueRequest;
import com.recon.api.domain.SaveExceptionIntegrationChannelRequest;
import com.recon.api.domain.SaveExceptionClosurePolicyRequest;
import com.recon.api.domain.SaveExceptionEscalationPolicyRequest;
import com.recon.api.domain.SaveExceptionPlaybookRequest;
import com.recon.api.domain.SaveExceptionSuppressionRuleRequest;
import com.recon.api.domain.SaveExceptionRoutingRuleRequest;
import com.recon.api.domain.SendExceptionCommunicationRequest;
import com.recon.api.domain.StoreManagerLiteResponse;
import com.recon.api.domain.SubmitKnownIssueFeedbackRequest;
import com.recon.api.domain.SyncExceptionExternalTicketRequest;
import com.recon.api.domain.UpdateExceptionCaseRequest;
import com.recon.api.security.ReconUserPrincipal;
import com.recon.api.service.ExceptionAutomationService;
import com.recon.api.service.ExceptionQueueService;
import com.recon.api.service.ExceptionEscalationService;
import com.recon.api.service.ExceptionWorkflowService;
import com.recon.api.service.ExceptionWorkbenchService;
import com.recon.api.service.ExceptionCollaborationService;
import com.recon.api.service.KnownIssueService;
import com.recon.api.service.ExceptionNoiseSuppressionService;
import com.recon.api.service.OperationsCommandCenterService;
import com.recon.api.service.RecurrenceAnalyticsService;
import com.recon.api.service.RegionalIncidentBoardService;
import com.recon.api.service.RootCauseAnalyticsService;
import com.recon.api.service.StoreManagerLiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exceptions")
@RequiredArgsConstructor
@Slf4j
public class ExceptionController {

    private final ExceptionWorkbenchService service;
    private final ExceptionQueueService queueService;
    private final ExceptionAutomationService automationService;
    private final ExceptionEscalationService exceptionEscalationService;
    private final ExceptionWorkflowService workflowService;
    private final RootCauseAnalyticsService rootCauseAnalyticsService;
    private final RecurrenceAnalyticsService recurrenceAnalyticsService;
    private final RegionalIncidentBoardService regionalIncidentBoardService;
    private final StoreManagerLiteService storeManagerLiteService;
    private final KnownIssueService knownIssueService;
    private final ExceptionCollaborationService exceptionCollaborationService;
    private final ExceptionNoiseSuppressionService exceptionNoiseSuppressionService;
    private final OperationsCommandCenterService operationsCommandCenterService;

    @GetMapping("/cases/{transactionKey}")
    public ResponseEntity<ApiResponse<ExceptionCaseDto>> getCase(
            @PathVariable("transactionKey") String transactionKey,
            @RequestParam(name = "reconView") String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireCaseAccess(principal, reconView, false);
            return ResponseEntity.ok(ApiResponse.ok(
                    service.getCase(principal.getTenantId(), transactionKey, reconView)));
        } catch (Exception e) {
            log.error("Get exception case error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/cases/{transactionKey}")
    public ResponseEntity<ApiResponse<ExceptionCaseDto>> upsertCase(
            @PathVariable("transactionKey") String transactionKey,
            @RequestBody UpdateExceptionCaseRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireCaseAccess(principal, request != null ? request.getReconView() : null, true);
            return ResponseEntity.ok(ApiResponse.ok(
                    service.upsertCase(
                            principal.getTenantId(),
                            transactionKey,
                            request != null ? request.getReconView() : null,
                            request,
                            principal.getUsername())));
        } catch (Exception e) {
            log.error("Upsert exception case error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cases/{transactionKey}/comments")
    public ResponseEntity<ApiResponse<ExceptionCaseDto>> addComment(
            @PathVariable("transactionKey") String transactionKey,
            @RequestBody AddExceptionCommentRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireCaseAccess(principal, request != null ? request.getReconView() : null, true);
            return ResponseEntity.ok(ApiResponse.ok(
                    service.addComment(
                            principal.getTenantId(),
                            transactionKey,
                            request != null ? request.getReconView() : null,
                            request,
                            principal.getUsername())));
        } catch (Exception e) {
            log.error("Add exception comment error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cases/{transactionKey}/playbook-steps/{stepId}/execute")
    public ResponseEntity<ApiResponse<ExceptionPlaybookStepExecutionResponseDto>> executePlaybookStep(
            @PathVariable("transactionKey") String transactionKey,
            @PathVariable("stepId") java.util.UUID stepId,
            @RequestParam(name = "reconView") String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireCaseAccess(principal, reconView, false);
            requirePermission(principal, "OPS_EXECUTE_SAFE");
            return ResponseEntity.ok(ApiResponse.ok(
                    service.executePlaybookStep(
                            principal.getTenantId(),
                            transactionKey,
                            reconView,
                            stepId,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Execute exception playbook step error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cases/bulk-update")
    public ResponseEntity<ApiResponse<BulkUpdateExceptionCasesResponse>> bulkUpdateCases(
            @RequestBody BulkUpdateExceptionCasesRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePermission(principal, "EXCEPTION_EDIT");
            if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
                throw new IllegalArgumentException("At least one exception case must be selected");
            }
            request.getItems().stream()
                    .map(item -> item != null ? item.getReconView() : null)
                    .filter(reconView -> reconView != null && !reconView.isBlank())
                    .distinct()
                    .forEach(reconView -> requireCaseAccess(principal, reconView, true));

            return ResponseEntity.ok(ApiResponse.ok(
                    service.bulkUpdateCases(
                            principal.getTenantId(),
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Bulk update exception cases error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/assignment-options")
    public ResponseEntity<ApiResponse<ExceptionAssignmentOptionsDto>> getAssignmentOptions(
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            if (!(principal.hasPermission("EXCEPTION_VIEW")
                    || principal.hasPermission("EXCEPTION_AUTOMATION_VIEW")
                    || principal.hasPermission("EXCEPTION_AUTOMATION_EDIT")
                    || principal.hasPermission("EXCEPTION_APPROVAL_VIEW")
                    || principal.hasPermission("EXCEPTION_POLICY_EDIT"))) {
                throw new AccessDeniedException("Missing exception access permission");
            }
            return ResponseEntity.ok(ApiResponse.ok(
                    service.getAssignmentOptions(principal.getTenantId())));
        } catch (Exception e) {
            log.error("Get exception assignment options error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/queues")
    public ResponseEntity<ApiResponse<ExceptionQueueResponse>> getQueues(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "queueType", required = false) String queueType,
            @RequestParam(name = "caseStatus", required = false) String caseStatus,
            @RequestParam(name = "severity", required = false) String severity,
            @RequestParam(name = "assignee", required = false) String assignee,
            @RequestParam(name = "assignedRole", required = false) String assignedRole,
            @RequestParam(name = "search", required = false) String search,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireQueueAccess(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    queueService.getQueue(
                            principal.getTenantId(),
                            principal.getUsername(),
                            reconView,
                            queueType,
                            caseStatus,
                            severity,
                            assignee,
                            assignedRole,
                            search)));
        } catch (Exception e) {
            log.error("Get exception queue error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/root-cause-analytics")
    public ResponseEntity<ApiResponse<RootCauseAnalyticsResponse>> getRootCauseAnalytics(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "storeId", required = false) String storeId,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReportAccess(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    rootCauseAnalyticsService.getAnalytics(
                            principal.getTenantId(),
                            reconView,
                            allowedReconViews(principal),
                            storeId,
                            fromBusinessDate,
                            toBusinessDate
                    )));
        } catch (Exception e) {
            log.error("Get root cause analytics error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/recurrence-analytics")
    public ResponseEntity<ApiResponse<RecurrenceAnalyticsResponse>> getRecurrenceAnalytics(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "storeId", required = false) String storeId,
            @RequestParam(name = "fromBusinessDate", required = false) String fromBusinessDate,
            @RequestParam(name = "toBusinessDate", required = false) String toBusinessDate,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReportAccess(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    recurrenceAnalyticsService.getAnalytics(
                            principal.getTenantId(),
                            reconView,
                            allowedReconViews(principal),
                            storeId,
                            fromBusinessDate,
                            toBusinessDate
                    )));
        } catch (Exception e) {
            log.error("Get recurrence analytics error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/operations-command-center")
    public ResponseEntity<ApiResponse<OperationsCommandCenterResponse>> getOperationsCommandCenter(
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireReportAccess(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    operationsCommandCenterService.getCenter(
                            principal.getTenantId(),
                            principal.getUsername(),
                            allowedReconViews(principal),
                            reconView
                    )));
        } catch (Exception e) {
            log.error("Get operations command center error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/regional-incident-board")
    public ResponseEntity<ApiResponse<RegionalIncidentBoardResponse>> getRegionalIncidentBoard(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "outbreakStatus", required = false) String outbreakStatus,
            @RequestParam(name = "search", required = false) String search,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireQueueAccess(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    regionalIncidentBoardService.getBoard(
                            principal.getTenantId(),
                            principal.getUsername(),
                            allowedReconViews(principal),
                            reconView,
                            outbreakStatus,
                            search
                    )));
        } catch (Exception e) {
            log.error("Get regional incident board error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/store-manager-lite")
    public ResponseEntity<ApiResponse<StoreManagerLiteResponse>> getStoreManagerLite(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "storeId", required = false) String storeId,
            @RequestParam(name = "search", required = false) String search,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireQueueAccess(principal, reconView);
            if (storeId != null && !storeId.isBlank() && !principal.canAccessStore(storeId)) {
                throw new AccessDeniedException("Missing access to store: " + storeId);
            }
            return ResponseEntity.ok(ApiResponse.ok(
                    storeManagerLiteService.getView(
                            principal.getTenantId(),
                            principal.getUsername(),
                            principal.getStoreIds(),
                            reconView,
                            storeId,
                            search
                    )));
        } catch (Exception e) {
            log.error("Get store manager lite error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/ticketing-center")
    public ResponseEntity<ApiResponse<ExceptionTicketingCenterResponse>> getTicketingCenter(
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireQueueAccess(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionCollaborationService.getCenter(
                            principal.getTenantId(),
                            reconView
                    )));
        } catch (Exception e) {
            log.error("Get ticketing center error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/integration-channels")
    public ResponseEntity<ApiResponse<ExceptionIntegrationChannelDto>> createIntegrationChannel(
            @RequestBody SaveExceptionIntegrationChannelRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePermission(principal, "EXCEPTION_EDIT");
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionCollaborationService.saveChannel(
                            principal.getTenantId(),
                            null,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Create integration channel error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/integration-channels/{channelId}")
    public ResponseEntity<ApiResponse<ExceptionIntegrationChannelDto>> updateIntegrationChannel(
            @PathVariable("channelId") java.util.UUID channelId,
            @RequestBody SaveExceptionIntegrationChannelRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePermission(principal, "EXCEPTION_EDIT");
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionCollaborationService.saveChannel(
                            principal.getTenantId(),
                            channelId,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Update integration channel error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cases/{transactionKey}/external-tickets")
    public ResponseEntity<ApiResponse<ExceptionExternalTicketDto>> createCaseExternalTicket(
            @PathVariable("transactionKey") String transactionKey,
            @RequestBody CreateExceptionExternalTicketRequest request,
            @RequestParam(name = "reconView") String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireCaseAccess(principal, reconView, true);
            requirePermission(principal, "EXCEPTION_EDIT");
            return ResponseEntity.ok(ApiResponse.ok(
                    service.createExternalTicket(
                            principal.getTenantId(),
                            transactionKey,
                            reconView,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Create case external ticket error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/cases/{transactionKey}/communications")
    public ResponseEntity<ApiResponse<ExceptionOutboundCommunicationDto>> sendCaseCommunication(
            @PathVariable("transactionKey") String transactionKey,
            @RequestBody SendExceptionCommunicationRequest request,
            @RequestParam(name = "reconView") String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireCaseAccess(principal, reconView, true);
            requirePermission(principal, "EXCEPTION_EDIT");
            return ResponseEntity.ok(ApiResponse.ok(
                    service.sendCommunication(
                            principal.getTenantId(),
                            transactionKey,
                            reconView,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Send case communication error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/incidents/external-tickets")
    public ResponseEntity<ApiResponse<ExceptionExternalTicketDto>> createIncidentExternalTicket(
            @RequestBody CreateExceptionExternalTicketRequest request,
            @RequestParam(name = "reconView") String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireQueueAccess(principal, reconView);
            requirePermission(principal, "EXCEPTION_EDIT");
            if (request != null && request.getStoreId() != null && !request.getStoreId().isBlank() && !principal.canAccessStore(request.getStoreId())) {
                throw new AccessDeniedException("Missing access to store: " + request.getStoreId());
            }
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionCollaborationService.createIncidentExternalTicket(
                            principal.getTenantId(),
                            reconView,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Create incident external ticket error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/incidents/communications")
    public ResponseEntity<ApiResponse<ExceptionOutboundCommunicationDto>> sendIncidentCommunication(
            @RequestBody SendExceptionCommunicationRequest request,
            @RequestParam(name = "reconView") String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireQueueAccess(principal, reconView);
            requirePermission(principal, "EXCEPTION_EDIT");
            if (request != null && request.getStoreId() != null && !request.getStoreId().isBlank() && !principal.canAccessStore(request.getStoreId())) {
                throw new AccessDeniedException("Missing access to store: " + request.getStoreId());
            }
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionCollaborationService.sendIncidentCommunication(
                            principal.getTenantId(),
                            reconView,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Send incident communication error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/integration-callbacks/{channelId}")
    public ResponseEntity<ApiResponse<ExceptionExternalTicketDto>> synchronizeExternalTicket(
            @PathVariable("channelId") java.util.UUID channelId,
            @RequestBody SyncExceptionExternalTicketRequest request,
            @RequestHeader(name = "X-RetailINQ-Sync-Secret", required = false) String sharedSecret) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionCollaborationService.synchronizeExternalTicket(
                            channelId,
                            sharedSecret,
                            request
                    )));
        } catch (Exception e) {
            log.error("Synchronize external ticket error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/known-issues")
    public ResponseEntity<ApiResponse<KnownIssueCatalogResponse>> getKnownIssues(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "activeOnly", required = false) Boolean activeOnly,
            @RequestParam(name = "search", required = false) String search,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireQueueAccess(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    knownIssueService.getCatalog(
                            principal.getTenantId(),
                            reconView,
                            activeOnly,
                            search
                    )));
        } catch (Exception e) {
            log.error("Get known issues error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/known-issues")
    public ResponseEntity<ApiResponse<KnownIssueDto>> createKnownIssue(
            @RequestBody SaveKnownIssueRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePermission(principal, "EXCEPTION_EDIT");
            return ResponseEntity.ok(ApiResponse.ok(
                    knownIssueService.saveKnownIssue(
                            principal.getTenantId(),
                            null,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Create known issue error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/known-issues/{knownIssueId}")
    public ResponseEntity<ApiResponse<KnownIssueDto>> updateKnownIssue(
            @PathVariable("knownIssueId") java.util.UUID knownIssueId,
            @RequestBody SaveKnownIssueRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePermission(principal, "EXCEPTION_EDIT");
            return ResponseEntity.ok(ApiResponse.ok(
                    knownIssueService.saveKnownIssue(
                            principal.getTenantId(),
                            knownIssueId,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Update known issue error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/known-issues/{knownIssueId}/feedback")
    public ResponseEntity<ApiResponse<KnownIssueFeedbackResponse>> submitKnownIssueFeedback(
            @PathVariable("knownIssueId") java.util.UUID knownIssueId,
            @RequestBody SubmitKnownIssueFeedbackRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePermission(principal, "EXCEPTION_QUEUE_VIEW");
            return ResponseEntity.ok(ApiResponse.ok(
                    knownIssueService.submitFeedback(
                            principal.getTenantId(),
                            knownIssueId,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Submit known issue feedback error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/automation-center")
    public ResponseEntity<ApiResponse<ExceptionAutomationCenterResponse>> getAutomationCenter(
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationView(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    automationService.getAutomationCenter(principal.getTenantId(), reconView)));
        } catch (Exception e) {
            log.error("Get exception automation center error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/suppression-rules")
    public ResponseEntity<ApiResponse<ExceptionSuppressionRuleDto>> createSuppressionRule(
            @RequestBody SaveExceptionSuppressionRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionNoiseSuppressionService.saveRule(
                            principal.getTenantId(),
                            null,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Create suppression rule error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/suppression-rules/{ruleId}")
    public ResponseEntity<ApiResponse<ExceptionSuppressionRuleDto>> updateSuppressionRule(
            @PathVariable("ruleId") java.util.UUID ruleId,
            @RequestBody SaveExceptionSuppressionRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionNoiseSuppressionService.saveRule(
                            principal.getTenantId(),
                            ruleId,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Update suppression rule error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/suppression-rules/{ruleId}")
    public ResponseEntity<ApiResponse<Void>> deleteSuppressionRule(
            @PathVariable("ruleId") java.util.UUID ruleId,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationEdit(principal, reconView);
            exceptionNoiseSuppressionService.deleteRule(principal.getTenantId(), ruleId);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            log.error("Delete suppression rule error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/routing-rules")
    public ResponseEntity<ApiResponse<ExceptionRoutingRuleDto>> createRoutingRule(
            @RequestBody SaveExceptionRoutingRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    automationService.saveRoutingRule(
                            principal.getTenantId(),
                            null,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Create exception routing rule error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/routing-rules/{ruleId}")
    public ResponseEntity<ApiResponse<ExceptionRoutingRuleDto>> updateRoutingRule(
            @PathVariable("ruleId") java.util.UUID ruleId,
            @RequestBody SaveExceptionRoutingRuleRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    automationService.saveRoutingRule(
                            principal.getTenantId(),
                            ruleId,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Update exception routing rule error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/routing-rules/{ruleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRoutingRule(
            @PathVariable("ruleId") java.util.UUID ruleId,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationEdit(principal, reconView);
            automationService.deleteRoutingRule(principal.getTenantId(), ruleId);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            log.error("Delete exception routing rule error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/playbooks")
    public ResponseEntity<ApiResponse<ExceptionPlaybookDto>> createPlaybook(
            @RequestBody SaveExceptionPlaybookRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    automationService.savePlaybook(
                            principal.getTenantId(),
                            null,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Create exception playbook error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/playbooks/{playbookId}")
    public ResponseEntity<ApiResponse<ExceptionPlaybookDto>> updatePlaybook(
            @PathVariable("playbookId") java.util.UUID playbookId,
            @RequestBody SaveExceptionPlaybookRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    automationService.savePlaybook(
                            principal.getTenantId(),
                            playbookId,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Update exception playbook error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/playbooks/{playbookId}")
    public ResponseEntity<ApiResponse<Void>> deletePlaybook(
            @PathVariable("playbookId") java.util.UUID playbookId,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireAutomationEdit(principal, reconView);
            automationService.deletePlaybook(principal.getTenantId(), playbookId);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            log.error("Delete exception playbook error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/approval-center")
    public ResponseEntity<ApiResponse<ExceptionApprovalCenterResponse>> getApprovalCenter(
            @RequestParam(name = "reconView", required = false) String reconView,
            @RequestParam(name = "requestStatus", required = false) String requestStatus,
            @RequestParam(name = "search", required = false) String search,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireApprovalView(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    workflowService.getApprovalCenter(
                            principal.getTenantId(),
                            principal.getUsername(),
                            allowedReconViews(principal),
                            reconView,
                            requestStatus,
                            search
                    )));
        } catch (Exception e) {
            log.error("Get exception approval center error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/escalation-policy-center")
    public ResponseEntity<ApiResponse<ExceptionEscalationPolicyCenterResponse>> getEscalationPolicyCenter(
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requireApprovalView(principal, reconView);
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionEscalationService.getCenter(
                            principal.getTenantId(),
                            allowedReconViews(principal),
                            reconView
                    )));
        } catch (Exception e) {
            log.error("Get exception escalation policy center error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/closure-policies")
    public ResponseEntity<ApiResponse<ExceptionClosurePolicyDto>> createClosurePolicy(
            @RequestBody SaveExceptionClosurePolicyRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePolicyEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    workflowService.savePolicy(
                            principal.getTenantId(),
                            null,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Create exception closure policy error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/closure-policies/{policyId}")
    public ResponseEntity<ApiResponse<ExceptionClosurePolicyDto>> updateClosurePolicy(
            @PathVariable("policyId") java.util.UUID policyId,
            @RequestBody SaveExceptionClosurePolicyRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePolicyEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    workflowService.savePolicy(
                            principal.getTenantId(),
                            policyId,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Update exception closure policy error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/escalation-policies")
    public ResponseEntity<ApiResponse<ExceptionEscalationPolicyDto>> createEscalationPolicy(
            @RequestBody SaveExceptionEscalationPolicyRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePolicyEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionEscalationService.savePolicy(
                            principal.getTenantId(),
                            null,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Create exception escalation policy error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/escalation-policies/{policyId}")
    public ResponseEntity<ApiResponse<ExceptionEscalationPolicyDto>> updateEscalationPolicy(
            @PathVariable("policyId") java.util.UUID policyId,
            @RequestBody SaveExceptionEscalationPolicyRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePolicyEdit(principal, request != null ? request.getReconView() : null);
            return ResponseEntity.ok(ApiResponse.ok(
                    exceptionEscalationService.savePolicy(
                            principal.getTenantId(),
                            policyId,
                            request,
                            principal.getUsername()
                    )));
        } catch (Exception e) {
            log.error("Update exception escalation policy error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/approval-requests/{requestId}/decision")
    public ResponseEntity<ApiResponse<ExceptionApprovalRequestDto>> decideApproval(
            @PathVariable("requestId") java.util.UUID requestId,
            @RequestBody DecideExceptionApprovalRequest request,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePermission(principal, "EXCEPTION_APPROVAL_EDIT");
            return ResponseEntity.ok(ApiResponse.ok(
                    workflowService.decideApproval(
                            principal.getTenantId(),
                            requestId,
                            request,
                            principal.getUsername(),
                            allowedReconViews(principal)
                    )));
        } catch (Exception e) {
            log.error("Decide exception approval error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/closure-policies/{policyId}")
    public ResponseEntity<ApiResponse<Void>> deleteClosurePolicy(
            @PathVariable("policyId") java.util.UUID policyId,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePolicyEdit(principal, reconView);
            workflowService.deletePolicy(principal.getTenantId(), policyId);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            log.error("Delete exception closure policy error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/escalation-policies/{policyId}")
    public ResponseEntity<ApiResponse<Void>> deleteEscalationPolicy(
            @PathVariable("policyId") java.util.UUID policyId,
            @RequestParam(name = "reconView", required = false) String reconView,
            @AuthenticationPrincipal ReconUserPrincipal principal) {
        try {
            requirePolicyEdit(principal, reconView);
            exceptionEscalationService.deletePolicy(principal.getTenantId(), policyId);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            log.error("Delete exception escalation policy error: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void requireCaseAccess(ReconUserPrincipal principal,
                                   String reconView,
                                   boolean edit) {
        String permission = edit ? "EXCEPTION_EDIT" : "EXCEPTION_VIEW";
        if (!principal.hasPermission(permission)) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
        if (!principal.hasPermission("RECON_VIEW")) {
            throw new AccessDeniedException("Missing permission: RECON_VIEW");
        }
        if (reconView == null || reconView.isBlank()) {
            throw new AccessDeniedException("reconView is required");
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

    private void requireQueueAccess(ReconUserPrincipal principal, String reconView) {
        requirePermission(principal, "EXCEPTION_QUEUE_VIEW");
        requireReconViewPermission(principal, reconView);
    }

    private void requireReportAccess(ReconUserPrincipal principal, String reconView) {
        requirePermission(principal, "REPORTS_VIEW");
        requireReconViewPermission(principal, reconView);
    }

    private void requireApprovalView(ReconUserPrincipal principal, String reconView) {
        requirePermission(principal, "EXCEPTION_APPROVAL_VIEW");
        requireReconViewPermission(principal, reconView);
    }

    private void requireAutomationView(ReconUserPrincipal principal, String reconView) {
        requirePermission(principal, "EXCEPTION_AUTOMATION_VIEW");
        requireReconViewPermission(principal, reconView);
    }

    private void requireAutomationEdit(ReconUserPrincipal principal, String reconView) {
        requirePermission(principal, "EXCEPTION_AUTOMATION_EDIT");
        requireReconViewPermission(principal, reconView);
    }

    private void requirePolicyEdit(ReconUserPrincipal principal, String reconView) {
        requirePermission(principal, "EXCEPTION_POLICY_EDIT");
        requireReconViewPermission(principal, reconView);
    }

    private void requireReconViewPermission(ReconUserPrincipal principal, String reconView) {
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

    private java.util.List<String> allowedReconViews(ReconUserPrincipal principal) {
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
        return allowed;
    }

    private void requirePermission(ReconUserPrincipal principal, String permission) {
        if (!principal.hasPermission(permission)) {
            throw new AccessDeniedException("Missing permission: " + permission);
        }
    }
}
