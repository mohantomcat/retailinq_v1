const BASE = '/api/v1'

const getAccessToken = () =>
    sessionStorage.getItem('recon_access_token')

export const authApi = {
    getLoginOptions: async (tenantId) => {
        const params = new URLSearchParams()
        if (tenantId) {
            params.set('tenantId', tenantId)
        }
        const res = await fetch(
            `${BASE}/auth/login-options${params.toString() ? `?${params}` : ''}`
        )
        const json = await res.json()
        if (!res.ok) {
            throw new Error(json.message || 'Failed to load login options')
        }
        return json.data
    },

    login: async ({username, password, tenantId}) => {
        const res = await fetch(`${BASE}/auth/login`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({username, password, tenantId}),
        })
        const json = await res.json()
        if (!res.ok) {
            throw new Error(json.message || 'Login failed')
        }
        return json.data
    },

    startOidcLogin: async ({tenantId}) => {
        const res = await fetch(`${BASE}/auth/oidc/start`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({tenantId}),
        })
        const json = await res.json()
        if (!res.ok) {
            throw new Error(json.message || 'OIDC login could not be started')
        }
        return json.data
    },

    completeOidcLogin: async ({code, state, error, errorDescription}) => {
        const res = await fetch(`${BASE}/auth/oidc/callback`, {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({code, state, error, errorDescription}),
        })
        const json = await res.json()
        if (!res.ok) {
            throw new Error(json.message || 'OIDC login failed')
        }
        return json.data
    },

    refresh: async (refreshToken) => {
        const res = await fetch(`${BASE}/auth/refresh`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-Refresh-Token': refreshToken,
            },
        })
        const json = await res.json()
        if (!res.ok) {
            throw new Error(json.message || 'Token refresh failed')
        }
        return json.data
    },

    me: async (accessToken) => {
        const token = accessToken || getAccessToken()

        const res = await fetch(`${BASE}/auth/me`, {
            headers: {
                Authorization: `Bearer ${token}`,
            },
        })
        const json = await res.json()
        if (!res.ok) {
            throw new Error(json.message || 'Failed to fetch user')
        }
        return json.data
    },

    updateProfile: async ({fullName, email}) => {
        const token = getAccessToken()

        const res = await fetch(`${BASE}/auth/profile`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({fullName, email}),
        })
        const json = await res.json()
        if (!res.ok) {
            throw new Error(json.message || 'Failed to update profile')
        }
        return json.data
    },

    changePassword: async ({currentPassword, newPassword}) => {
        const token = getAccessToken()

        const res = await fetch(`${BASE}/auth/change-password`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
            body: JSON.stringify({currentPassword, newPassword}),
        })
        const json = await res.json()
        if (!res.ok) {
            throw new Error(json.message || 'Failed to change password')
        }
        return json.data
    },
}
