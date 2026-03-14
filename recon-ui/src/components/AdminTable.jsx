import {
    Box,
    CircularProgress,
    Paper,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Tooltip,
    Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import {useAdminPalette} from './admin/theme'
import {useI18n} from '../context/I18nContext'

export default function AdminTable({
    title,
    columns,
    rows,
    loading,
    onAdd,
    addLabel = 'Add',
    selectedRow,
    onSelectRow,
    tableMaxHeight = 520,
}) {
    const palette = useAdminPalette()
    const {t} = useI18n()
    const isSelected = (row) => selectedRow?.id === row.id

    return (
        <Box>
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    justifyContent: 'space-between',
                    gap: 2,
                    mb: 2.5,
                }}
            >
                <Box>
                    <Typography
                        variant="h4"
                        sx={{
                            fontWeight: 700,
                            color: palette.text,
                            lineHeight: 1.2,
                        }}
                    >
                        {title}
                    </Typography>

                    <Typography
                        sx={{
                            fontSize: '0.95rem',
                            color: palette.textMuted,
                            mt: 1,
                        }}
                    >
                        {rows.length}{' '}
                        {t(rows.length !== 1 ? 'records' : 'record')}
                        {selectedRow && (
                            <Box
                                component="span"
                                sx={{
                                    ml: 1.25,
                                    color: palette.primary,
                                    fontWeight: 600,
                                }}
                            >
                                {' '} -{' '}
                                {t('{name} selected', {
                                    name:
                                        selectedRow.username ||
                                        selectedRow.name ||
                                        t('record'),
                                })}
                            </Box>
                        )}
                    </Typography>
                </Box>

                {onAdd && (
                    <Tooltip title={addLabel} arrow>
                        <Box
                            onClick={onAdd}
                            sx={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 0.9,
                                px: 2.25,
                                py: 1.2,
                                borderRadius: 999,
                                backgroundColor: palette.primary,
                                color: '#fff',
                                cursor: 'pointer',
                                fontSize: '0.95rem',
                                fontWeight: 700,
                                transition: 'all 0.15s ease',
                                userSelect: 'none',
                                boxShadow:
                                    '0 6px 16px rgba(37, 99, 235, 0.18)',
                                '&:hover': {
                                    backgroundColor: palette.primaryHover,
                                },
                            }}
                        >
                            <AddIcon sx={{fontSize: 18}}/>
                            {addLabel}
                        </Box>
                    </Tooltip>
                )}
            </Box>

            <TableContainer
                component={Paper}
                elevation={0}
                sx={{
                    border: `1px solid ${palette.border}`,
                    borderRadius: '24px',
                    overflow: 'hidden',
                    backgroundColor: palette.paperBg,
                }}
            >
                <Box sx={{maxHeight: tableMaxHeight, overflowY: 'auto'}}>
                    <Table stickyHeader>
                        <TableHead>
                            <TableRow
                                sx={{
                                    backgroundColor: palette.paperBgAlt,
                                }}
                            >
                                {columns.map((col) => (
                                    <TableCell
                                        key={col.key}
                                        align={col.align || 'left'}
                                        sx={{
                                            fontWeight: 700,
                                            fontSize: '0.82rem',
                                            color: palette.textMuted,
                                            borderBottom: `1px solid ${palette.border}`,
                                            py: 2,
                                            textTransform: 'uppercase',
                                            letterSpacing: '0.4px',
                                            width: col.width,
                                            whiteSpace: 'nowrap',
                                            backgroundColor: palette.paperBgAlt,
                                            position: 'sticky',
                                            top: 0,
                                            zIndex: 2,
                                        }}
                                    >
                                        {col.label}
                                    </TableCell>
                                ))}
                            </TableRow>
                        </TableHead>

                        <TableBody>
                            {loading ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={columns.length}
                                        align="center"
                                        sx={{py: 8}}
                                    >
                                        <CircularProgress size={28}/>
                                    </TableCell>
                                </TableRow>
                            ) : rows.length === 0 ? (
                                <TableRow>
                                    <TableCell
                                        colSpan={columns.length}
                                        align="center"
                                        sx={{
                                            py: 8,
                                            color: palette.textSoft,
                                            fontSize: '0.95rem',
                                        }}
                                    >
                                        {t('No records found')}
                                    </TableCell>
                                </TableRow>
                            ) : (
                                rows.map((row, idx) => {
                                    const sel = isSelected(row)

                                    return (
                                        <TableRow
                                            key={row.id || idx}
                                            onClick={() =>
                                                onSelectRow?.(sel ? null : row)
                                            }
                                            sx={{
                                                cursor: 'pointer',
                                                backgroundColor: sel
                                                    ? palette.selectedBg
                                                    : 'transparent',
                                                transition:
                                                    'background-color 0.12s ease',
                                                '&:hover': {
                                                    backgroundColor: sel
                                                        ? palette.selectedHoverBg
                                                        : palette.paperBgAlt,
                                                },
                                                '&:last-child td': {
                                                    borderBottom: 0,
                                                },
                                            }}
                                        >
                                            {columns.map((col) => (
                                                <TableCell
                                                    key={col.key}
                                                    align={col.align || 'left'}
                                                    sx={{
                                                        fontSize: '0.92rem',
                                                        color: palette.text,
                                                        py: 2,
                                                        borderBottom: `1px solid ${palette.borderSoft}`,
                                                        verticalAlign: 'middle',
                                                        whiteSpace: col.noWrap
                                                            ? 'nowrap'
                                                            : 'normal',
                                                    }}
                                                >
                                                    {col.render
                                                        ? col.render(
                                                              row[col.key],
                                                              row
                                                          )
                                                        : row[col.key]}
                                                </TableCell>
                                            ))}
                                        </TableRow>
                                    )
                                })
                            )}
                        </TableBody>
                    </Table>
                </Box>
            </TableContainer>
        </Box>
    )
}
