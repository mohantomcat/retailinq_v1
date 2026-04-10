import React, {useEffect, useMemo, useState} from 'react'
import {Box, Button, Divider, Menu, MenuItem, Paper, Stack, Typography} from '@mui/material'
import {useI18n} from '../context/I18nContext'

function getThemeMode() {
    return document.documentElement.getAttribute('data-recon-theme') === 'dark'
        ? 'Dark'
        : 'Light'
}

function getPalette(themeMode) {
    const isDark = themeMode === 'Dark'

    return {
        isDark,
        paperBg: isDark ? '#0F172A' : '#FFFFFF',
        border: isDark ? '#1E293B' : '#E5E7EB',
        borderSoft: isDark ? '#243041' : '#F1F5F9',
        title: isDark ? '#E2E8F0' : '#1F2937',
        text: isDark ? '#CBD5E1' : '#111827',
        textMuted: isDark ? '#94A3B8' : '#9CA3AF',
        headerBg: isDark ? '#13213B' : '#EAF2FF',
        headerText: isDark ? '#BFDBFE' : '#1D4ED8',
        headerBorder: isDark ? '#1D4ED8' : '#BFDBFE',
        rowHover: isDark ? '#162235' : '#F5F9FF',
        selectedRowBg: isDark ? '#1A2E4A' : '#DBEAFE',
        selectedRowBorder: isDark ? '#60A5FA' : '#3B82F6',
        buttonBorder: '#2563EB',
        buttonText: '#2563EB',
        buttonHoverBg: isDark ? '#0B1220' : '#EFF6FF',
        scrollbarTrack: isDark ? '#0F172A' : '#F1F5F9',
        scrollbarThumb: isDark ? '#334155' : '#CBD5E1',
    }
}

const buildButtonStyle = (palette) => ({
    textTransform: 'none',
    borderColor: palette.buttonBorder,
    color: palette.buttonText,
    fontWeight: 600,
    px: 1.75,
    '&:hover': {
        backgroundColor: palette.buttonHoverBg,
        borderColor: '#1D4ED8',
    },
})

