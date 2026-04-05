package com.recon.api.service;

import com.recon.api.domain.AddExceptionCommentRequest;
import com.recon.api.domain.BulkExceptionCaseFailureDto;
import com.recon.api.domain.BulkExceptionCaseRefRequest;
import com.recon.api.domain.BulkUpdateExceptionCasesRequest;
import com.recon.api.domain.BulkUpdateExceptionCasesResponse;
import com.recon.api.domain.ExceptionApprovalRequest;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionCaseDto;
import com.recon.api.domain.ExceptionCaseTimelineEventDto;
import com.recon.api.domain.ExceptionComment;
import com.recon.api.domain.ExceptionCommentDto;
import com.recon.api.domain.ExceptionAssignmentOptionsDto;
import com.recon.api.domain.ExceptionPlaybook;
import com.recon.api.domain.ExceptionPlaybookStep;
import com.recon.api.domain.ExceptionPlaybookStepExecutionResponseDto;
import com.recon.api.domain.TenantConfig;
import com.recon.api.domain.UpdateExceptionCaseRequest;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ExceptionApprovalRequestRepository;
import com.recon.api.repository.ExceptionCommentRepository;
import com.recon.api.repository.ExceptionPlaybookRepository;
import com.recon.api.repository.OperationsActionAuditRepository;
import com.recon.api.repository.RoleRepository;
import com.recon.api.repository.UserRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExceptionWorkbenchService {

    private static final String CLEAR_FIELD_TOKEN = "__CLEAR__";

    private final ExceptionCaseRepository caseRepository;
    private final ExceptionCommentRepository commentRepository;
    private final ExceptionApprovalRequestRepository approvalRepository;
    private final ExceptionPlaybookRepository playbookRepository;
    private final OperationsActionAuditRepository operationsActionAuditRepository;
    private final TenantService tenantService;
    private final ExceptionSlaService exceptionSlaService;
    private final RootCauseTaxonomyService rootCauseTaxonomyService;
    private final ExceptionScopeResolver exceptionScopeResolver;
    private final ExceptionAutomationService exceptionAutomationService;
    private final ExceptionWorkflowService exceptionWorkflowService;
    private final OperationsService operationsService;
    private final ExceptionBusinessValueService exceptionBusinessValueService;
    private final ExceptionOperationalOwnershipService exceptionOperationalOwnershipService;
    private final KnownIssueService knownIssueService;
    private final ExceptionCollaborationService exceptionCollaborationService;
    private final ExceptionNoiseSuppressionService exceptionNoiseSuppressionService;
    private final ExceptionEscalationService exceptionEscalationService;
    private final ExceptionCaseAuditService exceptionCaseAuditService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public ExceptionCaseDto getCase(String tenantId, String transactionKey, String reconView) {
        return caseRepository.findByTenantIdAndTransactionKeyAndReconView(
                        tenantId,
                        transactionKey,
                        normalize(reconView))
                .map(exceptionCase -> toDto(exceptionCase, null))
                .orElse(null);
    }

    @Transactional
    public ExceptionCaseDto upsertCase(String tenantId,
                                       String transactionKey,
                                       String reconView,
                                       UpdateExceptionCaseRequest request,
                                       String actorUsername) {
        UpdateExceptionCaseRequest safeRequest = request != null ? request : new UpdateExceptionCaseRequest();
        boolean manualAssignmentRequested = trimToNull(safeRequest.getAssigneeUsername()) != null
                || trimToNull(safeRequest.getAssignedRoleName()) != null;
        return upsertCaseInternal(
                tenantId,
                transactionKey,
                reconView,
                safeRequest,
                actorUsername,
                manualAssignmentRequested
        );
    }

    private ExceptionCaseDto upsertCaseInternal(String tenantId,
                                                String transactionKey,
                                                String reconView,
                                                UpdateExceptionCaseRequest safeRequest,
                                                String actorUsername,
                                                boolean manualAssignmentRequested) {
        String normalizedReconView = normalize(
                safeRequest.getReconView() != null
                        ? safeRequest.getReconView()
                        : reconView);
        String normalizedStatus = normalizeStatus(safeRequest.getCaseStatus());
        var existingCase = caseRepository.findByTenantIdAndTransactionKeyAndReconView(
                tenantId, transactionKey, normalizedReconView);
        boolean newCase = existingCase.isEmpty();
        ExceptionCase exceptionCase = existingCase
                .orElseGet(() -> ExceptionCase.builder()
                        .tenantId(tenantId)
                        .transactionKey(transactionKey)
                        .reconView(normalizedReconView)
                        .createdBy(actorUsername)
                        .updatedBy(actorUsername)
                        .caseStatus("OPEN")
                        .build());
        TenantConfig tenant = tenantService.getTenant(tenantId);
        String previousSeverity = exceptionCase.getSeverity();
        String previousCaseStatus = exceptionCase.getCaseStatus();
        var previousSensitiveSnapshot = exceptionCaseAuditService.captureSensitiveSnapshot(exceptionCase);
        String reopenReason = trimToNull(safeRequest.getReopenReason());
        boolean captureAuditSnapshot = Boolean.TRUE.equals(safeRequest.getCaptureAuditSnapshot());

        OwnershipSnapshot previousOwnership = OwnershipSnapshot.from(exceptionCase);
        String normalizedReasonCode = rootCauseTaxonomyService.normalizeReasonCode(
                safeRequest.getReasonCode());
        exceptionCase.setReasonCode(normalizedReasonCode);
        exceptionCase.setReconStatus(normalizeNullable(safeRequest.getReconStatus() != null
                ? safeRequest.getReconStatus()
                : exceptionCase.getReconStatus()));
        exceptionCase.setRootCauseCategory(resolveRootCauseCategory(
                safeRequest.getRootCauseCategory(),
                normalizedReasonCode,
                exceptionCase.getRootCauseCategory()));
        String normalizedSeverity = normalizeSeverity(
                safeRequest.getSeverity() != null ? safeRequest.getSeverity() : exceptionCase.getSeverity());
        exceptionCase.setSeverity(normalizedSeverity);
        exceptionCase.setAssigneeUsername(trimToNull(
                safeRequest.getAssigneeUsername()));
        exceptionCase.setAssignedRoleName(trimToNull(
                safeRequest.getAssignedRoleName()));
        exceptionCase.setNextAction(resolveRequestedText(
                safeRequest.getNextAction(),
                exceptionCase.getNextAction()));
        exceptionCase.setNextActionDueAt(parseDateTimeField(
                safeRequest.getNextActionDueAt(),
                exceptionCase.getNextActionDueAt(),
                tenant));
        exceptionCase.setHandoffNote(resolveRequestedText(
                safeRequest.getHandoffNote(),
                exceptionCase.getHandoffNote()));
        exceptionCase.setStoreId(trimToNull(
                safeRequest.getStoreId() != null ? safeRequest.getStoreId() : exceptionCase.getStoreId()));
        exceptionCase.setWkstnId(trimToNull(
                safeRequest.getWkstnId() != null ? safeRequest.getWkstnId() : exceptionCase.getWkstnId()));
        exceptionCase.setBusinessDate(parseBusinessDate(
                safeRequest.getBusinessDate(),
                exceptionCase.getBusinessDate()));
        exceptionCase.setNotes(trimToNull(
                safeRequest.getNotes()));
        exceptionCase.setUpdatedBy(actorUsername);
        boolean handoffChanged = ownershipChanged(previousOwnership, exceptionCase);
        if (handoffChanged) {
            exceptionCase.setLastHandoffBy(actorUsername);
            exceptionCase.setLastHandoffAt(LocalDateTime.now());
        }

        var automation = exceptionAutomationService.applyAutomation(exceptionCase, manualAssignmentRequested);

        boolean hasExistingComments = commentRepository.existsByExceptionCase(exceptionCase);
        String closureComment = trimToNull(safeRequest.getClosureComment());
        String requestedStatus = normalizedStatus;
        var businessValueForSuppression = exceptionBusinessValueService.enrichCases(
                tenantId,
                List.of(exceptionCase)
        ).get(exceptionBusinessValueService.caseKey(exceptionCase));
        var suppressionDecision = exceptionNoiseSuppressionService.evaluateSuppression(
                exceptionCase,
                businessValueForSuppression
        ).orElse(null);
        String suppressionWorkflowMessage = null;
        if (suppressionDecision != null) {
            normalizedStatus = suppressionDecision.targetStatus();
            if (closureComment == null) {
                closureComment = suppressionDecision.resultMessage();
            }
        }

        var matchedPolicy = exceptionWorkflowService.findMatchingPolicy(
                tenantId,
                normalizedReconView,
                normalizedStatus,
                normalizedSeverity
        ).orElse(null);

        if (suppressionDecision != null && matchedPolicy != null && matchedPolicy.isRequireApproval()) {
            normalizedStatus = requestedStatus;
            matchedPolicy = exceptionWorkflowService.findMatchingPolicy(
                    tenantId,
                    normalizedReconView,
                    normalizedStatus,
                    normalizedSeverity
            ).orElse(null);
            if (Objects.equals(closureComment, suppressionDecision.resultMessage())) {
                closureComment = trimToNull(safeRequest.getClosureComment());
            }
            suppressionWorkflowMessage = "Suppression rule matched, but closure approval policy prevented automatic closure";
            suppressionDecision = null;
        }

        exceptionWorkflowService.validateTransition(
                matchedPolicy,
                normalizedReasonCode,
                exceptionCase.getRootCauseCategory(),
                exceptionCase.getNotes(),
                closureComment,
                hasExistingComments
        );

        String workflowMessage = null;
        if (automation.routingRule() != null && exceptionCase.isAutoAssigned()) {
            workflowMessage = "Auto-routed via " + automation.routingRule().getRuleName();
        }
        if (automation.playbook() != null) {
            workflowMessage = workflowMessage == null
                    ? "Playbook suggested: " + automation.playbook().getPlaybookName()
                    : workflowMessage + " | Playbook: " + automation.playbook().getPlaybookName();
        }
        if (suppressionDecision != null) {
            workflowMessage = workflowMessage == null
                    ? suppressionDecision.resultMessage()
                    : workflowMessage + " | " + suppressionDecision.resultMessage();
        } else if (suppressionWorkflowMessage != null) {
            workflowMessage = workflowMessage == null
                    ? suppressionWorkflowMessage
                    : workflowMessage + " | " + suppressionWorkflowMessage;
        }
        if (matchedPolicy != null && matchedPolicy.isRequireApproval()) {
            exceptionCase.setCaseStatus("PENDING_APPROVAL");
            exceptionSlaService.applyRule(exceptionCase);
            ExceptionCase savedCase = caseRepository.save(exceptionCase);
            exceptionCaseAuditService.recordCaseChange(
                    savedCase,
                    actorUsername,
                    newCase ? "CASE_CREATED" : "CASE_UPDATED",
                    newCase ? "Case created" : "Case updated",
                    buildCaseTimelineSummary(savedCase),
                    previousSensitiveSnapshot,
                    exceptionCaseAuditService.captureSensitiveSnapshot(savedCase)
            );
            exceptionWorkflowService.submitApproval(
                    savedCase,
                    matchedPolicy,
                    safeRequest,
                    previousCaseStatus,
                    normalizedStatus,
                    normalizedSeverity,
                    normalizedReasonCode,
                    exceptionCase.getRootCauseCategory(),
                    exceptionCase.getNotes(),
                    actorUsername
            );
            workflowMessage = workflowMessage == null
                    ? "Approval submitted to " + matchedPolicy.getApproverRoleName()
                    : workflowMessage + " | Approval submitted to " + matchedPolicy.getApproverRoleName();
            saveHandoffAudit(savedCase, actorUsername, handoffChanged);
            exceptionCollaborationService.syncCaseTicketLifecycle(
                    savedCase,
                    newCase,
                    previousSeverity,
                    previousCaseStatus,
                    actorUsername
            );
            return toDto(savedCase, workflowMessage);
        }

        exceptionWorkflowService.cancelPendingApproval(
                exceptionCase,
                actorUsername,
                "Pending approval cancelled by direct case update"
        );

        boolean reopening = isReopenTransition(previousCaseStatus, normalizedStatus);
        if (reopening && reopenReason == null) {
            throw new IllegalArgumentException("reopenReason is required when reopening a resolved or ignored case");
        }
        exceptionCase.setCaseStatus(normalizedStatus);
        if (reopening) {
            exceptionCase.setReopenReason(reopenReason);
            exceptionCase.setReopenCount(Optional.ofNullable(exceptionCase.getReopenCount()).orElse(0) + 1);
            exceptionCase.setLastReopenedBy(actorUsername);
            exceptionCase.setLastReopenedAt(LocalDateTime.now());
            exceptionCase.setEscalationState("NONE");
        } else if (isTerminalStatus(normalizedStatus)) {
            exceptionEscalationService.clearEscalationOnClosure(exceptionCase);
        }
        exceptionSlaService.applyRule(exceptionCase);
        ExceptionCase savedCase = caseRepository.save(exceptionCase);

        if (closureComment != null) {
            commentRepository.save(ExceptionComment.builder()
                    .exceptionCase(savedCase)
                    .commentText(closureComment)
                    .createdBy(actorUsername)
                    .build());
            exceptionCaseAuditService.recordComment(savedCase, actorUsername, closureComment);
        }
        if (suppressionDecision != null) {
            exceptionNoiseSuppressionService.recordApplied(savedCase, suppressionDecision, actorUsername);
        }
        saveHandoffAudit(savedCase, actorUsername, handoffChanged);
        exceptionCollaborationService.syncCaseTicketLifecycle(
                savedCase,
                newCase,
                previousSeverity,
                previousCaseStatus,
                actorUsername
        );
        ExceptionCase finalCase = exceptionEscalationService.evaluateCase(savedCase, actorUsername);
        exceptionCaseAuditService.recordCaseChange(
                finalCase,
                actorUsername,
                newCase ? "CASE_CREATED" : "CASE_UPDATED",
                newCase ? "Case created" : "Case updated",
                buildCaseTimelineSummary(finalCase),
                previousSensitiveSnapshot,
                exceptionCaseAuditService.captureSensitiveSnapshot(finalCase)
        );
        if (reopening) {
            exceptionCaseAuditService.recordReopen(
                    finalCase,
                    actorUsername,
                    previousCaseStatus,
                    finalCase.getCaseStatus(),
                    reopenReason
            );
        }
        if (captureAuditSnapshot) {
            exceptionCaseAuditService.recordSensitiveFieldSnapshot(
                    finalCase,
                    actorUsername,
                    previousSensitiveSnapshot,
                    exceptionCaseAuditService.captureSensitiveSnapshot(finalCase)
            );
        }

        return toDto(finalCase, workflowMessage);
    }

    @Transactional(readOnly = true)
    public ExceptionAssignmentOptionsDto getAssignmentOptions(String tenantId) {
        return ExceptionAssignmentOptionsDto.builder()
                .usernames(userRepository.findByTenantId(tenantId).stream()
                        .filter(user -> user.isActive())
                        .map(user -> user.getUsername())
                        .sorted(String::compareToIgnoreCase)
                        .toList())
                .roleNames(roleRepository.findByTenantId(tenantId).stream()
                        .filter(role -> role.isActive())
                        .map(role -> role.getName())
                        .sorted(String::compareToIgnoreCase)
                        .toList())
                .build();
    }

    @Transactional
    public ExceptionCaseDto addComment(String tenantId,
                                       String transactionKey,
                                       String reconView,
                                       AddExceptionCommentRequest request,
                                       String actorUsername) {
        String normalizedReconView = normalize(
                request != null && request.getReconView() != null
                        ? request.getReconView()
                        : reconView);
        String commentText = trimToNull(request != null ? request.getCommentText() : null);
        if (commentText == null) {
            throw new IllegalArgumentException("Comment text is required");
        }

        ExceptionCase exceptionCase = caseRepository
                .findByTenantIdAndTransactionKeyAndReconView(
                        tenantId, transactionKey, normalizedReconView)
                .orElseGet(() -> caseRepository.save(ExceptionCase.builder()
                        .tenantId(tenantId)
                        .transactionKey(transactionKey)
                        .reconView(normalizedReconView)
                        .createdBy(actorUsername)
                        .updatedBy(actorUsername)
                        .caseStatus("OPEN")
                        .build()));

        commentRepository.save(ExceptionComment.builder()
                .exceptionCase(exceptionCase)
                .commentText(commentText)
                .createdBy(actorUsername)
                .build());
        exceptionCaseAuditService.recordComment(exceptionCase, actorUsername, commentText);

        exceptionCase.setUpdatedBy(actorUsername);
        exceptionSlaService.applyRule(exceptionCase);
        caseRepository.save(exceptionCase);

        return toDto(exceptionCase, null);
    }

    @Transactional
    public com.recon.api.domain.ExceptionExternalTicketDto createExternalTicket(String tenantId,
                                                                                String transactionKey,
                                                                                String reconView,
                                                                                com.recon.api.domain.CreateExceptionExternalTicketRequest request,
                                                                                String actorUsername) {
        ExceptionCase exceptionCase = caseRepository.findByTenantIdAndTransactionKeyAndReconView(
                        tenantId,
                        transactionKey,
                        normalize(reconView))
                .orElseThrow(() -> new IllegalArgumentException("Exception case not found"));
        return exceptionCollaborationService.createCaseExternalTicket(
                tenantId,
                exceptionCase,
                request,
                actorUsername
        );
    }

    @Transactional
    public com.recon.api.domain.ExceptionOutboundCommunicationDto sendCommunication(String tenantId,
                                                                                    String transactionKey,
                                                                                    String reconView,
                                                                                    com.recon.api.domain.SendExceptionCommunicationRequest request,
                                                                                    String actorUsername) {
        ExceptionCase exceptionCase = caseRepository.findByTenantIdAndTransactionKeyAndReconView(
                        tenantId,
                        transactionKey,
                        normalize(reconView))
                .orElseThrow(() -> new IllegalArgumentException("Exception case not found"));
        return exceptionCollaborationService.sendCaseCommunication(
                tenantId,
                exceptionCase,
                request,
                actorUsername
        );
    }

    @Transactional
    public ExceptionPlaybookStepExecutionResponseDto executePlaybookStep(String tenantId,
                                                                        String transactionKey,
                                                                        String reconView,
                                                                        UUID stepId,
                                                                        String actorUsername) {
        String normalizedReconView = normalize(reconView);
        ExceptionCase exceptionCase = caseRepository.findByTenantIdAndTransactionKeyAndReconView(
                        tenantId,
                        transactionKey,
                        normalizedReconView)
                .orElseThrow(() -> new IllegalArgumentException("Exception case not found"));

        ExceptionPlaybook playbook = resolveCasePlaybook(exceptionCase, actorUsername);
        if (playbook == null) {
            throw new IllegalArgumentException("No recommended playbook is associated with this case");
        }

        ExceptionPlaybookStep step = playbook.getSteps().stream()
                .filter(candidate -> Objects.equals(candidate.getId(), stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Playbook step not found"));

        String operationModuleId = trimToNull(step.getOperationModuleId());
        String operationActionKey = trimToNull(step.getOperationActionKey());
        if (operationModuleId == null || operationActionKey == null) {
            throw new IllegalArgumentException("Selected playbook step does not have a linked operation");
        }

        OperationsService.ActionSupportDescriptor actionSupport = operationsService.describeAction(
                operationModuleId,
                operationActionKey,
                exceptionCase.getReconView()
        );
        if (!actionSupport.actionExecutable()) {
            throw new IllegalArgumentException(actionSupport.actionSupportMessage());
        }

        var actionResult = operationsService.executePlaybookAction(
                tenantId,
                operationModuleId,
                operationActionKey,
                actorUsername,
                exceptionCase.getTransactionKey(),
                exceptionCase.getReconView(),
                playbook.getId(),
                step.getId(),
                step.getStepTitle()
        );

        return ExceptionPlaybookStepExecutionResponseDto.builder()
                .caseData(toDto(exceptionCase, null))
                .actionResult(actionResult)
                .build();
    }

    @Transactional
    public BulkUpdateExceptionCasesResponse bulkUpdateCases(String tenantId,
                                                            BulkUpdateExceptionCasesRequest request,
                                                            String actorUsername) {
        List<BulkExceptionCaseRefRequest> items = request != null ? request.getItems() : null;
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one exception case must be selected");
        }
        String bulkCaseStatus = request != null ? request.getCaseStatus() : null;
        String bulkAssigneeUsername = request != null ? request.getAssigneeUsername() : null;
        String bulkAssignedRoleName = request != null ? request.getAssignedRoleName() : null;
        String bulkReasonCode = request != null ? request.getReasonCode() : null;
        String bulkNextAction = request != null ? request.getNextAction() : null;
        String bulkNextActionDueAt = request != null ? request.getNextActionDueAt() : null;
        String bulkHandoffNote = request != null ? request.getHandoffNote() : null;
        String bulkCommentText = request != null ? request.getCommentText() : null;
        String bulkReopenReason = request != null ? request.getReopenReason() : null;
        Boolean bulkCaptureAuditSnapshot = request != null ? request.getCaptureAuditSnapshot() : null;
        boolean assignmentUpdateRequested = !isBlank(bulkAssigneeUsername) || !isBlank(bulkAssignedRoleName);
        boolean caseFieldUpdateRequested = !isBlank(bulkCaseStatus)
                || assignmentUpdateRequested
                || !isBlank(bulkReasonCode)
                || !isBlank(bulkNextAction)
                || !isBlank(bulkNextActionDueAt)
                || !isBlank(bulkHandoffNote);
        String commentText = trimToNull(bulkCommentText);

        if (isBlank(bulkCaseStatus)
                && isBlank(bulkAssigneeUsername)
                && isBlank(bulkAssignedRoleName)
                && isBlank(bulkReasonCode)
                && isBlank(bulkNextAction)
                && isBlank(bulkNextActionDueAt)
                && isBlank(bulkHandoffNote)
                && isBlank(bulkCommentText)) {
            throw new IllegalArgumentException("Select at least one bulk triage action");
        }

        List<BulkExceptionCaseFailureDto> failures = new java.util.ArrayList<>();
        int updatedCount = 0;

        for (BulkExceptionCaseRefRequest item : items) {
            if (item == null || isBlank(item.getTransactionKey()) || isBlank(item.getReconView())) {
                failures.add(BulkExceptionCaseFailureDto.builder()
                        .transactionKey(item != null ? item.getTransactionKey() : null)
                        .reconView(item != null ? item.getReconView() : null)
                        .error("transactionKey and reconView are required")
                        .build());
                continue;
            }

            try {
                String normalizedReconView = normalize(item.getReconView());
                ExceptionCase existingCase = caseRepository.findByTenantIdAndTransactionKeyAndReconView(
                                tenantId,
                                item.getTransactionKey(),
                                normalizedReconView)
                        .orElseThrow(() -> new IllegalArgumentException("Exception case not found"));

                UpdateExceptionCaseRequest effectiveRequest = new UpdateExceptionCaseRequest();
                effectiveRequest.setReconView(existingCase.getReconView());
                effectiveRequest.setReconStatus(existingCase.getReconStatus());
                effectiveRequest.setCaseStatus(resolveBulkField(bulkCaseStatus, existingCase.getCaseStatus()));
                effectiveRequest.setReasonCode(resolveBulkReasonCode(bulkReasonCode, existingCase.getReasonCode()));
                effectiveRequest.setRootCauseCategory(isBlank(bulkReasonCode) ? existingCase.getRootCauseCategory() : null);
                effectiveRequest.setSeverity(existingCase.getSeverity());
                effectiveRequest.setAssigneeUsername(resolveBulkField(bulkAssigneeUsername, existingCase.getAssigneeUsername()));
                effectiveRequest.setAssignedRoleName(resolveBulkField(bulkAssignedRoleName, existingCase.getAssignedRoleName()));
                effectiveRequest.setNextAction(resolveBulkField(bulkNextAction, existingCase.getNextAction()));
                effectiveRequest.setNextActionDueAt(resolveBulkDateTime(bulkNextActionDueAt, existingCase.getNextActionDueAt()));
                effectiveRequest.setHandoffNote(resolveBulkField(bulkHandoffNote, existingCase.getHandoffNote()));
                effectiveRequest.setStoreId(exceptionScopeResolver.resolveStoreId(existingCase));
                effectiveRequest.setWkstnId(exceptionScopeResolver.resolveWkstnId(existingCase));
                effectiveRequest.setBusinessDate(optionalToString(exceptionScopeResolver.resolveBusinessDate(existingCase)));
                effectiveRequest.setNotes(existingCase.getNotes());
                effectiveRequest.setClosureComment(null);
                effectiveRequest.setReopenReason(trimToNull(bulkReopenReason));
                effectiveRequest.setCaptureAuditSnapshot(bulkCaptureAuditSnapshot);

                if (caseFieldUpdateRequested) {
                    upsertCaseInternal(
                            tenantId,
                            existingCase.getTransactionKey(),
                            existingCase.getReconView(),
                            effectiveRequest,
                            actorUsername,
                            assignmentUpdateRequested
                    );
                }

                if (commentText != null) {
                    AddExceptionCommentRequest commentRequest = new AddExceptionCommentRequest();
                    commentRequest.setReconView(existingCase.getReconView());
                    commentRequest.setCommentText(commentText);
                    addComment(
                            tenantId,
                            existingCase.getTransactionKey(),
                            existingCase.getReconView(),
                            commentRequest,
                            actorUsername
                    );
                }

                updatedCount++;
            } catch (Exception ex) {
                failures.add(BulkExceptionCaseFailureDto.builder()
                        .transactionKey(item.getTransactionKey())
                        .reconView(item.getReconView())
                        .error(defaultIfBlank(ex.getMessage(), "Bulk update failed"))
                        .build());
            }
        }

        return BulkUpdateExceptionCasesResponse.builder()
                .requestedCount(items.size())
                .updatedCount(updatedCount)
                .failedCount(failures.size())
                .failures(failures)
                .build();
    }

    private ExceptionCaseDto toDto(ExceptionCase exceptionCase, String workflowMessage) {
        TenantConfig tenant = tenantService.getTenant(exceptionCase.getTenantId());
        List<ExceptionCommentDto> comments = commentRepository
                .findByExceptionCaseOrderByCreatedAtAsc(exceptionCase)
                .stream()
                .map(this::toCommentDto)
                .collect(Collectors.toList());

        var pendingApproval = exceptionWorkflowService.getPendingApproval(exceptionCase).orElse(null);
        var businessValue = exceptionBusinessValueService.enrichCases(
                exceptionCase.getTenantId(),
                List.of(exceptionCase)
        ).get(exceptionBusinessValueService.caseKey(exceptionCase));
        var knownIssueContext = knownIssueService.loadActiveContext(
                exceptionCase.getTenantId(),
                List.of(exceptionCase.getReconView())
        );
        var ticketChannels = exceptionCollaborationService.getActiveChannels(
                exceptionCase.getTenantId(),
                exceptionCase.getReconView(),
                "TICKETING"
        );
        var communicationChannels = exceptionCollaborationService.getActiveChannels(
                exceptionCase.getTenantId(),
                exceptionCase.getReconView(),
                "COMMUNICATION"
        );
        var externalTickets = exceptionCollaborationService.getCaseExternalTickets(exceptionCase);
        var communications = exceptionCollaborationService.getCaseCommunications(exceptionCase);

        return ExceptionCaseDto.builder()
                .id(exceptionCase.getId())
                .tenantId(exceptionCase.getTenantId())
                .transactionKey(exceptionCase.getTransactionKey())
                .reconView(exceptionCase.getReconView())
                .reconStatus(exceptionCase.getReconStatus())
                .caseStatus(exceptionCase.getCaseStatus())
                .reasonCode(exceptionCase.getReasonCode())
                .rootCauseCategory(exceptionCase.getRootCauseCategory())
                .severity(exceptionCase.getSeverity())
                .assigneeUsername(exceptionCase.getAssigneeUsername())
                .assignedRoleName(exceptionCase.getAssignedRoleName())
                .nextAction(exceptionCase.getNextAction())
                .nextActionDueAt(TimezoneConverter.toLocalDateTimeInput(
                        optionalToString(exceptionCase.getNextActionDueAt()),
                        tenant))
                .handoffNote(exceptionCase.getHandoffNote())
                .lastHandoffBy(exceptionCase.getLastHandoffBy())
                .lastHandoffAt(TimezoneConverter.toDisplay(
                        optionalToString(exceptionCase.getLastHandoffAt()),
                        tenant))
                .ownershipStatus(exceptionOperationalOwnershipService.resolveOwnershipStatus(exceptionCase))
                .storeId(exceptionScopeResolver.resolveStoreId(exceptionCase))
                .wkstnId(exceptionScopeResolver.resolveWkstnId(exceptionCase))
                .businessDate(optionalToString(exceptionScopeResolver.resolveBusinessDate(exceptionCase)))
                .autoAssigned(exceptionCase.isAutoAssigned())
                .routingRuleName(exceptionCase.getRoutingRuleName())
                .playbookName(exceptionCase.getPlaybookName())
                .notes(exceptionCase.getNotes())
                .escalationState(exceptionCase.getEscalationState())
                .escalationCount(exceptionCase.getEscalationCount())
                .lastEscalatedBy(exceptionCase.getLastEscalatedBy())
                .lastEscalatedAt(TimezoneConverter.toDisplay(
                        optionalToString(exceptionCase.getLastEscalatedAt()),
                        tenant))
                .escalationPolicyName(exceptionCase.getEscalationPolicyName())
                .escalationReason(exceptionCase.getEscalationReason())
                .reopenReason(exceptionCase.getReopenReason())
                .reopenCount(exceptionCase.getReopenCount())
                .lastReopenedBy(exceptionCase.getLastReopenedBy())
                .lastReopenedAt(TimezoneConverter.toDisplay(
                        optionalToString(exceptionCase.getLastReopenedAt()),
                        tenant))
                .slaTargetMinutes(exceptionCase.getSlaTargetMinutes())
                .dueAt(TimezoneConverter.toDisplay(
                        optionalToString(exceptionSlaService.resolveDueAt(exceptionCase)),
                        tenant))
                .breachedAt(TimezoneConverter.toDisplay(
                        optionalToString(exceptionCase.getBreachedAt()),
                        tenant))
                .slaStatus(exceptionSlaService.evaluateSlaStatus(exceptionCase))
                .approvalState(pendingApproval != null ? "PENDING" : "NONE")
                .workflowMessage(workflowMessage)
                .businessValue(businessValue)
                .matchedKnownIssue(knownIssueService.matchCase(exceptionCase, knownIssueContext))
                .ticketChannels(ticketChannels)
                .communicationChannels(communicationChannels)
                .externalTickets(externalTickets)
                .communications(communications)
                .pendingApprovalRequest(pendingApproval)
                .recommendedPlaybook(exceptionAutomationService.toPlaybookDtoById(
                        exceptionCase.getTenantId(),
                        exceptionCase.getPlaybookId()))
                .createdBy(exceptionCase.getCreatedBy())
                .updatedBy(exceptionCase.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(
                        optionalToString(exceptionCase.getCreatedAt()),
                        tenant))
                .updatedAt(TimezoneConverter.toDisplay(
                        optionalToString(exceptionCase.getUpdatedAt()),
                        tenant))
                .comments(comments)
                .timeline(buildTimeline(exceptionCase))
                .auditSnapshots(exceptionCaseAuditService.getAuditSnapshots(exceptionCase, tenant))
                .build();
    }

    private ExceptionCommentDto toCommentDto(ExceptionComment comment) {
        String tenantId = comment.getExceptionCase().getTenantId();
        return ExceptionCommentDto.builder()
                .id(comment.getId())
                .commentText(comment.getCommentText())
                .createdBy(comment.getCreatedBy())
                .createdAt(TimezoneConverter.toDisplay(
                        optionalToString(comment.getCreatedAt()),
                        tenantService.getTenant(tenantId)))
                .build();
    }

    private String optionalToString(Object value) {
        return value != null ? value.toString() : null;
    }

    private ExceptionPlaybook resolveCasePlaybook(ExceptionCase exceptionCase, String actorUsername) {
        ExceptionPlaybook resolvedPlaybook = null;
        if (exceptionCase.getPlaybookId() != null) {
            resolvedPlaybook = playbookRepository.findByIdAndTenantId(
                    exceptionCase.getPlaybookId(),
                    exceptionCase.getTenantId()
            ).orElse(null);
        }

        if (resolvedPlaybook == null) {
            resolvedPlaybook = exceptionAutomationService.findMatchingPlaybook(exceptionCase).orElse(null);
            if (resolvedPlaybook != null) {
                exceptionCase.setPlaybookId(resolvedPlaybook.getId());
                exceptionCase.setPlaybookName(resolvedPlaybook.getPlaybookName());
                exceptionCase.setUpdatedBy(actorUsername);
                caseRepository.save(exceptionCase);
            }
        }

        return resolvedPlaybook;
    }

    private List<ExceptionCaseTimelineEventDto> buildTimeline(ExceptionCase exceptionCase) {
        TenantConfig tenant = tenantService.getTenant(exceptionCase.getTenantId());
        List<TimelineItem> items = new ArrayList<>();

        items.add(timelineItem(
                "CASE",
                "CASE_CREATED",
                "Case created",
                buildCaseTimelineSummary(exceptionCase),
                exceptionCase.getCreatedBy(),
                exceptionCase.getCaseStatus(),
                exceptionCase.getCreatedAt(),
                tenant
        ));

        if (exceptionCase.getUpdatedAt() != null
                && exceptionCase.getCreatedAt() != null
                && !exceptionCase.getUpdatedAt().equals(exceptionCase.getCreatedAt())) {
            items.add(timelineItem(
                    "CASE",
                    "CASE_UPDATED",
                    "Case updated",
                    buildCaseTimelineSummary(exceptionCase),
                    exceptionCase.getUpdatedBy(),
                    exceptionCase.getCaseStatus(),
                    exceptionCase.getUpdatedAt(),
                    tenant
            ));
        }

        commentRepository.findByExceptionCaseOrderByCreatedAtAsc(exceptionCase)
                .forEach(comment -> {
                    if (exceptionOperationalOwnershipService.isHandoffComment(comment.getCommentText())) {
                        items.add(timelineItem(
                                "HANDOFF",
                                "SHIFT_HANDOFF",
                                "Shift handoff",
                                exceptionOperationalOwnershipService.stripHandoffPrefix(comment.getCommentText()),
                                comment.getCreatedBy(),
                                null,
                                comment.getCreatedAt(),
                                tenant
                        ));
                        return;
                    }
                    items.add(timelineItem(
                            "COMMENT",
                            "COMMENT_ADDED",
                            "Comment added",
                            comment.getCommentText(),
                            comment.getCreatedBy(),
                            "COMMENTED",
                            comment.getCreatedAt(),
                            tenant
                    ));
                });

        approvalRepository.findByExceptionCaseOrderByRequestedAtAsc(exceptionCase)
                .forEach(request -> {
                    items.add(timelineItem(
                            "APPROVAL",
                            "APPROVAL_REQUESTED",
                            "Approval requested",
                            buildApprovalRequestSummary(request),
                            request.getRequestedBy(),
                            request.getRequestStatus(),
                            request.getRequestedAt(),
                            tenant
                    ));
                    if (request.getDecisionAt() != null) {
                        items.add(timelineItem(
                                "APPROVAL",
                                "APPROVAL_" + request.getRequestStatus(),
                                "Approval " + request.getRequestStatus().toLowerCase(),
                                buildApprovalDecisionSummary(request),
                                trimToNull(request.getDecisionBy()) != null ? request.getDecisionBy() : request.getRequestedBy(),
                                request.getRequestStatus(),
                                request.getDecisionAt(),
                                tenant
                        ));
                    }
                });

        operationsActionAuditRepository.findByTenantIdAndTransactionKeyAndReconViewOrderByCreatedAtDesc(
                        exceptionCase.getTenantId(),
                        exceptionCase.getTransactionKey(),
                        exceptionCase.getReconView())
                .forEach(audit -> items.add(timelineItem(
                        "OPERATIONS",
                        "PLAYBOOK_ACTION_EXECUTED",
                        trimToNull(audit.getPlaybookStepTitle()) != null
                                ? "Playbook action executed"
                                : "Operations action executed",
                        buildOperationsTimelineSummary(audit),
                        audit.getRequestedBy(),
                        audit.getResultStatus(),
                        audit.getCreatedAt(),
                        tenant
                )));

        exceptionCollaborationService.getCaseExternalTicketRecords(exceptionCase)
                .forEach(ticket -> items.add(timelineItem(
                        "COLLABORATION",
                        "EXTERNAL_TICKET_CREATED",
                        "External ticket created",
                        buildExternalTicketTimelineSummary(exceptionCollaborationService.toTicketDto(ticket)),
                        ticket.getCreatedBy(),
                        ticket.getDeliveryStatus(),
                        ticket.getCreatedAt(),
                        tenant
                )));

        exceptionCollaborationService.getCaseTicketSyncEvents(exceptionCase)
                .forEach(event -> items.add(timelineItem(
                        "COLLABORATION",
                        defaultIfBlank(event.getEventType(), "EXTERNAL_TICKET_SYNC"),
                        "External ticket synchronized",
                        buildExternalTicketSyncTimelineSummary(event),
                        trimToNull(event.getExternalUpdatedBy()) != null
                                ? event.getExternalUpdatedBy()
                                : event.getChannel() != null ? event.getChannel().getChannelName() : "External system",
                        event.getExternalStatus(),
                        event.getSyncedAt(),
                        tenant
                )));

        exceptionCollaborationService.getCaseCommunicationRecords(exceptionCase)
                .forEach(record -> items.add(timelineItem(
                        "COLLABORATION",
                        "COMMUNICATION_SENT",
                        "Communication sent",
                        buildCommunicationTimelineSummary(exceptionCollaborationService.toCommunicationDto(record)),
                        record.getCreatedBy(),
                        record.getDeliveryStatus(),
                        record.getCreatedAt(),
                        tenant
                )));

        exceptionCaseAuditService.getTimelineItems(exceptionCase, tenant)
                .forEach(item -> items.add(new TimelineItem(item.timestamp(), item.event())));

        return items.stream()
                .sorted(Comparator.comparing(TimelineItem::timestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(TimelineItem::event)
                .toList();
    }

    private TimelineItem timelineItem(String sourceType,
                                      String eventType,
                                      String title,
                                      String summary,
                                      String actor,
                                      String status,
                                      LocalDateTime timestamp,
                                      TenantConfig tenant) {
        return new TimelineItem(
                timestamp,
                ExceptionCaseTimelineEventDto.builder()
                        .sourceType(sourceType)
                        .eventType(eventType)
                        .title(title)
                        .summary(summary)
                        .actor(actor)
                        .status(status)
                        .eventAt(TimezoneConverter.toDisplay(optionalToString(timestamp), tenant))
                        .build()
        );
    }

    private String buildCaseTimelineSummary(ExceptionCase exceptionCase) {
        StringBuilder summary = new StringBuilder("Status ")
                .append(defaultIfBlank(exceptionCase.getCaseStatus(), "OPEN"))
                .append(" / Severity ")
                .append(defaultIfBlank(exceptionCase.getSeverity(), "MEDIUM"));
        if (trimToNull(exceptionCase.getReasonCode()) != null) {
            summary.append(" / ").append(exceptionCase.getReasonCode());
        }
        if (trimToNull(exceptionCase.getAssigneeUsername()) != null) {
            summary.append(" / ").append(exceptionCase.getAssigneeUsername());
        } else if (trimToNull(exceptionCase.getAssignedRoleName()) != null) {
            summary.append(" / ").append(exceptionCase.getAssignedRoleName());
        }
        if (trimToNull(exceptionCase.getNextAction()) != null) {
            summary.append(" / Next: ").append(exceptionCase.getNextAction());
        }
        return summary.toString();
    }

    private String buildApprovalRequestSummary(ExceptionApprovalRequest request) {
        StringBuilder summary = new StringBuilder(defaultIfBlank(request.getPreviousCaseStatus(), "OPEN"))
                .append(" -> ")
                .append(defaultIfBlank(request.getRequestedCaseStatus(), "OPEN"));
        if (trimToNull(request.getApproverRoleName()) != null) {
            summary.append(" / approver ").append(request.getApproverRoleName());
        }
        if (trimToNull(request.getClosureComment()) != null) {
            summary.append(" / ").append(request.getClosureComment());
        }
        return summary.toString();
    }

    private String buildApprovalDecisionSummary(ExceptionApprovalRequest request) {
        StringBuilder summary = new StringBuilder(defaultIfBlank(request.getRequestedCaseStatus(), "OPEN"))
                .append(" / ")
                .append(defaultIfBlank(request.getRequestStatus(), "PENDING"));
        if (trimToNull(request.getDecisionNotes()) != null) {
            summary.append(" / ").append(request.getDecisionNotes());
        }
        return summary.toString();
    }

    private String buildOperationsTimelineSummary(com.recon.api.domain.OperationsActionAudit audit) {
        StringBuilder summary = new StringBuilder(defaultIfBlank(audit.getModuleId(), "operations"))
                .append(" -> ")
                .append(defaultIfBlank(audit.getActionKey(), "action"));
        if (trimToNull(audit.getPlaybookStepTitle()) != null) {
            summary.append(" / ").append(audit.getPlaybookStepTitle());
        }
        if (trimToNull(audit.getResultMessage()) != null) {
            summary.append(" / ").append(audit.getResultMessage());
        }
        return summary.toString();
    }

    private String buildExternalTicketTimelineSummary(com.recon.api.domain.ExceptionExternalTicketDto ticket) {
        StringBuilder summary = new StringBuilder(defaultIfBlank(ticket.getChannelName(), "channel"))
                .append(" / ")
                .append(defaultIfBlank(ticket.getTicketSummary(), "Ticket created"));
        if (trimToNull(ticket.getExternalReference()) != null) {
            summary.append(" / ref ").append(ticket.getExternalReference());
        }
        if (trimToNull(ticket.getExternalStatus()) != null) {
            summary.append(" / status ").append(ticket.getExternalStatus());
        }
        if (trimToNull(ticket.getErrorMessage()) != null) {
            summary.append(" / ").append(ticket.getErrorMessage());
        }
        return summary.toString();
    }

    private String buildExternalTicketSyncTimelineSummary(com.recon.api.domain.ExceptionExternalTicketSyncEvent event) {
        StringBuilder summary = new StringBuilder();
        if (event.getChannel() != null && trimToNull(event.getChannel().getChannelName()) != null) {
            summary.append(event.getChannel().getChannelName());
        } else {
            summary.append("external system");
        }
        if (trimToNull(event.getExternalReference()) != null) {
            summary.append(" / ref ").append(event.getExternalReference());
        }
        if (trimToNull(event.getExternalStatus()) != null) {
            summary.append(" / ").append(event.getExternalStatus());
        }
        if (trimToNull(event.getStatusNote()) != null) {
            summary.append(" / ").append(event.getStatusNote());
        }
        return summary.toString();
    }

    private String buildCommunicationTimelineSummary(com.recon.api.domain.ExceptionOutboundCommunicationDto record) {
        StringBuilder summary = new StringBuilder(defaultIfBlank(record.getChannelName(), "channel"));
        if (trimToNull(record.getSubject()) != null) {
            summary.append(" / ").append(record.getSubject());
        }
        if (trimToNull(record.getRecipient()) != null) {
            summary.append(" / ").append(record.getRecipient());
        }
        if (trimToNull(record.getErrorMessage()) != null) {
            summary.append(" / ").append(record.getErrorMessage());
        }
        return summary.toString();
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveRequestedText(String requestedValue, String existingValue) {
        if (requestedValue == null) {
            return existingValue;
        }
        return trimToNull(requestedValue);
    }

    private String resolveBulkField(String requestedValue, String existingValue) {
        String trimmed = trimToNull(requestedValue);
        if (trimmed == null) {
            return existingValue;
        }
        if (CLEAR_FIELD_TOKEN.equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private String resolveBulkReasonCode(String requestedValue, String existingValue) {
        String resolved = resolveBulkField(requestedValue, existingValue);
        return trimToNull(resolved);
    }

    private String resolveBulkDateTime(String requestedValue, LocalDateTime existingValue) {
        String trimmed = trimToNull(requestedValue);
        if (trimmed == null) {
            return optionalToString(existingValue);
        }
        if (CLEAR_FIELD_TOKEN.equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException("reconView is required");
        }
        return trimmed.toUpperCase();
    }

    private String normalizeStatus(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "OPEN" : trimmed.toUpperCase();
    }

    private String normalizeNullable(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase();
    }

    private String normalizeSeverity(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "MEDIUM" : trimmed.toUpperCase();
    }

    private boolean isTerminalStatus(String value) {
        String normalized = normalizeNullable(value);
        return "RESOLVED".equals(normalized) || "IGNORED".equals(normalized);
    }

    private boolean isReopenTransition(String previousCaseStatus, String nextCaseStatus) {
        return isTerminalStatus(previousCaseStatus) && !isTerminalStatus(nextCaseStatus);
    }

    private String resolveRootCauseCategory(String requestedCategory,
                                            String normalizedReasonCode,
                                            String existingCategory) {
        String normalizedCategory = rootCauseTaxonomyService.normalizeCategory(requestedCategory);
        if (normalizedCategory != null) {
            return normalizedCategory;
        }
        String derivedCategory = rootCauseTaxonomyService.deriveCategory(normalizedReasonCode);
        if (derivedCategory != null) {
            return derivedCategory;
        }
        return rootCauseTaxonomyService.normalizeCategory(existingCategory);
    }

    private LocalDate parseBusinessDate(String value, LocalDate fallback) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return fallback;
        }
        return LocalDate.parse(trimmed);
    }

    private LocalDateTime parseDateTimeField(String value, LocalDateTime fallback, TenantConfig tenant) {
        if (value == null) {
            return fallback;
        }
        String trimmed = trimToNull(value);
        if (trimmed == null || CLEAR_FIELD_TOKEN.equals(trimmed)) {
            return null;
        }
        return TimezoneConverter.parseLocalDateTimeInput(trimmed, tenant);
    }

    private boolean ownershipChanged(OwnershipSnapshot previous, ExceptionCase exceptionCase) {
        return !Objects.equals(previous.assigneeUsername(), exceptionCase.getAssigneeUsername())
                || !Objects.equals(previous.assignedRoleName(), exceptionCase.getAssignedRoleName())
                || !Objects.equals(previous.nextAction(), exceptionCase.getNextAction())
                || !Objects.equals(previous.nextActionDueAt(), exceptionCase.getNextActionDueAt())
                || !Objects.equals(previous.handoffNote(), exceptionCase.getHandoffNote());
    }

    private void saveHandoffAudit(ExceptionCase exceptionCase, String actorUsername, boolean handoffChanged) {
        if (!handoffChanged) {
            return;
        }
        String commentText = exceptionOperationalOwnershipService.buildHandoffComment(exceptionCase);
        if (commentText == null) {
            return;
        }
        commentRepository.save(ExceptionComment.builder()
                .exceptionCase(exceptionCase)
                .commentText(commentText)
                .createdBy(actorUsername)
                .build());
        exceptionCaseAuditService.recordComment(exceptionCase, actorUsername, commentText);
    }

    private record OwnershipSnapshot(String assigneeUsername,
                                     String assignedRoleName,
                                     String nextAction,
                                     LocalDateTime nextActionDueAt,
                                     String handoffNote) {
        private static OwnershipSnapshot from(ExceptionCase exceptionCase) {
            return new OwnershipSnapshot(
                    exceptionCase.getAssigneeUsername(),
                    exceptionCase.getAssignedRoleName(),
                    exceptionCase.getNextAction(),
                    exceptionCase.getNextActionDueAt(),
                    exceptionCase.getHandoffNote()
            );
        }
    }

    private record TimelineItem(LocalDateTime timestamp, ExceptionCaseTimelineEventDto event) {
    }
}
