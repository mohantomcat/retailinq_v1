import {useState} from 'react'
import {Alert, Box, Button, CircularProgress, IconButton, InputAdornment, TextField, Typography,} from '@mui/material'
import VisibilityIcon from '@mui/icons-material/Visibility'
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff'
import {useAuth} from '../context/AuthContext'
import {useNavigate} from 'react-router-dom'
import {useI18n} from '../context/I18nContext'

export default function Login() {
    const {t} = useI18n()
    const [username, setUsername] = useState('')
    const [password, setPassword] = useState('')
    const [tenantId, setTenantId] = useState(
        'tenant-india')
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
        <Box sx={{
            minHeight: '100vh',
            display: 'flex',
            backgroundColor: '#f5f7fa',
        }}>
            {/* Left panel */}
            <Box sx={{
                flex: 1,
                display: {xs: 'none', md: 'flex'},
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                background:
                    "linear-gradient(135deg, #0891b2 0%, #0e7490 40%, #0d9488 100%)",
                p: 6,
            }}>
                <Box sx={{
                    width: 64, height: 64,
                    backgroundColor: 'rgba(255,255,255,0.15)',
                    borderRadius: 3,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    mb: 3,
                }}>
                    <img src="/logo1.png" alt="RetailINQ"
                         style={{
                             width: 44, height: 44,
                             objectFit: 'contain',
                         }}
                         onError={e => {
                             e.target.style.display = 'none'
                         }}
                    />
                </Box>
                <Typography variant="h4" sx={{
                    color: '#fff',
                    fontWeight: 700,
                    mb: 2,
                    textAlign: 'center',
                }}>
                    RetailINQ
                </Typography>
                <Typography sx={{
                    color: 'rgba(255,255,255,0.75)',
                    textAlign: 'center',
                    maxWidth: 320,
                    lineHeight: 1.7,
                }}>
                    {t(
                        'Retail Reconciliation Analytics Platform for global retail operations'
                    )}
                </Typography>

                {/* Feature bullets */}
                {[
                    t('Real-time transaction reconciliation'),
                    t('Multi-store, multi-register support'),
                    t('Role-based access control'),
                    t('Global multi-tenant architecture'),
                ].map((f, i) => (
                    <Box key={i} sx={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: 1.5,
                        mt: 2,
                        alignSelf: 'flex-start',
                        maxWidth: 320,
                    }}>
                        <Box sx={{
                            width: 6, height: 6,
                            borderRadius: '50%',
                            backgroundColor: '#93c5fd',
                            flexShrink: 0,
                        }}/>
                        <Typography sx={{
                            color: 'rgba(255,255,255,0.8)',
                            fontSize: '0.875rem',
                        }}>
                            {f}
                        </Typography>
                    </Box>
                ))}
            </Box>

            {/* Right panel — login form */}
            <Box sx={{
                flex: {xs: 1, md: '0 0 460px'},
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'center',
                alignItems: 'center',
                p: 4,
                backgroundColor: '#ffffff',
            }}>
                <Box sx={{width: '100%', maxWidth: 360}}>

                    {/* Mobile logo */}
                    <Box sx={{
                        display: {xs: 'flex', md: 'none'},
                        alignItems: 'center',
                        gap: 1.5,
                        mb: 4,
                    }}>
                        <img src="/logo_old.png" alt="RetailIQ"
                             style={{height: 36}}
                             onError={e => {
                                 e.target.style.display = 'none'
                             }}
                        />
                        <Typography sx={{
                            fontWeight: 700,
                            fontSize: '1.25rem',
                            color: '#1e40af',
                        }}>
                            RetailIQ
                        </Typography>
                    </Box>

                    <Typography variant="h5" sx={{
                        fontWeight: 700,
                        color: '#111827',
                        mb: 0.5,
                    }}>
                        {t('Sign in')}
                    </Typography>
                    <Typography sx={{
                        color: '#6b7280',
                        fontSize: '0.875rem',
                        mb: 4,
                    }}>
                        {t('Enter your credentials to continue')}
                    </Typography>

                    {error && (
                        <Alert severity="error"
                               sx={{mb: 3, borderRadius: 2}}>
                            {error}
                        </Alert>
                    )}

                    <Box
                        component="form"
                        onSubmit={handleLogin}
                        sx={{
                            display: 'flex',
                            flexDirection: 'column',
                            gap: 2.5,
                        }}
                    >
                        <TextField
                            label={t('Username')}
                            value={username}
                            onChange={e =>
                                setUsername(e.target.value)}
                            fullWidth
                            size="small"
                            autoComplete="username"
                            autoFocus
                            disabled={loading}
                        />

                        <TextField
                            label={t('Password')}
                            type={showPass
                                ? 'text' : 'password'}
                            value={password}
                            onChange={e =>
                                setPassword(e.target.value)}
                            fullWidth
                            size="small"
                            autoComplete="current-password"
                            disabled={loading}
                            InputProps={{
                                endAdornment: (
                                    <InputAdornment
                                        position="end">
                                        <IconButton
                                            size="small"
                                            onClick={() =>
                                                setShowPass(
                                                    p => !p)}
                                            edge="end"
                                        >
                                            {showPass
                                                ? <VisibilityOffIcon
                                                    sx={{fontSize: 18}}/>
                                                : <VisibilityIcon
                                                    sx={{fontSize: 18}}/>
                                            }
                                        </IconButton>
                                    </InputAdornment>
                                ),
                            }}
                        />

                        <TextField
                            label={t('Organisation')}
                            value={tenantId}
                            onChange={e =>
                                setTenantId(e.target.value)}
                            fullWidth
                            size="small"
                            disabled={loading}
                            helperText={t('Your organisation ID')}
                        />

                        <Button
                            type="submit"
                            variant="contained"
                            fullWidth
                            disabled={loading}
                            sx={{
                                py: 1.25,
                                mt: 0.5,
                                fontWeight: 600,
                                fontSize: '0.9rem',
                                backgroundColor: '#0891b2',
                                borderRadius: 2,
                                textTransform: 'none',
                                '&:hover': {
                                    backgroundColor: '#0e7490',
                                },
                            }}
                        >
                            {loading
                                ? <CircularProgress
                                    size={20}
                                    sx={{color: '#fff'}}/>
                                : t('Sign in')
                            }
                        </Button>
                    </Box>

                    <Typography sx={{
                        mt: 4,
                        fontSize: '0.75rem',
                        color: '#9ca3af',
                        textAlign: 'center',
                    }}>
                        © 2026 RetailINQ. All rights reserved.
                    </Typography>
                </Box>
            </Box>
        </Box>
    )
}
