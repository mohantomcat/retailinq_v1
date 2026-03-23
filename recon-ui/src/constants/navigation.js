import {RECON_TAB_IDS, RECON_TAB_LABEL_BY_ID} from './reconViews'

export const RECONCILIATION_TAB_IDS = [
    ...RECON_TAB_IDS,
]

export const SECURITY_TAB_IDS = [
    'manage-users',
    'manage-roles',
    'manage-perms',
    'org-hierarchy',
    'tenant-access',
    'branding-center',
]

export const REPORT_TAB_IDS = [
    'operations-command-center',
    'executive-scorecards',
    'store-scorecards',
    'root-cause-analytics',
    'recurrence-analytics',
]

export const ALERT_TAB_IDS = [
    'alerts',
]

export const EXCEPTION_TAB_IDS = [
    'exception-queues',
    'store-manager-lite',
    'regional-incident-board',
    'noise-suppression',
    'known-issues',
    'ticketing-comms',
    'approval-center',
    'routing-playbooks',
]

export const SLA_TAB_IDS = [
    'sla-management',
]

export const OPERATIONS_TAB_IDS = [
    'operations',
    'recon-jobs',
]

export const ACTIVITY_TAB_IDS = [
    'audit-activity',
]

export const CONFIGURATION_TAB_IDS = [
    'module-configs',
    'system-configs',
]

export function getTabLabel(t, id) {
    if (RECON_TAB_LABEL_BY_ID[id]) {
        return t(RECON_TAB_LABEL_BY_ID[id])
    }

    switch (id) {
        case 'manage-users':
            return t('Manage Users')
        case 'manage-roles':
            return t('Manage Roles')
        case 'manage-perms':
            return t('Manage Permissions')
        case 'org-hierarchy':
            return t('Organization Hierarchy')
        case 'tenant-access':
            return t('Tenant Access Center')
        case 'branding-center':
            return t('Branding Center')
        case 'executive-scorecards':
            return t('Executive Scorecards')
        case 'operations-command-center':
            return t('Operations Command Center')
        case 'store-scorecards':
            return t('Store Scorecards')
        case 'root-cause-analytics':
            return t('Root Cause Analytics')
        case 'recurrence-analytics':
            return t('Recurrence & Prevention')
        case 'alerts':
            return t('Alerts')
        case 'exception-queues':
            return t('Exception Queues')
        case 'store-manager-lite':
            return t('Store Manager Lite')
        case 'regional-incident-board':
            return t('Regional Incident Board')
        case 'noise-suppression':
            return t('Noise Suppression')
        case 'known-issues':
            return t('Known Issues')
        case 'ticketing-comms':
            return t('Ticketing & Comms')
        case 'approval-center':
            return t('Approval Center')
        case 'routing-playbooks':
            return t('Routing & Playbooks')
        case 'sla-management':
            return t('SLA & Aging')
        case 'operations':
            return t('Operations')
        case 'recon-jobs':
            return t('Reconciliation Jobs')
        case 'audit-activity':
            return t('Audit & Activity')
        case 'module-configs':
            return t('Module Configurations')
        case 'system-configs':
            return t('System Configurations')
        default:
            return id
    }
}