const DetailTable = ({
    title = 'Detail Records',
    subtitle = '',
    data = [],
    onRowSelect,
    selectedRowKey = null,
    renderActions,
}) => {
    const [themeMode, setThemeMode] = useState(getThemeMode())
    const [exportAnchorEl, setExportAnchorEl] = useState(null)
    const {t} = useI18n()

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

    const palette = useMemo(() => getPalette(themeMode), [themeMode])

    const exportData = useMemo(
        () => (data || []).map((row) =>
            Object.fromEntries(
                Object.entries(row).filter(([key]) => !key.startsWith('__'))
            )
        ),
        [data]
    )

    const visibleColumns = useMemo(
        () => (exportData.length > 0 ? Object.keys(exportData[0]) : []),
        [exportData]
    )

    const closeExportMenu = () => setExportAnchorEl(null)

    const handleExportCSV = () => {
        closeExportMenu()
        if (!exportData.length) return

        const headers = Object.keys(exportData[0])
        const csvContent =
            headers.join(',') +
            '\n' +
            exportData
                .map((row) => headers.map((field) => `"${row[field] ?? ''}"`).join(','))
                .join('\n')

        const blob = new Blob([csvContent], {
            type: 'text/csv;charset=utf-8;',
        })

        const link = document.createElement('a')
        link.href = URL.createObjectURL(blob)
        link.download = 'reconciliation_data.csv'
        link.click()
    }

    const handleExportExcel = () => {
        closeExportMenu()
        if (!exportData.length) return

        const headers = Object.keys(exportData[0])
        const rows = exportData.map((row) =>
            `<tr>${headers.map((field) => `<td>${escapeHtml(row[field])}</td>`).join('')}</tr>`
        )
        const workbook = [
            '<html><head><meta charset="UTF-8"></head><body><table>',
            `<thead><tr>${headers.map((field) => `<th>${escapeHtml(field)}</th>`).join('')}</tr></thead>`,
            `<tbody>${rows.join('')}</tbody>`,
            '</table></body></html>',
        ].join('')

        const blob = new Blob([workbook], {
            type: 'application/vnd.ms-excel;charset=utf-8;',
        })
        const link = document.createElement('a')
        link.href = URL.createObjectURL(blob)
        link.download = 'reconciliation_data.xls'
        link.click()
    }

    const handleExportPDF = () => {
        closeExportMenu()
        if (!exportData.length) return

        Promise.all([
            import('jspdf'),
            import('jspdf-autotable'),
        ]).then(([jspdfModule, autoTableModule]) => {
            const jsPDF = jspdfModule.default
            const autoTable = autoTableModule.default
            const doc = new jsPDF()
            const headers = [Object.keys(exportData[0])]
            const body = exportData.map((row) => Object.values(row))

            autoTable(doc, {
                head: headers,
                body,
                styles: {fontSize: 8},
            })

            doc.save('reconciliation_data.pdf')
        })
    }

    return (
        <Paper
            elevation={0}
            sx={{
                backgroundColor: palette.paperBg,
                border: `1px solid ${palette.border}`,
                borderRadius: 3,
                p: 3,
            }}
        >
            <Stack
                direction="row"
                justifyContent="space-between"
                alignItems="flex-start"
                mb={subtitle ? 1.25 : 2}
                flexWrap="wrap"
                gap={1.5}
            >
                <Box>
                    <Typography
                        variant="h6"
                        sx={{
                            fontWeight: 700,
                            color: palette.title,
                        }}
                    >
                        {title || t('Detail Records')}
                    </Typography>
                    {subtitle ? (
                        <Typography
                            sx={{
                                mt: 0.45,
                                maxWidth: 780,
                                fontSize: '0.84rem',
                                color: palette.textMuted,
                                lineHeight: 1.55,
                            }}
                        >
                            {subtitle}
                        </Typography>
                    ) : null}
                </Box>

                <Box>
                    <Button
                        variant="outlined"
                        size="small"
                        onClick={(event) => setExportAnchorEl(event.currentTarget)}
                        disabled={!exportData.length}
                        sx={buildButtonStyle(palette)}
                    >
                        {t('Export')}
                    </Button>
                    <Menu
                        anchorEl={exportAnchorEl}
                        open={Boolean(exportAnchorEl)}
                        onClose={closeExportMenu}
                        PaperProps={{
                            sx: {
                                border: `1px solid ${palette.border}`,
                                backgroundColor: palette.paperBg,
                                color: palette.text,
                            },
                        }}
                    >
                        <MenuItem onClick={handleExportExcel}>{t('Export to Excel')}</MenuItem>
                        <MenuItem onClick={handleExportCSV}>{t('Export to CSV')}</MenuItem>
                        <MenuItem onClick={handleExportPDF}>{t('Export to PDF')}</MenuItem>
                    </Menu>
                </Box>
            </Stack>

            <Divider sx={{mb: 2, borderColor: palette.borderSoft}}/>

            <Box
                sx={{
                    overflowX: 'auto',
                    '&::-webkit-scrollbar': {
                        height: 8,
                    },
                    '&::-webkit-scrollbar-track': {
                        backgroundColor: palette.scrollbarTrack,
                    },
                    '&::-webkit-scrollbar-thumb': {
                        backgroundColor: palette.scrollbarThumb,
                        borderRadius: 8,
                    },
                }}
            >
                <table
                    style={{
                        width: '100%',
                        borderCollapse: 'collapse',
                        fontSize: '14px',
                    }}
                >
                    <thead>
                    <tr
                        style={{
                            backgroundColor: palette.headerBg,
                            textAlign: 'left',
                            color: palette.headerText,
                        }}
                    >
                        {visibleColumns.map((key) => (
                            <th
                                key={key}
                                style={{
                                    padding: '12px 14px',
                                    borderBottom: `1px solid ${palette.headerBorder}`,
                                    fontWeight: 600,
                                    letterSpacing: '0.3px',
                                    whiteSpace: 'nowrap',
                                }}
                            >
                                {key}
                            </th>
                        ))}
                        {renderActions ? (
                            <th
                                style={{
                                    padding: '12px 14px',
                                    borderBottom: `1px solid ${palette.headerBorder}`,
                                    fontWeight: 600,
                                    letterSpacing: '0.3px',
                                    whiteSpace: 'nowrap',
                                }}
                            >
                                {t('Actions')}
                            </th>
                        ) : null}
                    </tr>
                    </thead>

                    <tbody>
                    {(data || []).map((row, index) => (
                        (() => {
                            const isSelected = selectedRowKey && row.__rowKey === selectedRowKey
                            return (
                                <tr
                                    key={index}
                                    aria-selected={Boolean(isSelected)}
                                    style={{
                                        borderBottom: `1px solid ${palette.borderSoft}`,
                                        transition: 'background-color 0.2s ease, box-shadow 0.2s ease',
                                        cursor: onRowSelect ? 'pointer' : 'default',
                                        backgroundColor: isSelected ? palette.selectedRowBg : 'transparent',
                                        boxShadow: isSelected ? `inset 4px 0 0 ${palette.selectedRowBorder}` : 'none',
                                    }}
                                    onClick={() => onRowSelect?.(row)}
                                    onMouseEnter={(event) => {
                                        event.currentTarget.style.backgroundColor = isSelected
                                            ? palette.selectedRowBg
                                            : palette.rowHover
                                    }}
                                    onMouseLeave={(event) => {
                                        event.currentTarget.style.backgroundColor = isSelected
                                            ? palette.selectedRowBg
                                            : 'transparent'
                                    }}
                                >
                            {(() => {
                                const cellRenderers = row.__cellRenderers || {}
                                return Object.entries(row)
                                    .filter(([key]) => !key.startsWith('__'))
                                    .map(([key, value]) => (
                                        <td
                                            key={key}
                                            style={{
                                                padding: '12px 14px',
                                                color: palette.text,
                                                fontWeight: isSelected ? 600 : 400,
                                                verticalAlign: 'top',
                                                whiteSpace: 'nowrap',
                                            }}
                                        >
                                            {cellRenderers[key] ?? value}
                                        </td>
                                    ))
                            })()}
                            {renderActions ? (
                                <td
                                    style={{
                                        padding: '10px 14px',
                                        color: palette.text,
                                        verticalAlign: 'top',
                                        minWidth: 220,
                                    }}
                                    onClick={(event) => event.stopPropagation()}
                                >
                                    {renderActions(row)}
                                </td>
                            ) : null}
                                </tr>
                            )
                        })()
                    ))}
                    </tbody>
                </table>

                {exportData.length === 0 ? (
                    <Typography
                        variant="body2"
                        sx={{
                            textAlign: 'center',
                            color: palette.textMuted,
                            py: 4,
                        }}
                    >
                        {t('No records available.')}
                    </Typography>
                ) : null}
            </Box>
        </Paper>
    )
}

function escapeHtml(value) {
    return String(value ?? '')
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;')
}

export default DetailTable
