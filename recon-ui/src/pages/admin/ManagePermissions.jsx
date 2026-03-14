import {useEffect, useMemo, useState} from 'react'
import {
    Box,
    Chip,
    CircularProgress,
    Collapse,
    Divider,
    InputAdornment,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown'
import KeyboardArrowRightIcon from '@mui/icons-material/KeyboardArrowRight'
import {adminApi} from '../../services/adminApi'
import {useI18n} from '../../context/I18nContext'

const MODULE_COLORS = {
    RECONCILIATION: {
        bg: '#eff6ff',
        color: '#2563eb',
        light: '#f8fbff',
    },
    REPORTS: {
        bg: '#f0fdf4',
        color: '#16a34a',
        light: '#f7fef9',
    },
    SETTINGS: {
        bg: '#fefce8',
        color: '#ca8a04',
        light: '#fffef6',
    },
    ADMIN: {
        bg: '#fdf4ff',
        color: '#9333ea',
        light: '#fefaff',
    },
}

const MODULE_ORDER = [
    'RECONCILIATION',
    'REPORTS',
    'SETTINGS',
    'ADMIN',
]

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
        paperBg: isDark ? '#0F172A' : '#FFFFFF',
        paperBgAlt: isDark ? '#111827' : '#F8FAFC',
        border: isDark ? '#1E293B' : '#E5E7EB',
        borderSoft: isDark ? '#243041' : '#F1F5F9',
        inputBg: isDark ? '#0F172A' : '#FFFFFF',
    }
}

function getModuleColors(module) {
    return (
        MODULE_COLORS[module] || {
            bg: '#f8fafc',
            color: '#475569',
            light: '#ffffff',
        }
    )
}

function PermissionRow({permission, colors, onSelect, palette}) {
    return (
        <TableRow
            onClick={() => onSelect(permission)}
            sx={{
                cursor: 'pointer',
                '&:hover': {
                    backgroundColor: palette.paperBgAlt,
                },
                '&:last-child td': {
                    borderBottom: 0,
                },
            }}
        >
            <TableCell
                sx={{
                    py: 1.5,
                    borderBottom: `1px solid ${palette.borderSoft}`,
                    width: 220,
                    whiteSpace: 'nowrap',
                }}
            >
                <Chip
                    label={permission.code}
                    size="small"
                    sx={{
                        height: 24,
                        fontSize: '0.72rem',
                        fontFamily: 'monospace',
                        backgroundColor: colors.bg,
                        color: colors.color,
                        fontWeight: 700,
                    }}
                />
            </TableCell>

            <TableCell
                sx={{
                    py: 1.5,
                    borderBottom: `1px solid ${palette.borderSoft}`,
                }}
            >
                <Typography
                    sx={{
                        fontSize: '0.92rem',
                        fontWeight: 600,
                        color: palette.text,
                    }}
                >
                    {permission.name}
                </Typography>
            </TableCell>

            <TableCell
                sx={{
                    py: 1.5,
                    borderBottom: `1px solid ${palette.borderSoft}`,
                }}
            >
                <Typography
                    sx={{
                        fontSize: '0.84rem',
                        color: palette.textMuted,
                    }}
                >
                    {permission.description || '—'}
                </Typography>
            </TableCell>
        </TableRow>
    )
}

