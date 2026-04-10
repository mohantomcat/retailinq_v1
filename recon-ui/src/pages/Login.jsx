import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    IconButton,
    InputAdornment,
    Paper,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import VisibilityIcon from '@mui/icons-material/Visibility'
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff'
import TrendingUpRoundedIcon from '@mui/icons-material/TrendingUpRounded'
import CompareArrowsRoundedIcon from '@mui/icons-material/CompareArrowsRounded'
import ShieldRoundedIcon from '@mui/icons-material/ShieldRounded'
import PublicRoundedIcon from '@mui/icons-material/PublicRounded'
import {useLocation, useNavigate} from 'react-router-dom'
import BrandLockup from '../components/BrandLockup'
import {useAuth} from '../context/AuthContext'
import {useI18n} from '../context/I18nContext'
import {authApi} from '../services/authApi'
import {brandingApi} from '../services/brandingApi'
import {PRODUCT_NAME, getBrandTokens, resolveBranding} from '../branding/brandingUtils'

const featureCards = [
    {
        icon: <CompareArrowsRoundedIcon sx={{fontSize: 20}}/>,
        title: 'Reconciliation',
        text: 'Cross-system transaction visibility across store, register, and business date.',
    },
    {
        icon: <TrendingUpRoundedIcon sx={{fontSize: 20}}/>,
        title: 'Operational KPIs',
        text: 'Surface missing, duplicate, quantity, and total mismatch trends quickly.',
    },
    {
        icon: <ShieldRoundedIcon sx={{fontSize: 20}}/>,
        title: 'Role-Controlled Access',
        text: 'Support admin, operational, and audit workflows without exposing everything to everyone.',
    },
    {
        icon: <PublicRoundedIcon sx={{fontSize: 20}}/>,
        title: 'Multi-Connector Model',
        text: 'Unify on-prem Xstore, SIM, SIOCS cloud, and XOCS cloud reconciliation in one workspace.',
    },
]

