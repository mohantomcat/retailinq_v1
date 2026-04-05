package com.recon.api.service;

import com.recon.api.domain.DecideExceptionApprovalRequest;
import com.recon.api.domain.ExceptionApprovalCenterResponse;
import com.recon.api.domain.ExceptionApprovalCenterSummaryDto;
import com.recon.api.domain.ExceptionApprovalRequest;
import com.recon.api.domain.ExceptionApprovalRequestDto;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionClosurePolicy;
import com.recon.api.domain.ExceptionClosurePolicyDto;
import com.recon.api.domain.ExceptionComment;
import com.recon.api.domain.Role;
import com.recon.api.domain.SaveExceptionClosurePolicyRequest;
import com.recon.api.domain.UpdateExceptionCaseRequest;
import com.recon.api.domain.User;
import com.recon.api.repository.ExceptionApprovalRequestRepository;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ExceptionClosurePolicyRepository;
import com.recon.api.repository.ExceptionCommentRepository;
import com.recon.api.repository.UserRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExceptionWorkflowService {

    private static final Set<String> DECISION_STATUSES = Set.of("APPROVED", "REJECTED");

    private final ExceptionClosurePolicyRepository policyRepository;
    private final ExceptionApprovalRequestRepository approvalRepository;
    private final ExceptionCaseRepository caseRepository;
    private final ExceptionCommentRepository commentRepository;
    private final UserRepository userRepository;
    private final TenantService tenantService;
    private final ExceptionSlaService exceptionSlaService;
    private final ExceptionScopeResolver exceptionScopeResolver;
    private final AuditLedgerService auditLedgerService;

    @Transactional(readOnly = true)
    public Optional<ExceptionClosurePolicy> findMatchingPolicy(String tenantId,
                                                               String reconView,
                                                               String targetStatus,
                                                               String severity) {
        if (isBlank(tenantId) || isBlank(reconView) || isBlank(targetStatus) || isBlank(severity)) {
            return Optional.empty();
        }

        return policyRepository.findActivePolicies(
                        tenantId,
                        reconView.toUpperCase(Locale.ROOT),
                        targetStatus.toUpperCase(Locale.ROOT))
                .stream()
                .filter(policy -> severityRank(severity) >= severityRank(policy.getMinSeverity()))
                .sorted(Comparator
                        .comparingInt((ExceptionClosurePolicy policy) -> severityRank(policy.getMinSeverity())).reversed()
                        .thenComparing(ExceptionClosurePolicy::isRequireApproval).reversed()
                        .thenComparing(ExceptionClosurePolicy::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }

    public void validateTransition(ExceptionClosurePolicy policy,
                                   String reasonCode,
                                   String rootCauseCategory,
                                   String notes,
                                   String closureComment,
                                   boolean hasExistingComments) {
        if (policy == null) {
            return;
        }
        if (policy.isRequireReasonCode() && isBlank(reasonCode)) {
            throw new IllegalArgumentException("Reason code is required by the workflow policy");
        }
        if (policy.isRequireRootCauseCategory() && isBlank(rootCauseCategory)) {
            throw new IllegalArgumentException("Root cause category is required by the workflow policy");
        }
        if (policy.isRequireNotes() && isBlank(notes)) {
            throw new IllegalArgumentException("Resolution notes are required by the workflow policy");
        }
        if (policy.isRequireComment() && !hasExistingComments && isBlank(closureComment)) {
            throw new IllegalArgumentException("A closure comment is required by the workflow policy");
        }
        if (policy.isRequireApproval() && isBlank(policy.getApproverRoleName())) {
            throw new IllegalArgumentException("Workflow policy requires approval but no approver role is configured");
        }
    }

    @Transactional
    public ExceptionApprovalRequest submitApproval(ExceptionCase exceptionCase,
                                                   ExceptionClosurePolicy policy,
                                                   UpdateExceptionCaseRequest request,
                                                   String previousCaseStatus,
                                                   String requestedStatus,
                                                   String normalizedSeverity,
                                                   String normalizedReasonCode,
                                                   String resolvedRootCauseCategory,
                                                   String normalizedNotes,
                                                   String actorUsername) {
        ExceptionApprovalRequest approvalRequest = approvalRepository
                .findTopByExceptionCaseAndRequestStatusOrderByRequestedAtDesc(exceptionCase, "PENDING")
                .orElseGet(() -> ExceptionApprovalRequest.builder()
                        .exceptionCase(exceptionCase)
                        .policy(policy)
                        .tenantId(exceptionCase.getTenantId())
                        .transactionKey(exceptionCase.getTransactionKey())
                        .reconView(exceptionCase.getReconView())
                        .build());

        String previousStatus = approvalRequest.getId() != null
                ? defaultIfBlank(approvalRequest.getPreviousCaseStatus(), "OPEN")
                : normalizeCaseStatus(previousCaseStatus);

        if ("PENDING_APPROVAL".equals(previousStatus)) {
            previousStatus = "IN_REVIEW";
        }

        approvalRequest.setPolicy(policy);
        approvalRequest.setTenantId(exceptionCase.getTenantId());
        approvalRequest.setTransactionKey(exceptionCase.getTransactionKey());
        approvalRequest.setReconView(exceptionCase.getReconView());
        approvalRequest.setPreviousCaseStatus(previousStatus);
        approvalRequest.setRequestedCaseStatus(requestedStatus);
        approvalRequest.setRequestedSeverity(normalizedSeverity);
        approvalRequest.setRequestedReasonCode(normalizedReasonCode);
        approvalRequest.setRequestedRootCauseCategory(resolvedRootCauseCategory);
        approvalRequest.setRequestedAssigneeUsername(trimToNull(request.getAssigneeUsername()));
        approvalRequest.setRequestedAssignedRoleName(trimToNull(request.getAssignedRoleName()));
        approvalRequest.setRequestedNotes(normalizedNotes);
        approvalRequest.setClosureComment(trimToNull(request.getClosureComment()));
        approvalRequest.setApproverRoleName(trimToNull(policy.getApproverRoleName()));
        approvalRequest.setRequestStatus("PENDING");
        approvalRequest.setRequestedBy(actorUsername);
        approvalRequest.setDecisionBy(null);
        approvalRequest.setDecisionNotes(null);
        approvalRequest.setDecisionAt(null);
        ExceptionApprovalRequest saved = approvalRepository.save(approvalRequest);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(exceptionCase.getTenantId())
                .sourceType("EXCEPTION")
                .moduleKey(exceptionCase.getReconView())
                .entityType("EXCEPTION_APPROVAL")
                .entityKey(saved.getId().toString())
                .actionType("APPROVAL_REQUESTED")
                .title("Exception approval requested")
                .summary(saved.getRequestedCaseStatus())
                .actor(actorUsername)
                .status(saved.getRequestStatus())
                .referenceKey(saved.getTransactionKey())
                .controlFamily("EXCEPTION_CONTROL")
                .evidenceTags(java.util.List.of("EXCEPTION", "APPROVAL"))
                .afterState(saved)
                .build());
        return saved;
    }

    @Transactional
    public void cancelPendingApproval(ExceptionCase exceptionCase, String actorUsername, String reason) {
        approvalRepository.findTopByExceptionCaseAndRequestStatusOrderByRequestedAtDesc(exceptionCase, "PENDING")
                .ifPresent(request -> {
                    request.setRequestStatus("CANCELLED");
                    request.setDecisionBy(actorUsername);
                    request.setDecisionNotes(defaultIfBlank(reason, "Pending approval cancelled"));
                    request.setDecisionAt(LocalDateTime.now());
                    approvalRepository.save(request);
                });
    }

    @Transactional(readOnly = true)
    public Optional<ExceptionApprovalRequestDto> getPendingApproval(ExceptionCase exceptionCase) {
        return approvalRepository.findTopByExceptionCaseAndRequestStatusOrderByRequestedAtDesc(exceptionCase, "PENDING")
                .map(this::toApprovalDto);
    }

    @Transactional(readOnly = true)
    public ExceptionApprovalCenterResponse getApprovalCenter(String tenantId,
                                                             String username,
                                                             List<String> allowedReconViews,
                                                             String reconView,
                                                             String requestStatus,
                                                             String search) {
        String normalizedReconView = normalizeNullable(reconView);
        Set<String> userRoles = resolveUserRoles(tenantId, username);
        Predicate<ExceptionApprovalRequest> laneFilter = request ->
                allowedReconViews == null || allowedReconViews.isEmpty() || allowedReconViews.contains(request.getReconView());
        Predicate<ExceptionApprovalRequest> statusFilter = buildStatusFilter(requestStatus);
        Predicate<ExceptionApprovalRequest> searchFilter = buildSearchFilter(search);

        List<ExceptionApprovalRequest> recentRequests = approvalRepository.findRecentRequests(
                tenantId,
                normalizedReconView,
                LocalDateTime.now().minusDays(35)
        ).stream()
                .filter(laneFilter)
                .toList();

        Predicate<ExceptionApprovalRequest> reviewableFilter = request -> canReview(request, userRoles);

        List<ExceptionApprovalRequest> pendingApprovals = recentRequests.stream()
                .filter(request -> "PENDING".equalsIgnoreCase(request.getRequestStatus()))
                .filter(reviewableFilter)
                .filter(statusFilter)
                .filter(searchFilter)
                .sorted(Comparator
                        .comparing((ExceptionApprovalRequest request) -> isOverdue(request) ? 0 : 1)
                        .thenComparing(ExceptionApprovalRequest::getRequestedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(100)
                .toList();

        List<ExceptionApprovalRequest> recentDecisions = recentRequests.stream()
                .filter(request -> DECISION_STATUSES.contains(normalizeNullable(request.getRequestStatus())))
                .filter(request -> canReview(request, userRoles) || equalsIgnoreCase(request.getRequestedBy(), username))
                .filter(searchFilter)
                .sorted(Comparator.comparing(ExceptionApprovalRequest::getDecisionAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50)
                .toList();

        long approvedLast7Days = recentRequests.stream()
                .filter(request -> "APPROVED".equalsIgnoreCase(request.getRequestStatus()))
                .filter(request -> request.getDecisionAt() != null && request.getDecisionAt().isAfter(LocalDateTime.now().minusDays(7)))
                .count();
        long rejectedLast7Days = recentRequests.stream()
                .filter(request -> "REJECTED".equalsIgnoreCase(request.getRequestStatus()))
                .filter(request -> request.getDecisionAt() != null && request.getDecisionAt().isAfter(LocalDateTime.now().minusDays(7)))
                .count();

        List<ExceptionClosurePolicyDto> policies = policyRepository.findForApprovalCenter(tenantId, normalizedReconView).stream()
                .filter(policy -> allowedReconViews == null || allowedReconViews.isEmpty() || allowedReconViews.contains(policy.getReconView()))
                .map(this::toPolicyDto)
                .toList();

        return ExceptionApprovalCenterResponse.builder()
                .summary(ExceptionApprovalCenterSummaryDto.builder()
                        .pendingApprovals(pendingApprovals.size())
                        .overduePendingApprovals(pendingApprovals.stream().filter(this::isOverdue).count())
                        .approvedLast7Days(approvedLast7Days)
                        .rejectedLast7Days(rejectedLast7Days)
                        .activePolicies(policies.stream().filter(ExceptionClosurePolicyDto::isActive).count())
                        .build())
                .pendingApprovals(pendingApprovals.stream().map(this::toApprovalDto).toList())
                .recentDecisions(recentDecisions.stream().map(this::toApprovalDto).toList())
                .closurePolicies(policies)
                .build();
    }

    @Transactional
    public ExceptionClosurePolicyDto savePolicy(String tenantId,
                                                UUID policyId,
                                                SaveExceptionClosurePolicyRequest request,
                                                String actorUsername,
                                                Collection<String> allowedReconViews) {
        String reconView = normalizeRequired(request.getReconView(), "reconView");
        requireAllowedReconView(reconView, allowedReconViews);
        String targetStatus = normalizeRequired(request.getTargetStatus(), "targetStatus");
        String minSeverity = normalizeRequired(request.getMinSeverity(), "minSeverity");
        String policyName = trimToNull(request.getPolicyName());

        if (policyName == null) {
            throw new IllegalArgumentException("policyName is required");
        }
        if (request.isRequireApproval() && isBlank(request.getApproverRoleName())) {
            throw new IllegalArgumentException("Approver role is required when approval is enabled");
        }

        ExceptionClosurePolicy policy = policyId == null
                ? ExceptionClosurePolicy.builder()
                .tenantId(tenantId)
                .createdBy(actorUsername)
                .build()
                : policyRepository.findByIdAndTenantId(policyId, tenantId)
                .map(existing -> {
                    requireAllowedReconView(existing.getReconView(), allowedReconViews);
                    return existing;
                })
                .orElseThrow(() -> new IllegalArgumentException("Closure policy not found"));

        policy.setPolicyName(policyName);
        policy.setReconView(reconView);
        policy.setTargetStatus(targetStatus);
        policy.setMinSeverity(minSeverity);
        policy.setRequireReasonCode(request.isRequireReasonCode());
        policy.setRequireRootCauseCategory(request.isRequireRootCauseCategory());
        policy.setRequireNotes(request.isRequireNotes());
        policy.setRequireComment(request.isRequireComment());
        policy.setRequireApproval(request.isRequireApproval());
        policy.setApproverRoleName(trimToNull(request.getApproverRoleName()));
        policy.setActive(request.isActive());
        policy.setDescription(trimToNull(request.getDescription()));
        policy.setUpdatedBy(actorUsername);

        ExceptionClosurePolicy saved = policyRepository.save(policy);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("COMPLIANCE")
                .moduleKey(saved.getReconView())
                .entityType("EXCEPTION_CLOSURE_POLICY")
                .entityKey(saved.getId().toString())
                .actionType(policyId == null ? "POLICY_CREATED" : "POLICY_UPDATED")
                .title("Exception closure policy saved")
                .summary(saved.getPolicyName())
                .actor(actorUsername)
                .status(saved.isActive() ? "ACTIVE" : "INACTIVE")
                .referenceKey(saved.getId().toString())
                .controlFamily("SOX")
                .evidenceTags(java.util.List.of("EXCEPTION", "POLICY"))
                .afterState(saved)
                .build());
        return toPolicyDto(saved);
    }

    @Transactional
    public ExceptionClosurePolicyDto savePolicy(String tenantId,
                                                UUID policyId,
                                                SaveExceptionClosurePolicyRequest request,
                                                String actorUsername) {
        return savePolicy(tenantId, policyId, request, actorUsername, null);
    }

    @Transactional
    public void deletePolicy(String tenantId, UUID policyId, Collection<String> allowedReconViews) {
        ExceptionClosurePolicy policy = policyRepository.findByIdAndTenantId(policyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Closure policy not found"));
        requireAllowedReconView(policy.getReconView(), allowedReconViews);
        policyRepository.delete(policy);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("COMPLIANCE")
                .moduleKey(policy.getReconView())
                .entityType("EXCEPTION_CLOSURE_POLICY")
                .entityKey(policy.getId().toString())
                .actionType("POLICY_DELETED")
                .title("Exception closure policy deleted")
                .summary(policy.getPolicyName())
                .status("DELETED")
                .referenceKey(policy.getId().toString())
                .controlFamily("SOX")
                .evidenceTags(java.util.List.of("EXCEPTION", "POLICY"))
                .beforeState(policy)
                .build());
    }

    @Transactional
    public void deletePolicy(String tenantId, UUID policyId) {
        deletePolicy(tenantId, policyId, null);
    }

    @Transactional
    public ExceptionApprovalRequestDto decideApproval(String tenantId,
                                                      UUID requestId,
                                                      DecideExceptionApprovalRequest request,
                                                      String actorUsername,
                                                      List<String> allowedReconViews) {
        String decision = normalizeRequired(request.getDecision(), "decision");
        if (!DECISION_STATUSES.contains(decision)) {
            throw new IllegalArgumentException("decision must be APPROVED or REJECTED");
        }

        ExceptionApprovalRequest approvalRequest = approvalRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));
        if (allowedReconViews != null
                && !allowedReconViews.isEmpty()
                && !allowedReconViews.contains(approvalRequest.getReconView())) {
            throw new AccessDeniedException("You are not authorized for this reconciliation module");
        }
        if (!"PENDING".equalsIgnoreCase(approvalRequest.getRequestStatus())) {
            throw new IllegalArgumentException("Approval request is no longer pending");
        }

        Set<String> userRoles = resolveUserRoles(tenantId, actorUsername);
        if (!canReview(approvalRequest, userRoles)) {
            throw new AccessDeniedException("You are not authorized to review this approval request");
        }

        ExceptionCase exceptionCase = approvalRequest.getExceptionCase();
        if ("APPROVED".equals(decision)) {
            exceptionCase.setCaseStatus(approvalRequest.getRequestedCaseStatus());
            exceptionCase.setSeverity(defaultIfBlank(approvalRequest.getRequestedSeverity(), exceptionCase.getSeverity()));
            exceptionCase.setReasonCode(trimToNull(approvalRequest.getRequestedReasonCode()));
            exceptionCase.setRootCauseCategory(trimToNull(approvalRequest.getRequestedRootCauseCategory()));
            exceptionCase.setAssigneeUsername(trimToNull(approvalRequest.getRequestedAssigneeUsername()));
            exceptionCase.setAssignedRoleName(trimToNull(approvalRequest.getRequestedAssignedRoleName()));
            exceptionCase.setNotes(trimToNull(approvalRequest.getRequestedNotes()));
            if (isTerminalStatus(approvalRequest.getRequestedCaseStatus())) {
                exceptionCase.setEscalationState("NONE");
            }
            exceptionCase.setUpdatedBy(actorUsername);
            exceptionSlaService.applyRule(exceptionCase);
            caseRepository.save(exceptionCase);

            saveComment(exceptionCase, approvalRequest.getRequestedBy(), approvalRequest.getClosureComment());
            saveComment(exceptionCase, actorUsername, formatDecisionComment("APPROVED", request.getDecisionNotes()));
        } else {
            exceptionCase.setCaseStatus(defaultIfBlank(approvalRequest.getPreviousCaseStatus(), "IN_REVIEW"));
            exceptionCase.setUpdatedBy(actorUsername);
            exceptionSlaService.applyRule(exceptionCase);
            caseRepository.save(exceptionCase);

            saveComment(exceptionCase, actorUsername, formatDecisionComment("REJECTED", request.getDecisionNotes()));
        }

        approvalRequest.setRequestStatus(decision);
        approvalRequest.setDecisionBy(actorUsername);
        approvalRequest.setDecisionNotes(trimToNull(request.getDecisionNotes()));
        approvalRequest.setDecisionAt(LocalDateTime.now());

        ExceptionApprovalRequest saved = approvalRepository.save(approvalRequest);
        auditLedgerService.record(com.recon.api.domain.AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("EXCEPTION")
                .moduleKey(approvalRequest.getReconView())
                .entityType("EXCEPTION_APPROVAL")
                .entityKey(saved.getId().toString())
                .actionType(decision)
                .title("Exception approval " + decision.toLowerCase(Locale.ROOT))
                .summary(saved.getRequestedCaseStatus())
                .actor(actorUsername)
                .reason(trimToNull(request.getDecisionNotes()))
                .status(saved.getRequestStatus())
                .referenceKey(saved.getTransactionKey())
                .controlFamily("EXCEPTION_CONTROL")
                .evidenceTags(java.util.List.of("EXCEPTION", "APPROVAL"))
                .afterState(saved)
                .build());
        return toApprovalDto(saved);
    }

    private String formatDecisionComment(String decision, String decisionNotes) {
        String trimmed = trimToNull(decisionNotes);
        return trimmed == null
                ? "Approval " + decision.toLowerCase(Locale.ROOT)
                : "Approval " + decision.toLowerCase(Locale.ROOT) + ": " + trimmed;
    }

    private void saveComment(ExceptionCase exceptionCase, String createdBy, String commentText) {
        String trimmed = trimToNull(commentText);
        if (trimmed == null) {
            return;
        }
        commentRepository.save(ExceptionComment.builder()
                .exceptionCase(exceptionCase)
                .commentText(trimmed)
                .createdBy(createdBy)
                .build());
    }

    private boolean canReview(ExceptionApprovalRequest request, Set<String> userRoles) {
        if (userRoles.stream().anyMatch(role -> equalsIgnoreCase(role, "admin"))) {
            return true;
        }
        String approverRole = trimToNull(request.getApproverRoleName());
        if (approverRole == null) {
            return true;
        }
        return userRoles.stream().anyMatch(role -> equalsIgnoreCase(role, approverRole));
    }

    private Predicate<ExceptionApprovalRequest> buildStatusFilter(String requestStatus) {
        String normalized = normalizeNullable(requestStatus);
        if (normalized == null || "ALL".equals(normalized)) {
            return request -> true;
        }
        return request -> equalsIgnoreCase(request.getRequestStatus(), normalized);
    }

    private Predicate<ExceptionApprovalRequest> buildSearchFilter(String search) {
        String normalized = trimToNull(search);
        if (normalized == null) {
            return request -> true;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        return request -> java.util.stream.Stream.of(
                        request.getTransactionKey(),
                        request.getPolicy() != null ? request.getPolicy().getPolicyName() : null,
                        request.getRequestedBy(),
                        request.getApproverRoleName(),
                        exceptionScopeResolver.resolveStoreId(request.getExceptionCase()),
                        exceptionScopeResolver.resolveWkstnId(request.getExceptionCase()))
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(lowered));
    }

    private boolean isOverdue(ExceptionApprovalRequest request) {
        return "BREACHED".equalsIgnoreCase(exceptionSlaService.evaluateSlaStatus(request.getExceptionCase()));
    }

    private boolean isTerminalStatus(String caseStatus) {
        String normalized = normalizeNullable(caseStatus);
        return "RESOLVED".equals(normalized) || "IGNORED".equals(normalized);
    }

    private ExceptionApprovalRequestDto toApprovalDto(ExceptionApprovalRequest request) {
        String tenantId = request.getTenantId();
        ExceptionCase exceptionCase = request.getExceptionCase();
        return ExceptionApprovalRequestDto.builder()
                .id(request.getId())
                .caseId(exceptionCase.getId())
                .policyId(request.getPolicy() != null ? request.getPolicy().getId() : null)
                .transactionKey(request.getTransactionKey())
                .reconView(request.getReconView())
                .previousCaseStatus(request.getPreviousCaseStatus())
                .requestedCaseStatus(request.getRequestedCaseStatus())
                .requestedSeverity(request.getRequestedSeverity())
                .requestedReasonCode(request.getRequestedReasonCode())
                .requestedRootCauseCategory(request.getRequestedRootCauseCategory())
                .requestedAssigneeUsername(request.getRequestedAssigneeUsername())
                .requestedAssignedRoleName(request.getRequestedAssignedRoleName())
                .requestedNotes(request.getRequestedNotes())
                .closureComment(request.getClosureComment())
                .policyName(request.getPolicy() != null ? request.getPolicy().getPolicyName() : null)
                .approverRoleName(request.getApproverRoleName())
                .requestStatus(request.getRequestStatus())
                .requestedBy(request.getRequestedBy())
                .decisionBy(request.getDecisionBy())
                .decisionNotes(request.getDecisionNotes())
                .requestedAt(TimezoneConverter.toDisplay(valueOrNull(request.getRequestedAt()), tenantService.getTenant(tenantId)))
                .decisionAt(TimezoneConverter.toDisplay(valueOrNull(request.getDecisionAt()), tenantService.getTenant(tenantId)))
                .currentCaseStatus(exceptionCase.getCaseStatus())
                .storeId(exceptionScopeResolver.resolveStoreId(exceptionCase))
                .wkstnId(exceptionScopeResolver.resolveWkstnId(exceptionCase))
                .businessDate(exceptionScopeResolver.resolveBusinessDate(exceptionCase) != null
                        ? exceptionScopeResolver.resolveBusinessDate(exceptionCase).toString()
                        : null)
                .slaStatus(exceptionSlaService.evaluateSlaStatus(exceptionCase))
                .dueAt(TimezoneConverter.toDisplay(valueOrNull(exceptionCase.getDueAt()), tenantService.getTenant(tenantId)))
                .build();
    }

    private ExceptionClosurePolicyDto toPolicyDto(ExceptionClosurePolicy policy) {
        return ExceptionClosurePolicyDto.builder()
                .id(policy.getId())
                .policyName(policy.getPolicyName())
                .reconView(policy.getReconView())
                .targetStatus(policy.getTargetStatus())
                .minSeverity(policy.getMinSeverity())
                .requireReasonCode(policy.isRequireReasonCode())
                .requireRootCauseCategory(policy.isRequireRootCauseCategory())
                .requireNotes(policy.isRequireNotes())
                .requireComment(policy.isRequireComment())
                .requireApproval(policy.isRequireApproval())
                .approverRoleName(policy.getApproverRoleName())
                .active(policy.isActive())
                .description(policy.getDescription())
                .createdBy(policy.getCreatedBy())
                .updatedBy(policy.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(valueOrNull(policy.getCreatedAt()), tenantService.getTenant(policy.getTenantId())))
                .updatedAt(TimezoneConverter.toDisplay(valueOrNull(policy.getUpdatedAt()), tenantService.getTenant(policy.getTenantId())))
                .build();
    }

    private Set<String> resolveUserRoles(String tenantId, String username) {
        return userRepository.findByUsernameAndTenantId(username, tenantId)
                .map(User::getRoles)
                .orElse(Set.of())
                .stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    private int severityRank(String severity) {
        return switch (normalizeNullable(severity)) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }

    private String normalizeCaseStatus(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "OPEN" : normalized;
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private void requireAllowedReconView(String reconView, Collection<String> allowedReconViews) {
        if (allowedReconViews == null) {
            return;
        }
        String normalizedReconView = normalizeRequired(reconView, "reconView");
        Set<String> normalizedAllowedViews = allowedReconViews.stream()
                .map(this::normalizeNullable)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!normalizedAllowedViews.contains(normalizedReconView)) {
            throw new AccessDeniedException("You are not authorized for this reconciliation module");
        }
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return Objects.toString(left, "").equalsIgnoreCase(Objects.toString(right, ""));
    }

    private String valueOrNull(Object value) {
        return value != null ? value.toString() : null;
    }
}