function PermissionModuleCard({
                                  module,
                                  permissions,
                                  expanded,
                                  onToggle,
                                  onSelectPermission,
                                  palette,
                              }) {
    const colors = getModuleColors(module)

    return (
        <Paper
            elevation={0}
            sx={{
                border: `1px solid ${palette.border}`,
                borderRadius: '24px',
                overflow: 'hidden',
                backgroundColor: palette.paperBg,
            }}
        >
            <Box
                onClick={onToggle}
                sx={{
                    px: 3,
                    py: 1.8,
                    backgroundColor: palette.isDark ? '#111827' : colors.bg,
                    borderBottom: expanded
                        ? `1px solid ${palette.border}`
                        : 'none',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: 2,
                    cursor: 'pointer',
                    transition: 'all 0.15s ease',
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1.5,
                        minWidth: 0,
                    }}
                >
                    {expanded ? (
                        <KeyboardArrowDownIcon
                            sx={{
                                color: palette.textMuted,
                                fontSize: 20,
                            }}
                        />
                    ) : (
                        <KeyboardArrowRightIcon
                            sx={{
                                color: palette.textMuted,
                                fontSize: 20,
                            }}
                        />
                    )}

                    <Chip
                        label={module}
                        size="small"
                        sx={{
                            backgroundColor: colors.color,
                            color: '#fff',
                            fontWeight: 700,
                            fontSize: '0.72rem',
                            letterSpacing: '0.4px',
                            height: 30,
                        }}
                    />

                    <Typography
                        sx={{
                            fontSize: '0.9rem',
                            color: palette.textMuted,
                            fontWeight: 500,
                        }}
                    >
                        {permissions.length} permission
                        {permissions.length !== 1 ? 's' : ''}
                    </Typography>
                </Box>
            </Box>

            <Collapse in={expanded} timeout="auto" unmountOnExit>
                <Table>
                    <TableHead>
                        <TableRow
                            sx={{
                                backgroundColor: palette.paperBgAlt,
                            }}
                        >
                            {['Permission Code', 'Name', 'Description'].map(
                                (header) => (
                                    <TableCell
                                        key={header}
                                        sx={{
                                            fontWeight: 700,
                                            fontSize: '0.78rem',
                                            color: palette.textMuted,
                                            textTransform: 'uppercase',
                                            letterSpacing: '0.4px',
                                            py: 1.5,
                                            borderBottom:
                                                `1px solid ${palette.border}`,
                                        }}
                                    >
                                        {header}
                                    </TableCell>
                                )
                            )}
                        </TableRow>
                    </TableHead>

                    <TableBody>
                        {permissions.map((permission) => (
                            <PermissionRow
                                key={permission.code}
                                permission={permission}
                                colors={colors}
                                onSelect={onSelectPermission}
                                palette={palette}
                            />
                        ))}
                    </TableBody>
                </Table>
            </Collapse>
        </Paper>
    )
}

function PermissionDetailsPanel({permission, palette}) {
    if (!permission) return null

    const colors = getModuleColors(permission.module)

    return (
        <Paper
            elevation={0}
            sx={{
                mt: 3,
                border: `1px solid ${palette.border}`,
                borderRadius: '24px',
                overflow: 'hidden',
                backgroundColor: palette.paperBg,
            }}
        >
            <Box sx={{px: 3, pt: 2.75, pb: 2}}>
                <Typography
                    sx={{
                        fontSize: '1.05rem',
                        fontWeight: 700,
                        color: palette.text,
                    }}
                >
                    Permission Details
                </Typography>

                <Typography
                    sx={{
                        fontSize: '0.88rem',
                        color: palette.textMuted,
                        mt: 0.5,
                    }}
                >
                    System-defined permission information
                </Typography>
            </Box>

            <Divider sx={{borderColor: palette.borderSoft}}/>

            <Box sx={{px: 3, py: 2.5}}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                            xs: '1fr',
                            md: '180px 1fr',
                        },
                        rowGap: 2,
                        columnGap: 3,
                    }}
                >
                    <Typography
                        sx={{
                            fontSize: '0.78rem',
                            color: palette.textMuted,
                            textTransform: 'uppercase',
                            letterSpacing: '0.4px',
                            fontWeight: 700,
                        }}
                    >
                        Module
                    </Typography>
                    <Box>
                        <Chip
                            label={permission.module}
                            size="small"
                            sx={{
                                backgroundColor: colors.color,
                                color: '#fff',
                                fontWeight: 700,
                                fontSize: '0.72rem',
                                height: 28,
                            }}
                        />
                    </Box>

                    <Typography
                        sx={{
                            fontSize: '0.78rem',
                            color: palette.textMuted,
                            textTransform: 'uppercase',
                            letterSpacing: '0.4px',
                            fontWeight: 700,
                        }}
                    >
                        Code
                    </Typography>
                    <Box>
                        <Chip
                            label={permission.code}
                            size="small"
                            sx={{
                                backgroundColor: colors.bg,
                                color: colors.color,
                                fontWeight: 700,
                                fontSize: '0.74rem',
                                fontFamily: 'monospace',
                                height: 28,
                            }}
                        />
                    </Box>

                    <Typography
                        sx={{
                            fontSize: '0.78rem',
                            color: palette.textMuted,
                            textTransform: 'uppercase',
                            letterSpacing: '0.4px',
                            fontWeight: 700,
                        }}
                    >
                        Name
                    </Typography>
                    <Typography
                        sx={{
                            fontSize: '0.98rem',
                            fontWeight: 700,
                            color: palette.text,
                        }}
                    >
                        {permission.name}
                    </Typography>

                    <Typography
                        sx={{
                            fontSize: '0.78rem',
                            color: palette.textMuted,
                            textTransform: 'uppercase',
                            letterSpacing: '0.4px',
                            fontWeight: 700,
                        }}
                    >
                        Description
                    </Typography>
                    <Typography
                        sx={{
                            fontSize: '0.92rem',
                            color: palette.textMuted,
                            lineHeight: 1.6,
                        }}
                    >
                        {permission.description || '—'}
                    </Typography>

                    <Typography
                        sx={{
                            fontSize: '0.78rem',
                            color: palette.textMuted,
                            textTransform: 'uppercase',
                            letterSpacing: '0.4px',
                            fontWeight: 700,
                        }}
                    >
                        Type
                    </Typography>
                    <Typography
                        sx={{
                            fontSize: '0.9rem',
                            color: palette.textMuted,
                        }}
                    >
                        System-defined permission
                    </Typography>
                </Box>
            </Box>
        </Paper>
    )
}

