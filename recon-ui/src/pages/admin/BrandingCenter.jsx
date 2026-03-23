import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Paper,
    Stack,
    Typography,
} from '@mui/material'
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined'
import BrandLockup from '../../components/BrandLockup'
import {useAdminPalette} from '../../components/admin/theme'
import {useI18n} from '../../context/I18nContext'
import {useBranding} from '../../context/BrandingContext'
import {
    DEFAULT_BRANDING,
    PRODUCT_NAME,
    getBrandTokens,
    resolveBranding,
} from '../../branding/brandingUtils'
import {brandingApi} from '../../services/brandingApi'

const MAX_FILE_SIZE_BYTES = 400 * 1024

function readFileAsDataUrl(file) {
    return new Promise((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result || ''))
        reader.onerror = () => reject(new Error('Failed to read image'))
        reader.readAsDataURL(file)
    })
}

export default function BrandingCenter() {
    const palette = useAdminPalette()
    const {t} = useI18n()
    const {applyBranding} = useBranding()
    const [form, setForm] = useState(DEFAULT_BRANDING)
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [success, setSuccess] = useState('')

    const previewBranding = useMemo(() => resolveBranding(form), [form])
    const brandTokens = useMemo(
        () => getBrandTokens(previewBranding),
        [previewBranding]
    )

    const loadBranding = async () => {
        setLoading(true)
        try {
            const data = await brandingApi.getAdminBranding()
            setForm(resolveBranding(data))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to load branding')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadBranding()
    }, [])

    const handleLogoUpload = async (slot, event) => {
        const file = event.target.files?.[0]
        event.target.value = ''
        if (!file) {
            return
        }
        if (
            ![
                'image/png',
                'image/svg+xml',
                'image/jpeg',
                'image/webp',
            ].includes(file.type)
        ) {
            setError(t('Use SVG, PNG, JPEG, or WEBP logo files only.'))
            return
        }
        if (file.size > MAX_FILE_SIZE_BYTES) {
            setError(
                t('Logo files must be 400 KB or smaller for fast login loads.')
            )
            return
        }
        try {
            const dataUrl = await readFileAsDataUrl(file)
            setForm((current) => ({
                ...current,
                [slot]: dataUrl,
            }))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to read logo file')
        }
    }

    const saveBranding = async () => {
        setSaving(true)
        try {
            const saved = await brandingApi.saveTenantBranding({
                appName: PRODUCT_NAME,
                lightLogoData: previewBranding.lightLogoData || null,
                darkLogoData: previewBranding.darkLogoData || null,
                primaryColor: DEFAULT_BRANDING.primaryColor,
                secondaryColor: DEFAULT_BRANDING.secondaryColor,
            })
            const resolved = resolveBranding(saved)
            setForm(resolved)
            applyBranding(resolved)
            setSuccess(t('Tenant branding updated'))
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to save branding')
        } finally {
            setSaving(false)
        }
    }

    return (
        <Box sx={{px: 4, py: 3, maxWidth: 1520}}>
            <Typography
                sx={{fontSize: '1.15rem', fontWeight: 800, color: palette.text}}
            >
                {t('Branding Center')}
            </Typography>
            <Typography
                sx={{
                    fontSize: '0.88rem',
                    color: palette.textMuted,
                    mt: 0.5,
                    mb: 2.5,
                }}
            >
                {t(
                    'Upload tenant-specific light and dark logos while keeping the core RetailINQ product identity consistent.'
                )}
            </Typography>

            {error && (
                <Alert severity="error" sx={{mb: 2, borderRadius: 3}}>
                    {error}
                </Alert>
            )}
            {success && (
                <Alert severity="success" sx={{mb: 2, borderRadius: 3}}>
                    {success}
                </Alert>
            )}

            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: {xs: '1fr', xl: '1.04fr 0.96fr'},
                    gap: 2,
                }}
            >
                <Paper
                    elevation={0}
                    sx={{
                        p: 2.5,
                        borderRadius: '24px',
                        border: `1px solid ${palette.border}`,
                        backgroundColor: palette.paperBg,
                    }}
                >
                    <Stack spacing={2}>
                        <Typography
                            sx={{
                                fontSize: '0.86rem',
                                color: palette.textMuted,
                                lineHeight: 1.55,
                            }}
                        >
                            {t(
                                'RetailINQ remains the fixed product brand. Tenant branding here only controls the logo artwork shown in light and dark theme surfaces.'
                            )}
                        </Typography>

                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: {xs: '1fr', md: '1fr 1fr'},
                                gap: 1.5,
                            }}
                        >
                            {[
                                {
                                    key: 'lightLogoData',
                                    title: t('Light Theme Logo'),
                                    helper: t('Shown on login and light header.'),
                                },
                                {
                                    key: 'darkLogoData',
                                    title: t('Dark Theme Logo'),
                                    helper: t('Shown when users switch to dark mode.'),
                                },
                            ].map((slot) => {
                                const hasLogo = Boolean(previewBranding[slot.key])
                                return (
                                    <Paper
                                        key={slot.key}
                                        elevation={0}
                                        sx={{
                                            p: 1.5,
                                            borderRadius: '20px',
                                            border: `1px solid ${palette.borderSoft}`,
                                            backgroundColor: palette.paperBgAlt,
                                        }}
                                    >
                                        <Stack spacing={1.2}>
                                            <Box>
                                                <Typography
                                                    sx={{
                                                        fontSize: '0.88rem',
                                                        fontWeight: 800,
                                                        color: palette.text,
                                                    }}
                                                >
                                                    {slot.title}
                                                </Typography>
                                                <Typography
                                                    sx={{
                                                        mt: 0.35,
                                                        fontSize: '0.76rem',
                                                        color: palette.textMuted,
                                                    }}
                                                >
                                                    {slot.helper}
                                                </Typography>
                                            </Box>

                                            <Box
                                                sx={{
                                                    minHeight: 120,
                                                    borderRadius: 3,
                                                    border: `1px dashed ${palette.border}`,
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    justifyContent: 'center',
                                                    backgroundColor:
                                                        slot.key === 'darkLogoData'
                                                            ? '#0F172A'
                                                            : '#FFFFFF',
                                                    px: 2,
                                                }}
                                            >
                                                {hasLogo ? (
                                                    <BrandLockup
                                                        branding={previewBranding}
                                                        mode={
                                                            slot.key === 'darkLogoData'
                                                                ? 'dark'
                                                                : 'light'
                                                        }
                                                        productName={PRODUCT_NAME}
                                                        appNameColor={
                                                            slot.key === 'darkLogoData'
                                                                ? '#FFFFFF'
                                                                : '#22314D'
                                                        }
                                                        logoWidth={92}
                                                        logoHeight={50}
                                                        defaultLogoWidth={150}
                                                        defaultLogoHeight={44}
                                                        nameFontSize="1.18rem"
                                                        gap={1}
                                                        maxTextWidth={150}
                                                        hideProductNameWhenDefaultLogo
                                                    />
                                                ) : (
                                                    <Stack
                                                        spacing={0.8}
                                                        alignItems="center"
                                                    >
                                                        <ImageOutlinedIcon
                                                            sx={{
                                                                color:
                                                                    slot.key ===
                                                                    'darkLogoData'
                                                                        ? '#94A3B8'
                                                                        : palette.textSoft,
                                                            }}
                                                        />
                                                        <Typography
                                                            sx={{
                                                                fontSize: '0.76rem',
                                                                color:
                                                                    slot.key ===
                                                                    'darkLogoData'
                                                                        ? '#CBD5E1'
                                                                        : palette.textMuted,
                                                            }}
                                                        >
                                                            {t('No custom logo uploaded')}
                                                        </Typography>
                                                    </Stack>
                                                )}
                                            </Box>

                                            <Stack
                                                direction="row"
                                                spacing={1}
                                                flexWrap="wrap"
                                                useFlexGap
                                            >
                                                <Button
                                                    component="label"
                                                    variant="contained"
                                                    size="small"
                                                    disabled={loading || saving}
                                                >
                                                    {t('Upload Logo')}
                                                    <input
                                                        hidden
                                                        type="file"
                                                        accept=".svg,.png,.jpg,.jpeg,.webp"
                                                        onChange={(event) =>
                                                            handleLogoUpload(
                                                                slot.key,
                                                                event
                                                            )
                                                        }
                                                    />
                                                </Button>
                                                <Button
                                                    variant="outlined"
                                                    size="small"
                                                    disabled={!hasLogo || saving}
                                                    onClick={() =>
                                                        setForm((current) => ({
                                                            ...current,
                                                            [slot.key]: '',
                                                        }))
                                                    }
                                                >
                                                    {t('Remove')}
                                                </Button>
                                            </Stack>
                                        </Stack>
                                    </Paper>
                                )
                            })}
                        </Box>

                        <Stack
                            direction="row"
                            spacing={1.2}
                            flexWrap="wrap"
                            useFlexGap
                        >
                            <Button
                                variant="contained"
                                onClick={saveBranding}
                                disabled={loading || saving}
                            >
                                {saving ? t('Saving...') : t('Save Branding')}
                            </Button>
                            <Button
                                variant="outlined"
                                disabled={saving}
                                onClick={() => {
                                    setForm({
                                        ...DEFAULT_BRANDING,
                                        tenantId: previewBranding.tenantId,
                                    })
                                    setSuccess('')
                                    setError('')
                                }}
                            >
                                {t('Reset to Defaults')}
                            </Button>
                            <Button
                                variant="text"
                                disabled={saving}
                                onClick={loadBranding}
                            >
                                {t('Reload Saved Branding')}
                            </Button>
                        </Stack>
                    </Stack>
                </Paper>

                <Stack spacing={2}>
                    <Paper
                        elevation={0}
                        sx={{
                            p: 2.5,
                            borderRadius: '24px',
                            border: `1px solid ${palette.border}`,
                            backgroundColor: palette.paperBg,
                        }}
                    >
                        <Stack spacing={1.4}>
                            <Stack direction="row" spacing={1} alignItems="center">
                                <ImageOutlinedIcon
                                    sx={{color: 'var(--brand-primary)'}}
                                />
                                <Typography
                                    sx={{
                                        fontSize: '0.92rem',
                                        fontWeight: 800,
                                        color: palette.text,
                                    }}
                                >
                                    {t('Header Preview')}
                                </Typography>
                            </Stack>

                            <Paper
                                elevation={0}
                                sx={{
                                    borderRadius: '22px',
                                    overflow: 'hidden',
                                    border: `1px solid ${palette.borderSoft}`,
                                }}
                            >
                                <Box
                                    sx={{
                                        px: 2,
                                        py: 1.7,
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'space-between',
                                        background:
                                            'linear-gradient(180deg, #FFFFFF 0%, rgba(var(--brand-primary-rgb), 0.04) 100%)',
                                    }}
                                >
                                    <BrandLockup
                                        branding={previewBranding}
                                        mode="light"
                                        productName={PRODUCT_NAME}
                                        appNameColor="#22314D"
                                        subtitle={t('Operations workspace')}
                                        subtitleColor="#6F7C93"
                                        logoWidth={96}
                                        logoHeight={42}
                                        defaultLogoWidth={188}
                                        defaultLogoHeight={46}
                                        nameFontSize="1.22rem"
                                        gap={1}
                                        maxTextWidth={180}
                                        hideProductNameWhenDefaultLogo
                                    />
                                    <Box
                                        sx={{
                                            width: 140,
                                            height: 36,
                                            borderRadius: 999,
                                            border: `1px solid ${brandTokens.primaryBorder}`,
                                            backgroundColor:
                                                'rgba(255,255,255,0.88)',
                                        }}
                                    />
                                </Box>
                                <Box
                                    sx={{
                                        px: 2,
                                        py: 1.5,
                                        minHeight: 120,
                                        backgroundColor: '#F6F8FC',
                                    }}
                                />
                            </Paper>
                        </Stack>
                    </Paper>

                    <Paper
                        elevation={0}
                        sx={{
                            p: 2.5,
                            borderRadius: '24px',
                            border: `1px solid ${palette.border}`,
                            backgroundColor: palette.paperBg,
                        }}
                    >
                        <Typography
                            sx={{
                                fontSize: '0.92rem',
                                fontWeight: 800,
                                color: palette.text,
                                mb: 1.5,
                            }}
                        >
                            {t('Login Preview')}
                        </Typography>

                        <Paper
                            elevation={0}
                            sx={{
                                p: 2,
                                borderRadius: '24px',
                                border: `1px solid ${palette.borderSoft}`,
                                background:
                                    'linear-gradient(145deg, rgba(var(--brand-primary-rgb), 0.10) 0%, #FFFFFF 46%, rgba(var(--brand-secondary-rgb), 0.12) 100%)',
                            }}
                        >
                            <Stack spacing={1.6}>
                                <BrandLockup
                                    branding={previewBranding}
                                    mode="light"
                                    productName={PRODUCT_NAME}
                                    appNameColor="#22314D"
                                    logoWidth={112}
                                    logoHeight={52}
                                    defaultLogoWidth={218}
                                    defaultLogoHeight={54}
                                    nameFontSize="1.34rem"
                                    gap={1.1}
                                    maxTextWidth={210}
                                    hideProductNameWhenDefaultLogo
                                />
                                <Typography
                                    sx={{
                                        fontSize: '1.3rem',
                                        fontWeight: 800,
                                        color: '#22314D',
                                        lineHeight: 1.08,
                                    }}
                                >
                                    {t('Sign in to {appName}', {
                                        appName: PRODUCT_NAME,
                                    })}
                                </Typography>
                                <Paper
                                    elevation={0}
                                    sx={{
                                        p: 1.4,
                                        borderRadius: '18px',
                                        bgcolor: '#FFFFFF',
                                        border: `1px solid ${palette.borderSoft}`,
                                    }}
                                >
                                    <Stack spacing={1}>
                                        <Box
                                            sx={{
                                                height: 40,
                                                borderRadius: 3,
                                                border: `1px solid ${palette.borderSoft}`,
                                                backgroundColor: '#FFFFFF',
                                            }}
                                        />
                                        <Box
                                            sx={{
                                                height: 40,
                                                borderRadius: 3,
                                                border: `1px solid ${palette.borderSoft}`,
                                                backgroundColor: '#FFFFFF',
                                            }}
                                        />
                                        <Box
                                            sx={{
                                                height: 42,
                                                borderRadius: 3,
                                                background:
                                                    brandTokens.buttonGradient,
                                                boxShadow: `0 12px 24px ${brandTokens.primaryGlow}`,
                                            }}
                                        />
                                    </Stack>
                                </Paper>
                            </Stack>
                        </Paper>
                    </Paper>
                </Stack>
            </Box>
        </Box>
    )
}
