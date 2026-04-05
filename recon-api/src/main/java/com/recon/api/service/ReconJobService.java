package com.recon.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recon.api.domain.AuditLedgerWriteRequest;
import com.recon.api.domain.DashboardStats;
import com.recon.api.domain.OperationActionResponseDto;
import com.recon.api.domain.OperationsJobCenterResponse;
import com.recon.api.domain.ReconJobDefinition;
import com.recon.api.domain.ReconJobDefinitionDto;
import com.recon.api.domain.ReconJobNotificationDelivery;
import com.recon.api.domain.ReconJobNotificationDeliveryDto;
import com.recon.api.domain.ReconJobRetryEvent;
import com.recon.api.domain.ReconJobRun;
import com.recon.api.domain.ReconJobRunDto;
import com.recon.api.domain.ReconJobStepDefinition;
import com.recon.api.domain.ReconJobStepDto;
import com.recon.api.domain.ReconJobStepRun;
import com.recon.api.domain.ReconJobStepRunDto;
import com.recon.api.domain.ReconJobTemplateDto;
import com.recon.api.domain.ReconModuleDto;
import com.recon.api.domain.SaveReconJobDefinitionRequest;
import com.recon.api.domain.SaveReconJobStepRequest;
import com.recon.api.domain.TenantConfig;
import com.recon.api.repository.ReconJobDefinitionRepository;
import com.recon.api.repository.ReconJobNotificationDeliveryRepository;
import com.recon.api.repository.ReconJobRetryEventRepository;
import com.recon.api.repository.ReconJobRunRepository;
import com.recon.api.repository.ReconJobStepDefinitionRepository;
import com.recon.api.repository.ReconJobStepRunRepository;
import com.recon.api.util.TimezoneConverter;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReconJobService {

    private static final String STEP_TYPE_OPERATIONS_ACTION = "OPERATIONS_ACTION";
    private static final String STEP_TYPE_RECON_SUMMARY_SNAPSHOT = "RECON_SUMMARY_SNAPSHOT";
    private static final Set<String> ACTIVE_RUN_STATUSES = Set.of("PENDING", "RUNNING");
    private static final Set<String> DEFAULT_TEMPLATE_KEYS = Set.of(
            "SIM_RMS_EOD",
            "SIM_MFCS_EOD",
            "SIOCS_RMS_EOD"
    );

    private final ReconJobDefinitionRepository definitionRepository;
    private final ReconJobStepDefinitionRepository stepDefinitionRepository;
    private final ReconJobRunRepository runRepository;
    private final ReconJobStepRunRepository stepRunRepository;
    private final ReconJobRetryEventRepository retryEventRepository;
    private final ReconJobNotificationDeliveryRepository notificationDeliveryRepository;
    private final OperationsService operationsService;
    private final ReconQueryService reconQueryService;
    private final TenantService tenantService;
    private final AuditLedgerService auditLedgerService;
    private final ReconJobNotificationService reconJobNotificationService;
    private final ReconModuleService reconModuleService;
    private final ObjectMapper objectMapper;

    @Transactional
    public OperationsJobCenterResponse getJobCenter(String tenantId, Collection<String> allowedReconViews) {
        TenantConfig tenant = tenantService.getTenant(tenantId);
        Set<String> normalizedAllowedReconViews = normalizeAllowedReconViews(allowedReconViews);
        ensureDefaultJobDefinitions(tenant);
        List<ReconJobDefinition> definitions = definitionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<ReconJobDefinition> visibleDefinitions = definitions.stream()
                .filter(definition -> normalizedAllowedReconViews.contains(normalizeReconView(definition.getReconView())))
                .toList();
        Map<UUID, List<ReconJobStepDefinition>> stepsByDefinition = definitions.stream()
                .collect(Collectors.toMap(
                        ReconJobDefinition::getId,
                        definition -> stepDefinitionRepository.findByJobDefinitionIdOrderByStepOrderAsc(definition.getId()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<ReconJobRun> recentRuns = runRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        Map<UUID, List<ReconJobStepRun>> stepRunsByRun = groupStepRuns(recentRuns);
        Map<UUID, List<ReconJobNotificationDelivery>> deliveriesByRun = groupNotificationDeliveries(recentRuns);
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        return OperationsJobCenterResponse.builder()
                .enabledJobs(visibleDefinitions.stream().filter(ReconJobDefinition::isEnabled).count())
                .failedRunsLast24Hours(recentRuns.stream()
                        .filter(run -> normalizedAllowedReconViews.contains(normalizeReconView(run.getReconView())))
                        .filter(run -> "FAILED".equalsIgnoreCase(run.getRunStatus()))
                        .filter(run -> run.getCreatedAt() != null && run.getCreatedAt().isAfter(since))
                        .count())
                .successfulRunsLast24Hours(recentRuns.stream()
                        .filter(run -> normalizedAllowedReconViews.contains(normalizeReconView(run.getReconView())))
                        .filter(run -> "SUCCEEDED".equalsIgnoreCase(run.getRunStatus()))
                        .filter(run -> run.getCreatedAt() != null && run.getCreatedAt().isAfter(since))
                        .count())
                .pendingRetries(recentRuns.stream()
                        .filter(run -> normalizedAllowedReconViews.contains(normalizeReconView(run.getReconView())))
                        .filter(ReconJobRun::isRetryPending)
                        .count())
                .actionCatalog(operationsService.getReconJobActionCatalog(normalizedAllowedReconViews))
                .templates(recommendedTemplates(normalizedAllowedReconViews))
                .jobDefinitions(visibleDefinitions.stream()
                        .map(definition -> toDefinitionDto(definition, tenant, stepsByDefinition.getOrDefault(definition.getId(), List.of())))
                        .toList())
                .recentRuns(recentRuns.stream()
                        .filter(run -> normalizedAllowedReconViews.contains(normalizeReconView(run.getReconView())))
                        .map(run -> toRunDto(run, tenant, stepRunsByRun.getOrDefault(run.getId(), List.of()), deliveriesByRun.getOrDefault(run.getId(), List.of())))
                        .toList())
                .build();
    }

    private void ensureDefaultJobDefinitions(TenantConfig tenant) {
        String tenantId = tenant.getTenantId();
        if (trimToNull(tenantId) == null) {
            return;
        }
        Set<String> allReconViews = reconModuleService.getAllActiveModules().stream()
                .map(ReconModuleDto::getReconView)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        recommendedTemplates(allReconViews).stream()
                .filter(template -> DEFAULT_TEMPLATE_KEYS.contains(defaultIfBlank(template.getTemplateKey(), "")))
                .forEach(template -> ensureDefaultTemplateJobDefinition(tenant, template));
    }

    private void ensureDefaultTemplateJobDefinition(TenantConfig tenant, ReconJobTemplateDto template) {
        String tenantId = tenant.getTenantId();
        String templateKey = defaultIfBlank(template.getTemplateKey(), defaultIfBlank(template.getTemplateName(), "DEFAULT_JOB"));

        ReconJobDefinition definition = definitionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(existing -> defaultIfBlank(template.getTemplateName(), "")
                        .equalsIgnoreCase(defaultIfBlank(existing.getJobName(), "")))
                .findFirst()
                .orElse(null);

        if (definition == null) {
            definition = definitionRepository.save(ReconJobDefinition.builder()
                    .id(stableUuid(tenantId + "|RECON_JOB|" + templateKey))
                    .tenantId(tenantId)
                    .jobName(template.getTemplateName())
                    .reconView(normalizeReconView(template.getReconView()))
                    .cronExpression(validateCron(template.getCronExpression()))
                    .jobTimezone(defaultIfBlank(tenant.getTimezone(), "UTC"))
                    .windowType(defaultIfBlank(template.getWindowType(), "END_OF_DAY"))
                    .endOfDayLocalTime(trimToNull(template.getEndOfDayLocalTime()))
                    .businessDateOffsetDays(defaultInt(template.getBusinessDateOffsetDays(), 0))
                    .maxRetryAttempts(1)
                    .retryDelayMinutes(15)
                    .allowConcurrentRuns(false)
                    .enabled(true)
                    .scopeStoreIds(writeJson(List.of()))
                    .notifyOnSuccess(false)
                    .notifyOnFailure(true)
                    .nextScheduledAt(nextExecution(
                            validateCron(template.getCronExpression()),
                            defaultIfBlank(tenant.getTimezone(), "UTC"),
                            null))
                    .createdBy("system")
                    .updatedBy("system")
                    .build());
        }

        ensureDefaultTemplateSteps(tenantId, definition, template, templateKey);
    }

    private void ensureDefaultTemplateSteps(String tenantId,
                                            ReconJobDefinition definition,
                                            ReconJobTemplateDto template,
                                            String templateKey) {
        List<ReconJobStepDefinition> existingSteps = stepDefinitionRepository.findByJobDefinitionIdOrderByStepOrderAsc(definition.getId());
        Map<Integer, ReconJobStepDefinition> stepsByOrder = existingSteps.stream()
                .collect(Collectors.toMap(
                        ReconJobStepDefinition::getStepOrder,
                        step -> step,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<ReconJobStepDefinition> missingSteps = new ArrayList<>();
        Map<Integer, UUID> stepIdsByOrder = new LinkedHashMap<>();
        for (ReconJobStepDto templateStep : template.getSteps()) {
            Integer stepOrder = templateStep.getStepOrder();
            if (stepOrder == null) {
                continue;
            }
            stepIdsByOrder.put(stepOrder,
                    stepsByOrder.containsKey(stepOrder)
                            ? stepsByOrder.get(stepOrder).getId()
                            : stableUuid(tenantId + "|RECON_JOB|" + templateKey + "|STEP|" + stepOrder));
        }

        Map<UUID, Integer> templateStepOrderById = new HashMap<>();
        for (ReconJobStepDto templateStep : template.getSteps()) {
            if (templateStep.getId() != null && templateStep.getStepOrder() != null) {
                templateStepOrderById.put(templateStep.getId(), templateStep.getStepOrder());
            }
        }

        for (ReconJobStepDto templateStep : template.getSteps()) {
            Integer stepOrder = templateStep.getStepOrder();
            if (stepOrder == null || stepsByOrder.containsKey(stepOrder)) {
                continue;
            }
            Integer dependencyOrder = templateStep.getDependsOnStepId() == null
                    ? null
                    : templateStepOrderById.get(templateStep.getDependsOnStepId());
            missingSteps.add(ReconJobStepDefinition.builder()
                    .id(stepIdsByOrder.get(stepOrder))
                    .jobDefinitionId(definition.getId())
                    .stepOrder(stepOrder)
                    .stepLabel(templateStep.getStepLabel())
                    .stepType(templateStep.getStepType())
                    .moduleId(trimToNull(templateStep.getModuleId()))
                    .actionKey(trimToNull(templateStep.getActionKey()))
                    .dependsOnStepId(dependencyOrder == null ? null : stepIdsByOrder.get(dependencyOrder))
                    .settleDelaySeconds(defaultInt(templateStep.getSettleDelaySeconds(), 0))
                    .build());
        }

        if (!missingSteps.isEmpty()) {
            stepDefinitionRepository.saveAll(missingSteps);
        }
    }

    @Transactional
    public ReconJobDefinitionDto saveJobDefinition(String tenantId,
                                                   String username,
                                                   SaveReconJobDefinitionRequest request,
                                                   Collection<String> allowedReconViews) {
        validateSaveRequest(request);
        Set<String> normalizedAllowedReconViews = normalizeAllowedReconViews(allowedReconViews);
        ReconJobDefinition definition = request.getId() == null
                ? ReconJobDefinition.builder().tenantId(tenantId).createdBy(username).build()
                : definitionRepository.findById(request.getId())
                .filter(existing -> tenantId.equalsIgnoreCase(existing.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Reconciliation job not found"));
        if (definition.getId() != null) {
            requireAllowedReconView(definition.getReconView(), normalizedAllowedReconViews);
        }
        Map<String, Object> beforeState = definition.getId() == null ? null : snapshotDefinition(definition);

        definition.setJobName(request.getJobName().trim());
        definition.setReconView(normalizeReconView(request.getReconView()));
        requireAllowedReconView(definition.getReconView(), normalizedAllowedReconViews);
        definition.setCronExpression(validateCron(request.getCronExpression()));
        definition.setJobTimezone(validateTimezone(request.getJobTimezone()));
        definition.setWindowType(normalizeWindowType(request.getWindowType()));
        definition.setEndOfDayLocalTime(normalizeTime(request.getEndOfDayLocalTime()));
        definition.setBusinessDateOffsetDays(request.getBusinessDateOffsetDays() == null ? 0 : Math.max(0, request.getBusinessDateOffsetDays()));
        definition.setMaxRetryAttempts(request.getMaxRetryAttempts() == null ? 0 : Math.max(0, request.getMaxRetryAttempts()));
        definition.setRetryDelayMinutes(request.getRetryDelayMinutes() == null ? 15 : Math.max(1, request.getRetryDelayMinutes()));
        definition.setAllowConcurrentRuns(Boolean.TRUE.equals(request.getAllowConcurrentRuns()));
        definition.setEnabled(request.getEnabled() == null || request.getEnabled());
        definition.setNotifyOnSuccess(Boolean.TRUE.equals(request.getNotifyOnSuccess()));
        definition.setNotifyOnFailure(request.getNotifyOnFailure() == null || request.getNotifyOnFailure());
        definition.setNotificationChannelType(trimToNull(request.getNotificationChannelType()) == null
                ? null
                : request.getNotificationChannelType().trim().toUpperCase(Locale.ROOT));
        definition.setNotificationEndpoint(trimToNull(request.getNotificationEndpoint()));
        definition.setNotificationEmail(trimToNull(request.getNotificationEmail()));
        definition.setScopeStoreIds(writeJson(normalizeStoreScope(request.getScopeStoreIds())));
        definition.setUpdatedBy(username);
        definition.setNextScheduledAt(definition.isEnabled()
                ? nextExecution(definition.getCronExpression(), definition.getJobTimezone(), null)
                : null);
        if (!definition.isEnabled()) {
            definition.setLastScheduledAt(null);
        }

        ReconJobDefinition savedDefinition = definitionRepository.save(definition);
        replaceSteps(savedDefinition.getId(), request.getSteps());
        List<ReconJobStepDefinition> savedSteps = stepDefinitionRepository.findByJobDefinitionIdOrderByStepOrderAsc(savedDefinition.getId());
        TenantConfig tenant = tenantService.getTenant(tenantId);

        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(tenantId)
                .sourceType("OPERATIONS")
                .moduleKey(defaultIfBlank(savedDefinition.getReconView(), "OPERATIONS"))
                .entityType("RECON_JOB_DEFINITION")
                .entityKey(savedDefinition.getId().toString())
                .actionType(request.getId() == null ? "JOB_CREATED" : "JOB_UPDATED")
                .title(request.getId() == null ? "Reconciliation job created" : "Reconciliation job updated")
                .summary(savedDefinition.getJobName())
                .actor(username)
                .status(savedDefinition.isEnabled() ? "ENABLED" : "DISABLED")
                .controlFamily("OPERATIONS")
                .evidenceTags(List.of("RECON_JOB", "SCHEDULER"))
                .beforeState(beforeState)
                .afterState(snapshotDefinition(savedDefinition))
                .eventAt(LocalDateTime.now())
                .build());
        return toDefinitionDto(savedDefinition, tenant, savedSteps);
    }

    @Transactional
    public ReconJobRunDto triggerManualRun(String tenantId,
                                           UUID jobDefinitionId,
                                           String username,
                                           Collection<String> allowedReconViews) {
        ReconJobDefinition definition = loadDefinition(tenantId, jobDefinitionId);
        requireAllowedReconView(definition.getReconView(), normalizeAllowedReconViews(allowedReconViews));
        ReconJobRun run = createRun(definition, "MANUAL", username, LocalDateTime.now(), 1, null, null);
        executeRun(definition, run);
        return toRunDto(runRepository.findById(run.getId()).orElse(run),
                tenantService.getTenant(tenantId),
                stepRunRepository.findByJobRunIdInOrderByJobRunIdAscStepOrderAsc(List.of(run.getId())),
                notificationDeliveryRepository.findByJobRunIdInOrderByCreatedAtDesc(List.of(run.getId())));
    }

    @Scheduled(fixedDelayString = "${app.operations.job-scheduler-interval-ms:60000}")
    public void processScheduledJobs() {
        dispatchScheduledJobs();
        processDueRetries();
    }

    void dispatchScheduledJobs() {
        LocalDateTime now = LocalDateTime.now();
        List<ReconJobDefinition> dueDefinitions = definitionRepository.findByEnabledTrueAndNextScheduledAtLessThanEqualOrderByNextScheduledAtAsc(now);
        for (ReconJobDefinition definition : dueDefinitions) {
            try {
                if (!definition.isAllowConcurrentRuns()
                        && runRepository.existsByJobDefinitionIdAndRunStatusIn(definition.getId(), ACTIVE_RUN_STATUSES)) {
                    definition.setLastRunMessage("Skipped scheduled trigger because a previous run is still active");
                    definition.setNextScheduledAt(nextExecution(definition.getCronExpression(), definition.getJobTimezone(), now));
                    definitionRepository.save(definition);
                    continue;
                }
                LocalDateTime scheduledFor = definition.getNextScheduledAt() == null ? now : definition.getNextScheduledAt();
                definition.setLastScheduledAt(scheduledFor);
                definition.setNextScheduledAt(nextExecution(definition.getCronExpression(), definition.getJobTimezone(), scheduledFor));
                definitionRepository.save(definition);
                ReconJobRun run = createRun(definition, "SCHEDULED", "system-scheduler", scheduledFor, 1, null, null);
                executeRun(definition, run);
            } catch (Exception ex) {
                log.error("Failed to dispatch scheduled reconciliation job {}: {}", definition.getId(), ex.getMessage(), ex);
            }
        }
    }

    void processDueRetries() {
        LocalDateTime now = LocalDateTime.now();
        List<ReconJobRetryEvent> dueRetryEvents = retryEventRepository.findByRetryStatusAndScheduledForLessThanEqualOrderByScheduledForAsc("PENDING", now);
        for (ReconJobRetryEvent retryEvent : dueRetryEvents) {
            try {
                ReconJobRun failedRun = runRepository.findById(retryEvent.getFailedRunId())
                        .orElseThrow(() -> new EntityNotFoundException("Failed run not found"));
                ReconJobDefinition definition = definitionRepository.findById(retryEvent.getJobDefinitionId())
                        .orElseThrow(() -> new EntityNotFoundException("Reconciliation job not found"));
                retryEvent.setRetryStatus("RUNNING");
                retryEvent.setStartedAt(LocalDateTime.now());
                retryEventRepository.save(retryEvent);
                ReconJobRun retryRun = createRun(
                        definition,
                        "RETRY",
                        "system-scheduler",
                        retryEvent.getScheduledFor(),
                        retryEvent.getAttemptNumber(),
                        failedRun.getId(),
                        failedRun.getRootRunId() == null ? failedRun.getId() : failedRun.getRootRunId()
                );
                retryEvent.setRetryRunId(retryRun.getId());
                retryEventRepository.save(retryEvent);
                executeRun(definition, retryRun);
                ReconJobRun completedRetryRun = runRepository.findById(retryRun.getId()).orElse(retryRun);
                retryEvent.setCompletedAt(LocalDateTime.now());
                retryEvent.setRetryStatus("SUCCEEDED".equalsIgnoreCase(completedRetryRun.getRunStatus()) ? "SUCCEEDED" : "FAILED");
                retryEvent.setErrorMessage("SUCCEEDED".equalsIgnoreCase(completedRetryRun.getRunStatus()) ? null : completedRetryRun.getSummary());
                retryEventRepository.save(retryEvent);
            } catch (Exception ex) {
                retryEvent.setCompletedAt(LocalDateTime.now());
                retryEvent.setRetryStatus("FAILED");
                retryEvent.setErrorMessage(ex.getMessage());
                retryEventRepository.save(retryEvent);
                log.error("Failed to execute scheduled retry {}: {}", retryEvent.getId(), ex.getMessage(), ex);
            }
        }
    }

    private void executeRun(ReconJobDefinition definition, ReconJobRun run) {
        TenantConfig tenant = tenantService.getTenant(definition.getTenantId());
        List<ReconJobStepDefinition> stepDefinitions = stepDefinitionRepository.findByJobDefinitionIdOrderByStepOrderAsc(definition.getId());
        Map<UUID, ReconJobStepRun> runsByStepId = new LinkedHashMap<>();
        List<ReconJobStepRun> persistedStepRuns = new ArrayList<>();
        Map<String, Object> resultPayload = new LinkedHashMap<>();
        LocalDateTime startedAt = LocalDateTime.now();

        run.setRunStatus("RUNNING");
        run.setStartedAt(startedAt);
        run.setCompletedAt(null);
        run.setSummary(null);
        run.setResultPayload(null);
        runRepository.save(run);
        definition.setLastRunStartedAt(startedAt);
        definition.setLastRunStatus("RUNNING");
        definition.setLastRunMessage("Run in progress");
        definitionRepository.save(definition);

        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(definition.getTenantId())
                .sourceType("OPERATIONS")
                .moduleKey(defaultIfBlank(definition.getReconView(), "OPERATIONS"))
                .entityType("RECON_JOB_RUN")
                .entityKey(run.getId().toString())
                .actionType("JOB_RUN_STARTED")
                .title("Reconciliation job run started")
                .summary(run.getJobName())
                .actor(defaultIfBlank(run.getInitiatedBy(), "system"))
                .status("RUNNING")
                .referenceKey(definition.getId().toString())
                .controlFamily("OPERATIONS")
                .evidenceTags(List.of("RECON_JOB", "SCHEDULER"))
                .afterState(snapshotRun(run))
                .eventAt(startedAt)
                .build());

        boolean jobFailed = false;
        String failureMessage = null;

        try {
            for (ReconJobStepDefinition stepDefinition : stepDefinitions) {
                if (jobFailed) {
                    ReconJobStepRun blockedRun = saveStepRun(
                            run,
                            stepDefinition,
                            "BLOCKED",
                            "Skipped because a previous step failed",
                            stepRequestPayload(definition, run, stepDefinition),
                            null,
                            LocalDateTime.now(),
                            LocalDateTime.now()
                    );
                    persistedStepRuns.add(blockedRun);
                    runsByStepId.put(stepDefinition.getId(), blockedRun);
                    continue;
                }
                if (!dependencySatisfied(stepDefinition, runsByStepId)) {
                    ReconJobStepRun blockedRun = saveStepRun(
                            run,
                            stepDefinition,
                            "BLOCKED",
                            "Waiting on dependency step to succeed",
                            stepRequestPayload(definition, run, stepDefinition),
                            null,
                            LocalDateTime.now(),
                            LocalDateTime.now()
                    );
                    persistedStepRuns.add(blockedRun);
                    runsByStepId.put(stepDefinition.getId(), blockedRun);
                    jobFailed = true;
                    failureMessage = blockedRun.getMessage();
                    continue;
                }
                StepExecutionResult stepExecution = executeStep(definition, run, stepDefinition, tenant);
                ReconJobStepRun stepRun = saveStepRun(
                        run,
                        stepDefinition,
                        stepExecution.runStatus(),
                        stepExecution.message(),
                        stepExecution.requestPayload(),
                        stepExecution.responsePayload(),
                        stepExecution.startedAt(),
                        stepExecution.completedAt()
                );
                persistedStepRuns.add(stepRun);
                runsByStepId.put(stepDefinition.getId(), stepRun);
                if (!"SUCCEEDED".equalsIgnoreCase(stepExecution.runStatus())) {
                    jobFailed = true;
                    failureMessage = stepExecution.message();
                }
            }
        } catch (Exception ex) {
            log.error("Unexpected failure while executing reconciliation job run {}: {}", run.getId(), ex.getMessage(), ex);
            jobFailed = true;
            failureMessage = defaultIfBlank(ex.getMessage(), "Unexpected reconciliation job execution failure");
        }

        resultPayload.put("rootRunId", valueOrNull(run.getRootRunId()));
        resultPayload.put("attemptNumber", run.getAttemptNumber());
        resultPayload.put("businessDate", run.getBusinessDate());
        resultPayload.put("windowFromBusinessDate", run.getWindowFromBusinessDate());
        resultPayload.put("windowToBusinessDate", run.getWindowToBusinessDate());
        resultPayload.put("stepSummary", buildRunSummary(persistedStepRuns));
        resultPayload.put("steps", persistedStepRuns.stream()
                .map(stepRun -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("stepOrder", stepRun.getStepOrder());
                    payload.put("stepLabel", stepRun.getStepLabel());
                    payload.put("stepType", stepRun.getStepType());
                    payload.put("status", stepRun.getRunStatus());
                    payload.put("message", stepRun.getMessage());
                    payload.put("durationMs", stepRun.getDurationMs());
                    payload.put("response", readObject(stepRun.getResponsePayload()));
                    return payload;
                })
                .toList());

        scheduleRetryIfNeeded(definition, run, jobFailed, resultPayload);
        run.setRunStatus(jobFailed ? "FAILED" : "SUCCEEDED");
        run.setCompletedAt(LocalDateTime.now());
        run.setSummary(jobFailed
                ? defaultIfBlank(failureMessage, "Reconciliation job failed")
                : "Completed " + persistedStepRuns.stream().filter(step -> "SUCCEEDED".equalsIgnoreCase(step.getRunStatus())).count() + " steps successfully");
        run.setResultPayload(writeJson(resultPayload));
        runRepository.save(run);

        definition.setLastRunCompletedAt(run.getCompletedAt());
        definition.setLastRunStatus(run.getRunStatus());
        definition.setLastRunMessage(run.getSummary());
        definitionRepository.save(definition);

        auditLedgerService.record(AuditLedgerWriteRequest.builder()
                .tenantId(definition.getTenantId())
                .sourceType("OPERATIONS")
                .moduleKey(defaultIfBlank(definition.getReconView(), "OPERATIONS"))
                .entityType("RECON_JOB_RUN")
                .entityKey(run.getId().toString())
                .actionType("JOB_RUN_" + run.getRunStatus())
                .title("Reconciliation job run " + run.getRunStatus().toLowerCase(Locale.ROOT))
                .summary(run.getSummary())
                .actor(defaultIfBlank(run.getInitiatedBy(), "system"))
                .status(run.getRunStatus())
                .referenceKey(definition.getId().toString())
                .controlFamily("OPERATIONS")
                .evidenceTags(List.of("RECON_JOB", "SCHEDULER"))
                .afterState(snapshotRun(run))
                .metadata(resultPayload)
                .eventAt(run.getCompletedAt())
                .build());

        reconJobNotificationService.notifyRunCompletion(definition, run, resultPayload);
    }

    private StepExecutionResult executeStep(ReconJobDefinition definition,
                                            ReconJobRun run,
                                            ReconJobStepDefinition stepDefinition,
                                            TenantConfig tenant) {
        LocalDateTime startedAt = LocalDateTime.now();
        Map<String, Object> requestPayload = stepRequestPayload(definition, run, stepDefinition);
        Integer settleDelaySeconds = stepDefinition.getSettleDelaySeconds();
        if (settleDelaySeconds != null && settleDelaySeconds > 0) {
            try {
                Thread.sleep(Math.max(0L, Math.min(settleDelaySeconds, 300)) * 1000L);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return new StepExecutionResult(
                        "FAILED",
                        "Step interrupted while waiting for dependency settle delay",
                        requestPayload,
                        Map.of("interrupted", true),
                        startedAt,
                        LocalDateTime.now()
                );
            }
        }

        try {
            return switch (normalizeStepType(stepDefinition.getStepType())) {
                case STEP_TYPE_OPERATIONS_ACTION -> executeOperationsStep(definition, run, stepDefinition, requestPayload, startedAt);
                case STEP_TYPE_RECON_SUMMARY_SNAPSHOT -> executeSummarySnapshotStep(run, stepDefinition, tenant, requestPayload, startedAt);
                default -> new StepExecutionResult(
                        "FAILED",
                        "Unsupported step type: " + stepDefinition.getStepType(),
                        requestPayload,
                        null,
                        startedAt,
                        LocalDateTime.now()
                );
            };
        } catch (Exception ex) {
            return new StepExecutionResult(
                    "FAILED",
                    defaultIfBlank(ex.getMessage(), "Step execution failed"),
                    requestPayload,
                    Map.of("error", defaultIfBlank(ex.getMessage(), "Step execution failed")),
                    startedAt,
                    LocalDateTime.now()
            );
        }
    }

    private StepExecutionResult executeOperationsStep(ReconJobDefinition definition,
                                                      ReconJobRun run,
                                                      ReconJobStepDefinition stepDefinition,
                                                      Map<String, Object> requestPayload,
                                                      LocalDateTime startedAt) {
        OperationActionResponseDto response = operationsService.executeSafeAction(
                definition.getTenantId(),
                stepDefinition.getModuleId(),
                stepDefinition.getActionKey(),
                defaultIfBlank(run.getInitiatedBy(), "system-scheduler"),
                definition.getReconView()
        );
        String responseStatus = defaultIfBlank(response.getStatus(), "OK").toUpperCase(Locale.ROOT);
        boolean failed = responseStatus.contains("FAIL") || responseStatus.contains("ERROR");
        Map<String, Object> responsePayload = new LinkedHashMap<>();
        responsePayload.put("moduleId", response.getModuleId());
        responsePayload.put("actionKey", response.getActionKey());
        responsePayload.put("status", response.getStatus());
        responsePayload.put("message", response.getMessage());
        responsePayload.put("rawResponse", response.getRawResponse());
        return new StepExecutionResult(
                failed ? "FAILED" : "SUCCEEDED",
                defaultIfBlank(response.getMessage(), failed ? "Operations action failed" : "Operations action completed"),
                requestPayload,
                responsePayload,
                startedAt,
                LocalDateTime.now()
        );
    }

    private StepExecutionResult executeSummarySnapshotStep(ReconJobRun run,
                                                           ReconJobStepDefinition stepDefinition,
                                                           TenantConfig tenant,
                                                           Map<String, Object> requestPayload,
                                                           LocalDateTime startedAt) {
        DashboardStats stats = reconQueryService.getDashboardStats(
                readStringList(requestPayload.get("scopeStoreIds")),
                run.getWindowFromBusinessDate(),
                run.getWindowToBusinessDate(),
                run.getReconView(),
                tenant
        );
        Map<String, Object> responsePayload = new LinkedHashMap<>();
        responsePayload.put("totalTransactions", stats.getTotalTransactions());
        responsePayload.put("matched", stats.getMatched());
        responsePayload.put("itemMissing", stats.getItemMissing());
        responsePayload.put("quantityMismatch", stats.getQuantityMismatch());
        responsePayload.put("transactionTotalMismatch", stats.getTransactionTotalMismatch());
        responsePayload.put("processingPending", stats.getProcessingPending());
        responsePayload.put("processingFailed", stats.getProcessingFailed());
        responsePayload.put("matchRate", stats.getMatchRate());
        responsePayload.put("byStore", stats.getByStore());
        responsePayload.put("byStatus", stats.getByStatus());
        responsePayload.put("snapshotType", stepDefinition.getStepType());
        return new StepExecutionResult(
                "SUCCEEDED",
                "Captured reconciliation summary snapshot",
                requestPayload,
                responsePayload,
                startedAt,
                LocalDateTime.now()
        );
    }

    private void scheduleRetryIfNeeded(ReconJobDefinition definition,
                                       ReconJobRun run,
                                       boolean jobFailed,
                                       Map<String, Object> resultPayload) {
        if (!jobFailed || run.getAttemptNumber() == null || run.getAttemptNumber() > defaultInt(definition.getMaxRetryAttempts(), 0)) {
            run.setRetryPending(false);
            run.setNextRetryAt(null);
            return;
        }
        LocalDateTime scheduledRetryAt = LocalDateTime.now().plusMinutes(defaultInt(definition.getRetryDelayMinutes(), 15));
        run.setRetryPending(true);
        run.setNextRetryAt(scheduledRetryAt);
        retryEventRepository.save(ReconJobRetryEvent.builder()
                .tenantId(definition.getTenantId())
                .jobDefinitionId(definition.getId())
                .failedRunId(run.getId())
                .attemptNumber(run.getAttemptNumber() + 1)
                .scheduledFor(scheduledRetryAt)
                .retryStatus("PENDING")
                .build());
        resultPayload.put("retryPending", true);
        resultPayload.put("nextRetryAt", scheduledRetryAt.toString());
        resultPayload.put("remainingRetries", Math.max(0, defaultInt(definition.getMaxRetryAttempts(), 0) - run.getAttemptNumber() + 1));
    }

    private ReconJobRun createRun(ReconJobDefinition definition,
                                  String triggerType,
                                  String initiatedBy,
                                  LocalDateTime scheduledFor,
                                  int attemptNumber,
                                  UUID parentRunId,
                                  UUID rootRunId) {
        UUID runId = UUID.randomUUID();
        WindowContext windowContext = resolveWindowContext(definition, scheduledFor == null ? LocalDateTime.now() : scheduledFor);
        return runRepository.save(ReconJobRun.builder()
                .id(runId)
                .jobDefinitionId(definition.getId())
                .tenantId(definition.getTenantId())
                .jobName(definition.getJobName())
                .reconView(definition.getReconView())
                .triggerType(defaultIfBlank(triggerType, "MANUAL"))
                .runStatus("PENDING")
                .initiatedBy(trimToNull(initiatedBy))
                .parentRunId(parentRunId)
                .rootRunId(rootRunId == null ? runId : rootRunId)
                .attemptNumber(attemptNumber)
                .maxRetryAttempts(defaultInt(definition.getMaxRetryAttempts(), 0))
                .retryDelayMinutes(defaultInt(definition.getRetryDelayMinutes(), 15))
                .scheduledFor(scheduledFor)
                .businessDate(windowContext.businessDate())
                .windowFromBusinessDate(windowContext.windowFromBusinessDate())
                .windowToBusinessDate(windowContext.windowToBusinessDate())
                .build());
    }

    private void replaceSteps(UUID jobDefinitionId, List<SaveReconJobStepRequest> requests) {
        stepDefinitionRepository.deleteByJobDefinitionId(jobDefinitionId);
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<SaveReconJobStepRequest> normalizedRequests = requests.stream()
                .sorted(Comparator.comparing(request -> request.getStepOrder() == null ? Integer.MAX_VALUE : request.getStepOrder()))
                .toList();
        Map<String, UUID> idsByClientKey = new LinkedHashMap<>();
        List<ReconJobStepDefinition> definitions = new ArrayList<>();
        int fallbackStepOrder = 1;
        for (SaveReconJobStepRequest request : normalizedRequests) {
            UUID stepId = UUID.randomUUID();
            String clientStepKey = defaultIfBlank(request.getClientStepKey(), "step-" + fallbackStepOrder);
            idsByClientKey.put(clientStepKey, stepId);
            definitions.add(ReconJobStepDefinition.builder()
                    .id(stepId)
                    .jobDefinitionId(jobDefinitionId)
                    .stepOrder(request.getStepOrder() == null ? fallbackStepOrder : request.getStepOrder())
                    .stepLabel(defaultStepLabel(request))
                    .stepType(normalizeStepType(request.getStepType()))
                    .moduleId(trimToNull(request.getModuleId()))
                    .actionKey(trimToNull(request.getActionKey()))
                    .settleDelaySeconds(request.getSettleDelaySeconds() == null ? null : Math.max(0, request.getSettleDelaySeconds()))
                    .stepConfig(writeJson(stepRequestPayload(request)))
                    .build());
            fallbackStepOrder++;
        }
        for (int index = 0; index < definitions.size(); index++) {
            SaveReconJobStepRequest request = normalizedRequests.get(index);
            if (trimToNull(request.getDependsOnClientStepKey()) != null) {
                definitions.get(index).setDependsOnStepId(idsByClientKey.get(request.getDependsOnClientStepKey().trim()));
            }
        }
        stepDefinitionRepository.saveAll(definitions);
    }

    private boolean dependencySatisfied(ReconJobStepDefinition stepDefinition,
                                        Map<UUID, ReconJobStepRun> runsByStepId) {
        if (stepDefinition.getDependsOnStepId() == null) {
            return true;
        }
        ReconJobStepRun dependency = runsByStepId.get(stepDefinition.getDependsOnStepId());
        return dependency != null && "SUCCEEDED".equalsIgnoreCase(dependency.getRunStatus());
    }

    private ReconJobStepRun saveStepRun(ReconJobRun run,
                                        ReconJobStepDefinition stepDefinition,
                                        String runStatus,
                                        String message,
                                        Object requestPayload,
                                        Object responsePayload,
                                        LocalDateTime startedAt,
                                        LocalDateTime completedAt) {
        long durationMs = startedAt == null || completedAt == null
                ? 0L
                : Math.max(0L, Duration.between(startedAt, completedAt).toMillis());
        return stepRunRepository.save(ReconJobStepRun.builder()
                .jobRunId(run.getId())
                .stepDefinitionId(stepDefinition.getId())
                .tenantId(run.getTenantId())
                .stepOrder(stepDefinition.getStepOrder())
                .stepLabel(stepDefinition.getStepLabel())
                .stepType(stepDefinition.getStepType())
                .moduleId(stepDefinition.getModuleId())
                .actionKey(stepDefinition.getActionKey())
                .runStatus(defaultIfBlank(runStatus, "RECORDED"))
                .startedAt(startedAt)
                .completedAt(completedAt)
                .durationMs(durationMs)
                .message(message)
                .requestPayload(writeJson(requestPayload))
                .responsePayload(writeJson(responsePayload))
                .build());
    }

    private Map<UUID, List<ReconJobStepRun>> groupStepRuns(List<ReconJobRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return Map.of();
        }
        return stepRunRepository.findByJobRunIdInOrderByJobRunIdAscStepOrderAsc(
                        runs.stream().map(ReconJobRun::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ReconJobStepRun::getJobRunId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<UUID, List<ReconJobNotificationDelivery>> groupNotificationDeliveries(List<ReconJobRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return Map.of();
        }
        return notificationDeliveryRepository.findByJobRunIdInOrderByCreatedAtDesc(
                        runs.stream().map(ReconJobRun::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ReconJobNotificationDelivery::getJobRunId, LinkedHashMap::new, Collectors.toList()));
    }

    private ReconJobDefinitionDto toDefinitionDto(ReconJobDefinition definition,
                                                  TenantConfig tenant,
                                                  List<ReconJobStepDefinition> steps) {
        Map<UUID, String> labelsById = steps.stream()
                .collect(Collectors.toMap(ReconJobStepDefinition::getId, ReconJobStepDefinition::getStepLabel));
        return ReconJobDefinitionDto.builder()
                .id(definition.getId())
                .jobName(definition.getJobName())
                .reconView(definition.getReconView())
                .cronExpression(definition.getCronExpression())
                .jobTimezone(definition.getJobTimezone())
                .windowType(definition.getWindowType())
                .endOfDayLocalTime(definition.getEndOfDayLocalTime())
                .businessDateOffsetDays(definition.getBusinessDateOffsetDays())
                .maxRetryAttempts(definition.getMaxRetryAttempts())
                .retryDelayMinutes(definition.getRetryDelayMinutes())
                .allowConcurrentRuns(definition.isAllowConcurrentRuns())
                .enabled(definition.isEnabled())
                .notifyOnSuccess(definition.isNotifyOnSuccess())
                .notifyOnFailure(definition.isNotifyOnFailure())
                .notificationChannelType(definition.getNotificationChannelType())
                .notificationEndpoint(definition.getNotificationEndpoint())
                .notificationEmail(definition.getNotificationEmail())
                .scopeStoreIds(readStringList(definition.getScopeStoreIds()))
                .createdBy(definition.getCreatedBy())
                .updatedBy(definition.getUpdatedBy())
                .createdAt(toDisplay(definition.getCreatedAt(), tenant))
                .updatedAt(toDisplay(definition.getUpdatedAt(), tenant))
                .lastScheduledAt(toDisplay(definition.getLastScheduledAt(), tenant))
                .nextScheduledAt(toDisplay(definition.getNextScheduledAt(), tenant))
                .lastRunStartedAt(toDisplay(definition.getLastRunStartedAt(), tenant))
                .lastRunCompletedAt(toDisplay(definition.getLastRunCompletedAt(), tenant))
                .lastRunStatus(definition.getLastRunStatus())
                .lastRunMessage(definition.getLastRunMessage())
                .steps(steps.stream().map(step -> toStepDto(step, labelsById)).toList())
                .build();
    }

    private ReconJobRunDto toRunDto(ReconJobRun run,
                                    TenantConfig tenant,
                                    List<ReconJobStepRun> stepRuns,
                                    List<ReconJobNotificationDelivery> deliveries) {
        return ReconJobRunDto.builder()
                .id(run.getId())
                .jobDefinitionId(run.getJobDefinitionId())
                .parentRunId(run.getParentRunId())
                .rootRunId(run.getRootRunId())
                .jobName(run.getJobName())
                .reconView(run.getReconView())
                .triggerType(run.getTriggerType())
                .runStatus(run.getRunStatus())
                .initiatedBy(run.getInitiatedBy())
                .attemptNumber(run.getAttemptNumber())
                .maxRetryAttempts(run.getMaxRetryAttempts())
                .retryDelayMinutes(run.getRetryDelayMinutes())
                .retryPending(run.isRetryPending())
                .scheduledFor(toDisplay(run.getScheduledFor(), tenant))
                .startedAt(toDisplay(run.getStartedAt(), tenant))
                .completedAt(toDisplay(run.getCompletedAt(), tenant))
                .businessDate(run.getBusinessDate())
                .windowFromBusinessDate(run.getWindowFromBusinessDate())
                .windowToBusinessDate(run.getWindowToBusinessDate())
                .summary(run.getSummary())
                .resultPayload(readObject(run.getResultPayload()))
                .nextRetryAt(toDisplay(run.getNextRetryAt(), tenant))
                .stepRuns(stepRuns.stream().map(stepRun -> toStepRunDto(stepRun, tenant)).toList())
                .notifications(deliveries.stream().map(delivery -> toNotificationDto(delivery, tenant)).toList())
                .build();
    }

    private ReconJobStepDto toStepDto(ReconJobStepDefinition stepDefinition,
                                      Map<UUID, String> labelsById) {
        return ReconJobStepDto.builder()
                .id(stepDefinition.getId())
                .stepOrder(stepDefinition.getStepOrder())
                .stepLabel(stepDefinition.getStepLabel())
                .stepType(stepDefinition.getStepType())
                .moduleId(stepDefinition.getModuleId())
                .actionKey(stepDefinition.getActionKey())
                .dependsOnStepId(stepDefinition.getDependsOnStepId())
                .dependsOnStepLabel(labelsById.get(stepDefinition.getDependsOnStepId()))
                .settleDelaySeconds(stepDefinition.getSettleDelaySeconds())
                .build();
    }

    private ReconJobStepRunDto toStepRunDto(ReconJobStepRun stepRun,
                                            TenantConfig tenant) {
        return ReconJobStepRunDto.builder()
                .id(stepRun.getId())
                .stepDefinitionId(stepRun.getStepDefinitionId())
                .stepOrder(stepRun.getStepOrder())
                .stepLabel(stepRun.getStepLabel())
                .stepType(stepRun.getStepType())
                .moduleId(stepRun.getModuleId())
                .actionKey(stepRun.getActionKey())
                .runStatus(stepRun.getRunStatus())
                .startedAt(toDisplay(stepRun.getStartedAt(), tenant))
                .completedAt(toDisplay(stepRun.getCompletedAt(), tenant))
                .durationMs(stepRun.getDurationMs())
                .message(stepRun.getMessage())
                .requestPayload(readObject(stepRun.getRequestPayload()))
                .responsePayload(readObject(stepRun.getResponsePayload()))
                .build();
    }

    private ReconJobNotificationDeliveryDto toNotificationDto(ReconJobNotificationDelivery delivery,
                                                              TenantConfig tenant) {
        return ReconJobNotificationDeliveryDto.builder()
                .id(delivery.getId())
                .channelType(delivery.getChannelType())
                .destination(delivery.getDestination())
                .deliveryStatus(delivery.getDeliveryStatus())
                .responseCode(delivery.getResponseCode())
                .errorMessage(delivery.getErrorMessage())
                .payload(readObject(delivery.getPayloadJson()))
                .createdAt(toDisplay(delivery.getCreatedAt(), tenant))
                .build();
    }

    private List<ReconJobTemplateDto> recommendedTemplates(Collection<String> allowedReconViews) {
        Set<String> normalizedAllowedReconViews = normalizeAllowedReconViews(allowedReconViews);
        return List.of(
                template(
                        "XSTORE_SIM_EOD",
                        "Xstore vs SIM EOD",
                        "Publish new Xstore records, poll SIM, then capture the reconciliation summary.",
                        "XSTORE_SIM",
                        "0 30 23 * * *",
                        "END_OF_DAY",
                        "23:00",
                        0,
                        List.of(
                                templateStep(1, "Publish Xstore staged records", STEP_TYPE_OPERATIONS_ACTION, "xstore-publisher", "publish", null, 0),
                                templateStep(2, "Poll SIM transactions", STEP_TYPE_OPERATIONS_ACTION, "sim-poller", "poll", 1, 60),
                                templateStep(3, "Capture reconciliation summary", STEP_TYPE_RECON_SUMMARY_SNAPSHOT, null, null, 2, 30)
                        )
                ),
                template(
                        "XSTORE_SIOCS_EOD",
                        "Xstore vs SIOCS EOD",
                        "Download SIOCS data, publish staged files, then take an end-of-day snapshot.",
                        "XSTORE_SIOCS",
                        "0 45 23 * * *",
                        "END_OF_DAY",
                        "23:30",
                        0,
                        List.of(
                                templateStep(1, "Download SIOCS cloud data", STEP_TYPE_OPERATIONS_ACTION, "siocs-cloud-connector", "download", null, 0),
                                templateStep(2, "Publish SIOCS staged records", STEP_TYPE_OPERATIONS_ACTION, "siocs-cloud-connector", "publish", 1, 90),
                                templateStep(3, "Capture reconciliation summary", STEP_TYPE_RECON_SUMMARY_SNAPSHOT, null, null, 2, 30)
                        )
                ),
                template(
                        "XSTORE_XOCS_EOD",
                        "Xstore vs XOCS EOD",
                        "Download XOCS data, publish staged files, then capture the reconciliation summary.",
                        "XSTORE_XOCS",
                        "0 50 23 * * *",
                        "END_OF_DAY",
                        "23:30",
                        0,
                        List.of(
                                templateStep(1, "Download XOCS cloud data", STEP_TYPE_OPERATIONS_ACTION, "xocs-cloud-connector", "download", null, 0),
                                templateStep(2, "Publish XOCS staged records", STEP_TYPE_OPERATIONS_ACTION, "xocs-cloud-connector", "publish", 1, 90),
                                templateStep(3, "Capture reconciliation summary", STEP_TYPE_RECON_SUMMARY_SNAPSHOT, null, null, 2, 30)
                        )
                ),
                template(
                        "SIOCS_MFCS_EOD",
                        "SIOCS vs MFCS EOD",
                        "Download SIOCS and MFCS inventory transactions, publish both staged feeds, then capture the reconciliation summary.",
                        "SIOCS_MFCS",
                        "0 55 23 * * *",
                        "END_OF_DAY",
                        "23:45",
                        0,
                        List.of(
                                templateStep(1, "Download SIOCS cloud data", STEP_TYPE_OPERATIONS_ACTION, "siocs-cloud-connector", "download", null, 0),
                                templateStep(2, "Publish SIOCS staged records", STEP_TYPE_OPERATIONS_ACTION, "siocs-cloud-connector", "publish", 1, 90),
                                templateStep(3, "Download MFCS RDS data", STEP_TYPE_OPERATIONS_ACTION, "mfcs-rds-connector", "download", 2, 0),
                                templateStep(4, "Publish MFCS staged records", STEP_TYPE_OPERATIONS_ACTION, "mfcs-rds-connector", "publish", 3, 90),
                                templateStep(5, "Capture reconciliation summary", STEP_TYPE_RECON_SUMMARY_SNAPSHOT, null, null, 4, 30)
                        )
                ),
                  template(
                          "SIM_RMS_EOD",
                          "SIM vs RMS EOD",
                          "Poll SIM and RMS on-prem inventory transactions, then capture the reconciliation summary.",
                          "SIM_RMS",
                        "0 0 0 * * *",
                        "END_OF_DAY",
                        "23:55",
                        0,
                        List.of(
                                templateStep(1, "Poll SIM inventory transactions", STEP_TYPE_OPERATIONS_ACTION, "sim-db-connector", "poll", null, 0),
                                  templateStep(2, "Poll RMS inventory transactions", STEP_TYPE_OPERATIONS_ACTION, "rms-db-connector", "poll", 1, 60),
                                  templateStep(3, "Capture reconciliation summary", STEP_TYPE_RECON_SUMMARY_SNAPSHOT, null, null, 2, 30)
                          )
                  ),
                  template(
                          "SIM_MFCS_EOD",
                          "SIM vs MFCS EOD",
                          "Poll SIM inventory transactions, refresh MFCS staged data, then capture the reconciliation summary.",
                          "SIM_MFCS",
                          "0 5 0 * * *",
                          "END_OF_DAY",
                          "23:55",
                          0,
                          List.of(
                                  templateStep(1, "Poll SIM inventory transactions", STEP_TYPE_OPERATIONS_ACTION, "sim-db-connector", "poll", null, 0),
                                  templateStep(2, "Download MFCS RDS data", STEP_TYPE_OPERATIONS_ACTION, "mfcs-rds-connector", "download", 1, 0),
                                  templateStep(3, "Publish MFCS staged records", STEP_TYPE_OPERATIONS_ACTION, "mfcs-rds-connector", "publish", 2, 90),
                                  templateStep(4, "Capture reconciliation summary", STEP_TYPE_RECON_SUMMARY_SNAPSHOT, null, null, 3, 30)
                          )
                  ),
                  template(
                          "SIOCS_RMS_EOD",
                          "SIOCS vs RMS EOD",
                          "Download SIOCS inventory data, poll RMS, then capture the reconciliation summary.",
                          "SIOCS_RMS",
                          "0 10 0 * * *",
                          "END_OF_DAY",
                          "23:55",
                          0,
                          List.of(
                                  templateStep(1, "Download SIOCS cloud data", STEP_TYPE_OPERATIONS_ACTION, "siocs-cloud-connector", "download", null, 0),
                                  templateStep(2, "Publish SIOCS staged records", STEP_TYPE_OPERATIONS_ACTION, "siocs-cloud-connector", "publish", 1, 90),
                                  templateStep(3, "Poll RMS inventory transactions", STEP_TYPE_OPERATIONS_ACTION, "rms-db-connector", "poll", 2, 60),
                                  templateStep(4, "Capture reconciliation summary", STEP_TYPE_RECON_SUMMARY_SNAPSHOT, null, null, 3, 30)
                          )
                  )
          ).stream()
                .filter(template -> normalizedAllowedReconViews.contains(normalizeReconView(template.getReconView())))
                .toList();
    }

    private ReconJobTemplateDto template(String key,
                                         String name,
                                         String description,
                                         String reconView,
                                         String cronExpression,
                                         String windowType,
                                         String endOfDayLocalTime,
                                         Integer businessDateOffsetDays,
                                         List<ReconJobStepDto> steps) {
        return ReconJobTemplateDto.builder()
                .templateKey(key)
                .templateName(name)
                .description(description)
                .reconView(reconView)
                .cronExpression(cronExpression)
                .windowType(windowType)
                .endOfDayLocalTime(endOfDayLocalTime)
                .businessDateOffsetDays(businessDateOffsetDays)
                .steps(steps)
                .build();
    }

    private ReconJobStepDto templateStep(int order,
                                         String label,
                                         String stepType,
                                         String moduleId,
                                         String actionKey,
                                         Integer dependsOnStepOrder,
                                         Integer settleDelaySeconds) {
        UUID dependencyId = dependsOnStepOrder == null ? null : UUID.nameUUIDFromBytes(("step-" + dependsOnStepOrder).getBytes());
        return ReconJobStepDto.builder()
                .id(UUID.nameUUIDFromBytes(("step-" + order).getBytes()))
                .stepOrder(order)
                .stepLabel(label)
                .stepType(stepType)
                .moduleId(moduleId)
                .actionKey(actionKey)
                .dependsOnStepId(dependencyId)
                .settleDelaySeconds(settleDelaySeconds)
                .build();
    }

    private void validateSaveRequest(SaveReconJobDefinitionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Job definition is required");
        }
        if (trimToNull(request.getJobName()) == null) {
            throw new IllegalArgumentException("Job name is required");
        }
        String normalizedReconView = normalizeReconView(request.getReconView());
        validateCron(request.getCronExpression());
        validateTimezone(request.getJobTimezone());
        String windowType = normalizeWindowType(request.getWindowType());
        if ("END_OF_DAY".equals(windowType) && normalizeTime(request.getEndOfDayLocalTime()) == null) {
            throw new IllegalArgumentException("End-of-day time is required when window type is END_OF_DAY");
        }
        if (request.getSteps() == null || request.getSteps().isEmpty()) {
            throw new IllegalArgumentException("At least one job step is required");
        }
        Set<String> clientStepKeys = new LinkedHashSet<>();
        for (SaveReconJobStepRequest step : request.getSteps()) {
            if (step == null) {
                throw new IllegalArgumentException("Job steps cannot be empty");
            }
            normalizeStepType(step.getStepType());
            if (STEP_TYPE_OPERATIONS_ACTION.equalsIgnoreCase(step.getStepType())) {
                String moduleId = trimToNull(step.getModuleId());
                String actionKey = trimToNull(step.getActionKey());
                if (moduleId == null || actionKey == null) {
                    throw new IllegalArgumentException("Operations action steps require module and action");
                }
                var operationModule = reconModuleService.findOperationModule(moduleId, normalizedReconView)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Module %s is not available for reconciliation view %s".formatted(moduleId, normalizedReconView)
                        ));
                boolean supportedAction = operationModule.getSafeActions() != null
                        && operationModule.getSafeActions().stream()
                        .anyMatch(candidate -> candidate.equalsIgnoreCase(actionKey));
                if (!supportedAction) {
                    throw new IllegalArgumentException(
                            "Action %s is not supported for module %s in reconciliation view %s"
                                    .formatted(actionKey, moduleId, normalizedReconView)
                    );
                }
            }
            String clientStepKey = defaultIfBlank(step.getClientStepKey(), "step-" + defaultInt(step.getStepOrder(), clientStepKeys.size() + 1));
            if (!clientStepKeys.add(clientStepKey)) {
                throw new IllegalArgumentException("Duplicate step key: " + clientStepKey);
            }
        }
        for (SaveReconJobStepRequest step : request.getSteps()) {
            String dependsOn = trimToNull(step.getDependsOnClientStepKey());
            if (dependsOn != null && !clientStepKeys.contains(dependsOn)) {
                throw new IllegalArgumentException("Unknown dependency step: " + dependsOn);
            }
        }
    }

    private ReconJobDefinition loadDefinition(String tenantId, UUID jobDefinitionId) {
        return definitionRepository.findById(jobDefinitionId)
                .filter(definition -> tenantId.equalsIgnoreCase(definition.getTenantId()))
                .orElseThrow(() -> new EntityNotFoundException("Reconciliation job not found"));
    }

    private String normalizeReconView(String reconView) {
        String normalized = defaultIfBlank(reconView, "").toUpperCase(Locale.ROOT);
        if (!reconModuleService.isValidReconView(normalized)) {
            throw new IllegalArgumentException("Unsupported reconciliation view: " + reconView);
        }
        return normalized;
    }

    private Set<String> normalizeAllowedReconViews(Collection<String> allowedReconViews) {
        if (allowedReconViews == null) {
            return Set.of();
        }
        return allowedReconViews.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void requireAllowedReconView(String reconView, Set<String> allowedReconViews) {
        if (!allowedReconViews.contains(normalizeReconView(reconView))) {
            throw new IllegalArgumentException("Missing access to reconciliation view: " + reconView);
        }
    }

    private String normalizeWindowType(String windowType) {
        String normalized = defaultIfBlank(windowType, "CONTINUOUS").toUpperCase(Locale.ROOT);
        if (!Set.of("CONTINUOUS", "END_OF_DAY").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported window type: " + windowType);
        }
        return normalized;
    }

    private String normalizeStepType(String stepType) {
        String normalized = defaultIfBlank(stepType, "").toUpperCase(Locale.ROOT);
        if (!Set.of(STEP_TYPE_OPERATIONS_ACTION, STEP_TYPE_RECON_SUMMARY_SNAPSHOT).contains(normalized)) {
            throw new IllegalArgumentException("Unsupported step type: " + stepType);
        }
        return normalized;
    }

    private String validateCron(String cronExpression) {
        String normalized = defaultIfBlank(cronExpression, null);
        if (normalized == null) {
            throw new IllegalArgumentException("Cron expression is required");
        }
        String[] parts = normalized.trim().split("\\s+");
        if (parts.length == 5) {
            normalized = "0 " + normalized;
        }
        try {
            CronExpression.parse(normalized);
            return normalized;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid cron expression: " + cronExpression);
        }
    }

    private String validateTimezone(String timezone) {
        String normalized = defaultIfBlank(timezone, "UTC");
        try {
            return ZoneId.of(normalized).getId();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid timezone: " + timezone);
        }
    }

    private String normalizeTime(String timeValue) {
        String normalized = trimToNull(timeValue);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalTime.parse(normalized).toString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid time: " + timeValue);
        }
    }

    private UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(defaultIfBlank(value, "recon-job").getBytes(StandardCharsets.UTF_8));
    }

    private LocalDateTime nextExecution(String cronExpression,
                                        String timezone,
                                        LocalDateTime referenceTime) {
        CronExpression cron = CronExpression.parse(validateCron(cronExpression));
        ZoneId zoneId = ZoneId.of(validateTimezone(timezone));
        ZonedDateTime reference = (referenceTime == null ? LocalDateTime.now() : referenceTime).atZone(ZoneId.systemDefault())
                .withZoneSameInstant(zoneId);
        ZonedDateTime next = cron.next(reference);
        return next == null ? null : next.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private WindowContext resolveWindowContext(ReconJobDefinition definition,
                                               LocalDateTime scheduledFor) {
        if (!"END_OF_DAY".equalsIgnoreCase(definition.getWindowType())) {
            return new WindowContext(null, null, null);
        }
        ZoneId zoneId = ZoneId.of(validateTimezone(definition.getJobTimezone()));
        LocalDateTime effectiveTime = scheduledFor == null ? LocalDateTime.now() : scheduledFor;
        ZonedDateTime zonedDateTime = effectiveTime.atZone(ZoneId.systemDefault()).withZoneSameInstant(zoneId);
        LocalDate localDate = zonedDateTime.toLocalDate();
        LocalTime boundaryTime = LocalTime.parse(defaultIfBlank(definition.getEndOfDayLocalTime(), "23:00"));
        if (zonedDateTime.toLocalTime().isBefore(boundaryTime)) {
            localDate = localDate.minusDays(1);
        }
        LocalDate businessDate = localDate.minusDays(defaultInt(definition.getBusinessDateOffsetDays(), 0));
        String businessDateValue = businessDate.toString();
        return new WindowContext(businessDateValue, businessDateValue, businessDateValue);
    }

    private Map<String, Object> stepRequestPayload(ReconJobDefinition definition,
                                                   ReconJobRun run,
                                                   ReconJobStepDefinition stepDefinition) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jobDefinitionId", definition.getId());
        payload.put("jobRunId", run.getId());
        payload.put("jobName", definition.getJobName());
        payload.put("reconView", definition.getReconView());
        payload.put("triggerType", run.getTriggerType());
        payload.put("businessDate", run.getBusinessDate());
        payload.put("windowFromBusinessDate", run.getWindowFromBusinessDate());
        payload.put("windowToBusinessDate", run.getWindowToBusinessDate());
        payload.put("scopeStoreIds", readStringList(definition.getScopeStoreIds()));
        payload.put("stepType", stepDefinition.getStepType());
        payload.put("moduleId", stepDefinition.getModuleId());
        payload.put("actionKey", stepDefinition.getActionKey());
        return payload;
    }

    private Map<String, Object> stepRequestPayload(SaveReconJobStepRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientStepKey", defaultIfBlank(request.getClientStepKey(), null));
        payload.put("stepLabel", defaultStepLabel(request));
        payload.put("stepType", normalizeStepType(request.getStepType()));
        payload.put("moduleId", trimToNull(request.getModuleId()));
        payload.put("actionKey", trimToNull(request.getActionKey()));
        payload.put("dependsOnClientStepKey", trimToNull(request.getDependsOnClientStepKey()));
        payload.put("settleDelaySeconds", request.getSettleDelaySeconds() == null ? null : Math.max(0, request.getSettleDelaySeconds()));
        return payload;
    }

    private List<String> normalizeStoreScope(List<String> scopeStoreIds) {
        if (scopeStoreIds == null) {
            return List.of();
        }
        return scopeStoreIds.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<String, Object> buildRunSummary(List<ReconJobStepRun> stepRuns) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalSteps", stepRuns.size());
        summary.put("succeededSteps", stepRuns.stream().filter(step -> "SUCCEEDED".equalsIgnoreCase(step.getRunStatus())).count());
        summary.put("failedSteps", stepRuns.stream().filter(step -> "FAILED".equalsIgnoreCase(step.getRunStatus())).count());
        summary.put("blockedSteps", stepRuns.stream().filter(step -> "BLOCKED".equalsIgnoreCase(step.getRunStatus())).count());
        summary.put("averageStepDurationMs", stepRuns.isEmpty()
                ? 0L
                : Math.round(stepRuns.stream().mapToLong(step -> step.getDurationMs() == null ? 0L : step.getDurationMs()).average().orElse(0D)));
        return summary;
    }

    private Object readObject(String json) {
        String normalized = trimToNull(json);
        if (normalized == null) {
            return null;
        }
        try {
            return objectMapper.readValue(normalized, Object.class);
        } catch (Exception ex) {
            return normalized;
        }
    }

    private List<String> readStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> trimToNull(Objects.toString(item, null)))
                    .filter(Objects::nonNull)
                    .toList();
        }
        String json = trimToNull(Objects.toString(value, null));
        if (json == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            }).stream()
                    .map(this::trimToNull)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            return List.of(json);
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return Objects.toString(value, null);
        }
    }

    private Map<String, Object> snapshotDefinition(ReconJobDefinition definition) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", definition.getId());
        snapshot.put("jobName", definition.getJobName());
        snapshot.put("reconView", definition.getReconView());
        snapshot.put("cronExpression", definition.getCronExpression());
        snapshot.put("jobTimezone", definition.getJobTimezone());
        snapshot.put("windowType", definition.getWindowType());
        snapshot.put("endOfDayLocalTime", definition.getEndOfDayLocalTime());
        snapshot.put("businessDateOffsetDays", definition.getBusinessDateOffsetDays());
        snapshot.put("maxRetryAttempts", definition.getMaxRetryAttempts());
        snapshot.put("retryDelayMinutes", definition.getRetryDelayMinutes());
        snapshot.put("allowConcurrentRuns", definition.isAllowConcurrentRuns());
        snapshot.put("enabled", definition.isEnabled());
        snapshot.put("scopeStoreIds", readStringList(definition.getScopeStoreIds()));
        snapshot.put("notificationChannelType", definition.getNotificationChannelType());
        snapshot.put("notificationEndpoint", definition.getNotificationEndpoint());
        snapshot.put("notificationEmail", definition.getNotificationEmail());
        snapshot.put("notifyOnSuccess", definition.isNotifyOnSuccess());
        snapshot.put("notifyOnFailure", definition.isNotifyOnFailure());
        snapshot.put("lastRunStatus", definition.getLastRunStatus());
        snapshot.put("lastRunMessage", definition.getLastRunMessage());
        return snapshot;
    }

    private Map<String, Object> snapshotRun(ReconJobRun run) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", run.getId());
        snapshot.put("jobDefinitionId", run.getJobDefinitionId());
        snapshot.put("jobName", run.getJobName());
        snapshot.put("reconView", run.getReconView());
        snapshot.put("triggerType", run.getTriggerType());
        snapshot.put("runStatus", run.getRunStatus());
        snapshot.put("initiatedBy", run.getInitiatedBy());
        snapshot.put("attemptNumber", run.getAttemptNumber());
        snapshot.put("maxRetryAttempts", run.getMaxRetryAttempts());
        snapshot.put("retryDelayMinutes", run.getRetryDelayMinutes());
        snapshot.put("retryPending", run.isRetryPending());
        snapshot.put("scheduledFor", valueOrNull(run.getScheduledFor()));
        snapshot.put("startedAt", valueOrNull(run.getStartedAt()));
        snapshot.put("completedAt", valueOrNull(run.getCompletedAt()));
        snapshot.put("businessDate", run.getBusinessDate());
        snapshot.put("windowFromBusinessDate", run.getWindowFromBusinessDate());
        snapshot.put("windowToBusinessDate", run.getWindowToBusinessDate());
        snapshot.put("summary", run.getSummary());
        snapshot.put("nextRetryAt", valueOrNull(run.getNextRetryAt()));
        return snapshot;
    }

    private String defaultStepLabel(SaveReconJobStepRequest request) {
        String explicitLabel = trimToNull(request.getStepLabel());
        if (explicitLabel != null) {
            return explicitLabel;
        }
        if (STEP_TYPE_OPERATIONS_ACTION.equalsIgnoreCase(request.getStepType())) {
            return "%s %s".formatted(toDisplay(request.getModuleId()), toDisplay(request.getActionKey())).trim();
        }
        return toDisplay(request.getStepType());
    }

    private String toDisplay(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "Step";
        }
        return normalized.replace('-', ' ')
                .replace('_', ' ')
                .trim();
    }

    private String toDisplay(LocalDateTime value, TenantConfig tenant) {
        return value == null ? null : TimezoneConverter.toDisplay(value.toString(), tenant);
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String valueOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private record StepExecutionResult(String runStatus,
                                       String message,
                                       Object requestPayload,
                                       Object responsePayload,
                                       LocalDateTime startedAt,
                                       LocalDateTime completedAt) {
    }

    private record WindowContext(String businessDate,
                                 String windowFromBusinessDate,
                                 String windowToBusinessDate) {
    }
}
