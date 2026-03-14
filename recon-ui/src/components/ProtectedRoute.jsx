import {Navigate} from 'react-router-dom'
import {useAuth} from '../context/AuthContext'
import {Box, CircularProgress} from '@mui/material'

export default function ProtectedRoute({
                                           children, permission,
                                       }) {
    const {isAuthenticated, loading, hasPermission} =
        useAuth()

    if (loading) {
        return (
            <Box sx={{
                display: 'flex',
                justifyContent: 'center',
                alignItems: 'center',
                height: '100vh',
            }}>
                <CircularProgress/>
            </Box>
        )
    }

    if (!isAuthenticated) {
        return <Navigate to="/login" replace/>
    }

    if (permission && !hasPermission(permission)) {
        return <Navigate to="/unauthorized" replace/>
    }

    return children
}