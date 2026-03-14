import {useState} from 'react'
import {
    IconButton,
    ListItemIcon,
    ListItemText,
    Menu,
    MenuItem,
} from '@mui/material'
import MoreVertIcon from '@mui/icons-material/MoreVert'
import EditOutlinedIcon from '@mui/icons-material/EditOutlined'
import SecurityOutlinedIcon from '@mui/icons-material/SecurityOutlined'
import StoreOutlinedIcon from '@mui/icons-material/StoreOutlined'
import LockResetOutlinedIcon from '@mui/icons-material/LockResetOutlined'
import ToggleOffOutlinedIcon from '@mui/icons-material/ToggleOffOutlined'
import ToggleOnOutlinedIcon from '@mui/icons-material/ToggleOnOutlined'
import DeleteOutlineOutlinedIcon from '@mui/icons-material/DeleteOutlineOutlined'
import {useAdminPalette} from './theme'
import {useI18n} from '../../context/I18nContext'

export default function UserRowMenu({
    row,
    onEdit,
    onManageRoles,
    onManageStores,
    onResetPassword,
    onToggleActive,
    onDelete,
}) {
    const palette = useAdminPalette()
    const {t} = useI18n()
    const [anchorEl, setAnchorEl] = useState(null)

    const open = Boolean(anchorEl)

    const handleOpen = (event) => {
        event.stopPropagation()
        setAnchorEl(event.currentTarget)
    }

    const handleClose = () => {
        setAnchorEl(null)
    }

    const handleClick = (callback) => {
        handleClose()
        if (callback) callback(row)
    }

    return (
        <>
            <IconButton
                size="small"
                onClick={handleOpen}
                aria-label="more actions"
                sx={{color: palette.iconButton}}
            >
                <MoreVertIcon fontSize="small"/>
            </IconButton>

            <Menu
                anchorEl={anchorEl}
                open={open}
                onClose={handleClose}
                onClick={(e) => e.stopPropagation()}
                anchorOrigin={{
                    vertical: 'bottom',
                    horizontal: 'right',
                }}
                transformOrigin={{
                    vertical: 'top',
                    horizontal: 'right',
                }}
                PaperProps={{
                    sx: {
                        borderRadius: 3,
                        minWidth: 220,
                        backgroundColor: palette.menuBg,
                        color: palette.text,
                        boxShadow: '0 10px 30px rgba(15, 23, 42, 0.10)',
                        border: `1px solid ${palette.border}`,
                    },
                }}
            >
                <MenuItem
                    onClick={() => handleClick(onEdit)}
                    sx={{'&:hover': {backgroundColor: palette.menuHover}}}
                >
                    <ListItemIcon sx={{color: palette.textMuted}}>
                        <EditOutlinedIcon fontSize="small"/>
                    </ListItemIcon>
                    <ListItemText primary={t('Edit User')}/>
                </MenuItem>

                <MenuItem
                    onClick={() => handleClick(onManageRoles)}
                    sx={{'&:hover': {backgroundColor: palette.menuHover}}}
                >
                    <ListItemIcon sx={{color: palette.textMuted}}>
                        <SecurityOutlinedIcon fontSize="small"/>
                    </ListItemIcon>
                    <ListItemText primary={t('Manage Roles')}/>
                </MenuItem>

                <MenuItem
                    onClick={() => handleClick(onManageStores)}
                    sx={{'&:hover': {backgroundColor: palette.menuHover}}}
                >
                    <ListItemIcon sx={{color: palette.textMuted}}>
                        <StoreOutlinedIcon fontSize="small"/>
                    </ListItemIcon>
                    <ListItemText primary={t('Manage Store Access')}/>
                </MenuItem>

                <MenuItem
                    onClick={() => handleClick(onResetPassword)}
                    sx={{'&:hover': {backgroundColor: palette.menuHover}}}
                >
                    <ListItemIcon sx={{color: palette.textMuted}}>
                        <LockResetOutlinedIcon fontSize="small"/>
                    </ListItemIcon>
                    <ListItemText primary={t('Reset Password')}/>
                </MenuItem>

                <MenuItem
                    onClick={() => handleClick(onToggleActive)}
                    sx={{'&:hover': {backgroundColor: palette.menuHover}}}
                >
                    <ListItemIcon sx={{color: palette.textMuted}}>
                        {row?.active ? (
                            <ToggleOffOutlinedIcon fontSize="small"/>
                        ) : (
                            <ToggleOnOutlinedIcon fontSize="small"/>
                        )}
                    </ListItemIcon>
                    <ListItemText
                        primary={
                            row?.active
                                ? t('Deactivate User')
                                : t('Activate User')
                        }
                    />
                </MenuItem>

                <MenuItem
                    onClick={() => handleClick(onDelete)}
                    sx={{
                        color: palette.dangerText,
                        '&:hover': {backgroundColor: palette.dangerBg},
                    }}
                >
                    <ListItemIcon sx={{color: palette.dangerText}}>
                        <DeleteOutlineOutlinedIcon fontSize="small"/>
                    </ListItemIcon>
                    <ListItemText primary={t('Delete User')}/>
                </MenuItem>
            </Menu>
        </>
    )
}
