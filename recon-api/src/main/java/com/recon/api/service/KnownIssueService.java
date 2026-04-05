package com.recon.api.service;

import com.recon.api.domain.ExceptionCase;
import com.recon.api.domain.KnownIssue;
import com.recon.api.domain.KnownIssueCatalogResponse;
import com.recon.api.domain.KnownIssueDto;
import com.recon.api.domain.KnownIssueFeedback;
import com.recon.api.domain.KnownIssueFeedbackResponse;
import com.recon.api.domain.KnownIssueMatchDto;
import com.recon.api.domain.SaveKnownIssueRequest;
import com.recon.api.domain.SubmitKnownIssueFeedbackRequest;
import com.recon.api.repository.KnownIssueFeedbackRepository;
import com.recon.api.repository.KnownIssueRepository;
import com.recon.api.util.TimezoneConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnownIssueService {

    private final KnownIssueRepository knownIssueRepository;
    private final KnownIssueFeedbackRepository knownIssueFeedbackRepository;
    private final TenantService tenantService;

    @Transactional(readOnly = true)
    public KnownIssueCatalogResponse getCatalog(String tenantId,
                                                String reconView,
                                                Boolean activeOnly,
                                                String search,
                                                Collection<String> allowedReconViews) {
        List<KnownIssue> issues = (Boolean.TRUE.equals(activeOnly)
                ? knownIssueRepository.findByTenantIdAndActiveTrueOrderByPriorityWeightDescUpdatedAtDesc(tenantId)
                : knownIssueRepository.findByTenantIdOrderByActiveDescPriorityWeightDescUpdatedAtDesc(tenantId))
                .stream()
                .filter(matchesReconView(reconView))
                .filter(matchesAllowedReconViews(reconView, allowedReconViews))
                .filter(matchesSearch(search))
                .toList();

        Map<UUID, FeedbackCounts> feedbackCounts = feedbackCounts(tenantId, issues);

        return KnownIssueCatalogResponse.builder()
                .totalCount(issues.size())
                .activeCount(issues.stream().filter(KnownIssue::isActive).count())
                .items(issues.stream()
                        .map(issue -> toDto(issue, feedbackCounts.getOrDefault(issue.getId(), FeedbackCounts.empty())))
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public KnownIssueCatalogResponse getCatalog(String tenantId,
                                                String reconView,
                                                Boolean activeOnly,
                                                String search) {
        return getCatalog(tenantId, reconView, activeOnly, search, null);
    }

    @Transactional
    public KnownIssueDto saveKnownIssue(String tenantId,
                                        UUID issueId,
                                        SaveKnownIssueRequest request,
                                        String actorUsername,
                                        Collection<String> allowedReconViews) {
        if (request == null) {
            throw new IllegalArgumentException("Known issue request is required");
        }

        KnownIssue knownIssue = issueId == null
                ? KnownIssue.builder()
                .tenantId(tenantId)
                .createdBy(actorUsername)
                .updatedBy(actorUsername)
                .build()
                : knownIssueRepository.findByIdAndTenantId(issueId, tenantId)
                .map(existing -> {
                    requireAllowedReconView(existing.getReconView(), allowedReconViews);
                    return existing;
                })
                .orElseThrow(() -> new IllegalArgumentException("Known issue not found"));

        String title = trimToNull(request.getTitle());
        if (title == null) {
            throw new IllegalArgumentException("Known issue title is required");
        }

        String issueKey = normalizeIssueKey(request.getIssueKey(), title);
        boolean duplicateKey = issueId == null
                ? knownIssueRepository.existsByTenantIdAndIssueKeyIgnoreCase(tenantId, issueKey)
                : knownIssueRepository.existsByTenantIdAndIssueKeyIgnoreCaseAndIdNot(tenantId, issueKey, issueId);
        if (duplicateKey) {
            throw new IllegalArgumentException("Known issue key already exists: " + issueKey);
        }

        String reconView = normalize(request.getReconView());
        requireAllowedReconView(reconView, allowedReconViews);
        String reconStatus = normalize(request.getReconStatus());
        String reasonCode = normalize(request.getReasonCode());
        String rootCauseCategory = normalize(request.getRootCauseCategory());
        String storeId = normalize(request.getStoreId());
        String matchKeywords = normalizeKeywords(request.getMatchKeywords());

        if (reconStatus == null
                && reasonCode == null
                && rootCauseCategory == null
                && storeId == null
                && matchKeywords == null) {
            throw new IllegalArgumentException("Provide at least one matching rule: recon status, reason code, root cause, store, or keywords");
        }

        String probableCause = trimToNull(request.getProbableCause());
        String recommendedAction = trimToNull(request.getRecommendedAction());
        if (probableCause == null || recommendedAction == null) {
            throw new IllegalArgumentException("Probable cause and recommended action are required");
        }

        knownIssue.setIssueKey(issueKey);
        knownIssue.setTitle(title);
        knownIssue.setIssueSummary(trimToNull(request.getIssueSummary()));
        knownIssue.setReconView(reconView);
        knownIssue.setReconStatus(reconStatus);
        knownIssue.setReasonCode(reasonCode);
        knownIssue.setRootCauseCategory(rootCauseCategory);
        knownIssue.setStoreId(storeId);
        knownIssue.setMatchKeywords(matchKeywords);
        knownIssue.setProbableCause(probableCause);
        knownIssue.setRecommendedAction(recommendedAction);
        knownIssue.setEscalationGuidance(trimToNull(request.getEscalationGuidance()));
        knownIssue.setResolverNotes(trimToNull(request.getResolverNotes()));
        knownIssue.setPriorityWeight(resolvePriorityWeight(request.getPriorityWeight()));
        if (request.getActive() != null) {
            knownIssue.setActive(request.getActive());
        } else if (issueId == null) {
            knownIssue.setActive(true);
        }
        knownIssue.setUpdatedBy(actorUsername);

        KnownIssue saved = knownIssueRepository.save(knownIssue);
        Map<UUID, FeedbackCounts> feedbackCounts = feedbackCounts(tenantId, List.of(saved));
        return toDto(saved, feedbackCounts.getOrDefault(saved.getId(), FeedbackCounts.empty()));
    }

    @Transactional
    public KnownIssueDto saveKnownIssue(String tenantId,
                                        UUID issueId,
                                        SaveKnownIssueRequest request,
                                        String actorUsername) {
        return saveKnownIssue(tenantId, issueId, request, actorUsername, null);
    }

    @Transactional
    public KnownIssueFeedbackResponse submitFeedback(String tenantId,
                                                     UUID knownIssueId,
                                                     SubmitKnownIssueFeedbackRequest request,
                                                     String actorUsername,
                                                     Collection<String> allowedReconViews) {
        if (request == null || request.getHelpful() == null) {
            throw new IllegalArgumentException("Helpful or not helpful feedback is required");
        }

        KnownIssue knownIssue = knownIssueRepository.findByIdAndTenantId(knownIssueId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Known issue not found"));
        requireAllowedReconView(knownIssue.getReconView(), allowedReconViews);
        if (normalize(request.getReconView()) != null) {
            requireAllowedReconView(request.getReconView(), allowedReconViews);
        }

        String contextKey = buildContextKey(request);
        KnownIssueFeedback feedback = knownIssueFeedbackRepository
                .findByTenantIdAndKnownIssue_IdAndCreatedByAndContextKey(
                        tenantId,
                        knownIssueId,
                        actorUsername,
                        contextKey)
                .orElseGet(() -> KnownIssueFeedback.builder()
                        .tenantId(tenantId)
                        .knownIssue(knownIssue)
                        .createdBy(actorUsername)
                        .contextKey(contextKey)
                        .build());

        feedback.setTransactionKey(trimToNull(request.getTransactionKey()));
        feedback.setReconView(normalize(request.getReconView()));
        feedback.setIncidentKey(trimToNull(request.getIncidentKey()));
        feedback.setStoreId(normalize(request.getStoreId()));
        feedback.setSourceView(normalizeSourceView(request.getSourceView()));
        feedback.setHelpful(Boolean.TRUE.equals(request.getHelpful()));
        feedback.setFeedbackNotes(trimToNull(request.getFeedbackNotes()));
        knownIssueFeedbackRepository.save(feedback);

        long helpfulCount = knownIssueFeedbackRepository.countByTenantIdAndKnownIssue_IdAndHelpful(tenantId, knownIssueId, true);
        long notHelpfulCount = knownIssueFeedbackRepository.countByTenantIdAndKnownIssue_IdAndHelpful(tenantId, knownIssueId, false);

        return KnownIssueFeedbackResponse.builder()
                .knownIssueId(knownIssueId)
                .helpful(Boolean.TRUE.equals(request.getHelpful()))
                .helpfulCount(helpfulCount)
                .notHelpfulCount(notHelpfulCount)
                .message(Boolean.TRUE.equals(request.getHelpful())
                        ? "Known issue marked as helpful"
                        : "Known issue marked as not helpful")
                .build();
    }

    @Transactional
    public KnownIssueFeedbackResponse submitFeedback(String tenantId,
                                                     UUID knownIssueId,
                                                     SubmitKnownIssueFeedbackRequest request,
                                                     String actorUsername) {
        return submitFeedback(tenantId, knownIssueId, request, actorUsername, null);
    }

    @Transactional(readOnly = true)
    public KnownIssueCatalogContext loadActiveContext(String tenantId, Collection<String> reconViews) {
        Set<String> normalizedViews = reconViews == null
                ? Set.of()
                : reconViews.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<KnownIssue> issues = knownIssueRepository.findByTenantIdAndActiveTrueOrderByPriorityWeightDescUpdatedAtDesc(tenantId)
                .stream()
                .filter(issue -> issue.getReconView() == null
                        || normalizedViews.isEmpty()
                        || normalizedViews.contains(issue.getReconView()))
                .toList();

        return new KnownIssueCatalogContext(issues, feedbackCounts(tenantId, issues));
    }

    @Transactional(readOnly = true)
    public KnownIssueMatchDto matchCase(ExceptionCase exceptionCase, KnownIssueCatalogContext context) {
        if (exceptionCase == null || context == null) {
            return null;
        }
        return findBestMatch(
                MatchContext.builder()
                        .reconView(normalize(exceptionCase.getReconView()))
                        .reconStatus(normalize(exceptionCase.getReconStatus()))
                        .reasonCode(normalize(exceptionCase.getReasonCode()))
                        .rootCauseCategory(normalize(exceptionCase.getRootCauseCategory()))
                        .storeId(normalize(exceptionCase.getStoreId()))
                        .detailText(detailText(
                                exceptionCase.getReconStatus(),
                                exceptionCase.getReasonCode(),
                                exceptionCase.getNotes(),
                                exceptionCase.getNextAction(),
                                exceptionCase.getHandoffNote()))
                        .build(),
                context);
    }

    @Transactional(readOnly = true)
    public KnownIssueMatchDto matchIncident(ExceptionCase representativeCase,
                                            String incidentTitle,
                                            String incidentSummary,
                                            KnownIssueCatalogContext context) {
        if (representativeCase == null || context == null) {
            return null;
        }
        return findBestMatch(
                MatchContext.builder()
                        .reconView(normalize(representativeCase.getReconView()))
                        .reconStatus(normalize(representativeCase.getReconStatus()))
                        .reasonCode(normalize(representativeCase.getReasonCode()))
                        .rootCauseCategory(normalize(representativeCase.getRootCauseCategory()))
                        .storeId(normalize(representativeCase.getStoreId()))
                        .detailText(detailText(
                                incidentTitle,
                                incidentSummary,
                                representativeCase.getReconStatus(),
                                representativeCase.getReasonCode(),
                                representativeCase.getNotes()))
                        .build(),
                context);
    }

    private KnownIssueMatchDto findBestMatch(MatchContext context, KnownIssueCatalogContext catalogContext) {
        return catalogContext.issues().stream()
                .map(issue -> evaluate(issue, context))
                .filter(MatchEvaluation::matched)
                .sorted(Comparator
                        .comparingInt(MatchEvaluation::score).reversed()
                        .thenComparing(Comparator.comparingInt(MatchEvaluation::priorityWeight).reversed())
                        .thenComparing(MatchEvaluation::title, Comparator.nullsLast(String::compareToIgnoreCase)))
                .findFirst()
                .map(match -> toMatchDto(match, catalogContext.feedbackCounts().getOrDefault(match.knownIssue().getId(), FeedbackCounts.empty())))
                .orElse(null);
    }

    private MatchEvaluation evaluate(KnownIssue knownIssue, MatchContext context) {
        if (!knownIssue.isActive()) {
            return MatchEvaluation.noMatch(knownIssue);
        }

        int score = 0;
        List<String> matchedRules = new ArrayList<>();

        if (knownIssue.getReconView() != null) {
            if (!equalsIgnoreCase(knownIssue.getReconView(), context.reconView())) {
                return MatchEvaluation.noMatch(knownIssue);
            }
            score += 14;
            matchedRules.add("module");
        }
        if (knownIssue.getReconStatus() != null) {
            if (!equalsIgnoreCase(knownIssue.getReconStatus(), context.reconStatus())) {
                return MatchEvaluation.noMatch(knownIssue);
            }
            score += 25;
            matchedRules.add("recon status");
        }
        if (knownIssue.getReasonCode() != null) {
            if (!equalsIgnoreCase(knownIssue.getReasonCode(), context.reasonCode())) {
                return MatchEvaluation.noMatch(knownIssue);
            }
            score += 25;
            matchedRules.add("reason code");
        }
        if (knownIssue.getRootCauseCategory() != null) {
            if (!equalsIgnoreCase(knownIssue.getRootCauseCategory(), context.rootCauseCategory())) {
                return MatchEvaluation.noMatch(knownIssue);
            }
            score += 18;
            matchedRules.add("root cause");
        }
        if (knownIssue.getStoreId() != null) {
            if (!equalsIgnoreCase(knownIssue.getStoreId(), context.storeId())) {
                return MatchEvaluation.noMatch(knownIssue);
            }
            score += 14;
            matchedRules.add("store");
        }

        List<String> matchedKeywords = parseKeywords(knownIssue.getMatchKeywords()).stream()
                .filter(keyword -> containsIgnoreCase(context.detailText(), keyword))
                .distinct()
                .toList();
        if (!matchedKeywords.isEmpty()) {
            score += Math.min(20, matchedKeywords.size() * 10);
            matchedRules.add(matchedKeywords.size() == 1 ? "keyword" : matchedKeywords.size() + " keywords");
        }

        if (score < 24) {
            return MatchEvaluation.noMatch(knownIssue);
        }

        String confidence = score >= 55 ? "HIGH" : score >= 38 ? "MEDIUM" : "LOW";
        return MatchEvaluation.match(
                knownIssue,
                score,
                confidence,
                buildMatchReason(matchedRules),
                knownIssue.getPriorityWeight() != null ? knownIssue.getPriorityWeight() : 100
        );
    }

    private Map<UUID, FeedbackCounts> feedbackCounts(String tenantId, Collection<KnownIssue> knownIssues) {
        List<UUID> issueIds = knownIssues == null
                ? List.of()
                : knownIssues.stream()
                .map(KnownIssue::getId)
                .filter(Objects::nonNull)
                .toList();
        if (issueIds.isEmpty()) {
            return Map.of();
        }

        return knownIssueFeedbackRepository.findByTenantIdAndKnownIssue_IdIn(tenantId, issueIds).stream()
                .collect(Collectors.groupingBy(
                        feedback -> feedback.getKnownIssue().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> new FeedbackCounts(
                                list.stream().filter(KnownIssueFeedback::isHelpful).count(),
                                list.stream().filter(feedback -> !feedback.isHelpful()).count()
                        ))));
    }

    private KnownIssueDto toDto(KnownIssue knownIssue, FeedbackCounts feedbackCounts) {
        String tenantId = knownIssue.getTenantId();
        return KnownIssueDto.builder()
                .id(knownIssue.getId())
                .issueKey(knownIssue.getIssueKey())
                .title(knownIssue.getTitle())
                .issueSummary(knownIssue.getIssueSummary())
                .reconView(knownIssue.getReconView())
                .reconStatus(knownIssue.getReconStatus())
                .reasonCode(knownIssue.getReasonCode())
                .rootCauseCategory(knownIssue.getRootCauseCategory())
                .storeId(knownIssue.getStoreId())
                .matchKeywords(knownIssue.getMatchKeywords())
                .probableCause(knownIssue.getProbableCause())
                .recommendedAction(knownIssue.getRecommendedAction())
                .escalationGuidance(knownIssue.getEscalationGuidance())
                .resolverNotes(knownIssue.getResolverNotes())
                .priorityWeight(knownIssue.getPriorityWeight())
                .active(knownIssue.isActive())
                .helpfulCount(feedbackCounts.helpfulCount())
                .notHelpfulCount(feedbackCounts.notHelpfulCount())
                .createdBy(knownIssue.getCreatedBy())
                .updatedBy(knownIssue.getUpdatedBy())
                .createdAt(TimezoneConverter.toDisplay(stringValue(knownIssue.getCreatedAt()), tenantService.getTenant(tenantId)))
                .updatedAt(TimezoneConverter.toDisplay(stringValue(knownIssue.getUpdatedAt()), tenantService.getTenant(tenantId)))
                .build();
    }

    private KnownIssueMatchDto toMatchDto(MatchEvaluation match, FeedbackCounts feedbackCounts) {
        return KnownIssueMatchDto.builder()
                .id(match.knownIssue().getId())
                .issueKey(match.knownIssue().getIssueKey())
                .title(match.knownIssue().getTitle())
                .issueSummary(match.knownIssue().getIssueSummary())
                .probableCause(match.knownIssue().getProbableCause())
                .recommendedAction(match.knownIssue().getRecommendedAction())
                .escalationGuidance(match.knownIssue().getEscalationGuidance())
                .resolverNotes(match.knownIssue().getResolverNotes())
                .confidence(match.confidence())
                .matchReason(match.matchReason())
                .matchScore(match.score())
                .helpfulCount(feedbackCounts.helpfulCount())
                .notHelpfulCount(feedbackCounts.notHelpfulCount())
                .build();
    }

    private Predicate<KnownIssue> matchesReconView(String reconView) {
        String normalized = normalize(reconView);
        if (normalized == null) {
            return issue -> true;
        }
        return issue -> issue.getReconView() == null || Objects.equals(issue.getReconView(), normalized);
    }

    private Predicate<KnownIssue> matchesAllowedReconViews(String reconView, Collection<String> allowedReconViews) {
        if (normalize(reconView) != null || allowedReconViews == null) {
            return issue -> true;
        }
        Set<String> normalizedAllowedViews = allowedReconViews.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedAllowedViews.isEmpty()) {
            return issue -> false;
        }
        return issue -> issue.getReconView() == null || normalizedAllowedViews.contains(normalize(issue.getReconView()));
    }

    private void requireAllowedReconView(String reconView, Collection<String> allowedReconViews) {
        if (allowedReconViews == null) {
            return;
        }
        String normalizedReconView = normalize(reconView);
        if (normalizedReconView == null) {
            throw new AccessDeniedException("A reconciliation module is required for this action");
        }
        Set<String> normalizedAllowedViews = allowedReconViews.stream()
                .map(this::normalize)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!normalizedAllowedViews.contains(normalizedReconView)) {
            throw new AccessDeniedException("You are not authorized for this reconciliation module");
        }
    }

    private Predicate<KnownIssue> matchesSearch(String search) {
        String normalized = trimToNull(search);
        if (normalized == null) {
            return issue -> true;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        return issue -> java.util.stream.Stream.of(
                        issue.getIssueKey(),
                        issue.getTitle(),
                        issue.getIssueSummary(),
                        issue.getReconStatus(),
                        issue.getReasonCode(),
                        issue.getRootCauseCategory(),
                        issue.getStoreId(),
                        issue.getMatchKeywords(),
                        issue.getProbableCause(),
                        issue.getRecommendedAction())
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(lowered));
    }

    private String buildContextKey(SubmitKnownIssueFeedbackRequest request) {
        String sourceView = normalizeSourceView(request.getSourceView());
        if (trimToNull(request.getTransactionKey()) != null && normalize(request.getReconView()) != null) {
            return String.join("|",
                    "CASE",
                    normalize(request.getReconView()),
                    trimToNull(request.getTransactionKey()));
        }
        if (trimToNull(request.getIncidentKey()) != null) {
            return String.join("|",
                    "INCIDENT",
                    trimToNull(request.getIncidentKey()),
                    Objects.toString(normalize(request.getStoreId()), "ALL"),
                    sourceView);
        }
        return String.join("|",
                "SOURCE",
                sourceView,
                Objects.toString(normalize(request.getStoreId()), "ALL"));
    }

    private String buildMatchReason(List<String> matchedRules) {
        if (matchedRules.isEmpty()) {
            return "Matched by configured known issue rules";
        }
        if (matchedRules.size() == 1) {
            return "Matched on " + matchedRules.get(0);
        }
        if (matchedRules.size() == 2) {
            return "Matched on " + matchedRules.get(0) + " and " + matchedRules.get(1);
        }
        return "Matched on " + String.join(", ", matchedRules.subList(0, matchedRules.size() - 1))
                + ", and " + matchedRules.get(matchedRules.size() - 1);
    }

    private List<String> parseKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(keywords.split("[,;\\n]"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String detailText(String... values) {
        return java.util.Arrays.stream(values)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }

    private Integer resolvePriorityWeight(Integer requested) {
        if (requested == null || requested <= 0) {
            return 100;
        }
        return Math.min(requested, 1000);
    }

    private String normalizeIssueKey(String requestedKey, String title) {
        String base = trimToNull(requestedKey) != null ? requestedKey : title;
        String normalized = base.toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Unable to derive known issue key");
        }
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private String normalizeKeywords(String keywords) {
        List<String> normalized = parseKeywords(keywords);
        return normalized.isEmpty() ? null : String.join(", ", normalized);
    }

    private String normalizeSourceView(String sourceView) {
        return Objects.requireNonNullElse(normalize(sourceView), "UNKNOWN");
    }

    private String normalize(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : null;
    }

    private boolean containsIgnoreCase(String container, String expected) {
        return container != null
                && expected != null
                && container.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return Objects.equals(normalize(left), normalize(right));
    }

    public record KnownIssueCatalogContext(List<KnownIssue> issues, Map<UUID, FeedbackCounts> feedbackCounts) {
    }

    public record FeedbackCounts(long helpfulCount, long notHelpfulCount) {
        private static FeedbackCounts empty() {
            return new FeedbackCounts(0, 0);
        }
    }

    @lombok.Builder
    private record MatchContext(String reconView,
                                String reconStatus,
                                String reasonCode,
                                String rootCauseCategory,
                                String storeId,
                                String detailText) {
    }

    private record MatchEvaluation(KnownIssue knownIssue,
                                   boolean matched,
                                   int score,
                                   String confidence,
                                   String matchReason,
                                   int priorityWeight) {
        private static MatchEvaluation noMatch(KnownIssue knownIssue) {
            return new MatchEvaluation(knownIssue, false, 0, null, null, 0);
        }

        private static MatchEvaluation match(KnownIssue knownIssue,
                                             int score,
                                             String confidence,
                                             String matchReason,
                                             int priorityWeight) {
            return new MatchEvaluation(knownIssue, true, score, confidence, matchReason, priorityWeight);
        }

        private String title() {
            return knownIssue != null ? knownIssue.getTitle() : null;
        }
    }
}
