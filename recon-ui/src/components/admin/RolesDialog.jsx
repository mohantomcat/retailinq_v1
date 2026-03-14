import {
    Alert,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Typography,
} from '@mui/material'
import TransferList from '../../components/TransferList'
import {useAdminPalette} from './theme'
import {useI18n} from '../../context/I18nContext'

export default function RolesDialog({
    open,
    user,
    roles = [],
    selectedRoleIds = [],
    onChange,
    onClose,
    onSave,
    saving = false,
    error = '',
}) {
    const palette = useAdminPalette()
    const {t} = useI18n()

    const items = roles.map((role) => ({
        id: role.id,
        label: role.name,
        badge: role.permissionCodes?.length
            ? `${role.permissionCodes.length} perms`
            : '',
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
                {t('Manage Roles - {username}', {username: user?.username})}
            </DialogTitle>

            <DialogContent sx={{pt: '16px !important'}}>
                {error && (
                    <Alert severity="error" sx={{mb: 2, borderRadius: 2}}>
                        {error}
                    </Alert>
                )}

                <Typography
                    sx={{
                        fontSize: '0.85rem',
                        color: palette.textMuted,
                        mb: 2,
                    }}
                >
                    {t('Add or remove role assignments for this user.')}
                </Typography>

                <TransferList
                    items={items}
                    value={selectedRoleIds}
                    onChange={onChange}
                    leftTitle={t('Available Roles')}
                    rightTitle={t('Assigned Roles')}
                    leftSearchPlaceholder={t('Search available roles...')}
                    rightSearchPlaceholder={t('Search assigned roles...')}
                    leftEmptyText={t('No roles available')}
                    rightEmptyText={t('No roles assigned')}
                    addSelectedLabel=">"
                    addAllLabel=">>"
                    removeSelectedLabel="<"
                    removeAllLabel="<<"
                    addSelectedTitle="Add selected"
                    addAllTitle="Add all"
                    removeSelectedTitle="Remove selected"
                    removeAllTitle="Remove all"
                />
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
                    {saving ? t('Saving...') : t('Save Roles')}
                </Button>
            </DialogActions>
        </Dialog>
    )
}
