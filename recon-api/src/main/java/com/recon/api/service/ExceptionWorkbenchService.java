package com.recon.api.service;

import com.recon.api.domain.AddExceptionCommentRequest;
import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.ExceptionCaseDto;
import com.recon.api.domain.ExceptionComment;
import com.recon.api.domain.ExceptionCommentDto;
import com.recon.api.domain.UpdateExceptionCaseRequest;
import com.recon.api.repository.ExceptionCaseRepository;
import com.recon.api.repository.ExceptionCommentRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExceptionWorkbenchService {

    private final ExceptionCaseRepository caseRepository;
    private final ExceptionCommentRepository commentRepository;
    private final TenantService tenantService;

    @Transactional(readOnly = true)
    public ExceptionCaseDto getCase(String tenantId, String transactionKey, String reconView) {
        return caseRepository.findByTenantIdAndTransactionKeyAndReconView(
                        tenantId,
                        transactionKey,
                        normalize(reconView))
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public ExceptionCaseDto upsertCase(String tenantId,
                                       String transactionKey,
                                       String reconView,
                                       UpdateExceptionCaseRequest request,
                                       String actorUsername) {
        String normalizedReconView = normalize(
                request != null && request.getReconView() != null
                        ? request.getReconView()
                        : reconView);

        ExceptionCase exceptionCase = caseRepository
                .findByTenantIdAndTransactionKeyAndReconView(
                        tenantId, transactionKey, normalizedReconView)
                .orElseGet(() -> ExceptionCase.builder()
                        .tenantId(tenantId)
                        .transactionKey(transactionKey)
                        .reconView(normalizedReconView)
                        .createdBy(actorUsername)
                        .updatedBy(actorUsername)
                        .caseStatus("OPEN")
                        .build());

        exceptionCase.setCaseStatus(normalizeStatus(
                request != null ? request.getCaseStatus() : null));
        exceptionCase.setReasonCode(trimToNull(
                request != null ? request.getReasonCode() : null));
        exceptionCase.setAssigneeUsername(trimToNull(
                request != null ? request.getAssigneeUsername() : null));
        exceptionCase.setNotes(trimToNull(
                request != null ? request.getNotes() : null));
        exceptionCase.setUpdatedBy(actorUsername);

        return toDto(caseRepository.save(exceptionCase));
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

        exceptionCase.setUpdatedBy(actorUsername);
        caseRepository.save(exceptionCase);

        return toDto(exceptionCase);
    }

    private ExceptionCaseDto toDto(ExceptionCase exceptionCase) {
        List<ExceptionCommentDto> comments = commentRepository
                .findByExceptionCaseOrderByCreatedAtAsc(exceptionCase)
                .stream()
                .map(this::toCommentDto)
                .collect(Collectors.toList());

        return ExceptionCaseDto.builder()
                .id(exceptionCase.getId())
                .tenantId(exceptionCase.getTenantId())
                .transactionKey(exceptionCase.getTransactionKey())
                .reconView(exceptionCase.getReconView())
                .caseStatus(exceptionCase.getCaseStatus())
                .reasonCode(exceptionCase.getReasonCode())
                .assigneeUsername(exceptionCase.getAssigneeUsername())
                .notes(exceptionCase.getNotes())
                .createdBy(exceptionCase.getCreatedBy())
                .updatedBy(exceptionCase.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(
                        optionalToString(exceptionCase.getCreatedAt()),
                        tenantService.getTenant(exceptionCase.getTenantId())))
                .updatedAt(TimezoneConverter.toDisplay(
                        optionalToString(exceptionCase.getUpdatedAt()),
                        tenantService.getTenant(exceptionCase.getTenantId())))
                .comments(comments)
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

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
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
}
