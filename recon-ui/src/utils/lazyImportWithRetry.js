import {lazy} from 'react'

const RETRY_PREFIX = 'lazy-import-retry:'
const DYNAMIC_IMPORT_FAILURE_PATTERN =
    /Failed to fetch dynamically imported module|Importing a module script failed|Failed to fetch module/i

function shouldRetryLazyImport(error) {
    const message = error?.message || String(error || '')
    return DYNAMIC_IMPORT_FAILURE_PATTERN.test(message)
}

export function lazyImportWithRetry(importer, key) {
    return lazy(async () => {
        const retryKey = `${RETRY_PREFIX}${key}`
        const hasWindow = typeof window !== 'undefined'
        const hasRetried = hasWindow
            && window.sessionStorage.getItem(retryKey) === '1'

        try {
            const loadedModule = await importer()
            if (hasWindow) {
                window.sessionStorage.removeItem(retryKey)
            }
            return loadedModule
        } catch (error) {
            if (hasWindow && shouldRetryLazyImport(error) && !hasRetried) {
                window.sessionStorage.setItem(retryKey, '1')
                window.location.reload()
                return new Promise(() => {})
            }

            if (hasWindow) {
                window.sessionStorage.removeItem(retryKey)
            }

            throw error
        }
    })
}
