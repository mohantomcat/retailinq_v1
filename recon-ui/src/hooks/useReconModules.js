import {useMemo} from 'react'
import {useAuth} from '../context/AuthContext'

function normalizeModule(module, index) {
    return {
        reconView: module?.reconView || '',
        tabId: module?.tabId || '',
        label: module?.label || module?.moduleLabel || module?.reconView || '',
        targetSystem: module?.targetSystem || '',
        permissionCode: module?.permissionCode || '',
        groupCode: module?.groupCode || '',
        groupLabel: module?.groupLabel || '',
        groupDisplayOrder:
            typeof module?.groupDisplayOrder === 'number' ? module.groupDisplayOrder : index,
        groupSelectionRequired: Boolean(module?.groupSelectionRequired),
        displayOrder:
            typeof module?.displayOrder === 'number' ? module.displayOrder : index,
    }
}

export function useReconModules() {
    const {user} = useAuth()

    const modules = useMemo(() => {
        const accessibleModules = Array.isArray(user?.accessibleModules)
            ? user.accessibleModules
            : []

        return accessibleModules
            .map((module, index) => normalizeModule(module, index))
            .filter((module) => module.reconView && module.tabId && module.label)
            .sort((left, right) => left.displayOrder - right.displayOrder)
    }, [user?.accessibleModules])

    const moduleOptions = useMemo(
        () =>
            modules.map((module) => ({
                value: module.reconView,
                label: module.label,
                permission: module.permissionCode,
                tabId: module.tabId,
                targetSystem: module.targetSystem,
                groupCode: module.groupCode,
                groupLabel: module.groupLabel,
            })),
        [modules]
    )

    const moduleOptionsWithAll = useMemo(
        () => [{value: '', label: 'All Modules'}, ...moduleOptions],
        [moduleOptions]
    )

    const reconViewByTab = useMemo(
        () =>
            Object.fromEntries(
                modules.map((module) => [module.tabId, module.reconView])
            ),
        [modules]
    )

    const labelByValue = useMemo(
        () =>
            Object.fromEntries(
                modules.map((module) => [module.reconView, module.label])
            ),
        [modules]
    )

    const labelByTabId = useMemo(
        () =>
            Object.fromEntries(
                modules.map((module) => [module.tabId, module.label])
            ),
        [modules]
    )

    const targetSystemByTabId = useMemo(
        () =>
            Object.fromEntries(
                modules.map((module) => [module.tabId, module.targetSystem])
            ),
        [modules]
    )

    const modulesByReconView = useMemo(
        () =>
            Object.fromEntries(
                modules.map((module) => [module.reconView, module])
            ),
        [modules]
    )

    const modulesByTabId = useMemo(
        () =>
            Object.fromEntries(modules.map((module) => [module.tabId, module])),
        [modules]
    )

    const tabIds = useMemo(() => modules.map((module) => module.tabId), [modules])

    return {
        modules,
        moduleOptions,
        moduleOptionsWithAll,
        reconViewByTab,
        labelByValue,
        labelByTabId,
        tabIds,
        getModuleByReconView: (reconView) => modulesByReconView[reconView] || null,
        getModuleByTabId: (tabId) => modulesByTabId[tabId] || null,
        getTargetSystemByTab: (tabId) => targetSystemByTabId[tabId] || '',
    }
}
