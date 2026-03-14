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

function PermissionTable({rows, maxHeight = 240, palette, t}) {
    return (
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
                                {t('Permission Code')}
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
                                    {t('No permissions assigned')}
                                </TableCell>
                            </TableRow>
                        ) : (
                            rows.map((permission, index) => (
                                <TableRow key={permission}>
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
                                        <Chip
                                            label={permission}
                                            size="small"
                                            sx={{
                                                height: 24,
                                                fontSize: '0.74rem',
                                                fontWeight: 600,
                                                fontFamily: 'monospace',
                                                backgroundColor: palette.chipNeutralBg,
                                                color: palette.chipNeutralText,
                                            }}
                                        />
                                    </TableCell>
                                </TableRow>
                            ))
                        )}
                    </TableBody>
                </Table>
            </Box>
        </TableContainer>
    )
}

export default function RoleDetailsPanel({role}) {
    const palette = useAdminPalette()
    const {t} = useI18n()
    if (!role) return null

    const permissionCodes = role.permissionCodes || []

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
                    {t('Role Details')}
                </Typography>

                <Typography
                    sx={{
                        fontSize: '0.88rem',
                        color: palette.textMuted,
                        mt: 0.5,
                    }}
                >
                    {t('Selected role information and permission assignments')}
                </Typography>
            </Box>

            <Divider sx={{borderColor: palette.borderSoft}}/>

            <Box sx={{px: 3, py: 2.5}}>
                <Box
                    sx={{
                        display: 'grid',
                        gridTemplateColumns: {
                            xs: '1fr',
                            md: 'repeat(3, minmax(0, 1fr))',
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
                            {t('Role Name')}
                        </Typography>
                        <Typography
                            sx={{
                                fontSize: '0.95rem',
                                fontWeight: 700,
                                color: palette.text,
                            }}
                        >
                            {role.name || '-'}
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
                            {t('Description')}
                        </Typography>
                        <Typography
                            sx={{
                                fontSize: '0.95rem',
                                color: palette.textMuted,
                            }}
                        >
                            {role.description || '-'}
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
                            {t('Permission Count')}
                        </Typography>
                        <Typography
                            sx={{
                                fontSize: '0.95rem',
                                fontWeight: 600,
                                color: palette.textMuted,
                            }}
                        >
                            {permissionCodes.length}
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
                    <Chip
                        label={`${permissionCodes.length} Permission${
                            permissionCodes.length !== 1 ? 's' : ''
                        }`}
                        size="small"
                        sx={{
                            height: 24,
                            fontSize: '0.75rem',
                            fontWeight: 600,
                            backgroundColor: palette.chipBlueBg,
                            color: palette.chipBlueText,
                        }}
                    />
                </Box>

                <Box sx={{minWidth: 0}}>
                    <Typography
                        sx={{
                            fontSize: '0.9rem',
                            fontWeight: 700,
                            color: palette.text,
                            mb: 1.25,
                        }}
                    >
                        {t('Assigned Permissions')}
                    </Typography>

                    <PermissionTable
                        rows={permissionCodes}
                        palette={palette}
                        t={t}
                    />
                </Box>
            </Box>
        </Paper>
    )
}
