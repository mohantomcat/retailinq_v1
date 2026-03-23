export const RECON_VIEW_OPTIONS = [
    {
        value: 'XSTORE_SIM',
        label: 'Xstore vs SIM',
        permission: 'RECON_XSTORE_SIM',
        tabId: 'xstore-sim',
        targetSystem: 'SIM',
    },
    {
        value: 'XSTORE_SIOCS',
        label: 'Xstore vs SIOCS',
        permission: 'RECON_XSTORE_SIOCS',
        tabId: 'xstore-siocs',
        targetSystem: 'SIOCS',
    },
    {
        value: 'XSTORE_XOCS',
        label: 'Xstore vs XOCS',
        permission: 'RECON_XSTORE_XOCS',
        tabId: 'xstore-xocs',
        targetSystem: 'XOCS',
    },
    {
        value: 'SIOCS_MFCS',
        label: 'SIOCS vs MFCS',
        permission: 'RECON_SIOCS_MFCS',
        tabId: 'siocs-mfcs',
        targetSystem: 'MFCS',
    },
]

export const RECON_TAB_IDS = RECON_VIEW_OPTIONS.map((option) => option.tabId)

export const RECON_VIEW_OPTIONS_WITH_ALL = [
    {value: '', label: 'All Modules'},
    ...RECON_VIEW_OPTIONS,
]

export const RECON_VIEW_BY_TAB = Object.fromEntries(
    RECON_VIEW_OPTIONS.map((option) => [option.tabId, option.value])
)

export const RECON_VIEW_LABEL_BY_VALUE = Object.fromEntries(
    RECON_VIEW_OPTIONS.map((option) => [option.value, option.label])
)

export const RECON_TAB_LABEL_BY_ID = Object.fromEntries(
    RECON_VIEW_OPTIONS.map((option) => [option.tabId, option.label])
)

export function getReconTargetSystemByTab(tabId) {
    return RECON_VIEW_OPTIONS.find((option) => option.tabId === tabId)?.targetSystem || 'SIM'
}
