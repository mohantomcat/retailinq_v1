import {Box, Typography} from '@mui/material'
import {
    PRODUCT_NAME,
    getBrandLogoSrc,
    hasCustomBrandLogo,
} from '../branding/brandingUtils'

const WORDMARK_WIDTH_RATIO = 213 / 348

function scaleResponsiveValue(value, factor, fallback) {
    if (value == null) {
        return fallback
    }

    if (typeof value === 'number') {
        return Math.round(value * factor)
    }

    if (typeof value === 'object' && !Array.isArray(value)) {
        return Object.fromEntries(
            Object.entries(value).map(([key, item]) => [
                key,
                scaleResponsiveValue(item, factor, fallback),
            ])
        )
    }

    if (typeof value === 'string') {
        if (value === 'auto') {
            return fallback
        }

        const parsed = Number(value)
        if (Number.isFinite(parsed)) {
            return `${Math.round(parsed * factor)}px`
        }
    }

    return fallback
}

export default function BrandLockup({
    branding,
    mode = 'light',
    appNameColor,
    subtitle,
    subtitleColor,
    logoWidth = 120,
    logoHeight = 44,
    nameFontSize = '1.5rem',
    nameFontWeight = 800,
    gap = 1.2,
    maxTextWidth,
    subtitleWrap = false,
    hideProductNameWhenDefaultLogo = false,
    productName = PRODUCT_NAME,
    defaultLogoWidth = logoWidth,
    defaultLogoHeight = logoHeight,
    wordmarkWidth,
}) {
    const logoSrc = getBrandLogoSrc(branding, mode)
    const customLogo = hasCustomBrandLogo(branding)
    const fixedWordmarkSrc =
        mode === 'dark'
            ? '/retailinq-wordmark-dark.svg'
            : '/retailinq-wordmark-light.svg'
    const computedWordmarkWidth =
        wordmarkWidth ||
        scaleResponsiveValue(
            defaultLogoWidth,
            WORDMARK_WIDTH_RATIO,
            maxTextWidth
        ) ||
        maxTextWidth

    if (logoSrc && customLogo) {
        return (
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    gap,
                    minWidth: 0,
                }}
            >
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'flex-start',
                        width: logoWidth,
                        height: logoHeight,
                        minWidth: logoWidth,
                        overflow: 'hidden',
                        flexShrink: 0,
                    }}
                >
                    <Box
                        component="img"
                        src={logoSrc}
                        alt={`${productName} tenant logo`}
                        sx={{
                            display: 'block',
                            width: '100%',
                            height: '100%',
                            objectFit: 'contain',
                            objectPosition: 'left center',
                        }}
                    />
                </Box>

                <Box sx={{minWidth: 0}}>
                    <Box
                        component="img"
                        src={fixedWordmarkSrc}
                        alt={`${productName} wordmark`}
                        sx={{
                            display: 'block',
                            width: computedWordmarkWidth,
                            maxWidth: '100%',
                            height: 'auto',
                            objectFit: 'contain',
                            objectPosition: 'left center',
                        }}
                    />

                    {subtitle ? (
                        <Typography
                            sx={{
                                mt: 0.4,
                                fontSize: '0.78rem',
                                fontWeight: 600,
                                color: subtitleColor,
                                lineHeight: 1.2,
                                whiteSpace: subtitleWrap ? 'normal' : 'nowrap',
                                overflow: subtitleWrap ? 'visible' : 'hidden',
                                textOverflow: subtitleWrap ? 'clip' : 'ellipsis',
                                maxWidth: maxTextWidth,
                            }}
                        >
                            {subtitle}
                        </Typography>
                    ) : null}
                </Box>
            </Box>
        )
    }

    if (logoSrc && hideProductNameWhenDefaultLogo && !customLogo) {
        return (
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'center',
                    minWidth: 0,
                }}
            >
                <Box
                    component="img"
                    src={logoSrc}
                    alt={`${productName} logo`}
                    sx={{
                        display: 'block',
                        width: defaultLogoWidth,
                        maxWidth: '100%',
                        height: defaultLogoHeight,
                        objectFit: 'contain',
                        objectPosition: 'left center',
                    }}
                />
            </Box>
        )
    }

    return (
        <Box
            sx={{
                display: 'flex',
                alignItems: 'center',
                gap,
                minWidth: 0,
            }}
        >
            {logoSrc && (
                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'flex-start',
                        width: logoWidth,
                        height: logoHeight,
                        minWidth: logoWidth,
                        overflow: 'hidden',
                        flexShrink: 0,
                    }}
                >
                    <Box
                        component="img"
                        src={logoSrc}
                        alt={`${productName} logo`}
                        sx={{
                            display: 'block',
                            width: '100%',
                            height: '100%',
                            objectFit: 'contain',
                            objectPosition: 'left center',
                        }}
                    />
                </Box>
            )}

            <Box sx={{minWidth: 0}}>
                <Typography
                    sx={{
                        fontSize: nameFontSize,
                        fontWeight: nameFontWeight,
                        letterSpacing: '-0.04em',
                        color: appNameColor,
                        lineHeight: 1,
                        maxWidth: maxTextWidth,
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                    }}
                >
                    {productName}
                </Typography>

                {subtitle ? (
                    <Typography
                        sx={{
                            mt: 0.4,
                            fontSize: '0.78rem',
                            fontWeight: 600,
                            color: subtitleColor,
                            lineHeight: 1.2,
                            whiteSpace: subtitleWrap ? 'normal' : 'nowrap',
                            overflow: subtitleWrap ? 'visible' : 'hidden',
                            textOverflow: subtitleWrap ? 'clip' : 'ellipsis',
                            maxWidth: maxTextWidth,
                        }}
                    >
                        {subtitle}
                    </Typography>
                ) : null}
            </Box>
        </Box>
    )
}
