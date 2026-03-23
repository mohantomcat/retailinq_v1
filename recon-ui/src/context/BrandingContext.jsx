import {
    createContext,
    useCallback,
    useContext,
    useEffect,
    useMemo,
    useState,
} from 'react'
import {useAuth} from './AuthContext'
import {
    DEFAULT_BRANDING,
    applyBrandingVariables,
    resolveBranding,
} from '../branding/brandingUtils'
import {brandingApi} from '../services/brandingApi'

const BrandingContext = createContext(null)

export function BrandingProvider({children}) {
    const {user} = useAuth()
    const [branding, setBranding] = useState(DEFAULT_BRANDING)
    const [loading, setLoading] = useState(false)

    const applyBranding = useCallback((value) => {
        setBranding(resolveBranding(value))
    }, [])

    const refreshBranding = useCallback(
        async (tenantIdOverride) => {
            const tenantId = tenantIdOverride || user?.tenantId
            if (!tenantId) {
                setBranding(DEFAULT_BRANDING)
                return DEFAULT_BRANDING
            }

            const nextBranding = resolveBranding(
                await brandingApi.getCurrent(tenantId)
            )
            setBranding(nextBranding)
            return nextBranding
        },
        [user?.tenantId]
    )

    useEffect(() => {
        let active = true

        if (!user?.tenantId) {
            setBranding(DEFAULT_BRANDING)
            return () => {
                active = false
            }
        }

        setLoading(true)
        refreshBranding(user.tenantId)
            .catch(() => {
                if (active) {
                    setBranding(DEFAULT_BRANDING)
                }
            })
            .finally(() => {
                if (active) {
                    setLoading(false)
                }
            })

        return () => {
            active = false
        }
    }, [refreshBranding, user?.tenantId])

    useEffect(() => {
        applyBrandingVariables(branding)
    }, [branding])

    const value = useMemo(
        () => ({
            branding,
            loading,
            applyBranding,
            refreshBranding,
        }),
        [applyBranding, branding, loading, refreshBranding]
    )

    return (
        <BrandingContext.Provider value={value}>
            {children}
        </BrandingContext.Provider>
    )
}

export function useBranding() {
    const context = useContext(BrandingContext)
    if (!context) {
        throw new Error('useBranding must be used inside BrandingProvider')
    }
    return context
}
