package com.recon.api.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class SyncExceptionExternalTicketRequest {
    @JsonAlias({"ticketNumber", "number", "key", "id", "reference"})
    private String externalReference;

    @JsonAlias({"status", "state", "issueStatus"})
    private String externalStatus;

    @JsonAlias({"url", "link", "self"})
    private String externalUrl;

    @JsonAlias({"transaction_id", "transactionId"})
    private String transactionKey;

    @JsonAlias({"incident_id", "incidentId"})
    private String incidentKey;

    @JsonAlias({"module"})
    private String reconView;

    @JsonAlias({"summary", "short_description", "title"})
    private String ticketSummary;

    @JsonAlias({"description", "details"})
    private String ticketDescription;

    @JsonAlias({"statusNote", "status_note", "comment", "comments", "work_notes", "notes"})
    private String statusComment;

    @JsonAlias({"updatedBy", "updated_by", "assigned_to", "assignee", "owner"})
    private String externalUpdatedBy;

    @JsonAlias({"event", "eventType", "event_type", "updateType"})
    private String eventType;
}
