import {
    Box,
    Chip,
    Divider,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Typography,
} from '@mui/material'
import {useAdminPalette} from './theme'
import {useI18n} from '../../context/I18nContext'

function DetailListTable({
    title,
    rows,
    emptyText,
    renderValue,
    maxHeight = 220,
    palette,
}) {
    return (
        <Box sx={{flex: 1, minWidth: 0}}>
            <Typography
                sx={{
                    fontSize: '0.9rem',
                    fontWeight: 700,
                    color: palette.text,
                    mb: 1.25,
                }}
            >
                {title}
            </Typography>

            <TableContainer
                component={Paper}
                elevation={0}
                sx={{
                    border: `1px solid ${palette.border}`,
                    borderRadius: '18px',
                    overflow: 'hidden',
                    backgroundColor: palette.paperBg,
                }}
            >
                <Box sx={{maxHeight, overflowY: 'auto'}}>
                    <Table stickyHeader size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell
                                    sx={{
                                        backgroundColor: palette.paperBgAlt,
                                        fontWeight: 700,
                                        fontSize: '0.78rem',
                                        color: palette.textMuted,
                                        textTransform: 'uppercase',
                                        letterSpacing: '0.4px',
                                        borderBottom: `1px solid ${palette.border}`,
                                        py: 1.4,
                                    }}
                                >
                                    {title}
                                </TableCell>
                            </TableRow>
                        </TableHead>

                        <TableBody>
                            {rows.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        sx={{
                                            py: 2.5,
                                            color: palette.textSoft,
                                            fontSize: '0.88rem',
                                            fontStyle: 'italic',
                                            borderBottom: 0,
                                        }}
                                    >
                                        {emptyText}
                                    </TableCell>
                                </TableRow>
                            ) : (
                                rows.map((row, index) => (
                                    <TableRow key={index}>
                                        <TableCell
                                            sx={{
                                                py: 1.3,
                                                fontSize: '0.9rem',
                                                color: palette.text,
                                                borderBottom:
                                                    index === rows.length - 1
                                                        ? 0
                                                        : `1px solid ${palette.borderSoft}`,
                                            }}
                                        >
                                            {renderValue(row)}
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </Box>
            </TableContainer>
        </Box>
    )
}

export default function UserDetailsPanel({user, formatLastLogin}) {
    const palette = useAdminPalette()
    const {t} = useI18n()
    if (!user) return null

    const roles = user.roles || []
    const storeIds = user.effectiveStoreIds || user.storeIds || []

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
                    {t('User Details')}
                </Typography>

                <Typography
                    sx={{
                        fontSize: '0.88rem',
                        color: palette.textMuted,
                        mt: 0.5,
                    }}
                >
                    {t('Selected user information, roles, and store access')}
                </Typography>
            </Box>

            <Divider sx={{borderColor: palette.borderSoft}}/>

            <Box sx={{px: 3, py: 2.5}}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                            xs: '1fr',
                            md: 'repeat(4, minmax(0, 1fr))',
                        },
                        gap: 2,
                        mb: 3,
                    }}
                >
                    <Box>
                        <Typography
                            sx={{
                                fontSize: '0.75rem',
                                color: palette.textMuted,
                                textTransform: 'uppercase',
                                letterSpacing: '0.4px',
                                mb: 0.5,
                            }}
                        >
                            {t('User')}
                        </Typography>
                        <Typography
                            sx={{
                                fontSize: '0.95rem',
                                fontWeight: 700,
                                color: palette.text,
                            }}
                        >
                            {user.username || '-'}
                        </Typography>
                    </Box>

                    <Box>
                        <Typography
                            sx={{
                                fontSize: '0.75rem',
                                color: palette.textMuted,
                                textTransform: 'uppercase',
                                letterSpacing: '0.4px',
                                mb: 0.5,
                            }}
                        >
                            {t('Name')}
                        </Typography>
                        <Typography
                            sx={{
                                fontSize: '0.95rem',
                                fontWeight: 600,
                                color: palette.text,
                            }}
                        >
                            {user.fullName || '-'}
                        </Typography>
                    </Box>

                    <Box>
                        <Typography
                            sx={{
                                fontSize: '0.75rem',
                                color: palette.textMuted,
                                textTransform: 'uppercase',
                                letterSpacing: '0.4px',
                                mb: 0.5,
                            }}
                        >
                            {t('Email')}
                        </Typography>
                        <Typography
                            sx={{
                                fontSize: '0.95rem',
                                color: palette.textMuted,
                                wordBreak: 'break-word',
                            }}
                        >
                            {user.email || '-'}
                        </Typography>
                    </Box>

                    <Box>
                        <Typography
                            sx={{
                                fontSize: '0.75rem',
                                color: palette.textMuted,
                                textTransform: 'uppercase',
                                letterSpacing: '0.4px',
                                mb: 0.5,
                            }}
                        >
                            {t('Last Login')}
                        </Typography>
                        <Typography
                            sx={{
                                fontSize: '0.95rem',
                                color: palette.textMuted,
                            }}
                        >
                            {formatLastLogin
                                ? formatLastLogin(user.lastLogin)
                                : user.lastLogin || '-'}
                        </Typography>
                    </Box>
                </Box>

                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1,
                        mb: 2.5,
                        flexWrap: 'wrap',
                    }}
                >
                    <Typography
                        sx={{
                            fontSize: '0.82rem',
                            color: palette.textMuted,
                            fontWeight: 600,
                        }}
                    >
                        {t('Status')}
                    </Typography>
                    <Chip
                        label={user.active ? t('Active') : t('Inactive')}
                        size="small"
                        sx={{
                            height: 24,
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            backgroundColor: user.active
                                ? palette.chipGreenBg
                                : palette.chipRedBg,
                            color: user.active
                                ? palette.chipGreenText
                                : palette.chipRedText,
                        }}
                    />
                    <Chip
                        label={
                            user.accessScope?.accessLabel ||
                            (storeIds.length === 0
                                ? t('All Stores Access')
                                : `${storeIds.length} ${t('Assigned Stores')}`)
                        }
                        size="small"
                        sx={{
                            height: 24,
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            backgroundColor: palette.chipBlueBg,
                            color: palette.chipBlueText,
                        }}
                    />
                    <Chip
                        label={`${roles.length} Role${
                            roles.length !== 1 ? 's' : ''
                        }`}
                        size="small"
                        sx={{
                            height: 24,
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            backgroundColor: palette.chipNeutralBg,
                            color: palette.chipNeutralText,
                        }}
                    />
                </Box>

                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: {xs: 'column', lg: 'row'},
                        gap: 2.5,
                    }}
                >
                    <DetailListTable
                        title={t('Assigned Roles')}
                        rows={roles}
                        emptyText={t('No roles assigned')}
                        renderValue={(role) => role?.name || '-'}
                        palette={palette}
                    />

                    <DetailListTable
                        title={t('Assigned Stores')}
                        rows={storeIds}
                        emptyText={t('All stores')}
                        renderValue={(storeId) => `${t('Store')} ${storeId}`}
                        palette={palette}
                    />
                </Box>
            </Box>
        </Paper>
    )
}
