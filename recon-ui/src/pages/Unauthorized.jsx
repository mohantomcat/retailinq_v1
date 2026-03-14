import {Box, Button, Typography} from '@mui/material'
import LockOutlinedIcon from '@mui/icons-material/LockOutlined'
import {useNavigate} from 'react-router-dom'
import {useI18n} from '../context/I18nContext'

export default function Unauthorized() {
    const navigate = useNavigate()
    const {t} = useI18n()
    return (
        <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            height: '100vh',
            backgroundColor: '#f5f7fa',
            gap: 2,
        }}>
            <Box sx={{
                width: 64, height: 64,
                borderRadius: '50%',
                backgroundColor: '#fee2e2',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                mb: 1,
            }}>
                <LockOutlinedIcon sx={{
                    color: '#ef4444', fontSize: 32
                }}/>
            </Box>
            <Typography variant="h5"
                        sx={{fontWeight: 700, color: '#111827'}}>
                {t('Access Denied')}
            </Typography>
            <Typography sx={{
                color: '#6b7280', textAlign: 'center',
                maxWidth: 360,
            }}>
                {t(
                    "You don't have permission to view this page. Contact your administrator."
                )}
            </Typography>
            <Button variant="outlined"
                    onClick={() => navigate('/')}
                    sx={{
                        mt: 1, textTransform: 'none',
                        borderRadius: 2
                    }}>
                {t('Go to Dashboard')}
            </Button>
        </Box>
    )
}