export default function ManagePermissions() {
    const [themeMode, setThemeMode] = useState(getThemeMode())
    const {t} = useI18n()
    const [permissions, setPermissions] = useState([])
    const [loading, setLoading] = useState(true)
    const [searchText, setSearchText] = useState('')
    const [moduleFilter, setModuleFilter] = useState('ALL')
    const [selectedPermission, setSelectedPermission] = useState(null)
    const [expandedModules, setExpandedModules] = useState({
        RECONCILIATION: true,
        REPORTS: true,
        SETTINGS: true,
        ADMIN: true,
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
        adminApi.getPermissions()
            .then((data) => {
                setPermissions(data || [])
            })
            .catch(console.error)
            .finally(() => setLoading(false))
    }, [])

    const palette = useMemo(() => getPalette(themeMode), [themeMode])

    const filteredPermissions = useMemo(() => {
        return permissions.filter((permission) => {
            const q = searchText.trim().toLowerCase()

            const matchesSearch =
                !q ||
                (permission.code || '').toLowerCase().includes(q) ||
                (permission.name || '').toLowerCase().includes(q) ||
                (permission.description || '')
                    .toLowerCase()
                    .includes(q)

            const matchesModule =
                moduleFilter === 'ALL' ||
                permission.module === moduleFilter

            return matchesSearch && matchesModule
        })
    }, [permissions, searchText, moduleFilter])

    const grouped = useMemo(() => {
        const acc = {}

        filteredPermissions.forEach((permission) => {
            if (!acc[permission.module]) {
                acc[permission.module] = []
            }
            acc[permission.module].push(permission)
        })

        const ordered = {}
        MODULE_ORDER.forEach((module) => {
            if (acc[module]?.length) {
                ordered[module] = acc[module]
            }
        })

        Object.keys(acc)
            .filter((module) => !MODULE_ORDER.includes(module))
            .sort()
            .forEach((module) => {
                ordered[module] = acc[module]
            })

        return ordered
    }, [filteredPermissions])

    const moduleCounts = useMemo(() => {
        return permissions.reduce((acc, permission) => {
            acc[permission.module] = (acc[permission.module] || 0) + 1
            return acc
        }, {})
    }, [permissions])

    const visibleModules = Object.keys(grouped)

    const toggleModule = (module) => {
        setExpandedModules((prev) => ({
            ...prev,
            [module]: !prev[module],
        }))
    }

    const renderModuleFilterChip = (module, label, count) => {
        const isActive = moduleFilter === module
        const colors =
            module === 'ALL'
                ? {
                    bg: palette.isDark ? '#0F172A' : '#EFF6FF',
                    color: '#2563EB',
                    activeBg: '#2563EB',
                    activeColor: '#fff',
                }
                : getModuleColors(module)

        return (
            <Chip
                key={module}
                label={`${label}${typeof count === 'number' ? ` (${count})` : ''}`}
                onClick={() => setModuleFilter(module)}
                sx={{
                    height: 32,
                    fontSize: '0.78rem',
                    fontWeight: 700,
                    borderRadius: 999,
                    backgroundColor: isActive
                        ? colors.activeBg || colors.color
                        : colors.bg,
                    color: isActive
                        ? colors.activeColor || '#fff'
                        : colors.color,
                    cursor: 'pointer',
                    border: '1px solid',
                    borderColor: isActive
                        ? colors.activeBg || colors.color
                        : 'transparent',
                    '&:hover': {
                        backgroundColor: isActive
                            ? colors.activeBg || colors.color
                            : colors.bg,
                    },
                }}
            />
        )
    }

    return (
        <Box sx={{px: 4, py: 3, maxWidth: 1480}}>
            <Box sx={{mb: 3}}>
                <Typography
                    variant="h4"
                    sx={{
                        fontWeight: 700,
                        color: palette.text,
                        lineHeight: 1.2,
                    }}
                >
                    {t('Manage Permissions')}
                </Typography>

                <Typography
                    sx={{
                        fontSize: '0.95rem',
                        color: palette.textMuted,
                        mt: 1,
                    }}
                >
                    {permissions.length} permissions across{' '}
                    {Object.keys(moduleCounts).length} modules.
                    Permissions are system-defined and assigned to roles.
                </Typography>
            </Box>

            <Paper
                elevation={0}
                sx={{
                    mb: 2.5,
                    p: 2,
                    border: `1px solid ${palette.border}`,
                    borderRadius: '24px',
                    backgroundColor: palette.paperBg,
                }}
            >
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                            xs: '1fr',
                            md: 'minmax(300px, 1.5fr) 1fr',
                        },
                        gap: 1.5,
                        alignItems: 'center',
                    }}
                >
                    <TextField
                        size="small"
                        fullWidth
                        placeholder={t('Search by permission code, name, or description')}
                        value={searchText}
                        onChange={(e) => setSearchText(e.target.value)}
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <SearchIcon
                                        sx={{
                                            fontSize: 18,
                                            color: palette.textSoft,
                                        }}
                                    />
                                </InputAdornment>
                            ),
                        }}
                        sx={{
                            '& .MuiOutlinedInput-root': {
                                borderRadius: 999,
                                backgroundColor: palette.inputBg,
                                color: palette.text,
                                '& fieldset': {
                                    borderColor: palette.border,
                                },
                                '&:hover fieldset': {
                                    borderColor: palette.textSoft,
                                },
                                '&.Mui-focused fieldset': {
                                    borderColor: '#2563EB',
                                },
                            },
                        }}
                    />

                    <Box
                        sx={{
                            display: 'flex',
                            gap: 1,
                            flexWrap: 'wrap',
                            justifyContent: {
                                xs: 'flex-start',
                                md: 'flex-end',
                            },
                        }}
                    >
                        {renderModuleFilterChip(
                            'ALL',
                            t('All'),
                            permissions.length
                        )}

                        {MODULE_ORDER.filter(
                            (module) => moduleCounts[module]
                        ).map((module) =>
                            renderModuleFilterChip(
                                module,
                                module,
                                moduleCounts[module]
                            )
                        )}
                    </Box>
                </Box>
            </Paper>

            {loading ? (
                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'center',
                        py: 8,
                    }}
                >
                    <CircularProgress/>
                </Box>
            ) : visibleModules.length === 0 ? (
                <Paper
                    elevation={0}
                    sx={{
                        border: `1px solid ${palette.border}`,
                        borderRadius: '24px',
                        py: 8,
                        textAlign: 'center',
                        color: palette.textSoft,
                        backgroundColor: palette.paperBg,
                    }}
                >
                    {t('No permissions found')}
                </Paper>
            ) : (
                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 3,
                    }}
                >
                    {visibleModules.map((module) => (
                        <PermissionModuleCard
                            key={module}
                            module={module}
                            permissions={grouped[module]}
                            expanded={!!expandedModules[module]}
                            onToggle={() => toggleModule(module)}
                            onSelectPermission={setSelectedPermission}
                            palette={palette}
                        />
                    ))}
                </Box>
            )}

            <PermissionDetailsPanel
                permission={selectedPermission}
                palette={palette}
            />
        </Box>
    )
}
