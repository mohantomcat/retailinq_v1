import React, {useEffect, useMemo, useState} from 'react'
import {Box, Button, Divider, Paper, Stack, Typography,} from '@mui/material'
import * as XLSX from 'xlsx'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
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
        headerBg: isDark ? '#111827' : '#F8FAFC',
        headerText: isDark ? '#CBD5E1' : '#374151',
        rowHover: isDark ? '#111827' : '#F1F5F9',
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
    px: 2,
    '&:hover': {
        backgroundColor: palette.buttonHoverBg,
        borderColor: '#1D4ED8',
    },
})

const DetailTable = ({
                         title = 'Detail Records',
                         data = [],
                     }) => {
    const [themeMode, setThemeMode] = useState(getThemeMode())
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

    const exportData = data || []

    const handleExportCSV = () => {
        if (!exportData.length) return

        const headers = Object.keys(exportData[0])

        const csvContent =
            headers.join(',') +
            '\n' +
            exportData
                .map((row) =>
                    headers.map((field) => `"${row[field] ?? ''}"`).join(',')
                )
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
        if (!exportData.length) return

        const worksheet = XLSX.utils.json_to_sheet(exportData)
        const workbook = XLSX.utils.book_new()
        XLSX.utils.book_append_sheet(workbook, worksheet, 'Data')
        XLSX.writeFile(workbook, 'reconciliation_data.xlsx')
    }

    const handleExportPDF = () => {
        if (!exportData.length) return

        const doc = new jsPDF()

        const headers = [Object.keys(exportData[0])]
        const body = exportData.map((row) => Object.values(row))

        autoTable(doc, {
            head: headers,
            body,
            styles: {fontSize: 8},
        })

        doc.save('reconciliation_data.pdf')
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
                alignItems="center"
                mb={2}
                flexWrap="wrap"
                gap={1.5}
            >
                <Typography
                    variant="h6"
                    sx={{
                        fontWeight: 600,
                        color: palette.title,
                    }}
                >
                    {title || t('Detail Records')}
                </Typography>

                <Stack direction="row" spacing={2} flexWrap="wrap">
                    <Button
                        variant="outlined"
                        size="small"
                        onClick={handleExportExcel}
                        sx={buildButtonStyle(palette)}
                    >
                        {t('Excel')}
                    </Button>

                    <Button
                        variant="outlined"
                        size="small"
                        onClick={handleExportCSV}
                        sx={buildButtonStyle(palette)}
                    >
                        {t('CSV')}
                    </Button>

                    <Button
                        variant="outlined"
                        size="small"
                        onClick={handleExportPDF}
                        sx={buildButtonStyle(palette)}
                    >
                        {t('PDF')}
                    </Button>
                </Stack>
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
                        {exportData.length > 0 &&
                            Object.keys(exportData[0]).map((key) => (
                                <th
                                    key={key}
                                    style={{
                                        padding: '12px 14px',
                                        borderBottom: `1px solid ${palette.border}`,
                                        fontWeight: 600,
                                        letterSpacing: '0.3px',
                                    }}
                                >
                                    {key}
                                </th>
                            ))}
                    </tr>
                    </thead>

                    <tbody>
                    {exportData.map((row, index) => (
                        <tr
                            key={index}
                            style={{
                                borderBottom: `1px solid ${palette.borderSoft}`,
                                transition: 'background-color 0.2s ease',
                            }}
                            onMouseEnter={(e) =>
                                (e.currentTarget.style.backgroundColor =
                                    palette.rowHover)
                            }
                            onMouseLeave={(e) =>
                                (e.currentTarget.style.backgroundColor =
                                    'transparent')
                            }
                        >
                            {Object.values(row).map((value, i) => (
                                <td
                                    key={i}
                                    style={{
                                        padding: '12px 14px',
                                        color: palette.text,
                                        fontWeight: 400,
                                    }}
                                >
                                    {value}
                                </td>
                            ))}
                        </tr>
                    ))}
                    </tbody>
                </table>

                {exportData.length === 0 && (
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
                )}
            </Box>
        </Paper>
    )
}

export default DetailTable
