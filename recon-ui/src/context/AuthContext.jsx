import {createContext, useCallback, useContext, useEffect, useState,} from 'react'
import {authApi} from '../services/authApi'
import {DEFAULT_ALLOWED_LANGUAGES} from '../i18n/languageCatalog'

const AuthContext = createContext(null)

const TOKEN_KEY = 'recon_access_token'
const REFRESH_KEY = 'recon_refresh_token'
const USER_KEY = 'recon_user'

function sanitizeUserPreferences(user) {
    const allowedLanguages = Array.from(
        new Set(
            (user?.allowedLanguages || DEFAULT_ALLOWED_LANGUAGES).filter(Boolean)
        )
    )

    const preferredLanguage = allowedLanguages.includes(
        user?.preferredLanguage
    )
        ? user.preferredLanguage
        : allowedLanguages[0]

    return {
        ...user,
        allowedLanguages,
        preferredLanguage,
    }
}

export function AuthProvider({children}) {
    const [user, setUser] = useState(null)
    const [accessToken, setAccessToken] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        const token = sessionStorage.getItem(TOKEN_KEY)
        const refreshToken = sessionStorage.getItem(REFRESH_KEY)
        const stored = sessionStorage.getItem(USER_KEY)

        const bootstrap = async () => {
            if (token && stored) {
                try {
                    setAccessToken(token)
                    const parsedUser = sanitizeUserPreferences(JSON.parse(stored))
                    setUser(parsedUser)

                    if (refreshToken && !Array.isArray(parsedUser?.accessibleModules)) {
                        const data = await authApi.refresh(refreshToken)
                        const updatedUser = sanitizeUserPreferences({
                            ...parsedUser,
                            accessToken: data.accessToken ?? token,
                            permissions: data.permissions ?? parsedUser.permissions,
                            accessibleModules: data.accessibleModules ?? [],
                            storeIds: data.storeIds ?? parsedUser.storeIds,
                            effectiveStoreIds: data.effectiveStoreIds ?? parsedUser.effectiveStoreIds,
                            allStoreAccess: data.allStoreAccess ?? parsedUser.allStoreAccess,
                            accessScope: data.accessScope ?? parsedUser.accessScope,
                            authMode: data.authMode ?? parsedUser.authMode,
                        })
                        sessionStorage.setItem(TOKEN_KEY, data.accessToken ?? token)
                        sessionStorage.setItem(USER_KEY, JSON.stringify(updatedUser))
                        setAccessToken(data.accessToken ?? token)
                        setUser(updatedUser)
                    }
                } catch {
                    sessionStorage.clear()
                }
            }

            setLoading(false)
        }

        bootstrap()
    }, [])

    const persistUser = useCallback((nextUser) => {
        const sanitized = sanitizeUserPreferences(nextUser)
        sessionStorage.setItem(USER_KEY, JSON.stringify(sanitized))
        setUser(sanitized)
    }, [])

    const persistLoginResponse = useCallback((data) => {
        const normalizedData = sanitizeUserPreferences(data)

        sessionStorage.setItem(TOKEN_KEY, normalizedData.accessToken)
        sessionStorage.setItem(REFRESH_KEY, normalizedData.refreshToken)
        sessionStorage.setItem(USER_KEY, JSON.stringify(normalizedData))

        setAccessToken(normalizedData.accessToken)
        setUser(normalizedData)
        return normalizedData
    }, [])

    const login = useCallback(async (username, password, tenantId) => {
        const data = await authApi.login({
            username,
            password,
            tenantId,
        })

        return persistLoginResponse(data)
    }, [persistLoginResponse])

    const completeOidcLogin = useCallback(async ({code, state, error, errorDescription}) => {
        const data = await authApi.completeOidcLogin({
            code,
            state,
            error,
            errorDescription,
        })

        return persistLoginResponse(data)
    }, [persistLoginResponse])

    const logout = useCallback(() => {
        sessionStorage.removeItem(TOKEN_KEY)
        sessionStorage.removeItem(REFRESH_KEY)
        sessionStorage.removeItem(USER_KEY)
        setAccessToken(null)
        setUser(null)
    }, [])

    const refreshSession = useCallback(async () => {
        const refreshToken = sessionStorage.getItem(REFRESH_KEY)

        if (!refreshToken) {
            logout()
            return
        }

        try {
            const data = await authApi.refresh(refreshToken)

            sessionStorage.setItem(TOKEN_KEY, data.accessToken)
            setAccessToken(data.accessToken)

            const updatedUser = {
                ...(JSON.parse(sessionStorage.getItem(USER_KEY) || '{}')),
                accessToken: data.accessToken,
                permissions: data.permissions,
                accessibleModules:
                    data.accessibleModules ?? user?.accessibleModules,
                storeIds: data.storeIds ?? user?.storeIds,
                effectiveStoreIds:
                    data.effectiveStoreIds ?? user?.effectiveStoreIds,
                allStoreAccess:
                    data.allStoreAccess ?? user?.allStoreAccess,
                accessScope: data.accessScope ?? user?.accessScope,
                authMode: data.authMode ?? user?.authMode,
            }

            persistUser(updatedUser)
        } catch {
            logout()
        }
    }, [logout, persistUser, user])

    const updateUserProfile = useCallback(async (profileData) => {
        const updatedUserDto = await authApi.updateProfile(profileData)

        const currentUser =
            JSON.parse(sessionStorage.getItem(USER_KEY) || '{}') || {}

        const updatedUser = {
            ...currentUser,
            fullName: updatedUserDto.fullName,
            email: updatedUserDto.email,
            roles: updatedUserDto.roles ?? currentUser.roles,
            permissions:
                updatedUserDto.permissions ?? currentUser.permissions,
            accessibleModules:
                updatedUserDto.accessibleModules ??
                currentUser.accessibleModules,
            storeIds: updatedUserDto.storeIds ?? currentUser.storeIds,
            effectiveStoreIds:
                updatedUserDto.effectiveStoreIds ??
                currentUser.effectiveStoreIds,
            allStoreAccess:
                updatedUserDto.allStoreAccess ?? currentUser.allStoreAccess,
            accessScope:
                updatedUserDto.accessScope ?? currentUser.accessScope,
            active: updatedUserDto.active,
            tenantId: updatedUserDto.tenantId ?? currentUser.tenantId,
        }

        persistUser(updatedUser)
        return updatedUser
    }, [persistUser])

    const updateUserPreferences = useCallback(async ({
        preferredLanguage,
        allowedLanguages,
    }) => {
        const currentUser =
            JSON.parse(sessionStorage.getItem(USER_KEY) || '{}') || {}

        const updatedUser = sanitizeUserPreferences({
            ...currentUser,
            preferredLanguage:
                preferredLanguage ?? currentUser.preferredLanguage,
            allowedLanguages:
                allowedLanguages ?? currentUser.allowedLanguages,
        })

        persistUser(updatedUser)
        return updatedUser
    }, [persistUser])

    const changePassword = useCallback(async ({
                                                  currentPassword,
                                                  newPassword,
                                              }) => {
        return await authApi.changePassword({
            currentPassword,
            newPassword,
        })
    }, [])

    const hasPermission = useCallback((permission) => {
        if (!user?.permissions) return false
        return user.permissions.includes(permission)
    }, [user])

    const hasAnyPermission = useCallback((permissions) => {
        if (!user?.permissions) return false
        return permissions.some((p) => user.permissions.includes(p))
    }, [user])

    const getAccessibleStores = useCallback(() => {
        return user?.effectiveStoreIds || user?.storeIds || []
    }, [user])

    const value = {
        user,
        accessToken,
        loading,
        login,
        completeOidcLogin,
        logout,
        refreshSession,
        updateUserProfile,
        updateUserPreferences,
        changePassword,
        hasPermission,
        hasAnyPermission,
        getAccessibleStores,
        isAuthenticated: !!user,
    }

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    )
}

export function useAuth() {
    const ctx = useContext(AuthContext)
    if (!ctx) {
        throw new Error('useAuth must be used inside AuthProvider')
    }
    return ctx
}
