// Wraps all API calls with Authorization header
// and handles 401 auto-refresh

const TOKEN_KEY = 'recon_access_token'
const REFRESH_KEY = 'recon_refresh_token'

export async function apiFetch(url, options = {}) {
    const token = sessionStorage.getItem(TOKEN_KEY)

    const headers = {
        'Content-Type': 'application/json',
        ...(token ? {Authorization: `Bearer ${token}`} : {}),
        ...options.headers,
    }

    const res = await fetch(url, {
        ...options,
        headers,
    })

    if (res.status === 401) {
        const refreshToken = sessionStorage.getItem(REFRESH_KEY)

        if (refreshToken) {
            const refreshRes = await fetch('/api/v1/auth/refresh', {
                method: 'POST',
                headers: {
                    'X-Refresh-Token': refreshToken,
                },
            })

            if (refreshRes.ok) {
                const refreshJson = await refreshRes.json()
                const newToken = refreshJson?.data?.accessToken

                if (newToken) {
                    sessionStorage.setItem(TOKEN_KEY, newToken)

                    return fetch(url, {
                        ...options,
                        headers: {
                            ...headers,
                            Authorization: `Bearer ${newToken}`,
                        },
                    })
                }
            }
        }

        sessionStorage.clear()
        window.location.href = '/login'
        return res
    }

    return res
}

export async function parseResponse(res) {
    let json = null

    try {
        json = await res.json()
    } catch {
        json = null
    }

    if (!res.ok) {
        throw new Error(
            json?.message ||
            json?.error ||
            'API request failed'
        )
    }

    return json?.data ?? json ?? null
}