export default function Login() {
    const {t} = useI18n()
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [tenantId, setTenantId] = useState('tenant-india')
    const [showPass, setShowPass] = useState(false)
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState('')
    const [loginOptions, setLoginOptions] = useState(null)
    const [branding, setBranding] = useState(resolveBranding())

    const brandTokens = useMemo(() => getBrandTokens(branding), [branding])

    const {login, completeOidcLogin} = useAuth()
    const navigate = useNavigate()
    const location = useLocation()

    useEffect(() => {
        let active = true
        const timer = window.setTimeout(async () => {
            const [loginOptionsResult, brandingResult] = await Promise.allSettled([
                authApi.getLoginOptions(tenantId),
                brandingApi.getCurrent(tenantId),
            ])

            if (!active) {
                return
            }

            if (loginOptionsResult.status === 'fulfilled') {
                setLoginOptions(loginOptionsResult.value)
            } else {
                setLoginOptions(null)
            }

            if (brandingResult.status === 'fulfilled') {
                setBranding(resolveBranding(brandingResult.value))
            } else {
                setBranding(resolveBranding())
            }
        }, 180)

        return () => {
            active = false
            window.clearTimeout(timer)
        }
    }, [tenantId])

    useEffect(() => {
        const params = new URLSearchParams(location.search)
        const code = params.get('code')
        const state = params.get('state')
        const oidcError = params.get('error')

        if (!code && !oidcError) {
            return undefined
        }

        let active = true
        const completeLogin = async () => {
            setLoading(true)
            setError('')
            try {
                await completeOidcLogin({
                    code,
                    state,
                    error: oidcError,
                    errorDescription: params.get('error_description'),
                })
                if (active) {
                    navigate('/', {replace: true})
                }
            } catch (err) {
                if (active) {
                    setError(err.message || t('OIDC login failed'))
                    navigate('/login', {replace: true})
                }
            } finally {
                if (active) {
                    setLoading(false)
                }
            }
        }

        completeLogin()

        return () => {
            active = false
        }
    }, [completeOidcLogin, location.search, navigate, t])

    const handleLogin = async (e) => {
        e.preventDefault()
        if (!username.trim() || !password.trim()) {
            setError(t('Username and password are required'))
            return
        }
        if (loginOptions && loginOptions.localLoginEnabled === false) {
            setError(t('Local login is disabled for this tenant'))
            return
        }
        setLoading(true)
        setError('')
        try {
            await login(username, password, tenantId)
            navigate('/', {replace: true})
        } catch (err) {
            setError(err.message || t('Login failed'))
        } finally {
            setLoading(false)
        }
    }

    const handleOidcLogin = async () => {
        if (!tenantId.trim()) {
            setError(t('Organisation is required'))
            return
        }
        setLoading(true)
        setError('')
        try {
            const response = await authApi.startOidcLogin({tenantId})
            if (!response?.authorizationUrl) {
                throw new Error(t('OIDC login could not be started'))
            }
            window.location.assign(response.authorizationUrl)
        } catch (err) {
            setError(err.message || t('OIDC login could not be started'))
            setLoading(false)
        }
    }

    return (
        <Box
            sx={{
                minHeight: '100vh',
                display: 'flex',
                background: `linear-gradient(145deg, ${brandTokens.primarySurface} 0%, #F8FAFC 44%, ${brandTokens.secondarySurface} 100%)`,
                position: 'relative',
                overflowX: 'hidden',
                overflowY: 'auto',
            }}
        >
            <Box
                sx={{
                    position: 'absolute',
                    inset: 0,
                    pointerEvents: 'none',
                    backgroundImage: `
                        radial-gradient(circle at 14% 16%, rgba(${brandTokens.primaryRgb}, 0.16), transparent 24%),
                        radial-gradient(circle at 82% 18%, rgba(${brandTokens.secondaryRgb}, 0.12), transparent 26%),
                        radial-gradient(circle at 72% 86%, rgba(148,163,184,0.12), transparent 22%)
                    `,
                }}
            />

            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: {
                        xs: '1fr',
                        lg: 'minmax(560px, 1.08fr) minmax(420px, 480px)',
                    },
                    gap: {xs: 2, lg: 2.5},
                    width: '100%',
                    maxWidth: 1440,
                    mx: 'auto',
                    p: {xs: 1.5, sm: 2, lg: 2.25},
                    alignItems: {xs: 'stretch', lg: 'center'},
                    position: 'relative',
                    zIndex: 1,
                    minHeight: '100vh',
                }}
            >
                <Paper
                    elevation={0}
                    sx={{
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'space-between',
                        minHeight: {xs: 'auto', lg: 'calc(100vh - 16px)'},
                        p: {xs: 2.5, sm: 3, lg: 3.25},
                        borderRadius: 6,
                        border: '1px solid #DCE5F2',
                        background: `linear-gradient(180deg, rgba(255,255,255,0.98) 0%, rgba(${brandTokens.primaryRgb}, 0.06) 100%)`,
                        color: '#22314D',
                        overflow: 'hidden',
                        position: 'relative',
                        boxShadow: '0 28px 72px rgba(34,49,77,0.10)',
                    }}
                >
                    <Box
                        sx={{
                            position: 'absolute',
                            inset: 0,
                            pointerEvents: 'none',
                            backgroundImage: `
                                linear-gradient(rgba(255,255,255,0.78), rgba(255,255,255,0.16)),
                                radial-gradient(circle at top right, rgba(${brandTokens.secondaryRgb}, 0.16), transparent 28%),
                                radial-gradient(circle at bottom left, rgba(${brandTokens.primaryRgb}, 0.10), transparent 24%)
                            `,
                            opacity: 1,
                        }}
                    />

                    <Stack spacing={2.75} sx={{position: 'relative'}}>
                        <Stack spacing={1.4}>
                            <BrandLockup
                                branding={branding}
                                mode="light"
                                productName={PRODUCT_NAME}
                                appNameColor="#22314D"
                                logoWidth={{xs: 120, sm: 148}}
                                logoHeight={{xs: 52, sm: 62}}
                                defaultLogoWidth={{xs: 236, sm: 322}}
                                defaultLogoHeight="auto"
                                nameFontSize={{xs: '1.5rem', sm: '1.9rem'}}
                                nameFontWeight={800}
                                gap={1.4}
                                maxTextWidth={{xs: 180, sm: 260}}
                                hideProductNameWhenDefaultLogo
                            />

                            <Box
                                sx={{
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: 1,
                                    px: 1.35,
                                    py: 0.75,
                                    borderRadius: 999,
                                    border: '1px solid #D9E3F1',
                                    backgroundColor: 'rgba(255,255,255,0.82)',
                                    boxShadow: `0 10px 24px ${brandTokens.primaryGlow}`,
                                    alignSelf: 'flex-start',
                                    flexWrap: 'wrap',
                                }}
                            >
                                <Chip
                                    label={t(
                                        'Retail Reconciliation Control Tower'
                                    )}
                                    sx={{
                                        height: 24,
                                        backgroundColor:
                                            brandTokens.primarySurface,
                                        color: brandTokens.primary,
                                        fontWeight: 800,
                                        borderRadius: 999,
                                    }}
                                />
                                <Typography
                                    sx={{
                                        fontSize: '0.78rem',
                                        fontWeight: 700,
                                        color: '#6F7C93',
                                    }}
                                >
                                    {t('Enterprise workspace')}
                                </Typography>
                            </Box>
                        </Stack>

                        <Stack spacing={1.6}>
                            <Typography
                                sx={{
                                    fontSize: {xs: '1.78rem', sm: '2.08rem'},
                                    lineHeight: 1.08,
                                    fontWeight: 800,
                                    maxWidth: 600,
                                    letterSpacing: '-0.03em',
                                    color: '#22314D',
                                }}
                            >
                                {t(
                                    'Catch reconciliation gaps early and keep stores moving.'
                                )}
                            </Typography>
                            <Typography
                                sx={{
                                    maxWidth: 600,
                                    color: '#6F7C93',
                                    fontSize: '0.9rem',
                                    lineHeight: 1.6,
                                }}
                            >
                                {t(
                                    'Bring Xstore, SIM, SIOCS, and XOCS signals into one workspace for operations, finance, and integration teams.'
                                )}
                            </Typography>
                        </Stack>

                        <Box
                            sx={{
                                display: 'grid',
                                gridTemplateColumns: {xs: '1fr', md: '1fr 1fr'},
                                gap: 1.4,
                                maxWidth: 760,
                            }}
                        >
                            {featureCards.map((feature) => (
                                <Paper
                                    key={feature.title}
                                    elevation={0}
                                    sx={{
                                        p: 1.55,
                                        borderRadius: 3.5,
                                        backgroundColor: 'rgba(255,255,255,0.88)',
                                        border: '1px solid #E2EAF5',
                                        boxShadow:
                                            '0 12px 28px rgba(34,49,77,0.06)',
                                    }}
                                >
                                    <Stack spacing={0.9}>
                                        <Box
                                            sx={{
                                                width: 36,
                                                height: 36,
                                                borderRadius: 2.75,
                                                background: `linear-gradient(135deg, ${brandTokens.primarySurface} 0%, rgba(${brandTokens.secondaryRgb}, 0.10) 100%)`,
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                color: brandTokens.primary,
                                                border: `1px solid ${brandTokens.primaryBorder}`,
                                            }}
                                        >
                                            {feature.icon}
                                        </Box>
                                        <Typography
                                            sx={{
                                                fontWeight: 800,
                                                fontSize: '0.92rem',
                                                color: '#22314D',
                                            }}
                                        >
                                            {t(feature.title)}
                                        </Typography>
                                        <Typography
                                            sx={{
                                                fontSize: '0.8rem',
                                                color: '#6F7C93',
                                                lineHeight: 1.5,
                                            }}
                                        >
                                            {t(feature.text)}
                                        </Typography>
                                    </Stack>
                                </Paper>
                            ))}
                        </Box>

                    </Stack>
                </Paper>

                <Box
                    sx={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                    }}
                >
                    <Paper
                        elevation={0}
                        sx={{
                            width: '100%',
                            maxWidth: 460,
                            p: {xs: 2.5, sm: 3},
                            borderRadius: 6,
                            border: '1px solid #DCE5F2',
                            background: `linear-gradient(180deg, rgba(255,255,255,0.96) 0%, rgba(${brandTokens.secondaryRgb}, 0.05) 100%)`,
                            backdropFilter: 'blur(16px)',
                            boxShadow: '0 28px 72px rgba(34,49,77,0.12)',
                        }}
                    >
                        <Stack spacing={2.25}>
                            <Box>
                                <Typography
                                    sx={{
                                        fontSize: '1.5rem',
                                        fontWeight: 800,
                                        color: '#0F172A',
                                    }}
                                >
                                    {t('Sign in')}
                                </Typography>
                                <Typography
                                    sx={{
                                        mt: 0.55,
                                        fontSize: '0.88rem',
                                        color: '#64748B',
                                        lineHeight: 1.5,
                                    }}
                                >
                                    {t('Enter your account credentials and organisation identifier to continue.')}
                                </Typography>
                                {loginOptions && (
                                    <Stack
                                        direction="row"
                                        spacing={1}
                                        sx={{mt: 1.25, flexWrap: 'wrap'}}
                                        useFlexGap
                                    >
                                        <Chip
                                            size="small"
                                            label={
                                                loginOptions.preferredLoginMode ||
                                                'LOCAL'
                                            }
                                        />
                                        {loginOptions.oidcEnabled && (
                                            <Chip
                                                size="small"
                                                label={
                                                    loginOptions.oidcDisplayName ||
                                                    'OIDC'
                                                }
                                            />
                                        )}
                                        {loginOptions.samlEnabled && (
                                            <Chip
                                                size="small"
                                                label={
                                                    loginOptions.samlDisplayName ||
                                                    'SAML'
                                                }
                                            />
                                        )}
                                    </Stack>
                                )}
                            </Box>

                            {error && (
                                <Alert severity="error" sx={{borderRadius: 3}}>
                                    {error}
                                </Alert>
                            )}

                            <Box
                                component="form"
                                onSubmit={handleLogin}
                                sx={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: 1.6,
                                }}
                            >
                                <TextField
                                    label={t('Username')}
                                    value={username}
                                    onChange={(e) =>
                                        setUsername(e.target.value)
                                    }
                                    inputProps={{'data-testid': 'login-username'}}
                                    fullWidth
                                    size="small"
                                    autoComplete="username"
                                    autoFocus
                                    disabled={loading}
                                />

                                <TextField
                                    label={t('Password')}
                                    type={showPass ? 'text' : 'password'}
                                    value={password}
                                    onChange={(e) =>
                                        setPassword(e.target.value)
                                    }
                                    inputProps={{'data-testid': 'login-password'}}
                                    fullWidth
                                    size="small"
                                    autoComplete="current-password"
                                    disabled={loading}
                                    InputProps={{
                                        endAdornment: (
                                            <InputAdornment position="end">
                                                <IconButton
                                                    size="small"
                                                    onClick={() =>
                                                        setShowPass((p) => !p)
                                                    }
                                                    edge="end"
                                                >
                                                    {showPass ? (
                                                        <VisibilityOffIcon
                                                            sx={{fontSize: 18}}
                                                        />
                                                    ) : (
                                                        <VisibilityIcon
                                                            sx={{fontSize: 18}}
                                                        />
                                                    )}
                                                </IconButton>
                                            </InputAdornment>
                                        ),
                                    }}
                                />

                                <TextField
                                    label={t('Organisation')}
                                    value={tenantId}
                                    onChange={(e) => setTenantId(e.target.value)}
                                    inputProps={{'data-testid': 'login-tenant'}}
                                    fullWidth
                                    size="small"
                                    disabled={loading}
                                    helperText={t(
                                        'Organisation or tenant identifier'
                                    )}
                                />

                                {loginOptions &&
                                    loginOptions.localLoginEnabled === false && (
                                        <Alert
                                            severity="warning"
                                            sx={{borderRadius: 3}}
                                        >
                                            {t(
                                                'Local username/password login is disabled for this tenant.'
                                            )}
                                        </Alert>
                                    )}

                                <Button
                                    type="submit"
                                    variant="contained"
                                    data-testid="login-submit"
                                    fullWidth
                                    disabled={
                                        loading ||
                                        (loginOptions &&
                                            loginOptions.localLoginEnabled ===
                                                false)
                                    }
                                    sx={{
                                        py: 1.15,
                                        mt: 0.25,
                                        fontSize: '0.95rem',
                                        fontWeight: 800,
                                        background:
                                            brandTokens.buttonGradient,
                                        boxShadow: `0 12px 24px ${brandTokens.primaryGlow}`,
                                        '&:hover': {
                                            background:
                                                brandTokens.buttonGradientHover,
                                        },
                                    }}
                                >
                                    {loading ? (
                                        <CircularProgress
                                            size={20}
                                            sx={{color: '#fff'}}
                                        />
                                    ) : (
                                        t('Sign in to {appName}', {
                                            appName: PRODUCT_NAME,
                                        })
                                    )}
                                </Button>

                                {loginOptions?.oidcEnabled && (
                                    <Button
                                        type="button"
                                        variant="outlined"
                                        fullWidth
                                        onClick={handleOidcLogin}
                                        disabled={loading}
                                        sx={{
                                            py: 1.05,
                                            fontSize: '0.92rem',
                                            fontWeight: 800,
                                            textTransform: 'none',
                                        }}
                                    >
                                        {t('Continue with {provider}', {
                                            provider:
                                                loginOptions.oidcDisplayName ||
                                                'Enterprise SSO',
                                        })}
                                    </Button>
                                )}
                            </Box>

                            <Stack
                                direction="row"
                                justifyContent="space-between"
                                alignItems="center"
                                spacing={2}
                                sx={{
                                    pt: 1,
                                    borderTop: '1px solid #E2E8F0',
                                    color: '#94A3B8',
                                    fontSize: '0.75rem',
                                }}
                            >
                                <Typography sx={{fontSize: 'inherit'}}>
                                    {t('Secured workspace access')}
                                </Typography>
                                <Typography sx={{fontSize: 'inherit'}}>
                                    {`\u00A9 2026 ${PRODUCT_NAME}`}
                                </Typography>
                            </Stack>
                        </Stack>
                    </Paper>
                </Box>
            </Box>
        </Box>
    )
}
