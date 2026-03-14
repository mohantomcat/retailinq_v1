import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    TextField,
    Typography,
} from '@mui/material'
import AdminTable from '../../components/AdminTable'
import TransferList from '../../components/TransferList'
import RoleDetailsPanel from '../../components/admin/RoleDetailsPanel'
import RoleFilters from '../../components/admin/RoleFilters'
import RoleRowMenu from '../../components/admin/RoleRowMenu'
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
        text: isDark ? '#E2E8F0' : '#0F172A',
        textMuted: isDark ? '#94A3B8' : '#64748B',
        textSoft: isDark ? '#64748B' : '#94A3B8',
        border: isDark ? '#1E293B' : '#E5E7EB',
        borderSoft: isDark ? '#243041' : '#F1F5F9',
        inputBg: isDark ? '#0F172A' : '#FFFFFF',
        dialogBg: isDark
            ? 'linear-gradient(180deg, #0F172A 0%, #111827 100%)'
            : 'linear-gradient(180deg, #FFFFFF 0%, #FCFDFE 100%)',
    }
}

export default function ManageRoles() {
    const [themeMode, setThemeMode] = useState(getThemeMode())
    const {t} = useI18n()
    const [roles, setRoles] = useState([])
    const [permissions, setPermissions] = useState([])
    const [loading, setLoading] = useState(true)
    const [dialog, setDialog] = useState(null)
    const [selected, setSelected] = useState(null)
    const [error, setError] = useState('')
    const [saving, setSaving] = useState(false)

    const [form, setForm] = useState({
        name: '',
        description: '',
        tenantId: TENANT,
    })
    const [selectedPerms, setSelectedPerms] = useState([])
    const [selectedRow, setSelectedRow] = useState(null)

    const [searchText, setSearchText] = useState('')
    const [moduleFilter, setModuleFilter] = useState('all')

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
                backgroundColor: palette.isDark ? '#111827' : '#F8FAFC',
            },
        },
        '& .MuiInputLabel-root': {
            color: palette.textMuted,
        },
        '& .MuiInputBase-input.Mui-disabled': {
            WebkitTextFillColor: palette.textMuted,
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
            const [r, p] = await Promise.all([
                adminApi.getRoles(),
                adminApi.getPermissions(),
            ])
            setRoles(r)
            setPermissions(p)

            setSelectedRow((prev) => {
                if (!prev?.id) return prev
                return r.find((x) => x.id === prev.id) || prev
            })
        } catch (e) {
            setError(e.message)
        } finally {
            setLoading(false)
        }
    }

    const openCreate = () => {
        setForm({
            name: '',
            description: '',
            tenantId: TENANT,
        })
        setSelectedPerms([])
        setError('')
        setDialog('create')
    }

    const openEditRole = (role) => {
        setSelected(role)
        setSelectedRow(role)
        setForm({
            name: role.name,
            description: role.description || '',
            tenantId: TENANT,
        })
        setError('')
        setDialog('editRole')
    }

    const openManagePermissions = (role) => {
        setSelected(role)
        setSelectedRow(role)
        setForm({
            name: role.name,
            description: role.description || '',
            tenantId: TENANT,
        })
        setSelectedPerms([...(role.permissionCodes || [])])
        setError('')
        setDialog('permissions')
    }

    const openDelete = (role) => {
        setSelected(role)
        setSelectedRow(role)
        setError('')
        setDialog('delete')
    }

    const closeDialog = () => {
        setDialog(null)
        setSelected(null)
        setError('')
    }

    const handleCreate = async () => {
        if (!form.name.trim()) {
            setError(t('Role name is required'))
            return
        }

        setSaving(true)
        try {
            const permIds = permissions
                .filter((permission) =>
                    selectedPerms.includes(permission.code)
                )
                .map((permission) => permission.id)

            await adminApi.createRole({
                ...form,
                permissionIds: permIds,
            })

            await loadAll()
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleUpdateRole = async () => {
        if (!form.name.trim()) {
            setError(t('Role name is required'))
            return
        }

        setSaving(true)
        try {
            await adminApi.updateRole(selected.id, {
                name: form.name,
                description: form.description,
            })

            await loadAll()
            closeDialog()
        } catch (e) {
            setError(e.message)
        } finally {
            setSaving(false)
        }
    }

    const handleManagePermissions = async () => {
        setSaving(true)
        try {
            const permIds = permissions
                .filter((permission) =>
                    selectedPerms.includes(permission.code)
                )
                .map((permission) => permission.id)

            await adminApi.assignPermissions(selected.id, permIds)
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
            await adminApi.deleteRole(selected.id)
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

    const moduleOptions = useMemo(() => {
        return Array.from(
            new Set(permissions.map((permission) => permission.module))
        ).sort()
    }, [permissions])

    const filteredRoles = useMemo(() => {
        return roles.filter((role) => {
            const q = searchText.trim().toLowerCase()

            const matchesSearch =
                !q ||
                (role.name || '').toLowerCase().includes(q) ||
                (role.description || '').toLowerCase().includes(q) ||
                (role.permissionCodes || []).some((code) =>
                    String(code).toLowerCase().includes(q)
                )

            const matchesModule =
                moduleFilter === 'all' ||
                permissions.some(
                    (permission) =>
                        permission.module === moduleFilter &&
                        (role.permissionCodes || []).includes(
                            permission.code
                        )
                )

            return matchesSearch && matchesModule
        })
    }, [roles, searchText, moduleFilter, permissions])

    const permissionItems = useMemo(() => {
        return permissions.map((permission) => ({
            id: permission.code,
            label: permission.name,
            badge: permission.module,
        }))
    }, [permissions])

    const columns = [
        {
            key: 'name',
            label: t('Role Name'),
            render: (value, row) => (
                <Box>
                    <Typography
                        sx={{
                            fontWeight: 700,
                            fontSize: '0.92rem',
                            color: palette.text,
                        }}
                    >
                        {value}
                    </Typography>

                    {row.description && (
                        <Typography
                            sx={{
                                fontSize: '0.78rem',
                                color: palette.textSoft,
                                mt: 0.4,
                            }}
                        >
                            {row.description}
                        </Typography>
                    )}
                </Box>
            ),
        },
        {
            key: 'permissionCodes',
            label: t('Permissions'),
            noWrap: true,
            render: (value) => {
                const arr = [...(value || [])]

                if (arr.length === 0) return '-'
                if (arr.length === 1) return arr[0]

                return `${arr[0]} +${arr.length - 1}`
            },
        },
        {
            key: 'menu',
            label: '',
            width: 70,
            align: 'right',
            noWrap: true,
            render: (_, row) => (
                <RoleRowMenu
                    row={row}
                    onEdit={openEditRole}
                    onManagePermissions={openManagePermissions}
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

            <RoleFilters
                searchText={searchText}
                onSearchTextChange={setSearchText}
                moduleFilter={moduleFilter}
                onModuleFilterChange={setModuleFilter}
                moduleOptions={moduleOptions}
            />

            <AdminTable
                title={t('Manage Roles')}
                columns={columns}
                rows={filteredRoles}
                loading={loading}
                onAdd={openCreate}
                addLabel={t('Add Role')}
                selectedRow={selectedRow}
                onSelectRow={setSelectedRow}
                tableMaxHeight={520}
            />

            <RoleDetailsPanel role={selectedRow}/>

            <Dialog
                open={dialog === 'create'}
                onClose={closeDialog}
                maxWidth="lg"
                fullWidth
                PaperProps={{sx: dialogPaperSx}}
            >
                <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                    Create Role
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
                        {formField('Role Name', 'name')}
                        {formField('Description', 'description')}
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
                            Permission Assignment
                        </Typography>

                        <Typography
                            sx={{
                                fontSize: '0.82rem',
                                color: palette.textMuted,
                                mb: 1.5,
                            }}
                        >
                            Select the permissions to assign to this role.
                        </Typography>

                        <TransferList
                            items={permissionItems}
                            value={selectedPerms}
                            onChange={setSelectedPerms}
                            leftTitle="Available Permissions"
                            rightTitle="Assigned Permissions"
                            leftSearchPlaceholder="Search available permissions..."
                            rightSearchPlaceholder="Search assigned permissions..."
                            leftEmptyText="No permissions available"
                            rightEmptyText="No permissions assigned"
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
                open={dialog === 'editRole'}
                onClose={closeDialog}
                maxWidth="sm"
                fullWidth
                PaperProps={{sx: dialogPaperSx}}
            >
                <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                    Edit Role — {selected?.name}
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

                    {formField('Role Name', 'name')}
                    {formField('Description', 'description')}
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
                        onClick={handleUpdateRole}
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

            <Dialog
                open={dialog === 'permissions'}
                onClose={closeDialog}
                maxWidth="lg"
                fullWidth
                PaperProps={{sx: dialogPaperSx}}
            >
                <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                    Manage Permissions — {selected?.name}
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
                        <TextField
                            label="Role Name"
                            value={form.name}
                            fullWidth
                            size="small"
                            disabled
                            sx={textFieldSx}
                        />
                        <TextField
                            label="Description"
                            value={form.description}
                            fullWidth
                            size="small"
                            disabled
                            sx={textFieldSx}
                        />
                    </Box>

                    <Divider sx={{borderColor: palette.borderSoft}}/>

                    <TransferList
                        items={permissionItems}
                        value={selectedPerms}
                        onChange={setSelectedPerms}
                        leftTitle="Available Permissions"
                        rightTitle="Assigned Permissions"
                        leftSearchPlaceholder="Search available permissions..."
                        rightSearchPlaceholder="Search assigned permissions..."
                        leftEmptyText="No permissions available"
                        rightEmptyText="No permissions assigned"
                        addSelectedLabel=">"
                        addAllLabel=">>"
                        removeSelectedLabel="<"
                        removeAllLabel="<<"
                        addSelectedTitle="Add selected"
                        addAllTitle="Add all"
                        removeSelectedTitle="Remove selected"
                        removeAllTitle="Remove all"
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
                        onClick={handleManagePermissions}
                        disabled={saving}
                        sx={{
                            textTransform: 'none',
                            borderRadius: 2,
                        }}
                    >
                        {saving ? 'Saving...' : 'Save Permissions'}
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
                    Delete Role
                </DialogTitle>

                <DialogContent>
                    {error && (
                        <Alert severity="error" sx={{mb: 2, borderRadius: 2}}>
                            {error}
                        </Alert>
                    )}

                    <Typography sx={{color: palette.textMuted}}>
                        Are you sure you want to delete role{' '}
                        <strong>{selected?.name}</strong>?
                    </Typography>

                    <Typography
                        sx={{
                            color: '#ef4444',
                            fontSize: '0.82rem',
                            mt: 1,
                        }}
                    >
                        Users assigned to this role will lose its permissions.
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
