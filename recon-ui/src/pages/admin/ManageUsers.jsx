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
    Divider,
    FormControlLabel,
    Switch,
    TextField,
    Typography,
} from '@mui/material'
import AdminTable from '../../components/AdminTable'
import TransferList from '../../components/TransferList'
import UserRowMenu from '../../components/admin/UserRowMenu'
import UserDetailsPanel from '../../components/admin/UserDetailsPanel'
import UserFilters from '../../components/admin/UserFilters'
import RolesDialog from '../../components/admin/RolesDialog'
import StoresDialog from '../../components/admin/StoresDialog'
import {adminApi} from '../../services/adminApi'
import {useI18n} from '../../context/I18nContext'

const TENANT = 'tenant-india'

function getThemeMode() {
    return document.documentElement.getAttribute('data-recon-theme') === 'dark'
        ? 'Dark'
        : 'Light'
}

function getPalette(themeMode) {
    const isDark = themeMode === 'Dark'

    return {
        isDark,
        pageText: isDark ? '#E2E8F0' : '#0F172A',
        text: isDark ? '#E2E8F0' : '#0F172A',
        textMuted: isDark ? '#94A3B8' : '#64748B',
        textSoft: isDark ? '#64748B' : '#94A3B8',
        paperBg: isDark ? '#0F172A' : '#FFFFFF',
        paperBgAlt: isDark ? '#111827' : '#F8FAFC',
        border: isDark ? '#1E293B' : '#E5E7EB',
        borderSoft: isDark ? '#243041' : '#F1F5F9',
        inputBg: isDark ? '#0F172A' : '#FFFFFF',
        dangerText: '#EF4444',
        dangerBg: isDark ? '#2A1215' : '#FFF5F5',
        successBg: isDark ? '#0F172A' : '#F0FDF4',
        successText: '#16A34A',
        chipBlueBg: isDark ? '#0F172A' : '#EFF6FF',
        chipBlueText: '#2563EB',
        chipGreenBg: isDark ? '#0F172A' : '#F0FDF4',
        chipGreenText: '#16A34A',
        chipRedBg: isDark ? '#2A1215' : '#FFF5F5',
        chipRedText: '#EF4444',
        dialogBg: isDark
            ? 'linear-gradient(180deg, #0F172A 0%, #111827 100%)'
            : 'linear-gradient(180deg, #FFFFFF 0%, #FCFDFE 100%)',
    }
}

