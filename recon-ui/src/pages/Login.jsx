import {useState} from 'react'
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
import {useAuth} from '../context/AuthContext'
import {useNavigate} from 'react-router-dom'
import {useI18n} from '../context/I18nContext'

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

    const {login} = useAuth()
    const navigate = useNavigate()

    const handleLogin = async (e) => {
        e.preventDefault()
        if (!username.trim() || !password.trim()) {
            setError(t('Username and password are required'))
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

    return (
        <Box
            sx={{
                minHeight: '100vh',
                display: 'flex',
                background:
                    'linear-gradient(135deg, #EAF2FF 0%, #F8FAFC 48%, #E7F8F8 100%)',
                position: 'relative',
                overflow: 'hidden',
            }}
        >
            <Box
                sx={{
                    position: 'absolute',
                    inset: 0,
                    pointerEvents: 'none',
                    backgroundImage: `
                        radial-gradient(circle at 12% 18%, rgba(37,99,235,0.16), transparent 22%),
                        radial-gradient(circle at 82% 24%, rgba(15,124,134,0.14), transparent 24%),
                        radial-gradient(circle at 68% 82%, rgba(59,130,246,0.10), transparent 22%)
                    `,
                }}
            />

            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: {xs: '1fr', lg: 'minmax(540px, 1.1fr) minmax(420px, 480px)'},
                    gap: {xs: 2, lg: 3},
                    width: '100%',
                    maxWidth: 1440,
                    mx: 'auto',
                    p: {xs: 1.5, sm: 2, lg: 2},
                    alignItems: 'stretch',
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
                        border: '1px solid rgba(148,163,184,0.18)',
                        background:
                            'linear-gradient(160deg, rgba(15,23,42,0.96) 0%, rgba(15,124,134,0.92) 100%)',
                        color: '#FFFFFF',
                        overflow: 'hidden',
                        position: 'relative',
                    }}
                >
                    <Box
                        sx={{
                            position: 'absolute',
                            inset: 0,
                            pointerEvents: 'none',
                            backgroundImage: `
                                linear-gradient(rgba(255,255,255,0.07), rgba(255,255,255,0.07)),
                                radial-gradient(circle at top right, rgba(147,197,253,0.22), transparent 30%),
                                radial-gradient(circle at bottom left, rgba(45,212,191,0.18), transparent 28%)
                            `,
                            opacity: 0.9,
                        }}
                    />

                    <Stack spacing={2.75} sx={{position: 'relative'}}>
                        <Box>
                            <Stack direction="row" spacing={1.5} alignItems="center">
                                <Box
                                    sx={{
                                        width: 54,
                                        height: 54,
                                        borderRadius: 3.5,
                                        backgroundColor: 'rgba(255,255,255,0.14)',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        border: '1px solid rgba(255,255,255,0.14)',
                                        backdropFilter: 'blur(10px)',
                                    }}
                                >
                                    <img
                                        src="/logo9.svg"
                                        alt="RetailINQ"
                                        style={{width: 34, height: 34, objectFit: 'contain'}}
                                    />
                                </Box>
                                <Box>
                                    <Typography sx={{fontSize: '1.4rem', fontWeight: 800, lineHeight: 1}}>
                                        RetailINQ
                                    </Typography>
                                    <Typography sx={{mt: 0.35, color: 'rgba(255,255,255,0.72)', fontSize: '0.88rem'}}>
                                        {t('Enterprise retail reconciliation workspace')}
                                    </Typography>
                                </Box>
                            </Stack>
                        </Box>

                        <Stack spacing={1.6}>
                            <Chip
                                label={t('Reconciliation Control Tower')}
                                sx={{
                                    alignSelf: 'flex-start',
                                    backgroundColor: 'rgba(255,255,255,0.14)',
                                    color: '#FFFFFF',
                                    fontWeight: 700,
                                    borderRadius: 999,
                                    height: 28,
                                }}
                            />
                            <Typography
                                sx={{
                                    fontSize: {xs: '1.8rem', sm: '2.15rem'},
                                    lineHeight: 1.02,
                                    fontWeight: 800,
                                    maxWidth: 580,
                                    letterSpacing: '-0.03em',
                                }}
                            >
                                {t('Track every mismatch, duplicate, and late sync before it turns into an operational problem.')}
                            </Typography>
                            <Typography
                                sx={{
                                    maxWidth: 580,
                                    color: 'rgba(255,255,255,0.78)',
                                    fontSize: '0.93rem',
                                    lineHeight: 1.6,
                                }}
                            >
                                {t('RetailINQ brings together Xstore, SIM, SIOCS, and XOCS reconciliation into one operations-focused workflow for support, finance, and integration teams.')}
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
                                        p: 1.8,
                                        borderRadius: 3.5,
                                        backgroundColor: 'rgba(255,255,255,0.10)',
                                        border: '1px solid rgba(255,255,255,0.12)',
                                        backdropFilter: 'blur(10px)',
                                    }}
                                >
                                    <Stack spacing={0.9}>
                                        <Box
                                            sx={{
                                                width: 34,
                                                height: 34,
                                                borderRadius: 2.5,
                                                backgroundColor: 'rgba(255,255,255,0.12)',
                                                display: 'flex',
                                                alignItems: 'center',
                                                justifyContent: 'center',
                                                color: '#BFDBFE',
                                            }}
                                        >
                                            {feature.icon}
                                        </Box>
                                        <Typography sx={{fontWeight: 800, fontSize: '0.9rem'}}>
                                            {t(feature.title)}
                                        </Typography>
                                        <Typography sx={{fontSize: '0.79rem', color: 'rgba(255,255,255,0.74)', lineHeight: 1.45}}>
                                            {t(feature.text)}
                                        </Typography>
                                    </Stack>
                                </Paper>
                            ))}
                        </Box>
                    </Stack>

                    <Stack
                        direction={{xs: 'column', sm: 'row'}}
                        spacing={1.25}
                        sx={{
                            mt: 2.5,
                            position: 'relative',
                            color: 'rgba(255,255,255,0.72)',
                            fontSize: '0.78rem',
                        }}
                    >
                        <Typography>{t('Built for multi-store reconciliation teams')}</Typography>
                        <Typography sx={{display: {xs: 'none', sm: 'block'}}}>•</Typography>
                        <Typography>{t('Operational dashboarding and exception triage')}</Typography>
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
                            border: '1px solid #E2E8F0',
                            backgroundColor: 'rgba(255,255,255,0.92)',
                            backdropFilter: 'blur(16px)',
                            boxShadow: '0 24px 60px rgba(15,23,42,0.10)',
                        }}
                    >
                        <Stack spacing={2.25}>
                            <Box>
                                <Typography sx={{fontSize: '1.5rem', fontWeight: 800, color: '#0F172A'}}>
                                    {t('Sign in')}
                                </Typography>
                                <Typography sx={{mt: 0.55, fontSize: '0.88rem', color: '#64748B', lineHeight: 1.5}}>
                                    {t('Enter your account credentials and organisation identifier to continue into RetailINQ.')}
                                </Typography>
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
                                    onChange={(e) => setUsername(e.target.value)}
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
                                    onChange={(e) => setPassword(e.target.value)}
                                    fullWidth
                                    size="small"
                                    autoComplete="current-password"
                                    disabled={loading}
                                    InputProps={{
                                        endAdornment: (
                                            <InputAdornment position="end">
                                                <IconButton
                                                    size="small"
                                                    onClick={() => setShowPass((p) => !p)}
                                                    edge="end"
                                                >
                                                    {showPass
                                                        ? <VisibilityOffIcon sx={{fontSize: 18}}/>
                                                        : <VisibilityIcon sx={{fontSize: 18}}/>}
                                                </IconButton>
                                            </InputAdornment>
                                        ),
                                    }}
                                />

                                <TextField
                                    label={t('Organisation')}
                                    value={tenantId}
                                    onChange={(e) => setTenantId(e.target.value)}
                                    fullWidth
                                    size="small"
                                    disabled={loading}
                                    helperText={t('Organisation or tenant identifier')}
                                />

                                <Button
                                    type="submit"
                                    variant="contained"
                                    fullWidth
                                    disabled={loading}
                                    sx={{
                                        py: 1.15,
                                        mt: 0.25,
                                        borderRadius: 3,
                                        background:
                                            'linear-gradient(90deg, #2563EB 0%, #0F7C86 100%)',
                                        fontSize: '0.95rem',
                                        fontWeight: 700,
                                        '&:hover': {
                                            background:
                                                'linear-gradient(90deg, #1D4ED8 0%, #0F6A73 100%)',
                                        },
                                    }}
                                >
                                    {loading
                                        ? <CircularProgress size={20} sx={{color: '#fff'}}/>
                                        : t('Sign in to RetailINQ')}
                                </Button>
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
                                    © 2026 RetailINQ
                                </Typography>
                            </Stack>
                        </Stack>
                    </Paper>
                </Box>
            </Box>
        </Box>
    )
}
