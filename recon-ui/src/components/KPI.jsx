import {useEffect, useMemo, useState} from 'react'
import {Box, Card, CardContent, Typography} from '@mui/material'

function getThemeMode() {
    return document.documentElement.getAttribute('data-recon-theme') === 'dark'
        ? 'Dark'
        : 'Light'
}

function getPalette(themeMode) {
    const isDark = themeMode === 'Dark'

    return {
        isDark,
        cardBg: isDark ? '#0F172A' : '#FFFFFF',
        border: isDark ? '#1E293B' : '#E5E7EB',
        text: isDark ? '#E2E8F0' : '#111827',
        textMuted: isDark ? '#94A3B8' : '#6B7280',
        hoverShadow: isDark
            ? '0 8px 20px rgba(0,0,0,0.28)'
            : '0 6px 16px rgba(0,0,0,0.06)',
        selectedShadow: isDark
            ? '0 6px 18px rgba(37, 99, 235, 0.22)'
            : '0 4px 12px rgba(37, 99, 235, 0.08)',
        selectedBg: isDark ? '#0B1220' : '#FFFFFF',
    }
}

export default function KPI({title, value, onClick, selected}) {
    const [themeMode, setThemeMode] = useState(getThemeMode())

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

    return (
        <Card
            onClick={onClick}
            sx={{
                position: 'relative',
                cursor: 'pointer',
                height: '100%',
                borderRadius: 3,
                border: `1px solid ${selected ? '#2563EB' : palette.border}`,
                bgcolor: selected ? palette.selectedBg : palette.cardBg,
                transition: 'all 0.2s ease',
                boxShadow: selected ? palette.selectedShadow : 'none',

                '&:hover': {
                    boxShadow: palette.hoverShadow,
                    transform: 'translateY(-2px)',
                },
            }}
        >
            <Box
                sx={{
                    position: 'absolute',
                    left: 0,
                    top: 0,
                    bottom: 0,
                    width: 4,
                    borderTopLeftRadius: 12,
                    borderBottomLeftRadius: 12,
                    bgcolor: selected ? '#2563EB' : 'transparent',
                }}
            />

            <CardContent sx={{pl: 3}}>
                <Typography
                    variant="subtitle2"
                    sx={{
                        color: palette.textMuted,
                        fontWeight: 600,
                        letterSpacing: '0.4px',
                        textTransform: 'uppercase',
                        fontSize: '0.75rem',
                    }}
                    gutterBottom
                >
                    {title}
                </Typography>

                <Typography
                    variant="h4"
                    sx={{
                        fontWeight: 700,
                        color: palette.text,
                    }}
                >
                    {value ? value.toLocaleString() : 0}
                </Typography>
            </CardContent>
        </Card>
    )
}