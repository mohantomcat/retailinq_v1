import {
    Alert,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    Switch,
    Typography,
} from '@mui/material'
import TransferList from '../../components/TransferList'
import {useAdminPalette} from './theme'
import {useI18n} from '../../context/I18nContext'

export default function StoresDialog({
    open,
    user,
    storeOptions = [],
    selectedStoreIds = [],
    restrictStoreAccess = false,
    onRestrictStoreAccessChange,
    onChange,
    onClose,
    onSave,
    saving = false,
    error = '',
}) {
    const palette = useAdminPalette()
    const {t} = useI18n()

    const items = storeOptions.map((storeId) => ({
        id: String(storeId),
        label: `Store ${storeId}`,
        badge: String(storeId),
    }))

    return (
        <Dialog
            open={open}
            onClose={onClose}
            maxWidth="lg"
            fullWidth
            PaperProps={{
                sx: {
                    borderRadius: '24px',
                    border: `1px solid ${palette.border}`,
                    background: palette.dialogBg,
                },
            }}
        >
            <DialogTitle sx={{fontWeight: 700, pb: 1, color: palette.text}}>
                {t('Manage Store Access - {username}', {username: user?.username})}
            </DialogTitle>

            <DialogContent sx={{pt: '16px !important'}}>
                {error && (
                    <Alert severity="error" sx={{mb: 2, borderRadius: 2}}>
                        {error}
                    </Alert>
                )}

                <Box sx={{mb: 2}}>
                    <FormControlLabel
                        control={
                            <Switch
                                checked={restrictStoreAccess}
                                onChange={(e) =>
                                    onRestrictStoreAccessChange?.(
                                        e.target.checked
                                    )
                                }
                            />
                        }
                        label={t('Restrict access to selected stores')}
                        sx={{
                            color: palette.text,
                            '& .MuiSwitch-track': {
                                backgroundColor: palette.textSoft,
                            },
                        }}
                    />

                    <Typography
                        sx={{
                            fontSize: '0.82rem',
                            color: palette.textMuted,
                            mt: 0.5,
                        }}
                    >
                        {t('When turned off, the user can access all stores.')}
                    </Typography>
                </Box>

                {restrictStoreAccess ? (
                    <TransferList
                        items={items}
                        value={selectedStoreIds.map(String)}
                        onChange={onChange}
                        leftTitle={t('Available Stores')}
                        rightTitle={t('Assigned Stores')}
                        leftSearchPlaceholder={t('Search available stores...')}
                        rightSearchPlaceholder={t('Search assigned stores...')}
                        leftEmptyText={t('No stores available')}
                        rightEmptyText={t('No stores assigned')}
                        addSelectedLabel=">"
                        addAllLabel=">>"
                        removeSelectedLabel="<"
                        removeAllLabel="<<"
                        addSelectedTitle="Add selected"
                        addAllTitle="Add all"
                        removeSelectedTitle="Remove selected"
                        removeAllTitle="Remove all"
                    />
                ) : (
                    <Box
                        sx={{
                            border: `1px dashed ${palette.border}`,
                            borderRadius: '20px',
                            px: 3,
                            py: 4,
                            textAlign: 'center',
                            backgroundColor: palette.paperBgAlt,
                        }}
                    >
                        <Typography
                            sx={{
                                fontSize: '0.95rem',
                                fontWeight: 700,
                                color: palette.text,
                            }}
                        >
                            {t('All stores access enabled')}
                        </Typography>
                        <Typography
                            sx={{
                                fontSize: '0.84rem',
                                color: palette.textMuted,
                                mt: 0.75,
                            }}
                        >
                            {t('This user currently has access to all stores.')}
                        </Typography>
                    </Box>
                )}
            </DialogContent>

            <DialogActions sx={{px: 3, pb: 3}}>
                <Button
                    onClick={onClose}
                    sx={{textTransform: 'none', color: palette.textMuted}}
                >
                    {t('Cancel')}
                </Button>
                <Button
                    variant="contained"
                    onClick={onSave}
                    disabled={saving}
                    sx={{
                        textTransform: 'none',
                        borderRadius: 2,
                    }}
                >
                    {saving ? t('Saving...') : t('Save Store Access')}
                </Button>
            </DialogActions>
        </Dialog>
    )
}
