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
        cardBgAlt: isDark ? '#111827' : '#F8FAFC',
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

function getToneStyles(tone, palette) {
    switch (tone) {
        case 'neutral':
            return {
                accent: palette.isDark ? '#94A3B8' : '#64748B',
                valueColor: palette.text,
            }
        case 'success':
            return {
                accent: palette.isDark ? '#34D399' : '#15803D',
                valueColor: palette.isDark ? '#86EFAC' : '#15803D',
            }
        case 'warning':
            return {
                accent: palette.isDark ? '#FBBF24' : '#B45309',
                valueColor: palette.isDark ? '#FCD34D' : '#B45309',
            }
        case 'error':
            return {
                accent: palette.isDark ? '#F87171' : '#B91C1C',
                valueColor: palette.isDark ? '#FCA5A5' : '#B91C1C',
            }
        case 'info':
        default:
            return {
                accent: '#2563EB',
                valueColor: '#2563EB',
            }
    }
}

function formatValue(value) {
    if (value === null || value === undefined || value === '') {
        return '0'
    }
    if (typeof value === 'number') {
        return value.toLocaleString()
    }
    return String(value)
}

export default function KPI({
    title,
    value,
    onClick,
    selected,
    testId,
    supportingText,
    tone = 'info',
}) {
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
    const toneStyles = useMemo(() => getToneStyles(tone, palette), [tone, palette])
    const interactive = typeof onClick === 'function'
    const displayValue = formatValue(value)
    const isStringValue = typeof value === 'string'

    return (
        <Card
            onClick={onClick}
            data-testid={testId}
            sx={{
                position: 'relative',
                cursor: interactive ? 'pointer' : 'default',
                minHeight: 108,
                height: '100%',
                borderRadius: 3,
                border: `1px solid ${selected ? '#2563EB' : palette.border}`,
                bgcolor: selected ? palette.selectedBg : palette.cardBg,
                transition: 'all 0.2s ease',
                boxShadow: selected ? palette.selectedShadow : 'none',
                '&:hover': interactive
                    ? {
                        boxShadow: palette.hoverShadow,
                        transform: 'translateY(-2px)',
                    }
                    : undefined,
            }}
        >
            <Box
                sx={{
                    position: 'absolute',
                    left: 0,
                    top: 0,
                    bottom: 0,
                    width: 5,
                    borderTopLeftRadius: 12,
                    borderBottomLeftRadius: 12,
                    bgcolor: selected ? '#2563EB' : toneStyles.accent,
                }}
            />

            <CardContent sx={{pl: 2.4, pr: 2, py: 1.55}}>
                <Typography
                    sx={{
                        color: palette.textMuted,
                        fontWeight: 600,
                        fontSize: '0.78rem',
                        lineHeight: 1.35,
                    }}
                    gutterBottom
                >
                    {title}
                </Typography>

                <Typography
                    sx={{
                        fontWeight: 800,
                        color: selected ? '#2563EB' : toneStyles.valueColor,
                        fontSize: isStringValue ? '1.45rem' : '2rem',
                        lineHeight: 1.15,
                        letterSpacing: isStringValue ? '-0.02em' : '-0.03em',
                    }}
                >
                    {displayValue}
                </Typography>

                {supportingText ? (
                    <Typography
                        sx={{
                            mt: 0.45,
                            fontSize: '0.74rem',
                            color: palette.textMuted,
                            lineHeight: 1.45,
                            display: '-webkit-box',
                            WebkitLineClamp: 2,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                        }}
                    >
                        {supportingText}
                    </Typography>
                ) : null}
            </CardContent>
        </Card>
    )
}
