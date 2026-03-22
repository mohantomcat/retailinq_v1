package com.recon.api.service;

import com.recon.api.domain.ExceptionAutomationCenterResponse;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionPlaybook;
import com.recon.api.domain.ExceptionPlaybookDto;
import com.recon.api.domain.ExceptionPlaybookStep;
import com.recon.api.domain.ExceptionPlaybookStepDto;
import com.recon.api.domain.ExceptionRoutingRule;
import com.recon.api.domain.ExceptionRoutingRuleDto;
import com.recon.api.domain.SaveExceptionPlaybookRequest;
import com.recon.api.domain.SaveExceptionPlaybookStepRequest;
import com.recon.api.domain.SaveExceptionRoutingRuleRequest;
import com.recon.api.repository.ExceptionPlaybookRepository;
import com.recon.api.repository.ExceptionRoutingRuleRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExceptionAutomationService {

    private final ExceptionRoutingRuleRepository routingRuleRepository;
    private final ExceptionPlaybookRepository playbookRepository;
    private final TenantService tenantService;
    private final OperationsService operationsService;
    private final ExceptionNoiseSuppressionService exceptionNoiseSuppressionService;

    @Transactional(readOnly = true)
    public ExceptionAutomationCenterResponse getAutomationCenter(String tenantId, String reconView) {
        String normalizedReconView = normalizeNullable(reconView);
        return ExceptionAutomationCenterResponse.builder()
                .routingRules(routingRuleRepository.findForAutomationCenter(tenantId, normalizedReconView).stream()
                        .map(this::toRoutingRuleDto)
                        .toList())
                .playbooks(playbookRepository.findForAutomationCenter(tenantId, normalizedReconView).stream()
                        .map(this::toPlaybookDto)
                        .toList())
                .suppressionRules(exceptionNoiseSuppressionService.getRules(tenantId, normalizedReconView))
                .suppressionSummary(exceptionNoiseSuppressionService.getSummary(tenantId))
                .recentSuppressionActivity(exceptionNoiseSuppressionService.getRecentActivity(tenantId))
                .build();
    }

    @Transactional
    public ExceptionRoutingRuleDto saveRoutingRule(String tenantId,
                                                   UUID ruleId,
                                                   SaveExceptionRoutingRuleRequest request,
                                                   String actorUsername) {
        String ruleName = trimToNull(request.getRuleName());
        if (ruleName == null) {
            throw new IllegalArgumentException("ruleName is required");
        }
        if (isBlank(request.getReconView())) {
            throw new IllegalArgumentException("reconView is required");
        }
        if (isBlank(request.getTargetAssigneeUsername()) && isBlank(request.getTargetRoleName())) {
            throw new IllegalArgumentException("Either target assignee or target role is required");
        }

        ExceptionRoutingRule rule = ruleId == null
                ? ExceptionRoutingRule.builder()
                .tenantId(tenantId)
                .createdBy(actorUsername)
                .build()
                : routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Routing rule not found"));

        rule.setRuleName(ruleName);
        rule.setReconView(normalizeRequired(request.getReconView(), "reconView"));
        rule.setReconStatus(normalizeNullable(request.getReconStatus()));
        rule.setMinSeverity(normalizeRequired(defaultIfBlank(request.getMinSeverity(), "MEDIUM"), "minSeverity"));
        rule.setRootCauseCategory(normalizeNullable(request.getRootCauseCategory()));
        rule.setReasonCode(normalizeNullable(request.getReasonCode()));
        rule.setStoreId(trimToNull(request.getStoreId()));
        rule.setTargetAssigneeUsername(trimToNull(request.getTargetAssigneeUsername()));
        rule.setTargetRoleName(trimToNull(request.getTargetRoleName()));
        rule.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        rule.setActive(request.isActive());
        rule.setDescription(trimToNull(request.getDescription()));
        rule.setUpdatedBy(actorUsername);

        return toRoutingRuleDto(routingRuleRepository.save(rule));
    }

    @Transactional
    public void deleteRoutingRule(String tenantId, UUID ruleId) {
        ExceptionRoutingRule rule = routingRuleRepository.findByIdAndTenantId(ruleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Routing rule not found"));
        routingRuleRepository.delete(rule);
    }

    @Transactional
    public ExceptionPlaybookDto savePlaybook(String tenantId,
                                             UUID playbookId,
                                             SaveExceptionPlaybookRequest request,
                                             String actorUsername) {
        String playbookName = trimToNull(request.getPlaybookName());
        if (playbookName == null) {
            throw new IllegalArgumentException("playbookName is required");
        }
        if (isBlank(request.getReconView())) {
            throw new IllegalArgumentException("reconView is required");
        }
        if (request.getSteps() == null || request.getSteps().isEmpty()) {
            throw new IllegalArgumentException("At least one playbook step is required");
        }

        ExceptionPlaybook playbook = playbookId == null
                ? ExceptionPlaybook.builder()
                .tenantId(tenantId)
                .createdBy(actorUsername)
                .build()
                : playbookRepository.findByIdAndTenantId(playbookId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Playbook not found"));

        playbook.setPlaybookName(playbookName);
        playbook.setReconView(normalizeRequired(request.getReconView(), "reconView"));
        playbook.setReconStatus(normalizeNullable(request.getReconStatus()));
        playbook.setMinSeverity(normalizeRequired(defaultIfBlank(request.getMinSeverity(), "MEDIUM"), "minSeverity"));
        playbook.setRootCauseCategory(normalizeNullable(request.getRootCauseCategory()));
        playbook.setReasonCode(normalizeNullable(request.getReasonCode()));
        playbook.setActive(request.isActive());
        playbook.setDescription(trimToNull(request.getDescription()));
        playbook.setUpdatedBy(actorUsername);
        replaceSteps(playbook, request.getSteps());

        return toPlaybookDto(playbookRepository.save(playbook));
    }

    @Transactional
    public void deletePlaybook(String tenantId, UUID playbookId) {
        ExceptionPlaybook playbook = playbookRepository.findByIdAndTenantId(playbookId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Playbook not found"));
        playbookRepository.delete(playbook);
    }

    @Transactional(readOnly = true)
    public Optional<ExceptionRoutingRule> findMatchingRoutingRule(ExceptionCase exceptionCase) {
        if (exceptionCase == null || isBlank(exceptionCase.getTenantId()) || isBlank(exceptionCase.getReconView())) {
            return Optional.empty();
        }
        return routingRuleRepository.findActiveRules(exceptionCase.getTenantId(), exceptionCase.getReconView()).stream()
                .filter(rule -> matchesRule(rule, exceptionCase))
                .sorted(Comparator
                        .comparingInt(ExceptionRoutingRule::getPriority)
                        .thenComparingInt(this::routingSpecificity).reversed()
                        .thenComparing(ExceptionRoutingRule::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<ExceptionPlaybook> findMatchingPlaybook(ExceptionCase exceptionCase) {
        if (exceptionCase == null || isBlank(exceptionCase.getTenantId()) || isBlank(exceptionCase.getReconView())) {
            return Optional.empty();
        }
        return playbookRepository.findActivePlaybooks(exceptionCase.getTenantId(), exceptionCase.getReconView()).stream()
                .filter(playbook -> matchesPlaybook(playbook, exceptionCase))
                .sorted(Comparator
                        .comparingInt(this::playbookSpecificity).reversed()
                        .thenComparing(ExceptionPlaybook::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }

    public AutomationResolution applyAutomation(ExceptionCase exceptionCase, boolean manualAssignmentRequested) {
        ExceptionRoutingRule routingRule = findMatchingRoutingRule(exceptionCase).orElse(null);
        ExceptionPlaybook playbook = findMatchingPlaybook(exceptionCase).orElse(null);

        if (!manualAssignmentRequested && routingRule != null) {
            exceptionCase.setAssigneeUsername(trimToNull(routingRule.getTargetAssigneeUsername()));
            exceptionCase.setAssignedRoleName(trimToNull(routingRule.getTargetRoleName()));
            exceptionCase.setAutoAssigned(true);
            exceptionCase.setRoutingRuleId(routingRule.getId());
            exceptionCase.setRoutingRuleName(routingRule.getRuleName());
        } else if (manualAssignmentRequested) {
            exceptionCase.setAutoAssigned(false);
            exceptionCase.setRoutingRuleId(null);
            exceptionCase.setRoutingRuleName(null);
        } else {
            exceptionCase.setAutoAssigned(false);
            exceptionCase.setRoutingRuleId(null);
            exceptionCase.setRoutingRuleName(null);
        }

        if (playbook != null) {
            exceptionCase.setPlaybookId(playbook.getId());
            exceptionCase.setPlaybookName(playbook.getPlaybookName());
        } else {
            exceptionCase.setPlaybookId(null);
            exceptionCase.setPlaybookName(null);
        }

        return new AutomationResolution(routingRule, playbook);
    }

    @Transactional(readOnly = true)
    public ExceptionPlaybookDto toPlaybookDtoById(String tenantId, UUID playbookId) {
        if (playbookId == null) {
            return null;
        }
        return playbookRepository.findByIdAndTenantId(playbookId, tenantId)
                .map(this::toPlaybookDto)
                .orElse(null);
    }

    public ExceptionRoutingRuleDto toRoutingRuleDto(ExceptionRoutingRule rule) {
        return ExceptionRoutingRuleDto.builder()
                .id(rule.getId())
                .ruleName(rule.getRuleName())
                .reconView(rule.getReconView())
                .reconStatus(rule.getReconStatus())
                .minSeverity(rule.getMinSeverity())
                .rootCauseCategory(rule.getRootCauseCategory())
                .reasonCode(rule.getReasonCode())
                .storeId(rule.getStoreId())
                .targetAssigneeUsername(rule.getTargetAssigneeUsername())
                .targetRoleName(rule.getTargetRoleName())
                .priority(rule.getPriority())
                .active(rule.isActive())
                .description(rule.getDescription())
                .createdBy(rule.getCreatedBy())
                .updatedBy(rule.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(valueOrNull(rule.getCreatedAt()), tenantService.getTenant(rule.getTenantId())))
                .updatedAt(TimezoneConverter.toDisplay(valueOrNull(rule.getUpdatedAt()), tenantService.getTenant(rule.getTenantId())))
                .build();
    }

    public ExceptionPlaybookDto toPlaybookDto(ExceptionPlaybook playbook) {
        return ExceptionPlaybookDto.builder()
                .id(playbook.getId())
                .playbookName(playbook.getPlaybookName())
                .reconView(playbook.getReconView())
                .reconStatus(playbook.getReconStatus())
                .minSeverity(playbook.getMinSeverity())
                .rootCauseCategory(playbook.getRootCauseCategory())
                .reasonCode(playbook.getReasonCode())
                .active(playbook.isActive())
                .description(playbook.getDescription())
                .createdBy(playbook.getCreatedBy())
                .updatedBy(playbook.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(valueOrNull(playbook.getCreatedAt()), tenantService.getTenant(playbook.getTenantId())))
                .updatedAt(TimezoneConverter.toDisplay(valueOrNull(playbook.getUpdatedAt()), tenantService.getTenant(playbook.getTenantId())))
                .steps(playbook.getSteps().stream().sorted(Comparator.comparing(ExceptionPlaybookStep::getStepOrder))
                        .map(this::toPlaybookStepDto)
                        .toList())
                .build();
    }

    public ExceptionPlaybookStepDto toPlaybookStepDto(ExceptionPlaybookStep step) {
        OperationsService.ActionSupportDescriptor actionSupport = operationsService.describeAction(
                step.getOperationModuleId(),
                step.getOperationActionKey()
        );
        return ExceptionPlaybookStepDto.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .stepTitle(step.getStepTitle())
                .stepDetail(step.getStepDetail())
                .operationModuleId(step.getOperationModuleId())
                .operationActionKey(step.getOperationActionKey())
                .actionConfigured(actionSupport.actionConfigured())
                .actionExecutable(actionSupport.actionExecutable())
                .actionExecutionMode(actionSupport.actionExecutionMode())
                .actionSupportMessage(actionSupport.actionSupportMessage())
                .build();
    }

    private void replaceSteps(ExceptionPlaybook playbook, List<SaveExceptionPlaybookStepRequest> stepRequests) {
        List<ExceptionPlaybookStep> nextSteps = new ArrayList<>();
        int index = 1;
        for (SaveExceptionPlaybookStepRequest request : stepRequests) {
            if (request == null || isBlank(request.getStepTitle())) {
                continue;
            }
            nextSteps.add(ExceptionPlaybookStep.builder()
                    .id(request.getId())
                    .playbook(playbook)
                    .stepOrder(request.getStepOrder() != null ? request.getStepOrder() : index)
                    .stepTitle(trimToNull(request.getStepTitle()))
                    .stepDetail(trimToNull(request.getStepDetail()))
                    .operationModuleId(trimToNull(request.getOperationModuleId()))
                    .operationActionKey(trimToNull(request.getOperationActionKey()))
                    .build());
            index++;
        }
        if (nextSteps.isEmpty()) {
            throw new IllegalArgumentException("At least one valid playbook step is required");
        }
        playbook.getSteps().clear();
        playbook.getSteps().addAll(nextSteps);
    }

    private boolean matchesRule(ExceptionRoutingRule rule, ExceptionCase exceptionCase) {
        return severityRank(exceptionCase.getSeverity()) >= severityRank(rule.getMinSeverity())
                && matchesNullable(rule.getReconStatus(), exceptionCase.getReconStatus())
                && matchesNullable(rule.getRootCauseCategory(), exceptionCase.getRootCauseCategory())
                && matchesNullable(rule.getReasonCode(), exceptionCase.getReasonCode())
                && matchesNullable(rule.getStoreId(), exceptionCase.getStoreId());
    }

    private boolean matchesPlaybook(ExceptionPlaybook playbook, ExceptionCase exceptionCase) {
        return severityRank(exceptionCase.getSeverity()) >= severityRank(playbook.getMinSeverity())
                && matchesNullable(playbook.getReconStatus(), exceptionCase.getReconStatus())
                && matchesNullable(playbook.getRootCauseCategory(), exceptionCase.getRootCauseCategory())
                && matchesNullable(playbook.getReasonCode(), exceptionCase.getReasonCode());
    }

    private int routingSpecificity(ExceptionRoutingRule rule) {
        int score = 0;
        if (!isBlank(rule.getReconStatus())) score++;
        if (!isBlank(rule.getRootCauseCategory())) score++;
        if (!isBlank(rule.getReasonCode())) score++;
        if (!isBlank(rule.getStoreId())) score++;
        if (!isBlank(rule.getTargetAssigneeUsername())) score++;
        if (!isBlank(rule.getTargetRoleName())) score++;
        return score;
    }

    private int playbookSpecificity(ExceptionPlaybook playbook) {
        int score = 0;
        if (!isBlank(playbook.getReconStatus())) score++;
        if (!isBlank(playbook.getRootCauseCategory())) score++;
        if (!isBlank(playbook.getReasonCode())) score++;
        return score;
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

    private boolean matchesNullable(String expected, String actual) {
        String normalizedExpected = normalizeNullable(expected);
        return normalizedExpected == null || Objects.equals(normalizedExpected, normalizeNullable(actual));
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

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private String defaultIfBlank(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String valueOrNull(Object value) {
        return value != null ? value.toString() : null;
    }

    public record AutomationResolution(ExceptionRoutingRule routingRule,
                                       ExceptionPlaybook playbook) {
    }
}
