import {useEffect, useMemo, useState} from 'react'

function getThemeMode() {
    return document.documentElement.getAttribute('data-recon-theme') === 'dark'
        ? 'Dark'
        : 'Light'
}

export function getAdminPalette(themeMode) {
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
        menuBg: isDark ? '#111827' : '#FFFFFF',
        menuHover: isDark ? '#0F172A' : '#F8FAFC',
        iconButton: isDark ? '#CBD5E1' : '#475569',
        selectedBg: isDark
            ? 'rgba(var(--brand-primary-rgb), 0.18)'
            : 'var(--brand-primary-surface)',
        selectedHoverBg: isDark
            ? 'rgba(var(--brand-primary-rgb), 0.28)'
            : 'var(--brand-primary-surface-strong)',
        primary: 'var(--brand-primary)',
        primaryHover: 'var(--brand-primary-hover)',
        chipBlueBg: isDark
            ? 'rgba(var(--brand-primary-rgb), 0.18)'
            : 'var(--brand-primary-surface)',
        chipBlueText: isDark ? '#BFDBFE' : 'var(--brand-primary)',
        chipGreenBg: isDark ? 'rgba(34, 197, 94, 0.16)' : '#F0FDF4',
        chipGreenText: isDark ? '#86EFAC' : '#16A34A',
        chipRedBg: isDark ? '#2A1215' : '#FFF5F5',
        chipRedText: '#EF4444',
        chipNeutralBg: isDark ? '#111827' : '#F1F5F9',
        chipNeutralText: isDark ? '#CBD5E1' : '#475569',
        dangerBg: isDark ? '#2A1215' : '#FFF5F5',
        dangerText: '#EF4444',
        successBg: isDark ? 'rgba(34, 197, 94, 0.16)' : '#F0FDF4',
        successText: '#16A34A',
        scrollbarTrack: isDark ? '#0F172A' : '#F8FAFC',
        scrollbarThumb: isDark ? '#334155' : '#E2E8F0',
        dialogBg: isDark
            ? 'linear-gradient(180deg, #0F172A 0%, #111827 100%)'
            : 'linear-gradient(180deg, #FFFFFF 0%, #FCFDFE 100%)',
    }
}

export function useAdminPalette() {
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

    return useMemo(() => getAdminPalette(themeMode), [themeMode])
}
