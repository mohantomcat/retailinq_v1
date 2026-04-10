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
    expiresInDays: 90,
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

function formatDate(value) {
    if (!value) return '-'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
    })
}

function formatFinding(value) {
    return String(value || '')
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (letter) => letter.toUpperCase())
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
        autoProvisionUsers: false,
        allowedEmailDomains: '',
        oidcUsernameClaim: 'preferred_username',
        oidcEmailClaim: 'email',
        oidcGroupsClaim: 'groups',
        samlEmailAttribute: '',
        samlGroupsAttribute: '',
    })
    const [reconGroupForm, setReconGroupForm] = useState({})
    const [endpointProfileForm, setEndpointProfileForm] = useState({})
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

    useEffect(() => {
        const groups = Array.isArray(center?.reconGroups) ? center.reconGroups : []
        setReconGroupForm(
            Object.fromEntries(
                groups.map((group) => [group.groupCode, group.selectedReconView || ''])
            )
        )
    }, [center?.reconGroups])

    useEffect(() => {
        const profiles = Array.isArray(center?.systemEndpointProfiles) ? center.systemEndpointProfiles : []
        setEndpointProfileForm(
            Object.fromEntries(
                profiles.map((profile) => [profile.systemName, profile.selectedEndpointMode || ''])
            )
        )
    }, [center?.systemEndpointProfiles])

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

    const saveReconGroups = async () => {
        const missingRequiredGroups = (center?.reconGroups || [])
            .filter((group) => group.selectionRequired && !reconGroupForm[group.groupCode])
            .map((group) => group.groupLabel || group.groupCode)
        const hasAnySelection = Object.values(reconGroupForm).some(Boolean)
        if (hasAnySelection && missingRequiredGroups.length) {
            setError(`${t('Select an active lane for required groups')}: ${missingRequiredGroups.join(', ')}`)
            return
        }
        setSaving(true)
        try {
            const reconGroups = await adminApi.saveTenantReconGroupSelections({
                selections: Object.entries(reconGroupForm).map(([groupCode, selectedReconView]) => ({
                    groupCode,
                    selectedReconView: selectedReconView || null,
                })),
            })
            setCenter((current) => ({...current, reconGroups}))
            setSuccess(t('Tenant reconciliation lane selections updated. Users may need to sign in again to see the new module set.'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to save reconciliation lane selections')
        } finally {
            setSaving(false)
        }
    }

    const saveSystemEndpointProfiles = async () => {
        setSaving(true)
        try {
            const systemEndpointProfiles = await adminApi.saveTenantSystemEndpointProfiles({
                selections: Object.entries(endpointProfileForm).map(([systemName, endpointMode]) => ({
                    systemName,
                    endpointMode: endpointMode || null,
                })),
            })
            setCenter((current) => ({...current, systemEndpointProfiles}))
            setSuccess(t('Tenant system endpoint profiles updated. Future lane-first operations and integration views will use these profiles.'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to save system endpoint profiles')
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

    const reviewUserAccess = async (userId, decision = 'CERTIFIED') => {
        setSaving(true)
        try {
            await adminApi.reviewUserAccess(userId, {
                decision,
                nextReviewInDays: decision === 'CERTIFIED' ? 90 : 14,
            })
            await loadData()
            setSuccess(t('User access review updated'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to update access review')
        } finally {
            setSaving(false)
        }
    }

    const storeCatalog = center?.storeCatalog || []
    const apiKeys = center?.apiKeys || []
    const governance = center?.governance || {}
    const userFindings = governance.userFindings || []
    const apiKeyFindings = governance.apiKeyFindings || []
    const reconGroups = center?.reconGroups || []
    const systemEndpointProfiles = center?.systemEndpointProfiles || []
    const hasAnyReconGroupSelection = reconGroups.some((group) => Boolean(reconGroupForm[group.groupCode]))
    const missingRequiredReconGroups = reconGroups
        .filter((group) => group.selectionRequired && !reconGroupForm[group.groupCode])
        .map((group) => group.groupLabel || group.groupCode)

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

            <Paper elevation={0} sx={{p: 2.25, mb: 2, borderRadius: 2, border: `1px solid ${palette.border}`}}>
                <Stack spacing={1.75}>
                    <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 2, flexWrap: 'wrap'}}>
                        <Box>
                            <Typography sx={{fontWeight: 800, color: palette.text}}>
                                {t('Access Governance')}
                            </Typography>
                            <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, mt: 0.25}}>
                                {t('Certification, high-risk access, identity source, and API key exposure.')}
                            </Typography>
                        </Box>
                        <Chip
                            label={`${t('Preferred Login Mode')}: ${governance.preferredLoginMode || authForm.preferredLoginMode || 'LOCAL'}`}
                            sx={{backgroundColor: palette.chipBlueBg, color: palette.chipBlueText, fontWeight: 700}}
                        />
                    </Box>

                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr 1fr', md: 'repeat(4, minmax(0, 1fr))'}, gap: 1}}>
                        {[
                            [t('Active Users'), governance.activeUsers ?? 0],
                            [t('Review Due'), governance.usersDueForReview ?? 0],
                            [t('High Privilege'), governance.highPrivilegeUsers ?? 0],
                            [t('API Keys Expiring'), governance.apiKeysExpiringSoon ?? 0],
                        ].map(([label, value]) => (
                            <Box key={label} sx={{border: `1px solid ${palette.borderSoft}`, borderRadius: 2, p: 1.25}}>
                                <Typography sx={{fontSize: '0.74rem', color: palette.textMuted, fontWeight: 700}}>
                                    {label}
                                </Typography>
                                <Typography sx={{fontSize: '1.2rem', fontWeight: 800, color: palette.text}}>
                                    {value}
                                </Typography>
                            </Box>
                        ))}
                    </Box>

                    <Stack spacing={1}>
                        {userFindings.slice(0, 6).map((finding) => (
                            <Box
                                key={finding.userId}
                                sx={{
                                    display: 'grid',
                                    gridTemplateColumns: {xs: '1fr', md: 'minmax(180px, 1.2fr) minmax(220px, 2fr) auto'},
                                    gap: 1,
                                    alignItems: 'center',
                                    border: `1px solid ${palette.borderSoft}`,
                                    borderRadius: 2,
                                    p: 1.25,
                                }}
                            >
                                <Box>
                                    <Typography sx={{fontSize: '0.88rem', fontWeight: 800, color: palette.text}}>
                                        {finding.fullName || finding.username}
                                    </Typography>
                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                        {finding.identityProvider || 'LOCAL'} - {t('Review due')} {formatDate(finding.accessReviewDueAt)}
                                    </Typography>
                                </Box>
                                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                                    {(finding.findingTypes || []).map((type) => (
                                        <Chip
                                            key={`${finding.userId}-${type}`}
                                            size="small"
                                            label={formatFinding(type)}
                                            sx={{backgroundColor: palette.paperBgAlt, color: palette.text, border: `1px solid ${palette.borderSoft}`}}
                                        />
                                    ))}
                                </Stack>
                                <Stack direction="row" spacing={0.75} justifyContent="flex-end">
                                    <Button
                                        size="small"
                                        variant="contained"
                                        onClick={() => reviewUserAccess(finding.userId, 'CERTIFIED')}
                                        disabled={saving}
                                        sx={{textTransform: 'none', borderRadius: 2}}
                                    >
                                        {t('Certify')}
                                    </Button>
                                    <Button
                                        size="small"
                                        variant="outlined"
                                        onClick={() => reviewUserAccess(finding.userId, 'NEEDS_CHANGES')}
                                        disabled={saving}
                                        sx={{textTransform: 'none', borderRadius: 2}}
                                    >
                                        {t('Needs Changes')}
                                    </Button>
                                </Stack>
                            </Box>
                        ))}
                        {userFindings.length === 0 && (
                            <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                {t('No access review findings.')}
                            </Typography>
                        )}
                    </Stack>

                    {apiKeyFindings.length > 0 && (
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            {apiKeyFindings.slice(0, 4).map((finding) => (
                                <Chip
                                    key={finding.id}
                                    label={`${finding.keyName}: ${(finding.findingTypes || []).map(formatFinding).join(', ')}`}
                                    sx={{backgroundColor: palette.paperBgAlt, color: palette.text, border: `1px solid ${palette.borderSoft}`}}
                                />
                            ))}
                        </Stack>
                    )}
                </Stack>
            </Paper>

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
                        <FormControlLabel control={<Switch checked={!!authForm.autoProvisionUsers} onChange={(event) => setAuthForm((current) => ({...current, autoProvisionUsers: event.target.checked}))}/>} label={t('Auto Provision SSO Users')}/>
                        <TextField size="small" label={t('Allowed Email Domains')} value={authForm.allowedEmailDomains || ''} onChange={(event) => setAuthForm((current) => ({...current, allowedEmailDomains: event.target.value}))} helperText={t('Comma-separated domains for SSO provisioning')}/>
                        <TextField size="small" label={t('OIDC Display Name')} value={authForm.oidcDisplayName || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcDisplayName: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Issuer URL')} value={authForm.oidcIssuerUrl || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcIssuerUrl: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Client Id')} value={authForm.oidcClientId || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcClientId: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Username Claim')} value={authForm.oidcUsernameClaim || 'preferred_username'} onChange={(event) => setAuthForm((current) => ({...current, oidcUsernameClaim: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Email Claim')} value={authForm.oidcEmailClaim || 'email'} onChange={(event) => setAuthForm((current) => ({...current, oidcEmailClaim: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Groups Claim')} value={authForm.oidcGroupsClaim || 'groups'} onChange={(event) => setAuthForm((current) => ({...current, oidcGroupsClaim: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Display Name')} value={authForm.samlDisplayName || ''} onChange={(event) => setAuthForm((current) => ({...current, samlDisplayName: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Entity Id')} value={authForm.samlEntityId || ''} onChange={(event) => setAuthForm((current) => ({...current, samlEntityId: event.target.value}))}/>
                        <TextField size="small" label={t('SAML SSO URL')} value={authForm.samlSsoUrl || ''} onChange={(event) => setAuthForm((current) => ({...current, samlSsoUrl: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Email Attribute')} value={authForm.samlEmailAttribute || ''} onChange={(event) => setAuthForm((current) => ({...current, samlEmailAttribute: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Groups Attribute')} value={authForm.samlGroupsAttribute || ''} onChange={(event) => setAuthForm((current) => ({...current, samlGroupsAttribute: event.target.value}))}/>
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
                        <Typography sx={{fontWeight: 800, color: palette.text, mb: 0.75}}>
                            {t('Reconciliation Lane Groups')}
                        </Typography>
                        <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, mb: 1.5}}>
                            {t('Choose the active lane for each exclusive application-stack group. Untouched tenants stay on legacy visibility. After you save any lane selection, only the selected lane in each group remains visible and blank groups are hidden.')}
                        </Typography>
                        {hasAnyReconGroupSelection ? (
                            <Alert severity="warning" sx={{mb: 1.5, borderRadius: 3}}>
                                {missingRequiredReconGroups.length
                                    ? `${t('Strict tenant lane mode will be applied on save. Required groups still missing')}: ${missingRequiredReconGroups.join(', ')}`
                                    : t('Strict tenant lane mode will be applied on save. Users may need to sign in again to pick up the narrowed module set.')}
                            </Alert>
                        ) : null}
                        <Stack spacing={1.5}>
                            {reconGroups.map((group) => (
                                <Box
                                    key={group.groupCode}
                                    sx={{
                                        p: 1.5,
                                        borderRadius: '18px',
                                        backgroundColor: palette.paperBgAlt,
                                        border: `1px solid ${palette.borderSoft}`,
                                    }}
                                >
                                    <Stack spacing={1}>
                                        <Box>
                                            <Typography sx={{fontSize: '0.9rem', fontWeight: 700, color: palette.text}}>
                                                {group.groupLabel}
                                            </Typography>
                                            <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                {group.groupDescription}
                                            </Typography>
                                        </Box>
                                        <TextField
                                            select
                                            size="small"
                                            label={t('Active Lane')}
                                            value={reconGroupForm[group.groupCode] || ''}
                                            onChange={(event) =>
                                                setReconGroupForm((current) => ({
                                                    ...current,
                                                    [group.groupCode]: event.target.value,
                                                }))
                                            }
                                        >
                                            <MenuItem value="">{t('No tenant selection')}</MenuItem>
                                            {(group.modules || []).map((module) => (
                                                <MenuItem key={module.reconView} value={module.reconView}>
                                                    {module.label}
                                                </MenuItem>
                                            ))}
                                        </TextField>
                                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                            <Chip
                                                size="small"
                                                label={group.selectionRequired ? t('Required') : t('Optional')}
                                                sx={{
                                                    backgroundColor: palette.chipBlueBg,
                                                    color: palette.chipBlueText,
                                                    fontWeight: 700,
                                                }}
                                            />
                                            {group.selectionRequired && !reconGroupForm[group.groupCode] ? (
                                                <Chip
                                                    size="small"
                                                    label={t('No lane selected')}
                                                    sx={{
                                                        backgroundColor: '#FFF7ED',
                                                        color: '#C2410C',
                                                        fontWeight: 700,
                                                    }}
                                                />
                                            ) : null}
                                            {group.selectedReconView && (
                                                <Chip
                                                    size="small"
                                                    label={group.selectedReconView}
                                                    sx={{
                                                        backgroundColor: palette.paperBg,
                                                        color: palette.text,
                                                        border: `1px solid ${palette.borderSoft}`,
                                                    }}
                                                />
                                            )}
                                        </Stack>
                                    </Stack>
                                </Box>
                            ))}
                            <Button
                                variant="contained"
                                onClick={saveReconGroups}
                                disabled={loading || saving || reconGroups.length === 0}
                                sx={{textTransform: 'none', borderRadius: 2.5}}
                            >
                                {saving ? t('Saving...') : t('Save Lane Selections')}
                            </Button>
                        </Stack>
                    </Paper>

                    <Paper elevation={0} sx={{p: 2.25, borderRadius: '24px', border: `1px solid ${palette.border}`}}>
                        <Typography sx={{fontWeight: 800, color: palette.text, mb: 0.75}}>
                            {t('System Endpoint Profiles')}
                        </Typography>
                        <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, mb: 1.5}}>
                            {t('Define how each source system is accessed for this tenant. The selected mode stays at the system level so future cloud REST, RDS, or DAS changes do not require new sidebar modules.')}
                        </Typography>
                        <Stack spacing={1.5}>
                            {systemEndpointProfiles.map((profile) => {
                                const selectedOption = (profile.options || []).find(
                                    (option) => option.endpointMode === (endpointProfileForm[profile.systemName] || profile.selectedEndpointMode)
                                )
                                return (
                                    <Box
                                        key={profile.systemName}
                                        sx={{
                                            p: 1.5,
                                            borderRadius: '18px',
                                            backgroundColor: palette.paperBgAlt,
                                            border: `1px solid ${palette.borderSoft}`,
                                        }}
                                    >
                                        <Stack spacing={1}>
                                            <Box>
                                                <Typography sx={{fontSize: '0.9rem', fontWeight: 700, color: palette.text}}>
                                                    {profile.systemLabel}
                                                </Typography>
                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                    {selectedOption?.notes || t('Select the active endpoint mode and runtime for this system.')}
                                                </Typography>
                                            </Box>
                                            <TextField
                                                select
                                                size="small"
                                                label={t('Endpoint Mode')}
                                                value={endpointProfileForm[profile.systemName] || ''}
                                                onChange={(event) =>
                                                    setEndpointProfileForm((current) => ({
                                                        ...current,
                                                        [profile.systemName]: event.target.value,
                                                    }))
                                                }
                                            >
                                                {(profile.options || []).map((option) => (
                                                    <MenuItem
                                                        key={`${profile.systemName}-${option.endpointMode}`}
                                                        value={option.endpointMode}
                                                        disabled={!option.implemented}
                                                    >
                                                        {option.endpointMode}
                                                        {!option.implemented ? ` - ${t('Planned')}` : ''}
                                                    </MenuItem>
                                                ))}
                                            </TextField>
                                            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                                {selectedOption?.connectorLabel ? (
                                                    <Chip
                                                        size="small"
                                                        label={selectedOption.connectorLabel}
                                                        sx={{
                                                            backgroundColor: palette.chipBlueBg,
                                                            color: palette.chipBlueText,
                                                            fontWeight: 700,
                                                        }}
                                                    />
                                                ) : null}
                                                <Chip
                                                    size="small"
                                                    label={selectedOption?.implemented ? t('Implemented') : t('Planned')}
                                                    sx={{
                                                        backgroundColor: selectedOption?.implemented ? palette.tealChipBg : palette.cardBg,
                                                        color: selectedOption?.implemented ? palette.tealChipText : palette.textMuted,
                                                        fontWeight: 700,
                                                        border: selectedOption?.implemented ? 'none' : `1px solid ${palette.borderSoft}`,
                                                    }}
                                                />
                                                {selectedOption?.integrationConnectorKey ? (
                                                    <Chip
                                                        size="small"
                                                        label={selectedOption.integrationConnectorKey}
                                                        sx={{
                                                            backgroundColor: palette.paperBg,
                                                            color: palette.text,
                                                            border: `1px solid ${palette.borderSoft}`,
                                                        }}
                                                    />
                                                ) : null}
                                            </Stack>
                                        </Stack>
                                    </Box>
                                )
                            })}
                            <Button
                                variant="contained"
                                onClick={saveSystemEndpointProfiles}
                                disabled={loading || saving || systemEndpointProfiles.length === 0}
                                sx={{textTransform: 'none', borderRadius: 2.5}}
                            >
                                {saving ? t('Saving...') : t('Save Endpoint Profiles')}
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
                            <TextField size="small" type="number" label={t('Expires In Days')} value={keyForm.expiresInDays} onChange={(event) => setKeyForm((current) => ({...current, expiresInDays: Number(event.target.value || 90)}))}/>
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
                                        <Typography sx={{fontSize: '0.76rem', color: palette.textMuted, alignSelf: 'center'}}>
                                            {t('Expires')} {formatDate(apiKey.expiresAt)}
                                        </Typography>
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
