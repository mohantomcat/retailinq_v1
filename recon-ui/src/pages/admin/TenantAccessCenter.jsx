import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
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

function formatDateTime(value) {
    if (!value) return '-'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    })
}

function formatFinding(value) {
    return String(value || '')
        .replaceAll('_', ' ')
        .toLowerCase()
        .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function isHighPrivilegePermission(permission) {
    const normalized = String(permission || '').trim().toUpperCase()
    return Boolean(normalized) && (
        normalized.startsWith('ADMIN_')
        || normalized.endsWith('_MANAGE')
        || ['AUDIT_EXPORT', 'AUDIT_GLOBAL_VIEW', 'API_ACCESS_MANAGE', 'ACCESS_REVIEW_MANAGE'].includes(normalized)
    )
}

export default function TenantAccessCenter() {
    const palette = useAdminPalette()
    const {t} = useI18n()
    const [center, setCenter] = useState(null)
    const [users, setUsers] = useState([])
    const [roles, setRoles] = useState([])
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
        oidcRedirectUri: '',
        oidcScopes: 'openid profile email',
        oidcClientSecretRef: '',
        samlDisplayName: '',
        samlEntityId: '',
        samlAcsUrl: '',
        samlSsoUrl: '',
        samlIdpEntityId: '',
        samlIdpMetadataUrl: '',
        samlIdpVerificationCertificate: '',
        autoProvisionUsers: false,
        allowedEmailDomains: '',
        oidcUsernameClaim: 'preferred_username',
        oidcEmailClaim: 'email',
        oidcGroupsClaim: 'groups',
        samlEmailAttribute: '',
        samlGroupsAttribute: '',
        samlUsernameAttribute: 'uid',
        scimEnabled: false,
        scimBearerTokenRef: '',
        scimGroupPushEnabled: false,
        scimDeprovisionPolicy: 'DEACTIVATE',
        managerAccessReviewRemindersEnabled: false,
        managerAccessReviewReminderIntervalDays: 7,
        governanceNotificationMaxAttempts: 3,
        governanceNotificationBackoffMinutes: 15,
        managerAccessReviewAdditionalEmails: '',
        managerAccessReviewTeamsWebhookUrl: '',
        managerAccessReviewSlackWebhookUrl: '',
        managerAccessReviewEscalationEnabled: false,
        managerAccessReviewEscalationAfterDays: 3,
        managerAccessReviewEscalationEmailRecipients: '',
        managerAccessReviewEscalationTeamsWebhookUrl: '',
        managerAccessReviewEscalationSlackWebhookUrl: '',
        privilegedActionAlertsEnabled: false,
        privilegedActionAlertEmailRecipients: '',
        privilegedActionAlertTeamsWebhookUrl: '',
        privilegedActionAlertSlackWebhookUrl: '',
        managerAccessReviewReminderSubjectTemplate: '',
        managerAccessReviewReminderBodyTemplate: '',
        managerAccessReviewEscalationSubjectTemplate: '',
        managerAccessReviewEscalationBodyTemplate: '',
        privilegedActionAlertSubjectTemplate: '',
        privilegedActionAlertBodyTemplate: '',
    })
    const [groupMappingForm, setGroupMappingForm] = useState([
        {oidcGroup: '', roleId: '', active: true},
    ])
    const [reconGroupForm, setReconGroupForm] = useState({})
    const [endpointProfileForm, setEndpointProfileForm] = useState({})
    const [keyForm, setKeyForm] = useState(EMPTY_KEY_FORM)
    const [createdKey, setCreatedKey] = useState('')
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [success, setSuccess] = useState('')
    const [reviewDialog, setReviewDialog] = useState({
        open: false,
        userId: '',
        userLabel: '',
        decision: 'CERTIFIED',
        notes: '',
        deactivateUser: false,
    })
    const [ackDialog, setAckDialog] = useState({
        open: false,
        userId: '',
        userLabel: '',
        note: '',
    })
    const [emergencyForm, setEmergencyForm] = useState({
        userId: '',
        roleIds: [],
        expiresInHours: 4,
        justification: '',
        approvalNote: '',
    })
    const [revokeDialog, setRevokeDialog] = useState({
        open: false,
        grantId: '',
        userLabel: '',
        revokeNote: '',
    })

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
            setRoles(accessCenter?.roles || [])
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
        const mappings = Array.isArray(center?.oidcGroupRoleMappings)
            ? center.oidcGroupRoleMappings
            : []
        setGroupMappingForm(
            mappings.length
                ? mappings.map((mapping) => ({
                    oidcGroup: mapping.oidcGroup || '',
                    roleId: mapping.roleId ? String(mapping.roleId) : '',
                    active: mapping.active !== false,
                }))
                : [{oidcGroup: '', roleId: '', active: true}]
        )
    }, [center?.oidcGroupRoleMappings])

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

    const roleNameById = useMemo(
        () => new Map(roles.map((role) => [String(role.id), role.name || String(role.id)])),
        [roles]
    )

    const privilegedRoles = useMemo(
        () => roles.filter((role) => (role.permissionCodes || []).some(isHighPrivilegePermission)),
        [roles]
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

    const saveGroupRoleMappings = async () => {
        const mappings = groupMappingForm
            .map((mapping) => ({
                oidcGroup: mapping.oidcGroup.trim(),
                roleId: mapping.roleId || null,
                active: mapping.active !== false,
            }))
            .filter((mapping) => mapping.oidcGroup || mapping.roleId)

        if (mappings.some((mapping) => !mapping.oidcGroup || !mapping.roleId)) {
            setError(t('Each external mapping needs a group and role'))
            return
        }

        setSaving(true)
        try {
            const oidcGroupRoleMappings = await adminApi.saveIdentityGroupRoleMappings({mappings})
            setCenter((current) => ({...current, oidcGroupRoleMappings}))
            setSuccess(t('External group role mappings updated'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to save external group role mappings')
        } finally {
            setSaving(false)
        }
    }

    const addGroupMappingRow = () => {
        setGroupMappingForm((current) => [
            ...current,
            {oidcGroup: '', roleId: '', active: true},
        ])
    }

    const removeGroupMappingRow = (index) => {
        setGroupMappingForm((current) => {
            const next = current.filter((_, itemIndex) => itemIndex !== index)
            return next.length ? next : [{oidcGroup: '', roleId: '', active: true}]
        })
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

    const startQuarterlyAccessReviewCycle = async () => {
        setSaving(true)
        try {
            const response = await adminApi.startQuarterlyAccessReviewCycle()
            await loadData()
            setSuccess(
                `${t('Quarterly review cycle started')}: ${response?.queuedUsers ?? 0} ${t('users queued')}`
            )
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to start quarterly access review cycle')
        } finally {
            setSaving(false)
        }
    }

    const openReviewDialog = (finding, decision = 'CERTIFIED') => {
        setReviewDialog({
            open: true,
            userId: String(finding?.userId || ''),
            userLabel: finding?.fullName || finding?.username || '',
            decision,
            notes: '',
            deactivateUser: false,
        })
    }

    const submitReviewUserAccess = async () => {
        if (!reviewDialog.userId) {
            setError(t('Select a user to review'))
            return
        }
        if (!reviewDialog.notes.trim()) {
            setError(t('Review note is required'))
            return
        }
        setSaving(true)
        try {
            await adminApi.reviewUserAccess(reviewDialog.userId, {
                decision: reviewDialog.decision,
                notes: reviewDialog.notes,
                deactivateUser: reviewDialog.deactivateUser,
                nextReviewInDays: reviewDialog.decision === 'CERTIFIED' ? 90 : 14,
            })
            await loadData()
            setReviewDialog({
                open: false,
                userId: '',
                userLabel: '',
                decision: 'CERTIFIED',
                notes: '',
                deactivateUser: false,
            })
            setSuccess(t('User access review updated'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to update access review')
        } finally {
            setSaving(false)
        }
    }

    const openAckDialog = (finding) => {
        setAckDialog({
            open: true,
            userId: String(finding?.userId || ''),
            userLabel: finding?.fullName || finding?.username || '',
            note: '',
        })
    }

    const submitAcknowledgeReminder = async () => {
        if (!ackDialog.userId) {
            setError(t('Select a user to acknowledge'))
            return
        }
        if (!ackDialog.note.trim()) {
            setError(t('Acknowledgment note is required'))
            return
        }
        setSaving(true)
        try {
            await adminApi.acknowledgeAccessReviewReminder(ackDialog.userId, {
                note: ackDialog.note,
            })
            await loadData()
            setAckDialog({
                open: false,
                userId: '',
                userLabel: '',
                note: '',
            })
            setSuccess(t('Manager reminder acknowledged'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to acknowledge manager reminder')
        } finally {
            setSaving(false)
        }
    }

    const grantEmergencyAccess = async () => {
        if (!emergencyForm.userId) {
            setError(t('Select a user for emergency access'))
            return
        }
        if ((emergencyForm.roleIds || []).length === 0) {
            setError(t('Select at least one privileged role'))
            return
        }
        if (!String(emergencyForm.justification || '').trim() || !String(emergencyForm.approvalNote || '').trim()) {
            setError(t('Justification and approval note are required'))
            return
        }
        setSaving(true)
        try {
            await adminApi.grantEmergencyAccess({
                userId: emergencyForm.userId,
                roleIds: emergencyForm.roleIds,
                expiresInHours: Number(emergencyForm.expiresInHours || 4),
                justification: emergencyForm.justification,
                approvalNote: emergencyForm.approvalNote,
            })
            await loadData()
            setEmergencyForm({
                userId: '',
                roleIds: [],
                expiresInHours: 4,
                justification: '',
                approvalNote: '',
            })
            setSuccess(t('Emergency access granted'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to grant emergency access')
        } finally {
            setSaving(false)
        }
    }

    const openRevokeDialog = (grant) => {
        setRevokeDialog({
            open: true,
            grantId: String(grant?.id || ''),
            userLabel: grant?.fullName || grant?.username || '',
            revokeNote: '',
        })
    }

    const revokeEmergencyAccess = async () => {
        if (!revokeDialog.grantId) {
            setError(t('Select an emergency access grant'))
            return
        }
        if (!revokeDialog.revokeNote.trim()) {
            setError(t('Revoke note is required'))
            return
        }
        setSaving(true)
        try {
            await adminApi.revokeEmergencyAccess(revokeDialog.grantId, {
                revokeNote: revokeDialog.revokeNote,
            })
            await loadData()
            setRevokeDialog({
                open: false,
                grantId: '',
                userLabel: '',
                revokeNote: '',
            })
            setSuccess(t('Emergency access revoked'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to revoke emergency access')
        } finally {
            setSaving(false)
        }
    }

    const storeCatalog = center?.storeCatalog || []
    const apiKeys = center?.apiKeys || []
    const governance = center?.governance || {}
    const userFindings = governance.userFindings || []
    const apiKeyFindings = governance.apiKeyFindings || []
    const emergencyAccessGrants = center?.emergencyAccessGrants || []
    const privilegedActionAlerts = center?.privilegedActionAlerts || []
    const reconGroups = center?.reconGroups || []
    const systemEndpointProfiles = center?.systemEndpointProfiles || []
    const templatePlaceholderHint = t('Available placeholders: {{tenantId}}, {{dashboardUrl}}, {{pendingUserCount}}, {{pendingUsersList}}, {{managerName}}, {{escalationAfterDays}}, {{alertTitle}}, {{summary}}, {{actor}}, {{severity}}.')
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
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            <Chip
                                label={`${t('Preferred Login Mode')}: ${governance.preferredLoginMode || authForm.preferredLoginMode || 'LOCAL'}`}
                                sx={{backgroundColor: palette.chipBlueBg, color: palette.chipBlueText, fontWeight: 700}}
                            />
                            <Button
                                variant="outlined"
                                onClick={startQuarterlyAccessReviewCycle}
                                disabled={loading || saving}
                                sx={{textTransform: 'none', borderRadius: 2}}
                            >
                                {saving ? t('Starting...') : t('Start Quarterly Review')}
                            </Button>
                        </Stack>
                    </Box>

                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr 1fr', md: 'repeat(6, minmax(0, 1fr))'}, gap: 1}}>
                        {[
                            [t('Active Users'), governance.activeUsers ?? 0],
                            [t('Review Due'), governance.usersDueForReview ?? 0],
                            [t('Pending Manager'), governance.pendingManagerReviews ?? 0],
                            [t('Acknowledged'), governance.acknowledgedManagerReviews ?? 0],
                            [t('Escalated'), governance.escalatedManagerReviews ?? 0],
                            [t('High Privilege'), governance.highPrivilegeUsers ?? 0],
                            [t('Emergency Access'), governance.activeEmergencyAccessUsers ?? 0],
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
                                    <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                        {t('Manager')}: {finding.managerFullName || finding.managerUsername || t('Unassigned')}
                                    </Typography>
                                    {finding.accessReviewLastReminderAt ? (
                                        <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                            {t('Last reminder')}: {formatDateTime(finding.accessReviewLastReminderAt)}
                                        </Typography>
                                    ) : null}
                                    {finding.accessReviewReminderAcknowledgedAt ? (
                                        <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                            {t('Acknowledged')}: {formatDateTime(finding.accessReviewReminderAcknowledgedAt)} {t('by')} {finding.accessReviewReminderAcknowledgedBy || t('manager')}
                                        </Typography>
                                    ) : null}
                                    {finding.accessReviewLastEscalatedAt ? (
                                        <Typography sx={{fontSize: '0.74rem', color: palette.textMuted}}>
                                            {t('Escalated')}: {formatDateTime(finding.accessReviewLastEscalatedAt)}
                                        </Typography>
                                    ) : null}
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
                                <Stack direction="row" spacing={0.75} justifyContent="flex-end" flexWrap="wrap" useFlexGap>
                                    {finding.accessReviewStatus === 'PENDING_MANAGER' && finding.accessReviewLastReminderAt ? (
                                        <Button
                                            size="small"
                                            variant="text"
                                            onClick={() => openAckDialog(finding)}
                                            disabled={saving}
                                            sx={{textTransform: 'none', borderRadius: 2}}
                                        >
                                            {t('Acknowledge')}
                                        </Button>
                                    ) : null}
                                    <Button
                                        size="small"
                                        variant="contained"
                                        onClick={() => openReviewDialog(finding, 'CERTIFIED')}
                                        disabled={saving}
                                        sx={{textTransform: 'none', borderRadius: 2}}
                                    >
                                        {t('Certify')}
                                    </Button>
                                    <Button
                                        size="small"
                                        variant="outlined"
                                        onClick={() => openReviewDialog(finding, 'NEEDS_CHANGES')}
                                        disabled={saving}
                                        sx={{textTransform: 'none', borderRadius: 2}}
                                    >
                                        {t('Needs Changes')}
                                    </Button>
                                    <Button
                                        size="small"
                                        variant="outlined"
                                        color="warning"
                                        onClick={() => openReviewDialog(finding, 'REVOKE_REQUESTED')}
                                        disabled={saving}
                                        sx={{textTransform: 'none', borderRadius: 2}}
                                    >
                                        {t('Request Revoke')}
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
                        <FormControlLabel control={<Switch checked={!!authForm.scimEnabled} onChange={(event) => setAuthForm((current) => ({...current, scimEnabled: event.target.checked, scimGroupPushEnabled: event.target.checked ? current.scimGroupPushEnabled : false}))}/>} label={t('Enable SCIM Provisioning')}/>
                        <FormControlLabel control={<Switch checked={!!authForm.apiKeyAuthEnabled} onChange={(event) => setAuthForm((current) => ({...current, apiKeyAuthEnabled: event.target.checked}))}/>} label={t('Enable Tenant API Keys')}/>
                        <FormControlLabel control={<Switch checked={!!authForm.autoProvisionUsers} onChange={(event) => setAuthForm((current) => ({...current, autoProvisionUsers: event.target.checked}))}/>} label={t('Auto Provision SSO Users')}/>
                        <TextField size="small" label={t('Allowed Email Domains')} value={authForm.allowedEmailDomains || ''} onChange={(event) => setAuthForm((current) => ({...current, allowedEmailDomains: event.target.value}))} helperText={t('Comma-separated domains for SSO provisioning')}/>
                        <TextField size="small" label={t('OIDC Display Name')} value={authForm.oidcDisplayName || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcDisplayName: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Issuer URL')} value={authForm.oidcIssuerUrl || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcIssuerUrl: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Client Id')} value={authForm.oidcClientId || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcClientId: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Redirect URI')} value={authForm.oidcRedirectUri || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcRedirectUri: event.target.value}))} helperText={t('Use the login callback URL registered in the identity provider')}/>
                        <TextField size="small" label={t('OIDC Scopes')} value={authForm.oidcScopes || 'openid profile email'} onChange={(event) => setAuthForm((current) => ({...current, oidcScopes: event.target.value}))} helperText={t('openid profile email is the recommended baseline')}/>
                        <TextField size="small" label={t('OIDC Client Secret Reference')} value={authForm.oidcClientSecretRef || ''} onChange={(event) => setAuthForm((current) => ({...current, oidcClientSecretRef: event.target.value}))} helperText={t('Environment variable or JVM property name for confidential clients')}/>
                        <TextField size="small" label={t('OIDC Username Claim')} value={authForm.oidcUsernameClaim || 'preferred_username'} onChange={(event) => setAuthForm((current) => ({...current, oidcUsernameClaim: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Email Claim')} value={authForm.oidcEmailClaim || 'email'} onChange={(event) => setAuthForm((current) => ({...current, oidcEmailClaim: event.target.value}))}/>
                        <TextField size="small" label={t('OIDC Groups Claim')} value={authForm.oidcGroupsClaim || 'groups'} onChange={(event) => setAuthForm((current) => ({...current, oidcGroupsClaim: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Display Name')} value={authForm.samlDisplayName || ''} onChange={(event) => setAuthForm((current) => ({...current, samlDisplayName: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Entity Id')} value={authForm.samlEntityId || ''} onChange={(event) => setAuthForm((current) => ({...current, samlEntityId: event.target.value}))}/>
                        <TextField size="small" label={t('SAML ACS URL')} value={authForm.samlAcsUrl || ''} onChange={(event) => setAuthForm((current) => ({...current, samlAcsUrl: event.target.value}))} helperText={t('Use the ACS callback URL exposed by this application')}/>
                        <TextField size="small" label={t('SAML SSO URL')} value={authForm.samlSsoUrl || ''} onChange={(event) => setAuthForm((current) => ({...current, samlSsoUrl: event.target.value}))}/>
                        <TextField size="small" label={t('SAML IdP Entity Id')} value={authForm.samlIdpEntityId || ''} onChange={(event) => setAuthForm((current) => ({...current, samlIdpEntityId: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Metadata URL')} value={authForm.samlIdpMetadataUrl || ''} onChange={(event) => setAuthForm((current) => ({...current, samlIdpMetadataUrl: event.target.value}))} helperText={t('Optional. When set, metadata discovery is used for IdP details.')}/>
                        <TextField size="small" multiline minRows={4} label={t('SAML Verification Certificate')} value={authForm.samlIdpVerificationCertificate || ''} onChange={(event) => setAuthForm((current) => ({...current, samlIdpVerificationCertificate: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Email Attribute')} value={authForm.samlEmailAttribute || ''} onChange={(event) => setAuthForm((current) => ({...current, samlEmailAttribute: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Groups Attribute')} value={authForm.samlGroupsAttribute || ''} onChange={(event) => setAuthForm((current) => ({...current, samlGroupsAttribute: event.target.value}))}/>
                        <TextField size="small" label={t('SAML Username Attribute')} value={authForm.samlUsernameAttribute || 'uid'} onChange={(event) => setAuthForm((current) => ({...current, samlUsernameAttribute: event.target.value}))}/>
                        <TextField size="small" label={t('SCIM Bearer Token Reference')} value={authForm.scimBearerTokenRef || ''} onChange={(event) => setAuthForm((current) => ({...current, scimBearerTokenRef: event.target.value}))} helperText={t('Environment variable or JVM property name used to validate SCIM bearer tokens')}/>
                        <FormControlLabel control={<Switch checked={!!authForm.scimGroupPushEnabled} disabled={!authForm.scimEnabled} onChange={(event) => setAuthForm((current) => ({...current, scimGroupPushEnabled: event.target.checked}))}/>} label={t('Enable SCIM Group Push')}/>
                        <TextField
                            select
                            size="small"
                            label={t('SCIM Deprovision Policy')}
                            value={authForm.scimDeprovisionPolicy || 'DEACTIVATE'}
                            disabled={!authForm.scimEnabled}
                            onChange={(event) => setAuthForm((current) => ({...current, scimDeprovisionPolicy: event.target.value}))}
                            helperText={t('Deactivate keeps role assignments. Remove access also clears mapped application roles.')}
                        >
                            <MenuItem value="DEACTIVATE">{t('Deactivate')}</MenuItem>
                            <MenuItem value="REMOVE_ACCESS">{t('Remove Access')}</MenuItem>
                        </TextField>

                        <Box sx={{pt: 1.5, mt: 0.5, borderTop: `1px solid ${palette.borderSoft}`}}>
                            <Typography sx={{fontWeight: 800, color: palette.text, mb: 0.75}}>
                                {t('Governance Notifications')}
                            </Typography>
                            <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, mb: 1.25}}>
                                {t('Deliver manager access review reminders and privileged access alerts by email, Teams, and Slack.')}
                            </Typography>
                            <Stack spacing={1.25}>
                                <FormControlLabel
                                    control={
                                        <Switch
                                            checked={!!authForm.managerAccessReviewRemindersEnabled}
                                            onChange={(event) =>
                                                setAuthForm((current) => ({
                                                    ...current,
                                                    managerAccessReviewRemindersEnabled: event.target.checked,
                                                }))
                                            }
                                        />
                                    }
                                    label={t('Enable Manager Review Reminders')}
                                />
                                <TextField
                                    size="small"
                                    type="number"
                                    label={t('Reminder Interval (Days)')}
                                    value={authForm.managerAccessReviewReminderIntervalDays ?? 7}
                                    disabled={!authForm.managerAccessReviewRemindersEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewReminderIntervalDays: Number(event.target.value || 7),
                                        }))
                                    }
                                    helperText={t('Managers receive one reminder per pending review cycle at this cadence.')}
                                    inputProps={{min: 1, max: 30}}
                                />
                                <TextField
                                    size="small"
                                    type="number"
                                    label={t('Retry Attempts')}
                                    value={authForm.governanceNotificationMaxAttempts ?? 3}
                                    disabled={!authForm.managerAccessReviewRemindersEnabled && !authForm.privilegedActionAlertsEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            governanceNotificationMaxAttempts: Number(event.target.value || 3),
                                        }))
                                    }
                                    helperText={t('Queued email and webhook deliveries retry up to this many times before they fail closed.')}
                                    inputProps={{min: 1, max: 10}}
                                />
                                <TextField
                                    size="small"
                                    type="number"
                                    label={t('Retry Backoff (Minutes)')}
                                    value={authForm.governanceNotificationBackoffMinutes ?? 15}
                                    disabled={!authForm.managerAccessReviewRemindersEnabled && !authForm.privilegedActionAlertsEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            governanceNotificationBackoffMinutes: Number(event.target.value || 15),
                                        }))
                                    }
                                    helperText={t('Each retry waits longer using exponential backoff from this base interval.')}
                                    inputProps={{min: 1, max: 1440}}
                                />
                                <TextField
                                    size="small"
                                    label={t('Reminder Summary Emails')}
                                    value={authForm.managerAccessReviewAdditionalEmails || ''}
                                    disabled={!authForm.managerAccessReviewRemindersEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewAdditionalEmails: event.target.value,
                                        }))
                                    }
                                    helperText={t('Optional comma-separated email recipients for tenant-level reminder summaries.')}
                                />
                                <TextField
                                    size="small"
                                    label={t('Reminder Teams Webhook URL')}
                                    value={authForm.managerAccessReviewTeamsWebhookUrl || ''}
                                    disabled={!authForm.managerAccessReviewRemindersEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewTeamsWebhookUrl: event.target.value,
                                        }))
                                    }
                                />
                                <TextField
                                    size="small"
                                    label={t('Reminder Slack Webhook URL')}
                                    value={authForm.managerAccessReviewSlackWebhookUrl || ''}
                                    disabled={!authForm.managerAccessReviewRemindersEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewSlackWebhookUrl: event.target.value,
                                        }))
                                    }
                                />
                                <FormControlLabel
                                    control={
                                        <Switch
                                            checked={!!authForm.managerAccessReviewEscalationEnabled}
                                            disabled={!authForm.managerAccessReviewRemindersEnabled}
                                            onChange={(event) =>
                                                setAuthForm((current) => ({
                                                    ...current,
                                                    managerAccessReviewEscalationEnabled: event.target.checked,
                                                }))
                                            }
                                        />
                                    }
                                    label={t('Enable Manager Escalation')}
                                />
                                <TextField
                                    size="small"
                                    type="number"
                                    label={t('Escalate After (Days)')}
                                    value={authForm.managerAccessReviewEscalationAfterDays ?? 3}
                                    disabled={!authForm.managerAccessReviewEscalationEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewEscalationAfterDays: Number(event.target.value || 3),
                                        }))
                                    }
                                    helperText={t('Escalation triggers when a reminder is not acknowledged or completed within this many days.')}
                                    inputProps={{min: 1, max: 30}}
                                />
                                <TextField
                                    size="small"
                                    label={t('Escalation Emails')}
                                    value={authForm.managerAccessReviewEscalationEmailRecipients || ''}
                                    disabled={!authForm.managerAccessReviewEscalationEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewEscalationEmailRecipients: event.target.value,
                                        }))
                                    }
                                    helperText={t('Optional comma-separated escalation recipients for unworked manager reviews.')}
                                />
                                <TextField
                                    size="small"
                                    label={t('Escalation Teams Webhook URL')}
                                    value={authForm.managerAccessReviewEscalationTeamsWebhookUrl || ''}
                                    disabled={!authForm.managerAccessReviewEscalationEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewEscalationTeamsWebhookUrl: event.target.value,
                                        }))
                                    }
                                />
                                <TextField
                                    size="small"
                                    label={t('Escalation Slack Webhook URL')}
                                    value={authForm.managerAccessReviewEscalationSlackWebhookUrl || ''}
                                    disabled={!authForm.managerAccessReviewEscalationEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewEscalationSlackWebhookUrl: event.target.value,
                                        }))
                                    }
                                />
                                <TextField
                                    size="small"
                                    label={t('Reminder Subject Template')}
                                    value={authForm.managerAccessReviewReminderSubjectTemplate || ''}
                                    disabled={!authForm.managerAccessReviewRemindersEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewReminderSubjectTemplate: event.target.value,
                                        }))
                                    }
                                    helperText={templatePlaceholderHint}
                                />
                                <TextField
                                    size="small"
                                    multiline
                                    minRows={4}
                                    label={t('Reminder Body Template')}
                                    value={authForm.managerAccessReviewReminderBodyTemplate || ''}
                                    disabled={!authForm.managerAccessReviewRemindersEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewReminderBodyTemplate: event.target.value,
                                        }))
                                    }
                                    helperText={templatePlaceholderHint}
                                />
                                <TextField
                                    size="small"
                                    label={t('Escalation Subject Template')}
                                    value={authForm.managerAccessReviewEscalationSubjectTemplate || ''}
                                    disabled={!authForm.managerAccessReviewEscalationEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewEscalationSubjectTemplate: event.target.value,
                                        }))
                                    }
                                    helperText={templatePlaceholderHint}
                                />
                                <TextField
                                    size="small"
                                    multiline
                                    minRows={4}
                                    label={t('Escalation Body Template')}
                                    value={authForm.managerAccessReviewEscalationBodyTemplate || ''}
                                    disabled={!authForm.managerAccessReviewEscalationEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            managerAccessReviewEscalationBodyTemplate: event.target.value,
                                        }))
                                    }
                                    helperText={templatePlaceholderHint}
                                />
                                <FormControlLabel
                                    control={
                                        <Switch
                                            checked={!!authForm.privilegedActionAlertsEnabled}
                                            onChange={(event) =>
                                                setAuthForm((current) => ({
                                                    ...current,
                                                    privilegedActionAlertsEnabled: event.target.checked,
                                                }))
                                            }
                                        />
                                    }
                                    label={t('Enable Privileged Action Alerts')}
                                />
                                <TextField
                                    size="small"
                                    label={t('Privileged Alert Emails')}
                                    value={authForm.privilegedActionAlertEmailRecipients || ''}
                                    disabled={!authForm.privilegedActionAlertsEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            privilegedActionAlertEmailRecipients: event.target.value,
                                        }))
                                    }
                                    helperText={t('Comma-separated recipients for emergency access and privileged change alerts.')}
                                />
                                <TextField
                                    size="small"
                                    label={t('Privileged Alert Teams Webhook URL')}
                                    value={authForm.privilegedActionAlertTeamsWebhookUrl || ''}
                                    disabled={!authForm.privilegedActionAlertsEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            privilegedActionAlertTeamsWebhookUrl: event.target.value,
                                        }))
                                    }
                                />
                                <TextField
                                    size="small"
                                    label={t('Privileged Alert Slack Webhook URL')}
                                    value={authForm.privilegedActionAlertSlackWebhookUrl || ''}
                                    disabled={!authForm.privilegedActionAlertsEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            privilegedActionAlertSlackWebhookUrl: event.target.value,
                                        }))
                                    }
                                />
                                <TextField
                                    size="small"
                                    label={t('Privileged Alert Subject Template')}
                                    value={authForm.privilegedActionAlertSubjectTemplate || ''}
                                    disabled={!authForm.privilegedActionAlertsEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            privilegedActionAlertSubjectTemplate: event.target.value,
                                        }))
                                    }
                                    helperText={templatePlaceholderHint}
                                />
                                <TextField
                                    size="small"
                                    multiline
                                    minRows={4}
                                    label={t('Privileged Alert Body Template')}
                                    value={authForm.privilegedActionAlertBodyTemplate || ''}
                                    disabled={!authForm.privilegedActionAlertsEnabled}
                                    onChange={(event) =>
                                        setAuthForm((current) => ({
                                            ...current,
                                            privilegedActionAlertBodyTemplate: event.target.value,
                                        }))
                                    }
                                    helperText={templatePlaceholderHint}
                                />
                            </Stack>
                        </Box>
                        <Button variant="contained" onClick={saveAuthConfig} disabled={loading || saving} sx={{textTransform: 'none', borderRadius: 2.5}}>
                            {saving ? t('Saving...') : t('Save Auth Settings')}
                        </Button>

                        <Box sx={{pt: 1.5, mt: 0.5, borderTop: `1px solid ${palette.borderSoft}`}}>
                            <Typography sx={{fontWeight: 800, color: palette.text, mb: 0.75}}>
                                {t('External Group Role Mapping')}
                            </Typography>
                            <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, mb: 1.25}}>
                                {t('Map identity provider groups or roles to application roles for OIDC, SAML, and SCIM access.')}
                            </Typography>
                            <Stack spacing={1.25}>
                                {groupMappingForm.map((mapping, index) => (
                                    <Box
                                        key={`oidc-mapping-${index}`}
                                        sx={{
                                            display: 'grid',
                                            gridTemplateColumns: {xs: '1fr', md: 'minmax(180px, 1.4fr) minmax(180px, 1fr) auto auto'},
                                            gap: 1,
                                            alignItems: 'center',
                                            p: 1.25,
                                            borderRadius: '16px',
                                            backgroundColor: palette.paperBgAlt,
                                            border: `1px solid ${palette.borderSoft}`,
                                        }}
                                    >
                                        <TextField
                                            size="small"
                                            label={t('External Group')}
                                            value={mapping.oidcGroup}
                                            onChange={(event) =>
                                                setGroupMappingForm((current) =>
                                                    current.map((item, itemIndex) =>
                                                        itemIndex === index
                                                            ? {...item, oidcGroup: event.target.value}
                                                            : item
                                                    )
                                                )
                                            }
                                        />
                                        <TextField
                                            select
                                            size="small"
                                            label={t('Application Role')}
                                            value={mapping.roleId}
                                            onChange={(event) =>
                                                setGroupMappingForm((current) =>
                                                    current.map((item, itemIndex) =>
                                                        itemIndex === index
                                                            ? {...item, roleId: event.target.value}
                                                            : item
                                                    )
                                                )
                                            }
                                        >
                                            <MenuItem value="">{t('Select a role')}</MenuItem>
                                            {roles.map((role) => (
                                                <MenuItem key={role.id} value={String(role.id)}>
                                                    {role.name}
                                                </MenuItem>
                                            ))}
                                        </TextField>
                                        <FormControlLabel
                                            control={
                                                <Switch
                                                    checked={mapping.active !== false}
                                                    onChange={(event) =>
                                                        setGroupMappingForm((current) =>
                                                            current.map((item, itemIndex) =>
                                                                itemIndex === index
                                                                    ? {...item, active: event.target.checked}
                                                                    : item
                                                            )
                                                        )
                                                    }
                                                />
                                            }
                                            label={t('Active')}
                                        />
                                        <Button
                                            size="small"
                                            variant="text"
                                            onClick={() => removeGroupMappingRow(index)}
                                            disabled={saving}
                                            sx={{textTransform: 'none'}}
                                        >
                                            {t('Remove')}
                                        </Button>
                                    </Box>
                                ))}
                                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                                    <Button variant="outlined" onClick={addGroupMappingRow} disabled={saving} sx={{textTransform: 'none', borderRadius: 2.5}}>
                                        {t('Add Mapping')}
                                    </Button>
                                    <Button variant="contained" onClick={saveGroupRoleMappings} disabled={loading || saving || roles.length === 0} sx={{textTransform: 'none', borderRadius: 2.5}}>
                                        {saving ? t('Saving...') : t('Save External Mappings')}
                                    </Button>
                                </Stack>
                            </Stack>
                        </Box>
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
                            {t('Privileged Access')}
                        </Typography>
                        <Typography sx={{fontSize: '0.78rem', color: palette.textMuted, mb: 1.5}}>
                            {t('Grant temporary admin access with approval evidence, automatic expiry, and an audit-backed alert feed.')}
                        </Typography>
                        <Stack spacing={1.25}>
                            <TextField
                                select
                                size="small"
                                label={t('User')}
                                value={emergencyForm.userId}
                                onChange={(event) => setEmergencyForm((current) => ({...current, userId: event.target.value}))}
                            >
                                <MenuItem value="">{t('Select a user')}</MenuItem>
                                {users.filter((user) => user.active).map((user) => (
                                    <MenuItem key={user.id} value={String(user.id)}>
                                        {user.fullName || user.username}
                                    </MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                select
                                size="small"
                                label={t('Privileged Roles')}
                                SelectProps={{
                                    multiple: true,
                                    value: emergencyForm.roleIds,
                                    renderValue: (selected) =>
                                        normalizeSelectArray(selected)
                                            .map((id) => roleNameById.get(id) || id)
                                            .join(', '),
                                }}
                                value={emergencyForm.roleIds}
                                onChange={(event) =>
                                    setEmergencyForm((current) => ({
                                        ...current,
                                        roleIds: normalizeSelectArray(event.target.value),
                                    }))
                                }
                            >
                                {privilegedRoles.map((role) => (
                                    <MenuItem key={role.id} value={String(role.id)}>
                                        {role.name}
                                    </MenuItem>
                                ))}
                            </TextField>
                            <TextField
                                size="small"
                                type="number"
                                label={t('Expires In Hours')}
                                value={emergencyForm.expiresInHours}
                                onChange={(event) =>
                                    setEmergencyForm((current) => ({
                                        ...current,
                                        expiresInHours: Number(event.target.value || 4),
                                    }))
                                }
                                inputProps={{min: 1, max: 24}}
                            />
                            <TextField
                                size="small"
                                multiline
                                minRows={2}
                                label={t('Justification')}
                                value={emergencyForm.justification}
                                onChange={(event) => setEmergencyForm((current) => ({...current, justification: event.target.value}))}
                            />
                            <TextField
                                size="small"
                                multiline
                                minRows={2}
                                label={t('Approval Note')}
                                value={emergencyForm.approvalNote}
                                onChange={(event) => setEmergencyForm((current) => ({...current, approvalNote: event.target.value}))}
                            />
                            <Button
                                variant="contained"
                                onClick={grantEmergencyAccess}
                                disabled={loading || saving || privilegedRoles.length === 0}
                                sx={{textTransform: 'none', borderRadius: 2.5}}
                            >
                                {saving ? t('Saving...') : t('Grant Emergency Access')}
                            </Button>

                            <Box sx={{pt: 1.5, borderTop: `1px solid ${palette.borderSoft}`}}>
                                <Typography sx={{fontWeight: 800, color: palette.text, mb: 1}}>
                                    {t('Recent Emergency Grants')}
                                </Typography>
                                <Stack spacing={1}>
                                    {emergencyAccessGrants.slice(0, 6).map((grant) => (
                                        <Paper key={grant.id} elevation={0} sx={{p: 1.25, borderRadius: '16px', backgroundColor: palette.paperBgAlt, border: `1px solid ${palette.borderSoft}`}}>
                                            <Stack spacing={0.75}>
                                                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1, flexWrap: 'wrap'}}>
                                                    <Box>
                                                        <Typography sx={{fontSize: '0.88rem', fontWeight: 700, color: palette.text}}>
                                                            {grant.fullName || grant.username}
                                                        </Typography>
                                                        <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                            {(grant.roles || []).map((role) => role.name).join(', ') || t('No roles')}
                                                        </Typography>
                                                    </Box>
                                                    <Stack direction="row" spacing={1} alignItems="center">
                                                        <Chip
                                                            size="small"
                                                            label={grant.active ? t('Active') : t('Closed')}
                                                            sx={{
                                                                backgroundColor: grant.active ? palette.chipRedBg : palette.chipNeutralBg,
                                                                color: grant.active ? palette.chipRedText : palette.chipNeutralText,
                                                                fontWeight: 700,
                                                            }}
                                                        />
                                                        {grant.active ? (
                                                            <Button
                                                                size="small"
                                                                onClick={() => openRevokeDialog(grant)}
                                                                disabled={saving}
                                                                sx={{textTransform: 'none'}}
                                                            >
                                                                {t('Revoke')}
                                                            </Button>
                                                        ) : null}
                                                    </Stack>
                                                </Box>
                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                    {t('Expires')} {formatDateTime(grant.expiresAt)} · {t('Approved by')} {grant.grantedBy}
                                                </Typography>
                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                    {grant.justification}
                                                </Typography>
                                            </Stack>
                                        </Paper>
                                    ))}
                                    {emergencyAccessGrants.length === 0 ? (
                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('No emergency access grants recorded.')}
                                        </Typography>
                                    ) : null}
                                </Stack>
                            </Box>

                            <Box sx={{pt: 1.5, borderTop: `1px solid ${palette.borderSoft}`}}>
                                <Typography sx={{fontWeight: 800, color: palette.text, mb: 1}}>
                                    {t('Privileged Action Alerts')}
                                </Typography>
                                <Stack spacing={1}>
                                    {privilegedActionAlerts.slice(0, 8).map((alertItem) => (
                                        <Box key={alertItem.id} sx={{p: 1.25, borderRadius: '16px', backgroundColor: palette.paperBgAlt, border: `1px solid ${palette.borderSoft}`}}>
                                            <Stack direction="row" justifyContent="space-between" spacing={1} flexWrap="wrap" useFlexGap>
                                                <Box>
                                                    <Typography sx={{fontSize: '0.86rem', fontWeight: 700, color: palette.text}}>
                                                        {alertItem.title}
                                                    </Typography>
                                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                        {alertItem.detail || alertItem.actionType}
                                                    </Typography>
                                                </Box>
                                                <Chip
                                                    size="small"
                                                    label={alertItem.severity || 'HIGH'}
                                                    sx={{
                                                        backgroundColor: alertItem.severity === 'CRITICAL' ? palette.chipRedBg : palette.chipBlueBg,
                                                        color: alertItem.severity === 'CRITICAL' ? palette.chipRedText : palette.chipBlueText,
                                                        fontWeight: 700,
                                                    }}
                                                />
                                            </Stack>
                                            <Typography sx={{fontSize: '0.74rem', color: palette.textMuted, mt: 0.75}}>
                                                {alertItem.actor || t('system')} · {formatDateTime(alertItem.eventAt)}
                                            </Typography>
                                        </Box>
                                    ))}
                                    {privilegedActionAlerts.length === 0 ? (
                                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted}}>
                                            {t('No privileged action alerts.')}
                                        </Typography>
                                    ) : null}
                                </Stack>
                            </Box>
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

            <Dialog
                open={ackDialog.open}
                onClose={() => setAckDialog((current) => ({...current, open: false}))}
                maxWidth="sm"
                fullWidth
            >
                <DialogTitle>{t('Acknowledge Reminder')}</DialogTitle>
                <DialogContent sx={{display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important'}}>
                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                        {ackDialog.userLabel || t('Selected user')}
                    </Typography>
                    <TextField
                        size="small"
                        multiline
                        minRows={3}
                        label={t('Acknowledgment Note')}
                        value={ackDialog.note}
                        onChange={(event) =>
                            setAckDialog((current) => ({
                                ...current,
                                note: event.target.value,
                            }))
                        }
                    />
                </DialogContent>
                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button
                        onClick={() => setAckDialog((current) => ({...current, open: false}))}
                        sx={{textTransform: 'none', color: palette.textMuted}}
                    >
                        {t('Cancel')}
                    </Button>
                    <Button
                        variant="contained"
                        onClick={submitAcknowledgeReminder}
                        disabled={saving}
                        sx={{textTransform: 'none', borderRadius: 2}}
                    >
                        {saving ? t('Saving...') : t('Acknowledge')}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog
                open={reviewDialog.open}
                onClose={() => setReviewDialog((current) => ({...current, open: false}))}
                maxWidth="sm"
                fullWidth
            >
                <DialogTitle>{t('Review Access')}</DialogTitle>
                <DialogContent sx={{display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important'}}>
                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                        {reviewDialog.userLabel || t('Selected user')}
                    </Typography>
                    <TextField
                        select
                        size="small"
                        label={t('Decision')}
                        value={reviewDialog.decision}
                        onChange={(event) =>
                            setReviewDialog((current) => ({
                                ...current,
                                decision: event.target.value,
                            }))
                        }
                    >
                        <MenuItem value="CERTIFIED">{t('Certified')}</MenuItem>
                        <MenuItem value="NEEDS_CHANGES">{t('Needs Changes')}</MenuItem>
                        <MenuItem value="REVOKE_REQUESTED">{t('Revoke Requested')}</MenuItem>
                    </TextField>
                    <TextField
                        size="small"
                        multiline
                        minRows={3}
                        label={t('Review Note')}
                        value={reviewDialog.notes}
                        onChange={(event) =>
                            setReviewDialog((current) => ({
                                ...current,
                                notes: event.target.value,
                            }))
                        }
                    />
                    <FormControlLabel
                        control={
                            <Switch
                                checked={reviewDialog.deactivateUser}
                                onChange={(event) =>
                                    setReviewDialog((current) => ({
                                        ...current,
                                        deactivateUser: event.target.checked,
                                    }))
                                }
                            />
                        }
                        label={t('Deactivate user as part of this review')}
                    />
                </DialogContent>
                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button
                        onClick={() => setReviewDialog((current) => ({...current, open: false}))}
                        sx={{textTransform: 'none', color: palette.textMuted}}
                    >
                        {t('Cancel')}
                    </Button>
                    <Button
                        variant="contained"
                        onClick={submitReviewUserAccess}
                        disabled={saving}
                        sx={{textTransform: 'none', borderRadius: 2}}
                    >
                        {saving ? t('Saving...') : t('Save Review')}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog
                open={revokeDialog.open}
                onClose={() => setRevokeDialog((current) => ({...current, open: false}))}
                maxWidth="sm"
                fullWidth
            >
                <DialogTitle>{t('Revoke Emergency Access')}</DialogTitle>
                <DialogContent sx={{display: 'flex', flexDirection: 'column', gap: 2, pt: '16px !important'}}>
                    <Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>
                        {revokeDialog.userLabel || t('Selected user')}
                    </Typography>
                    <TextField
                        size="small"
                        multiline
                        minRows={3}
                        label={t('Revoke Note')}
                        value={revokeDialog.revokeNote}
                        onChange={(event) =>
                            setRevokeDialog((current) => ({
                                ...current,
                                revokeNote: event.target.value,
                            }))
                        }
                    />
                </DialogContent>
                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button
                        onClick={() => setRevokeDialog((current) => ({...current, open: false}))}
                        sx={{textTransform: 'none', color: palette.textMuted}}
                    >
                        {t('Cancel')}
                    </Button>
                    <Button
                        variant="contained"
                        color="error"
                        onClick={revokeEmergencyAccess}
                        disabled={saving}
                        sx={{textTransform: 'none', borderRadius: 2}}
                    >
                        {saving ? t('Saving...') : t('Revoke')}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    )
}
