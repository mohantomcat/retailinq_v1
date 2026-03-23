import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    FormControlLabel,
    MenuItem,
    Paper,
    Stack,
    Switch,
    TextField,
    Typography,
} from '@mui/material'
import {adminApi} from '../../services/adminApi'
import {useAdminPalette} from '../../components/admin/theme'
import {useI18n} from '../../context/I18nContext'

const EMPTY_KEY_FORM = {
    keyName: '',
    description: '',
    permissionCodes: ['RECON_VIEW'],
    allStoreAccess: true,
    allowedStoreIds: [],
}

function normalizeSelectArray(value) {
    if (Array.isArray(value)) {
        return value.map((item) => String(item)).filter(Boolean)
    }
    if (typeof value === 'string') {
        return value
            .split(',')
            .map((item) => item.trim())
            .filter(Boolean)
    }
    if (value == null) {
        return []
    }
    return [String(value)].filter(Boolean)
}

export default function TenantAccessCenter() {
    const palette = useAdminPalette()
    const {t} = useI18n()
    const [center, setCenter] = useState(null)
    const [users, setUsers] = useState([])
    const [orgUnits, setOrgUnits] = useState([])
    const [selectedUserId, setSelectedUserId] = useState('')
    const [selectedOrgUnitIds, setSelectedOrgUnitIds] = useState([])
    const [includeDescendants, setIncludeDescendants] = useState(true)
    const [authForm, setAuthForm] = useState({
        localLoginEnabled: true,
        preferredLoginMode: 'LOCAL',
        oidcEnabled: false,
        samlEnabled: false,
        apiKeyAuthEnabled: false,
        oidcDisplayName: '',
        oidcIssuerUrl: '',
        oidcClientId: '',
        samlDisplayName: '',
        samlEntityId: '',
        samlSsoUrl: '',
    })
    const [keyForm, setKeyForm] = useState(EMPTY_KEY_FORM)
    const [createdKey, setCreatedKey] = useState('')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [success, setSuccess] = useState('')

    const loadData = async () => {
        setLoading(true)
        try {
            const [accessCenter, tenantUsers, units] = await Promise.all([
                adminApi.getTenantAccessCenter(),
                adminApi.getUsers(),
                adminApi.getOrganizationUnits(),
            ])
            setCenter(accessCenter)
            setUsers(tenantUsers)
            setOrgUnits(units)
            if (accessCenter?.authConfig) {
                setAuthForm((current) => ({...current, ...accessCenter.authConfig}))
            }
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to load tenant access center')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadData()
    }, [])

    const orgUnitNameById = useMemo(
        () =>
            new Map(
                orgUnits.map((unit) => [String(unit.id), unit.unitName || unit.unitKey || String(unit.id)])
            ),
        [orgUnits]
    )

    const selectedUser = useMemo(
        () => users.find((user) => String(user.id) === String(selectedUserId)) || null,
        [users, selectedUserId]
    )

    useEffect(() => {
        if (!selectedUser) {
            setSelectedOrgUnitIds([])
            setIncludeDescendants(true)
            return
        }
        const scopes = Array.isArray(selectedUser.accessScope?.organizationScopes)
            ? selectedUser.accessScope.organizationScopes
            : []
        setSelectedOrgUnitIds(
            scopes
                .map((scope) => scope?.organizationUnitId)
                .filter(Boolean)
                .map((id) => String(id))
        )
        setIncludeDescendants(scopes[0]?.includeDescendants ?? true)
    }, [selectedUser])

    const permissionOptions = useMemo(() => {
        const base = ['RECON_VIEW', 'REPORTS_VIEW', 'EXCEPTION_QUEUE_VIEW', 'AUDIT_VIEW']
        return Array.from(new Set(base.concat(users.flatMap((user) => user.permissions || [])))).sort()
    }, [users])

    const saveAuthConfig = async () => {
        setSaving(true)
        try {
            const authConfig = await adminApi.saveTenantAuthConfig(authForm)
            setCenter((current) => ({...current, authConfig}))
            setSuccess(t('Tenant auth settings updated'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to save auth settings')
        } finally {
            setSaving(false)
        }
    }

    const saveUserScope = async () => {
        if (!selectedUserId) {
            setError(t('Select a user first'))
            return
        }
        setSaving(true)
        try {
            await adminApi.assignOrganizationScopes(selectedUserId, selectedOrgUnitIds, includeDescendants)
            await loadData()
            setSuccess(t('User organization scope updated'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to save user organization scope')
        } finally {
            setSaving(false)
        }
    }

    const createApiKey = async () => {
        if (!keyForm.keyName.trim()) {
            setError(t('Key name is required'))
            return
        }
        setSaving(true)
        try {
            const response = await adminApi.createTenantApiKey(keyForm)
            setCenter((current) => ({
                ...current,
                apiKeys: [response.apiKey, ...(current?.apiKeys || [])],
            }))
            setCreatedKey(response.plainTextKey || '')
            setKeyForm(EMPTY_KEY_FORM)
            setSuccess(t('API key created'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to create API key')
        } finally {
            setSaving(false)
        }
    }

    const deactivateApiKey = async (id) => {
        setSaving(true)
        try {
            const updated = await adminApi.deactivateTenantApiKey(id)
            setCenter((current) => ({
                ...current,
                apiKeys: (current?.apiKeys || []).map((apiKey) => apiKey.id === id ? updated : apiKey),
            }))
            setSuccess(t('API key deactivated'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to deactivate API key')
        } finally {
            setSaving(false)
        }
    }

    const storeCatalog = center?.storeCatalog || []
    const apiKeys = center?.apiKeys || []

    return (
        <Box sx={{px: 4, py: 3, maxWidth: 1480}}>
            <Typography sx={{fontSize: '1.15rem', fontWeight: 800, color: palette.text}}>
                {t('Tenant Access Center')}
            </Typography>
            <Typography sx={{fontSize: '0.88rem', color: palette.textMuted, mt: 0.5, mb: 2.5}}>
                {t('Manage tenant login modes, API keys, and user organization scope assignments.')}
            </Typography>

            {error && <Alert severity="error" sx={{mb: 2, borderRadius: 3}}>{error}</Alert>}
            {success && <Alert severity="success" sx={{mb: 2, borderRadius: 3}}>{success}</Alert>}
            {createdKey && (
                <Alert severity="warning" sx={{mb: 2, borderRadius: 3}}>
                    <Typography sx={{fontWeight: 700}}>{t('Copy this API key now')}</Typography>
                    <Typography sx={{fontFamily: 'monospace', wordBreak: 'break-all'}}>{createdKey}</Typography>
                </Alert>
            )}

            <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: '1fr 1fr'}, gap: 2}}>
                <Paper elevation={0} sx={{p: 2.25, borderRadius: '24px', border: `1px solid ${palette.border}`}}>
                    <Typography sx={{fontWeight: 800, color: palette.text, mb: 1.5}}>
                        {t('Authentication Modes')}
                    </Typography>
                    <Stack spacing={1.5}>
                        <TextField
                            select
                            size="small"
                            label={t('Preferred Login Mode')}
                            value={authForm.preferredLoginMode || 'LOCAL'}
                            onChange={(event) => setAuthForm((current) => ({...current, preferredLoginMode: event.target.value}))}
                        >
                            {['LOCAL', 'OIDC', 'SAML'].map((mode) => <MenuItem key={mode} value={mode}>{mode}</MenuItem>)}
                        </TextField>
                        <FormControlLabel control={<Switch checked={!!authForm.localLoginEnabled} onChange={(event) => setAuthForm((current) => ({...current, localLoginEnabled: event.target.checked}))}/>} label={t('Enable Local Login')}/>
                        <FormControlLabel control={<Switch checked={!!authForm.oidcEnabled} onChange={(event) => setAuthForm((current) => ({...current, oidcEnabled: event.target.checked}))}/>} label={t('Enable OIDC / SSO')}/>
                        <FormControlLabel control={<Switch checked={!!authForm.samlEnabled} onChange={(event) => setAuthForm((current) => ({...current, samlEnabled: event.target.checked}))}/>} label={t('Enable SAML')}/>
                        <FormControlLabel control={<Switch checked={!!authForm.apiKeyAuthEnabled} onChange={(event) => setAuthForm((current) => ({...current, apiKeyAuthEnabled: event.target.checked}))}/>} label={t('Enable Tenant API Keys')}/>
                        <TextField size="small" label={t('OIDC Display Name')} value={authForm.oidcDisplayName || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcDisplayName: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Issuer URL')} value={authForm.oidcIssuerUrl || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcIssuerUrl: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Client Id')} value={authForm.oidcClientId || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcClientId: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Display Name')} value={authForm.samlDisplayName || ''} onChange={(event) => setAuthForm((current) => ({...current, samlDisplayName: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Entity Id')} value={authForm.samlEntityId || ''} onChange={(event) => setAuthForm((current) => ({...current, samlEntityId: event.target.value}))}/>
                        <TextField size="small" label={t('SAML SSO URL')} value={authForm.samlSsoUrl || ''} onChange={(event) => setAuthForm((current) => ({...current, samlSsoUrl: event.target.value}))}/>
                        <Button variant="contained" onClick={saveAuthConfig} disabled={loading || saving} sx={{textTransform: 'none', borderRadius: 2.5}}>
                            {saving ? t('Saving...') : t('Save Auth Settings')}
                        </Button>
                    </Stack>
                </Paper>

                <Stack spacing={2}>
                    <Paper elevation={0} sx={{p: 2.25, borderRadius: '24px', border: `1px solid ${palette.border}`}}>
                        <Typography sx={{fontWeight: 800, color: palette.text, mb: 1.5}}>
                            {t('User Organization Scope')}
                        </Typography>
                        <Stack spacing={1.5}>
                            <TextField
                                select
                                size="small"
                                label={t('User')}
                                value={selectedUserId}
                                onChange={(event) =>
                                    setSelectedUserId(String(event.target.value || ''))
                                }
                            >
                                <MenuItem value="">{t('Select a user')}</MenuItem>
                                {users.map((user) => (
                                    <MenuItem key={user.id} value={String(user.id)}>
                                        {user.fullName || user.username}
                                    </MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                select
                                size="small"
                                label={t('Organization Units')}
                                SelectProps={{
                                    multiple: true,
                                    value: selectedOrgUnitIds,
                                    renderValue: (selected) =>
                                        normalizeSelectArray(selected)
                                            .map((id) => orgUnitNameById.get(id) || id)
                                            .join(', '),
                                }}
                                value={selectedOrgUnitIds}
                                onChange={(event) =>
                                    setSelectedOrgUnitIds(
                                        normalizeSelectArray(event.target.value)
                                    )
                                }
                            >
                                {orgUnits.filter((unit) => unit.active).map((unit) => (
                                    <MenuItem key={unit.id} value={String(unit.id)}>
                                        {unit.unitName} ({unit.unitType})
                                    </MenuItem>
                                ))}
                            </TextField>
                            <FormControlLabel control={<Switch checked={includeDescendants} onChange={(event) => setIncludeDescendants(event.target.checked)}/>} label={t('Include descendant stores')}/>
                            {selectedUser && (
                                <Chip
                                    label={selectedUser.accessScope?.accessLabel || t('All stores')}
                                    sx={{alignSelf: 'flex-start', backgroundColor: palette.chipBlueBg, color: palette.chipBlueText, fontWeight: 700}}
                                />
                            )}
                            <Button variant="contained" onClick={saveUserScope} disabled={!selectedUserId || saving} sx={{textTransform: 'none', borderRadius: 2.5}}>
                                {saving ? t('Saving...') : t('Save User Scope')}
                            </Button>
                        </Stack>
                    </Paper>

                    <Paper elevation={0} sx={{p: 2.25, borderRadius: '24px', border: `1px solid ${palette.border}`}}>
                        <Typography sx={{fontWeight: 800, color: palette.text, mb: 1.5}}>
                            {t('Tenant API Keys')}
                        </Typography>
                        <Stack spacing={1.25}>
                            <TextField size="small" label={t('Key Name')} value={keyForm.keyName} onChange={(event) => setKeyForm((current) => ({...current, keyName: event.target.value}))}/>
                            <TextField size="small" label={t('Description')} value={keyForm.description} onChange={(event) => setKeyForm((current) => ({...current, description: event.target.value}))}/>
                            <TextField
                                select
                                size="small"
                                label={t('Permission Codes')}
                                SelectProps={{
                                    multiple: true,
                                    value: keyForm.permissionCodes,
                                    renderValue: (selected) => (selected || []).join(', '),
                                }}
                                value={keyForm.permissionCodes}
                                onChange={(event) => setKeyForm((current) => ({...current, permissionCodes: event.target.value}))}
                            >
                                {permissionOptions.map((code) => <MenuItem key={code} value={code}>{code}</MenuItem>)}
                            </TextField>
                            <FormControlLabel control={<Switch checked={keyForm.allStoreAccess} onChange={(event) => setKeyForm((current) => ({...current, allStoreAccess: event.target.checked, allowedStoreIds: event.target.checked ? [] : current.allowedStoreIds}))}/>} label={t('Allow all stores')}/>
                            {!keyForm.allStoreAccess && (
                                <TextField
                                    select
                                    size="small"
                                    label={t('Allowed Stores')}
                                    SelectProps={{
                                        multiple: true,
                                        value: keyForm.allowedStoreIds,
                                        renderValue: (selected) => (selected || []).join(', '),
                                    }}
                                    value={keyForm.allowedStoreIds}
                                    onChange={(event) => setKeyForm((current) => ({...current, allowedStoreIds: event.target.value}))}
                                >
                                    {storeCatalog.map((storeId) => <MenuItem key={storeId} value={storeId}>{storeId}</MenuItem>)}
                                </TextField>
                            )}
                            <Button variant="outlined" onClick={createApiKey} disabled={saving} sx={{textTransform: 'none', borderRadius: 2.5}}>
                                {saving ? t('Saving...') : t('Create API Key')}
                            </Button>
                            {apiKeys.map((apiKey) => (
                                <Paper key={apiKey.id} elevation={0} sx={{p: 1.25, borderRadius: '16px', backgroundColor: palette.paperBgAlt, border: `1px solid ${palette.borderSoft}`}}>
                                    <Stack direction="row" justifyContent="space-between" spacing={1}>
                                        <Box>
                                            <Typography sx={{fontSize: '0.88rem', fontWeight: 700, color: palette.text}}>
                                                {apiKey.keyName}
                                            </Typography>
                                            <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                {apiKey.keyPrefix} · {apiKey.allStoreAccess ? t('All stores') : `${(apiKey.allowedStoreIds || []).length} ${t('stores')}`}
                                            </Typography>
                                        </Box>
                                        {apiKey.active && (
                                            <Button size="small" onClick={() => deactivateApiKey(apiKey.id)} sx={{textTransform: 'none'}}>
                                                {t('Deactivate')}
                                            </Button>
                                        )}
                                    </Stack>
                                </Paper>
                            ))}
                        </Stack>
                    </Paper>
                </Stack>
            </Box>
        </Box>
    )
}
