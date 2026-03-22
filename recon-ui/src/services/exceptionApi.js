import {apiFetch, parseResponse} from './apiFetch'

const BASE = '/api/v1/exceptions'

export const exceptionApi = {
    getAssignmentOptions: async () => {
        const res = await apiFetch(`${BASE}/assignment-options`)
        return parseResponse(res)
    },

    getQueues: async ({
        reconView = '',
        queueType = '',
        caseStatus = '',
        severity = '',
        assignee = '',
        assignedRole = '',
        search = '',
    } = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        if (queueType) params.append('queueType', queueType)
        if (caseStatus) params.append('caseStatus', caseStatus)
        if (severity) params.append('severity', severity)
        if (assignee) params.append('assignee', assignee)
        if (assignedRole) params.append('assignedRole', assignedRole)
        if (search) params.append('search', search)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/queues${suffix}`)
        return parseResponse(res)
    },

    getRegionalIncidentBoard: async ({
        reconView = '',
        outbreakStatus = '',
        search = '',
    } = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        if (outbreakStatus) params.append('outbreakStatus', outbreakStatus)
        if (search) params.append('search', search)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/regional-incident-board${suffix}`)
        return parseResponse(res)
    },

    getStoreManagerLite: async ({
        reconView = '',
        storeId = '',
        search = '',
    } = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        if (storeId) params.append('storeId', storeId)
        if (search) params.append('search', search)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/store-manager-lite${suffix}`)
        return parseResponse(res)
    },

    getTicketingCenter: async ({reconView = ''} = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/ticketing-center${suffix}`)
        return parseResponse(res)
    },

    createIntegrationChannel: async (payload) => {
        const res = await apiFetch(`${BASE}/integration-channels`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateIntegrationChannel: async (channelId, payload) => {
        const res = await apiFetch(`${BASE}/integration-channels/${channelId}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    getKnownIssues: async ({
        reconView = '',
        activeOnly = null,
        search = '',
    } = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        if (activeOnly !== undefined && activeOnly !== null) params.append('activeOnly', `${activeOnly}`)
        if (search) params.append('search', search)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/known-issues${suffix}`)
        return parseResponse(res)
    },

    createKnownIssue: async (payload) => {
        const res = await apiFetch(`${BASE}/known-issues`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateKnownIssue: async (knownIssueId, payload) => {
        const res = await apiFetch(`${BASE}/known-issues/${knownIssueId}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    submitKnownIssueFeedback: async ({knownIssueId, ...payload}) => {
        const res = await apiFetch(`${BASE}/known-issues/${knownIssueId}/feedback`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    createCaseExternalTicket: async ({transactionKey, reconView, ...payload}) => {
        const encoded = encodeURIComponent(transactionKey)
        const res = await apiFetch(`${BASE}/cases/${encoded}/external-tickets?reconView=${encodeURIComponent(reconView)}`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    sendCaseCommunication: async ({transactionKey, reconView, ...payload}) => {
        const encoded = encodeURIComponent(transactionKey)
        const res = await apiFetch(`${BASE}/cases/${encoded}/communications?reconView=${encodeURIComponent(reconView)}`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    createIncidentExternalTicket: async ({reconView, ...payload}) => {
        const res = await apiFetch(`${BASE}/incidents/external-tickets?reconView=${encodeURIComponent(reconView)}`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    sendIncidentCommunication: async ({reconView, ...payload}) => {
        const res = await apiFetch(`${BASE}/incidents/communications?reconView=${encodeURIComponent(reconView)}`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    bulkUpdateCases: async (payload) => {
        const res = await apiFetch(`${BASE}/cases/bulk-update`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    getEscalationPolicyCenter: async ({reconView = ''} = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/escalation-policy-center${suffix}`)
        return parseResponse(res)
    },

    getApprovalCenter: async ({
        reconView = '',
        requestStatus = '',
        search = '',
    } = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        if (requestStatus) params.append('requestStatus', requestStatus)
        if (search) params.append('search', search)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/approval-center${suffix}`)
        return parseResponse(res)
    },

    getAutomationCenter: async ({reconView = ''} = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/automation-center${suffix}`)
        return parseResponse(res)
    },

    getRootCauseAnalytics: async ({
        reconView = '',
        storeId = '',
        fromBusinessDate = '',
        toBusinessDate = '',
    } = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        if (storeId) params.append('storeId', storeId)
        if (fromBusinessDate) params.append('fromBusinessDate', fromBusinessDate)
        if (toBusinessDate) params.append('toBusinessDate', toBusinessDate)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/root-cause-analytics${suffix}`)
        return parseResponse(res)
    },

    getRecurrenceAnalytics: async ({
        reconView = '',
        storeId = '',
        fromBusinessDate = '',
        toBusinessDate = '',
    } = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        if (storeId) params.append('storeId', storeId)
        if (fromBusinessDate) params.append('fromBusinessDate', fromBusinessDate)
        if (toBusinessDate) params.append('toBusinessDate', toBusinessDate)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/recurrence-analytics${suffix}`)
        return parseResponse(res)
    },

    getOperationsCommandCenter: async ({
        reconView = '',
    } = {}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/operations-command-center${suffix}`)
        return parseResponse(res)
    },

    getCase: async ({transactionKey, reconView}) => {
        const encoded = encodeURIComponent(transactionKey)
        const res = await apiFetch(
            `${BASE}/cases/${encoded}?reconView=${encodeURIComponent(reconView)}`
        )
        return parseResponse(res)
    },

    saveCase: async ({
        transactionKey,
        reconView,
        reconStatus,
        caseStatus,
        reasonCode,
        rootCauseCategory,
        severity,
        assigneeUsername,
        assignedRoleName,
        nextAction,
        nextActionDueAt,
        handoffNote,
        storeId,
        wkstnId,
        businessDate,
        notes,
        closureComment,
        reopenReason,
        captureAuditSnapshot,
    }) => {
        const encoded = encodeURIComponent(transactionKey)
        const res = await apiFetch(`${BASE}/cases/${encoded}`, {
            method: 'PUT',
            body: JSON.stringify({
                reconView,
                reconStatus,
                caseStatus,
                reasonCode,
                rootCauseCategory,
                severity,
                assigneeUsername,
                assignedRoleName,
                nextAction,
                nextActionDueAt,
                handoffNote,
                storeId,
                wkstnId,
                businessDate,
                notes,
                closureComment,
                reopenReason,
                captureAuditSnapshot,
            }),
        })
        return parseResponse(res)
    },

    createClosurePolicy: async (payload) => {
        const res = await apiFetch(`${BASE}/closure-policies`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateClosurePolicy: async (policyId, payload) => {
        const res = await apiFetch(`${BASE}/closure-policies/${policyId}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    createEscalationPolicy: async (payload) => {
        const res = await apiFetch(`${BASE}/escalation-policies`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateEscalationPolicy: async (policyId, payload) => {
        const res = await apiFetch(`${BASE}/escalation-policies/${policyId}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deleteClosurePolicy: async ({policyId, reconView}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/closure-policies/${policyId}${suffix}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    deleteEscalationPolicy: async ({policyId, reconView}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/escalation-policies/${policyId}${suffix}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    decideApproval: async ({requestId, decision, decisionNotes}) => {
        const res = await apiFetch(`${BASE}/approval-requests/${requestId}/decision`, {
            method: 'POST',
            body: JSON.stringify({
                decision,
                decisionNotes,
            }),
        })
        return parseResponse(res)
    },

    createRoutingRule: async (payload) => {
        const res = await apiFetch(`${BASE}/routing-rules`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateRoutingRule: async (ruleId, payload) => {
        const res = await apiFetch(`${BASE}/routing-rules/${ruleId}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    createSuppressionRule: async (payload) => {
        const res = await apiFetch(`${BASE}/suppression-rules`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updateSuppressionRule: async (ruleId, payload) => {
        const res = await apiFetch(`${BASE}/suppression-rules/${ruleId}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deleteSuppressionRule: async ({ruleId, reconView}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/suppression-rules/${ruleId}${suffix}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    deleteRoutingRule: async ({ruleId, reconView}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/routing-rules/${ruleId}${suffix}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    createPlaybook: async (payload) => {
        const res = await apiFetch(`${BASE}/playbooks`, {
            method: 'POST',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    updatePlaybook: async (playbookId, payload) => {
        const res = await apiFetch(`${BASE}/playbooks/${playbookId}`, {
            method: 'PUT',
            body: JSON.stringify(payload),
        })
        return parseResponse(res)
    },

    deletePlaybook: async ({playbookId, reconView}) => {
        const params = new URLSearchParams()
        if (reconView) params.append('reconView', reconView)
        const suffix = params.toString() ? `?${params}` : ''
        const res = await apiFetch(`${BASE}/playbooks/${playbookId}${suffix}`, {
            method: 'DELETE',
        })
        return parseResponse(res)
    },

    addComment: async ({transactionKey, reconView, commentText}) => {
        const encoded = encodeURIComponent(transactionKey)
        const res = await apiFetch(`${BASE}/cases/${encoded}/comments`, {
            method: 'POST',
            body: JSON.stringify({
                reconView,
                commentText,
            }),
        })
        return parseResponse(res)
    },

    executePlaybookStep: async ({transactionKey, reconView, stepId}) => {
        const encoded = encodeURIComponent(transactionKey)
        const res = await apiFetch(
            `${BASE}/cases/${encoded}/playbook-steps/${stepId}/execute?reconView=${encodeURIComponent(reconView)}`,
            {
                method: 'POST',
            }
        )
        return parseResponse(res)
    },
}
