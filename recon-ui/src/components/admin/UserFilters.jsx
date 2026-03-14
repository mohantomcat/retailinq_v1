import {
    Box,
    InputAdornment,
    MenuItem,
    Paper,
    TextField,
} from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import {useAdminPalette} from './theme'
import {useI18n} from '../../context/I18nContext'

export default function UserFilters({
    searchText,
    onSearchTextChange,
    roleFilter,
    onRoleFilterChange,
    statusFilter,
    onStatusFilterChange,
    storeFilter,
    onStoreFilterChange,
    roleOptions = [],
}) {
    const palette = useAdminPalette()
    const {t} = useI18n()

    const fieldSx = {
        '& .MuiOutlinedInput-root': {
            borderRadius: 999,
            backgroundColor: palette.inputBg,
            color: palette.text,
            '& fieldset': {
                borderColor: palette.border,
            },
            '&:hover fieldset': {
                borderColor: palette.textSoft,
            },
            '&.Mui-focused fieldset': {
                borderColor: palette.primary,
            },
        },
        '& .MuiInputLabel-root': {
            color: palette.textMuted,
        },
        '& .MuiSvgIcon-root': {
            color: palette.textMuted,
        },
    }

    return (
        <Paper
            elevation={0}
            sx={{
                mb: 2.5,
                p: 2,
                border: `1px solid ${palette.border}`,
                borderRadius: '24px',
                backgroundColor: palette.paperBg,
            }}
        >
            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: {
                        xs: '1fr',
                        md: 'minmax(260px, 1.6fr) minmax(180px, 1fr) minmax(180px, 1fr) minmax(180px, 1fr)',
                    },
                    gap: 1.5,
                    alignItems: 'center',
                }}
            >
                <TextField
                    size="small"
                    fullWidth
                    placeholder={t('Search by username, name, or email')}
                    value={searchText}
                    onChange={(e) => onSearchTextChange?.(e.target.value)}
                    InputProps={{
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon
                                    sx={{
                                        fontSize: 18,
                                        color: palette.textSoft,
                                    }}
                                />
                            </InputAdornment>
                        ),
                    }}
                    sx={fieldSx}
                />

                <TextField
                    select
                    size="small"
                    fullWidth
                    label={t('Role')}
                    value={roleFilter}
                    onChange={(e) => onRoleFilterChange?.(e.target.value)}
                    sx={fieldSx}
                >
                    <MenuItem value="all">{t('All Roles')}</MenuItem>
                    {roleOptions.map((role) => (
                        <MenuItem key={role.id} value={role.id}>
                            {role.name}
                        </MenuItem>
                    ))}
                </TextField>

                <TextField
                    size="small"
                    fullWidth
                    label={t('Store ID')}
                    placeholder="e.g. 1007"
                    value={storeFilter}
                    onChange={(e) => onStoreFilterChange?.(e.target.value)}
                    sx={fieldSx}
                />

                <TextField
                    select
                    size="small"
                    fullWidth
                    label={t('Status')}
                    value={statusFilter}
                    onChange={(e) => onStatusFilterChange?.(e.target.value)}
                    sx={fieldSx}
                >
                    <MenuItem value="all">{t('All Status')}</MenuItem>
                    <MenuItem value="active">{t('Active')}</MenuItem>
                    <MenuItem value="inactive">{t('Inactive')}</MenuItem>
                </TextField>
            </Box>
        </Paper>
    )
}
