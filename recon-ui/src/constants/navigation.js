export const RECONCILIATION_TAB_IDS = [
    'xstore-sim',
    'xstore-siocs',
    'xstore-xocs',
    'sim-rms',
]

export const SECURITY_TAB_IDS = [
    'manage-users',
    'manage-roles',
    'manage-perms',
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
]

export const ACTIVITY_TAB_IDS = [
    'audit-activity',
]

export const CONFIGURATION_TAB_IDS = [
    'module-configs',
    'system-configs',
]

export function getTabLabel(t, id) {
    switch (id) {
        case 'xstore-sim':
            return t('Xstore vs SIM')
        case 'xstore-siocs':
            return t('Xstore vs SIOCS')
        case 'xstore-xocs':
            return t('Xstore vs XOCS')
        case 'sim-rms':
            return t('SIM vs RMS')
        case 'manage-users':
            return t('Manage Users')
        case 'manage-roles':
            return t('Manage Roles')
        case 'manage-perms':
            return t('Manage Permissions')
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