export default function ManageUsers() {
    const [themeMode, setThemeMode] = useState(getThemeMode())
    const {t} = useI18n()
    const [users, setUsers] = useState([])
    const [roles, setRoles] = useState([])
    const [stores, setStores] = useState([])
    const [loading, setLoading] = useState(true)
    const [dialog, setDialog] = useState(null)
    const [selected, setSelected] = useState(null)
    const [error, setError] = useState('')
    const [saving, setSaving] = useState(false)

    const [form, setForm] = useState({
        username: '',
        email: '',
        password: '',
        fullName: '',
        tenantId: TENANT,
    })
    const [selectedRoles, setSelectedRoles] = useState([])
    const [selectedStores, setSelectedStores] = useState([])
    const [restrictStoreAccess, setRestrictStoreAccess] =
        useState(false)

    const [selectedRow, setSelectedRow] = useState(null)

    const [searchText, setSearchText] = useState('')
    const [roleFilter, setRoleFilter] = useState('all')
    const [storeFilter, setStoreFilter] = useState('')
    const [statusFilter, setStatusFilter] = useState('all')

    const [resetPasswordForm, setResetPasswordForm] = useState({
        newPassword: '',
        confirmPassword: '',
    })

    useEffect(() => {
        const observer = new MutationObserver(() => {
            setThemeMode(getThemeMode())
        })

        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['data-recon-theme'],
        })

        return () => observer.disconnect()
    }, [])

    useEffect(() => {
        loadAll()
    }, [])

    const palette = useMemo(() => getPalette(themeMode), [themeMode])

    const textFieldSx = {
        '& .MuiOutlinedInput-root': {
            color: palette.text,
            backgroundColor: palette.inputBg,
            '& fieldset': {
                borderColor: palette.border,
            },
            '&:hover fieldset': {
                borderColor: palette.textSoft,
            },
            '&.Mui-focused fieldset': {
                borderColor: '#2563EB',
            },
            '&.Mui-disabled': {
                WebkitTextFillColor: palette.textMuted,
                backgroundColor: palette.paperBgAlt,
            },
        },
        '& .MuiInputLabel-root': {
            color: palette.textMuted,
        },
        '& .MuiInputBase-input.Mui-disabled': {
            WebkitTextFillColor: palette.textMuted,
        },
        '& .MuiFormHelperText-root': {
            color: palette.textMuted,
        },
    }

    const dialogPaperSx = {
        borderRadius: '24px',
        border: `1px solid ${palette.border}`,
        background: palette.dialogBg,
    }

    const loadAll = async () => {
        setLoading(true)
        try {
            const [u, r] = await Promise.all([
                adminApi.getUsers(),
                adminApi.getRoles(),
            ])
            setUsers(u)
            setRoles(r)

            const allStoreIds = Array.from(
                new Set(
                    u.flatMap((user) =>
                        (user.storeIds || []).map(String)
                    )
                )
            ).sort((a, b) => a.localeCompare(b))

            setStores(allStoreIds)

            setSelectedRow((prev) => {
                if (!prev?.id) return prev
                return u.find((x) => x.id === prev.id) || prev
            })
        } catch (e) {
            setError(e.message)
        } finally {
            setLoading(false)
        }
    }

    const openCreate = () => {
        setForm({
            username: '',
            email: '',
            password: '',
            fullName: '',
            tenantId: TENANT,
        })
        setSelectedRoles([])
        setSelectedStores([])
        setRestrictStoreAccess(false)
        setError('')
        setDialog('create')
    }

    const openEdit = (user) => {
        setSelected(user)
        setSelectedRow(user)
        setForm({
            username: user.username,
            email: user.email,
            fullName: user.fullName || '',
            password: '',
            tenantId: user.tenantId,
        })
        setError('')
        setDialog('edit')
    }

    const openRoles = (user) => {
        setSelected(user)
        setSelectedRow(user)
        setSelectedRoles(user.roles?.map((r) => r.id) || [])
        setError('')
        setDialog('roles')
    }

    const openStores = (user) => {
        setSelected(user)
        setSelectedRow(user)
        setSelectedStores([...(user.storeIds || []).map(String)])
        setRestrictStoreAccess((user.storeIds || []).length > 0)
        setError('')
        setDialog('stores')
    }

    const openDeactivate = (user) => {
        setSelected(user)
        setSelectedRow(user)
        setError('')
        setDialog('deactivate')
    }

    const openDelete = (user) => {
        setSelected(user)
        setSelectedRow(user)
        setError('')
        setDialog('delete')
    }

    const openResetPassword = (user) => {
        setSelected(user)
        setSelectedRow(user)
        setResetPasswordForm({
            newPassword: '',
            confirmPassword: '',
        })
        setError('')
        setDialog('resetPassword')
    }

    const closeDialog = () => {
        setDialog(null)
        setSelected(null)
        setError('')
    }

    const handleCreate = async () => {
        if (!form.username || !form.email || !form.password) {
            setError(t('Username, email and password are required'))
            return
        }

        setSaving(true)
        try {
            await adminApi.createUser({
                ...form,
                roleIds: selectedRoles,
                storeIds: restrictStoreAccess ? selectedStores : [],
            })
            await loadAll()
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleEdit = async () => {
        setSaving(true)
        try {
            await adminApi.updateUser(selected.id, form)
            await loadAll()
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleAssignRoles = async () => {
        setSaving(true)
        try {
            await adminApi.assignRoles(selected.id, selectedRoles)
            await loadAll()
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleAssignStores = async () => {
        setSaving(true)
        try {
            await adminApi.assignStores(
                selected.id,
                restrictStoreAccess ? selectedStores : []
            )
            await loadAll()
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleDeactivate = async () => {
        setSaving(true)
        try {
            await adminApi.deactivateUser(selected.id)
            await loadAll()
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleActivate = async () => {
        setSaving(true)
        try {
            await adminApi.activateUser(selected.id)
            await loadAll()
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleDelete = async () => {
        setSaving(true)
        try {
            await adminApi.deleteUser(selected.id)
            await loadAll()
            setSelectedRow((prev) =>
                prev?.id === selected?.id ? null : prev
            )
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleResetPassword = async () => {
        const {newPassword, confirmPassword} = resetPasswordForm

        if (!newPassword || !confirmPassword) {
            setError(t('New password and confirm password are required'))
            return
        }

        if (newPassword !== confirmPassword) {
            setError(t('Passwords do not match'))
            return
        }

        if (newPassword.length < 6) {
            setError(t('Password must be at least 6 characters'))
            return
        }

        setSaving(true)
        try {
            await adminApi.resetPassword(selected.id, newPassword)
            await loadAll()
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleMenuResetPassword = (user) => {
        openResetPassword(user)
    }

    const handleToggleActive = (user) => {
        setSelected(user)
        setSelectedRow(user)
        setError('')

        if (user?.active) {
            setDialog('deactivate')
            return
        }

        setDialog('activate')
    }

    const formatLastLogin = (value) => {
        if (!value) return '-'

        const dt = new Date(value)
        if (Number.isNaN(dt.getTime())) return value

        return dt.toLocaleString('en-IN', {
            day: '2-digit',
            month: 'short',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        })
    }

    const filteredUsers = useMemo(() => {
        return users.filter((user) => {
            const search = searchText.trim().toLowerCase()
            const username = (user.username || '').toLowerCase()
            const fullName = (user.fullName || '').toLowerCase()
            const email = (user.email || '').toLowerCase()

            const matchesSearch =
                !search ||
                username.includes(search) ||
                fullName.includes(search) ||
                email.includes(search)

            const matchesRole =
                roleFilter === 'all' ||
                (user.roles || []).some((r) => r.id === roleFilter)

            const storeText = storeFilter.trim()
            const storeIds = (user.storeIds || []).map(String)

            const matchesStore =
                !storeText ||
                storeIds.some((id) => id.includes(storeText))

            const matchesStatus =
                statusFilter === 'all' ||
                (statusFilter === 'active' && user.active) ||
                (statusFilter === 'inactive' && !user.active)

            return (
                matchesSearch &&
                matchesRole &&
                matchesStore &&
                matchesStatus
            )
        })
    }, [users, searchText, roleFilter, storeFilter, statusFilter])

    const createStoreItems = useMemo(
        () =>
            stores.map((storeId) => ({
                id: String(storeId),
                label: `Store ${storeId}`,
                badge: String(storeId),
            })),
        [stores]
    )

    const createRoleItems = useMemo(
        () =>
            roles.map((role) => ({
                id: role.id,
                label: role.name,
                badge: role.permissionCodes?.length
                    ? `${role.permissionCodes.length} perms`
                    : '',
            })),
        [roles]
    )

    const columns = [
        {
            key: 'username',
            label: t('Username'),
            noWrap: true,
            render: (v) => (
                <Typography
                    sx={{
                        fontSize: '0.92rem',
                        fontWeight: 700,
                        color: palette.text,
                    }}
                >
                    {v}
                </Typography>
            ),
        },
        {
            key: 'fullName',
            label: t('Name'),
            render: (v) => v || '-',
        },
        {
            key: 'email',
            label: t('Email'),
            noWrap: true,
            render: (v) => (
                <Typography
                    sx={{
                        fontSize: '0.9rem',
                        color: palette.textMuted,
                    }}
                >
                    {v || '-'}
                </Typography>
            ),
        },
        {
            key: 'roles',
            label: t('Roles'),
            noWrap: true,
            render: (v) => {
                const arr = v || []
                if (arr.length === 0) return '-'
                if (arr.length === 1) return arr[0]?.name || '-'
                return `${arr[0]?.name || 'Role'} +${arr.length - 1}`
            },
        },
        {
            key: 'storeIds',
            label: t('Assigned Stores'),
            noWrap: true,
            render: (v) => {
                const arr = v || []
                if (arr.length === 0) {
                    return (
                        <Chip
                            label="All"
                            size="small"
                            sx={{
                                height: 24,
                                fontSize: '0.75rem',
                                backgroundColor: palette.chipGreenBg,
                                color: palette.chipGreenText,
                                fontWeight: 600,
                            }}
                        />
                    )
                }
                if (arr.length === 1) return `Store ${arr[0]}`
                return `${arr.length} stores`
            },
        },
        {
            key: 'active',
            label: t('Status'),
            noWrap: true,
            render: (v) => (
                <Chip
                    label={v ? 'Active' : 'Inactive'}
                    size="small"
                    sx={{
                        height: 24,
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        backgroundColor: v
                            ? palette.chipGreenBg
                            : palette.chipRedBg,
                        color: v
                            ? palette.chipGreenText
                            : palette.chipRedText,
                    }}
                />
            ),
        },
        {
            key: 'lastLogin',
            label: t('Last Login'),
            noWrap: true,
            render: (v, row) =>
                formatLastLogin(
                    v ||
                    row.lastLoginAt ||
                    row.lastLoginTs ||
                    row.lastLoginTime ||
                    row.lastLoginDateTime
                ),
        },
        {
            key: 'menu',
            label: '',
            width: 70,
            align: 'right',
            noWrap: true,
            render: (_, row) => (
                <UserRowMenu
                    row={row}
                    onEdit={openEdit}
                    onManageRoles={openRoles}
                    onManageStores={openStores}
                    onResetPassword={handleMenuResetPassword}
                    onToggleActive={handleToggleActive}
                    onDelete={openDelete}
                />
            ),
        },
    ]

    const formField = (label, key, opts = {}) => (
        <TextField
            label={label}
            value={form[key]}
            onChange={(e) =>
                setForm((f) => ({...f, [key]: e.target.value}))
            }
            fullWidth
            size="small"
            sx={textFieldSx}
            {...opts}
        />
    )

    return (
        <Box sx={{px: 4, py: 3, maxWidth: 1480}}>
            {error && dialog === null && (
                <Alert severity="error" sx={{mb: 2, borderRadius: 3}}>
                    {error}
                </Alert>
            )}

            <UserFilters
                searchText={searchText}
                onSearchTextChange={setSearchText}
                roleFilter={roleFilter}
                onRoleFilterChange={setRoleFilter}
                storeFilter={storeFilter}
                onStoreFilterChange={setStoreFilter}
                statusFilter={statusFilter}
                onStatusFilterChange={setStatusFilter}
                roleOptions={roles}
            />

            <AdminTable
                title={t('Manage Users')}
                columns={columns}
                rows={filteredUsers}
                loading={loading}
                onAdd={openCreate}
                addLabel={t('Add User')}
                selectedRow={selectedRow}
                onSelectRow={setSelectedRow}
                tableMaxHeight={540}
            />

            <UserDetailsPanel
                user={selectedRow}
                formatLastLogin={formatLastLogin}
            />

            <Dialog
                open={dialog === 'create'}
                onClose={closeDialog}
                maxWidth="lg"
                fullWidth
                PaperProps={{sx: dialogPaperSx}}
            >
                <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                    Create User
                </DialogTitle>

                <DialogContent
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 2.5,
                        pt: '16px !important',
                    }}
                >
                    {error && (
                        <Alert severity="error" sx={{borderRadius: 2}}>
                            {error}
                        </Alert>
                    )}

                    <Box
                        sx={{
                            display: 'grid',
                            gridTemplateColumns: {
                                xs: '1fr',
                                md: '1fr 1fr',
                            },
                            gap: 2,
                        }}
                    >
                        {formField('Full Name', 'fullName')}
                        {formField('Username', 'username')}
                        {formField('Email', 'email', {
                            type: 'email',
                        })}
                        {formField('Password', 'password', {
                            type: 'password',
                        })}
                    </Box>

                    <Divider sx={{borderColor: palette.borderSoft}}/>

                    <Box>
                        <Typography
                            sx={{
                                fontSize: '0.95rem',
                                fontWeight: 700,
                                color: palette.text,
                                mb: 0.5,
                            }}
                        >
                            Role Assignment
                        </Typography>

                        <Typography
                            sx={{
                                fontSize: '0.82rem',
                                color: palette.textMuted,
                                mb: 1.5,
                            }}
                        >
                            Select the roles to assign to this user.
                        </Typography>

                        <TransferList
                            items={createRoleItems}
                            value={selectedRoles}
                            onChange={setSelectedRoles}
                            leftTitle="Available Roles"
                            rightTitle="Assigned Roles"
                            leftSearchPlaceholder="Search available roles..."
                            rightSearchPlaceholder="Search assigned roles..."
                            leftEmptyText="No roles available"
                            rightEmptyText="No roles assigned"
                            addSelectedLabel=">"
                            addAllLabel=">>"
                            removeSelectedLabel="<"
                            removeAllLabel="<<"
                            addSelectedTitle="Add selected"
                            addAllTitle="Add all"
                            removeSelectedTitle="Remove selected"
                            removeAllTitle="Remove all"
                        />
                    </Box>

                    <Divider sx={{borderColor: palette.borderSoft}}/>

                    <Box>
                        <Box
                            sx={{
                                display: 'flex',
                                alignItems: {
                                    xs: 'flex-start',
                                    md: 'center',
                                },
                                justifyContent: 'space-between',
                                gap: 2,
                                flexDirection: {
                                    xs: 'column',
                                    md: 'row',
                                },
                                mb: 1.5,
                            }}
                        >
                            <Box>
                                <Typography
                                    sx={{
                                        fontSize: '0.95rem',
                                        fontWeight: 700,
                                        color: palette.text,
                                    }}
                                >
                                    Store Access
                                </Typography>

                                <Typography
                                    sx={{
                                        fontSize: '0.82rem',
                                        color: palette.textMuted,
                                        mt: 0.5,
                                    }}
                                >
                                    Turn off restriction to allow access
                                    to all stores.
                                </Typography>
                            </Box>

                            <FormControlLabel
                                control={
                                    <Switch
                                        checked={restrictStoreAccess}
                                        onChange={(e) =>
                                            setRestrictStoreAccess(
                                                e.target.checked
                                            )
                                        }
                                    />
                                }
                                label="Restrict access to selected stores"
                                sx={{
                                    mr: 0,
                                    color: palette.textMuted,
                                }}
                            />
                        </Box>

                        {restrictStoreAccess ? (
                            <TransferList
                                items={createStoreItems}
                                value={selectedStores.map(String)}
                                onChange={setSelectedStores}
                                leftTitle="Available Stores"
                                rightTitle="Assigned Stores"
                                leftSearchPlaceholder="Search available stores..."
                                rightSearchPlaceholder="Search assigned stores..."
                                leftEmptyText="No stores available"
                                rightEmptyText="No stores assigned"
                                addSelectedLabel=">"
                                addAllLabel=">>"
                                removeSelectedLabel="<"
                                removeAllLabel="<<"
                                addSelectedTitle="Add selected"
                                addAllTitle="Add all"
                                removeSelectedTitle="Remove selected"
                                removeAllTitle="Remove all"
                            />
                        ) : (
                            <Box
                                sx={{
                                    border: `1px dashed ${palette.border}`,
                                    borderRadius: '20px',
                                    px: 3,
                                    py: 4,
                                    textAlign: 'center',
                                    backgroundColor: palette.paperBgAlt,
                                }}
                            >
                                <Typography
                                    sx={{
                                        fontSize: '0.95rem',
                                        fontWeight: 700,
                                        color: palette.text,
                                    }}
                                >
                                    All stores access will be granted
                                </Typography>
                                <Typography
                                    sx={{
                                        fontSize: '0.84rem',
                                        color: palette.textMuted,
                                        mt: 0.75,
                                    }}
                                >
                                    No store restriction will be applied
                                    for this new user.
                                </Typography>
                            </Box>
                        )}
                    </Box>
                </DialogContent>

                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button
                        onClick={closeDialog}
                        sx={{textTransform: 'none', color: palette.textMuted}}
                    >
                        Cancel
                    </Button>
                    <Button
                        variant="contained"
                        onClick={handleCreate}
                        disabled={saving}
                        sx={{
                            textTransform: 'none',
                            borderRadius: 2,
                        }}
                    >
                        {saving ? 'Creating...' : 'Create'}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog
                open={dialog === 'edit'}
                onClose={closeDialog}
                maxWidth="sm"
                fullWidth
                PaperProps={{sx: dialogPaperSx}}
            >
                <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                    Edit User — {selected?.username}
                </DialogTitle>
                <DialogContent
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 2.5,
                        pt: '16px !important',
                    }}
                >
                    {error && (
                        <Alert severity="error" sx={{borderRadius: 2}}>
                            {error}
                        </Alert>
                    )}

                    {formField('Full Name', 'fullName')}
                    {formField('Email', 'email', {type: 'email'})}
                    {formField(
                        'New Password (leave blank to keep)',
                        'password',
                        {type: 'password'}
                    )}
                </DialogContent>

                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button
                        onClick={closeDialog}
                        sx={{textTransform: 'none', color: palette.textMuted}}
                    >
                        Cancel
                    </Button>
                    <Button
                        variant="contained"
                        onClick={handleEdit}
                        disabled={saving}
                        sx={{
                            textTransform: 'none',
                            borderRadius: 2,
                        }}
                    >
                        {saving ? 'Saving...' : 'Save'}
                    </Button>
                </DialogActions>
            </Dialog>

            <RolesDialog
                open={dialog === 'roles'}
                user={selected}
                roles={roles}
                selectedRoleIds={selectedRoles}
                onChange={setSelectedRoles}
                onClose={closeDialog}
                onSave={handleAssignRoles}
                saving={saving}
                error={error}
            />

            <StoresDialog
                open={dialog === 'stores'}
                user={selected}
                storeOptions={stores}
                selectedStoreIds={selectedStores}
                restrictStoreAccess={restrictStoreAccess}
                onRestrictStoreAccessChange={setRestrictStoreAccess}
                onChange={setSelectedStores}
                onClose={closeDialog}
                onSave={handleAssignStores}
                saving={saving}
                error={error}
            />

            <Dialog
                open={dialog === 'resetPassword'}
                onClose={closeDialog}
                maxWidth="sm"
                fullWidth
                PaperProps={{sx: dialogPaperSx}}
            >
                <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                    Reset Password — {selected?.username}
                </DialogTitle>

                <DialogContent
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 2,
                        pt: '16px !important',
                    }}
                >
                    {error && (
                        <Alert severity="error" sx={{borderRadius: 2}}>
                            {error}
                        </Alert>
                    )}

                    <Typography
                        sx={{
                            fontSize: '0.84rem',
                            color: palette.textMuted,
                        }}
                    >
                        Set a new password for this user.
                    </Typography>

                    <TextField
                        label="New Password"
                        type="password"
                        fullWidth
                        size="small"
                        value={resetPasswordForm.newPassword}
                        onChange={(e) =>
                            setResetPasswordForm((prev) => ({
                                ...prev,
                                newPassword: e.target.value,
                            }))
                        }
                        sx={textFieldSx}
                    />

                    <TextField
                        label="Confirm Password"
                        type="password"
                        fullWidth
                        size="small"
                        value={resetPasswordForm.confirmPassword}
                        onChange={(e) =>
                            setResetPasswordForm((prev) => ({
                                ...prev,
                                confirmPassword: e.target.value,
                            }))
                        }
                        sx={textFieldSx}
                    />
                </DialogContent>

                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button
                        onClick={closeDialog}
                        sx={{textTransform: 'none', color: palette.textMuted}}
                    >
                        Cancel
                    </Button>
                    <Button
                        variant="contained"
                        onClick={handleResetPassword}
                        disabled={saving}
                        sx={{
                            textTransform: 'none',
                            borderRadius: 2,
                        }}
                    >
                        {saving ? 'Saving...' : 'Reset Password'}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog
                open={dialog === 'activate'}
                onClose={closeDialog}
                maxWidth="xs"
                fullWidth
                PaperProps={{sx: dialogPaperSx}}
            >
                <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                    Activate User
                </DialogTitle>

                <DialogContent>
                    {error && (
                        <Alert severity="error" sx={{mb: 2, borderRadius: 2}}>
                            {error}
                        </Alert>
                    )}

                    <Typography sx={{color: palette.textMuted}}>
                        Are you sure you want to activate{' '}
                        <strong>{selected?.username}</strong>?
                    </Typography>
                </DialogContent>

                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button
                        onClick={closeDialog}
                        sx={{textTransform: 'none', color: palette.textMuted}}
                    >
                        Cancel
                    </Button>
                    <Button
                        variant="contained"
                        onClick={handleActivate}
                        disabled={saving}
                        sx={{
                            textTransform: 'none',
                            borderRadius: 2,
                        }}
                    >
                        {saving ? 'Activating...' : 'Activate'}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog
                open={dialog === 'deactivate'}
                onClose={closeDialog}
                maxWidth="xs"
                fullWidth
                PaperProps={{sx: dialogPaperSx}}
            >
                <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                    Deactivate User
                </DialogTitle>

                <DialogContent>
                    {error && (
                        <Alert severity="error" sx={{mb: 2, borderRadius: 2}}>
                            {error}
                        </Alert>
                    )}

                    <Typography sx={{color: palette.textMuted}}>
                        Are you sure you want to deactivate{' '}
                        <strong>{selected?.username}</strong>? They will no
                        longer be able to sign in.
                    </Typography>
                </DialogContent>

                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button
                        onClick={closeDialog}
                        sx={{textTransform: 'none', color: palette.textMuted}}
                    >
                        Cancel
                    </Button>
                    <Button
                        variant="contained"
                        color="error"
                        onClick={handleDeactivate}
                        disabled={saving}
                        sx={{
                            textTransform: 'none',
                            borderRadius: 2,
                        }}
                    >
                        {saving ? 'Deactivating...' : 'Deactivate'}
                    </Button>
                </DialogActions>
            </Dialog>

            <Dialog
                open={dialog === 'delete'}
                onClose={closeDialog}
                maxWidth="xs"
                fullWidth
                PaperProps={{sx: dialogPaperSx}}
            >
                <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                    Delete User
                </DialogTitle>

                <DialogContent>
                    {error && (
                        <Alert severity="error" sx={{mb: 2, borderRadius: 2}}>
                            {error}
                        </Alert>
                    )}

                    <Typography sx={{color: palette.textMuted}}>
                        Permanently delete{' '}
                        <strong>{selected?.username}</strong>?
                    </Typography>

                    <Typography
                        sx={{
                            color: palette.dangerText,
                            fontSize: '0.82rem',
                            mt: 1,
                        }}
                    >
                        This action cannot be undone.
                    </Typography>
                </DialogContent>

                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button
                        onClick={closeDialog}
                        sx={{textTransform: 'none', color: palette.textMuted}}
                    >
                        Cancel
                    </Button>
                    <Button
                        variant="contained"
                        color="error"
                        onClick={handleDelete}
                        disabled={saving}
                        sx={{
                            textTransform: 'none',
                            borderRadius: 2,
                        }}
                    >
                        {saving ? 'Deleting...' : 'Delete'}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    )
}
