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
        default:
            return id
    }
}
