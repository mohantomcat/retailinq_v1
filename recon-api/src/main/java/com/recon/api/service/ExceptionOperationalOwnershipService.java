package com.recon.api.service;

import com.recon.api.domain.ExceptionCase;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ExceptionOperationalOwnershipService {

    private static final long ACTION_DUE_SOON_HOURS = 4L;
    private static final String HANDOFF_PREFIX = "[Shift handoff]";

    public String resolveOwnershipStatus(ExceptionCase exceptionCase) {
        if (exceptionCase == null || isUnassigned(exceptionCase)) {
            return "UNOWNED";
        }
        if (trimToNull(exceptionCase.getNextAction()) == null) {
            return "NO_NEXT_ACTION";
        }
        if (isActionOverdue(exceptionCase)) {
            return "ACTION_OVERDUE";
        }
        if (isActionDueSoon(exceptionCase)) {
            return "ACTION_DUE_SOON";
        }
        return "ON_TRACK";
    }

    public boolean hasOwnershipGap(ExceptionCase exceptionCase) {
        return exceptionCase == null
                || isUnassigned(exceptionCase)
                || trimToNull(exceptionCase.getNextAction()) == null;
    }

    public boolean isActionDue(ExceptionCase exceptionCase) {
        return isActionOverdue(exceptionCase) || isActionDueSoon(exceptionCase);
    }

    public boolean isActionOverdue(ExceptionCase exceptionCase) {
        LocalDateTime nextActionDueAt = exceptionCase != null ? exceptionCase.getNextActionDueAt() : null;
        return nextActionDueAt != null && nextActionDueAt.isBefore(LocalDateTime.now());
    }

    public boolean isActionDueSoon(ExceptionCase exceptionCase) {
        LocalDateTime nextActionDueAt = exceptionCase != null ? exceptionCase.getNextActionDueAt() : null;
        if (nextActionDueAt == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return !nextActionDueAt.isBefore(now)
                && !nextActionDueAt.isAfter(now.plusHours(ACTION_DUE_SOON_HOURS));
    }

    public String buildHandoffComment(ExceptionCase exceptionCase) {
        if (exceptionCase == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        String assignee = trimToNull(exceptionCase.getAssigneeUsername());
        String team = trimToNull(exceptionCase.getAssignedRoleName());
        String nextAction = trimToNull(exceptionCase.getNextAction());
        String handoffNote = trimToNull(exceptionCase.getHandoffNote());

        if (assignee != null) {
            parts.add("Owner " + assignee);
        } else if (team != null) {
            parts.add("Owner team " + team);
        } else {
            parts.add("Owner unassigned");
        }
        if (assignee != null && team != null) {
            parts.add("Team " + team);
        }
        if (nextAction != null) {
            parts.add("Next action " + nextAction);
        }
        if (exceptionCase.getNextActionDueAt() != null) {
            parts.add("Due " + exceptionCase.getNextActionDueAt());
        }
        if (handoffNote != null) {
            parts.add(handoffNote);
        }
        return parts.isEmpty() ? null : HANDOFF_PREFIX + " " + String.join(" / ", parts);
    }

    public boolean isHandoffComment(String commentText) {
        return Objects.toString(commentText, "").startsWith(HANDOFF_PREFIX);
    }

    public String stripHandoffPrefix(String commentText) {
        String value = Objects.toString(commentText, "");
        if (!value.startsWith(HANDOFF_PREFIX)) {
            return trimToNull(value);
        }
        return trimToNull(value.substring(HANDOFF_PREFIX.length()));
    }

    private boolean isUnassigned(ExceptionCase exceptionCase) {
        return trimToNull(exceptionCase.getAssigneeUsername()) == null
                && trimToNull(exceptionCase.getAssignedRoleName()) == null;
    }

    private String trimToNull(String value) {
        String trimmed = Objects.toString(value, "").trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
