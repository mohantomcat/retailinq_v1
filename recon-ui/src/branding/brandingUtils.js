export const PRODUCT_NAME = 'RetailINQ'
export const PRODUCT_TAGLINE = 'Retail Reconciliation | Analytics | Integrations'

export const DEFAULT_BRANDING = {
    tenantId: '',
    appName: PRODUCT_NAME,
    lightLogoData: '',
    darkLogoData: '',
    primaryColor: '#3F6FD8',
    secondaryColor: '#5F7CE2',
    customized: false,
}

function trimToNull(value) {
    if (value == null) {
        return null
    }
    const trimmed = String(value).trim()
    return trimmed ? trimmed : null
}

export function normalizeHexColor(value, fallback) {
    const trimmed = trimToNull(value)
    if (!trimmed) {
        return fallback
    }
    const normalized = trimmed.toUpperCase()
    return /^#[0-9A-F]{6}$/.test(normalized) ? normalized : fallback
}

function hexToRgbParts(hex) {
    const normalized = normalizeHexColor(hex, DEFAULT_BRANDING.primaryColor)
    return {
        r: parseInt(normalized.slice(1, 3), 16),
        g: parseInt(normalized.slice(3, 5), 16),
        b: parseInt(normalized.slice(5, 7), 16),
    }
}

function mixChannel(source, target, weight) {
    return Math.round(source + (target - source) * weight)
}

export function mixHex(hex, targetHex, weight) {
    const source = hexToRgbParts(hex)
    const target = hexToRgbParts(targetHex)
    const red = mixChannel(source.r, target.r, weight)
    const green = mixChannel(source.g, target.g, weight)
    const blue = mixChannel(source.b, target.b, weight)
    return `#${[red, green, blue]
        .map((part) => part.toString(16).padStart(2, '0'))
        .join('')
        .toUpperCase()}`
}

export function rgba(hex, alpha) {
    const {r, g, b} = hexToRgbParts(hex)
    return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

export function getRgbString(hex) {
    const {r, g, b} = hexToRgbParts(hex)
    return `${r}, ${g}, ${b}`
}

export function getBrandTokens(input) {
    const branding = resolveBranding(input)
    const primary = branding.primaryColor
    const secondary = branding.secondaryColor
    const primaryHover = mixHex(primary, '#0F172A', 0.12)
    const secondaryHover = mixHex(secondary, '#0F172A', 0.12)

    return {
        primary,
        secondary,
        primaryHover,
        secondaryHover,
        primaryRgb: getRgbString(primary),
        secondaryRgb: getRgbString(secondary),
        primarySurface: rgba(primary, 0.08),
        primarySurfaceStrong: rgba(primary, 0.14),
        secondarySurface: rgba(secondary, 0.10),
        secondarySurfaceStrong: rgba(secondary, 0.16),
        primaryBorder: rgba(primary, 0.22),
        secondaryBorder: rgba(secondary, 0.22),
        primaryGlow: rgba(primary, 0.18),
        secondaryGlow: rgba(secondary, 0.18),
        buttonGradient: `linear-gradient(135deg, ${primary} 0%, ${secondary} 100%)`,
        buttonGradientHover: `linear-gradient(135deg, ${primaryHover} 0%, ${secondaryHover} 100%)`,
    }
}

export function resolveBranding(value) {
    const next = value || {}
    return {
        ...DEFAULT_BRANDING,
        ...next,
        tenantId: trimToNull(next.tenantId) || DEFAULT_BRANDING.tenantId,
        appName: PRODUCT_NAME,
        lightLogoData: trimToNull(next.lightLogoData) || '',
        darkLogoData: trimToNull(next.darkLogoData) || '',
        primaryColor: DEFAULT_BRANDING.primaryColor,
        secondaryColor: DEFAULT_BRANDING.secondaryColor,
        customized: Boolean(
            trimToNull(next.lightLogoData) || trimToNull(next.darkLogoData)
        ),
    }
}

export function hasCustomBrandLogo(input) {
    const branding = resolveBranding(input)
    return Boolean(branding.lightLogoData || branding.darkLogoData)
}

export function getBrandLogoSrc(input, mode = 'light') {
    const branding = resolveBranding(input)
    if (mode === 'dark') {
        return branding.darkLogoData || branding.lightLogoData || '/logo-dark.svg'
    }
    return branding.lightLogoData || '/logo-light.svg'
}

export function applyBrandingVariables(input) {
    if (typeof document === 'undefined') {
        return
    }

    const tokens = getBrandTokens(input)
    const root = document.documentElement

    root.style.setProperty('--brand-primary', tokens.primary)
    root.style.setProperty('--brand-secondary', tokens.secondary)
    root.style.setProperty('--brand-primary-hover', tokens.primaryHover)
    root.style.setProperty('--brand-secondary-hover', tokens.secondaryHover)
    root.style.setProperty('--brand-primary-rgb', tokens.primaryRgb)
    root.style.setProperty('--brand-secondary-rgb', tokens.secondaryRgb)
    root.style.setProperty('--brand-primary-surface', tokens.primarySurface)
    root.style.setProperty(
        '--brand-primary-surface-strong',
        tokens.primarySurfaceStrong
    )
    root.style.setProperty('--brand-secondary-surface', tokens.secondarySurface)
    root.style.setProperty(
        '--brand-secondary-surface-strong',
        tokens.secondarySurfaceStrong
    )
    root.style.setProperty('--brand-primary-border', tokens.primaryBorder)
    root.style.setProperty('--brand-secondary-border', tokens.secondaryBorder)
    root.style.setProperty('--brand-primary-glow', tokens.primaryGlow)
    root.style.setProperty('--brand-secondary-glow', tokens.secondaryGlow)
}
