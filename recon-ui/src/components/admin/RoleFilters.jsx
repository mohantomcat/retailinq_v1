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

export default function RoleFilters({
    searchText,
    onSearchTextChange,
    moduleFilter,
    onModuleFilterChange,
    moduleOptions = [],
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
                        md: 'minmax(280px, 1.6fr) minmax(220px, 1fr)',
                    },
                    gap: 1.5,
                    alignItems: 'center',
                }}
            >
                <TextField
                    size="small"
                    fullWidth
                    placeholder={t('Search by role name, description, or permission')}
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
                    label={t('Permission Module')}
                    value={moduleFilter}
                    onChange={(e) =>
                        onModuleFilterChange?.(e.target.value)
                    }
                    sx={fieldSx}
                >
                    <MenuItem value="all">{t('All Modules')}</MenuItem>
                    {moduleOptions.map((module) => (
                        <MenuItem key={module} value={module}>
                            {module}
                        </MenuItem>
                    ))}
                </TextField>
            </Box>
        </Paper>
    )
}
