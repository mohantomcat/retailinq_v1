import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    Paper,
    Stack,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    TextField,
    Typography,
} from '@mui/material'
import {adminApi} from '../../services/adminApi'
import {useAdminPalette} from '../../components/admin/theme'
import {useI18n} from '../../context/I18nContext'

const UNIT_TYPES = ['ORGANIZATION', 'REGION', 'DISTRICT', 'FRANCHISE', 'STORE']

const EMPTY_FORM = {
    unitKey: '',
    unitName: '',
    unitType: 'REGION',
    parentUnitId: '',
    storeId: '',
    sortOrder: 0,
    active: true,
}

export default function OrganizationHierarchy() {
    const palette = useAdminPalette()
    const {t} = useI18n()
    const [units, setUnits] = useState([])
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)
    const [error, setError] = useState('')
    const [dialogOpen, setDialogOpen] = useState(false)
    const [editingUnit, setEditingUnit] = useState(null)
    const [form, setForm] = useState(EMPTY_FORM)

    const loadUnits = async () => {
        setLoading(true)
        try {
            setUnits(await adminApi.getOrganizationUnits())
            setError('')
        } catch (err) {
            setError(err.message || 'Failed to load organization hierarchy')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadUnits()
    }, [])

    const summary = useMemo(() => ({
        regions: units.filter((unit) => unit.unitType === 'REGION').length,
        districts: units.filter((unit) => unit.unitType === 'DISTRICT').length,
        franchises: units.filter((unit) => unit.unitType === 'FRANCHISE').length,
        stores: units.filter((unit) => unit.unitType === 'STORE').length,
    }), [units])

    const openCreate = () => {
        setEditingUnit(null)
        setForm(EMPTY_FORM)
        setDialogOpen(true)
        setError('')
    }

    const openEdit = (unit) => {
        setEditingUnit(unit)
        setForm({
            unitKey: unit.unitKey || '',
            unitName: unit.unitName || '',
            unitType: unit.unitType || 'REGION',
            parentUnitId: unit.parentUnitId || '',
            storeId: unit.storeId || '',
            sortOrder: unit.sortOrder ?? 0,
            active: unit.active ?? true,
        })
        setDialogOpen(true)
        setError('')
    }

    const closeDialog = () => {
        setDialogOpen(false)
        setEditingUnit(null)
        setForm(EMPTY_FORM)
    }

    const handleSave = async () => {
        if (!form.unitKey.trim() || !form.unitName.trim()) {
            setError(t('Unit key and unit name are required'))
            return
        }
        if (form.unitType === 'STORE' && !form.storeId.trim()) {
            setError(t('Store id is required for store units'))
            return
        }

        setSaving(true)
        try {
            await adminApi.saveOrganizationUnit(editingUnit?.id, {
                ...form,
                parentUnitId: form.parentUnitId || null,
                storeId: form.unitType === 'STORE' ? form.storeId : null,
            })
            closeDialog()
            await loadUnits()
        } catch (err) {
            setError(err.message || 'Failed to save organization unit')
        } finally {
            setSaving(false)
        }
    }

    return (
        <Box sx={{px: 4, py: 3, maxWidth: 1480}}>
            <Stack
                direction={{xs: 'column', md: 'row'}}
                spacing={1.5}
                sx={{mb: 2.5}}
                justifyContent="space-between"
                alignItems={{xs: 'flex-start', md: 'center'}}
            >
                <Box>
                    <Typography sx={{fontSize: '1.15rem', fontWeight: 800, color: palette.text}}>
                        {t('Organization Hierarchy')}
                    </Typography>
                    <Typography sx={{fontSize: '0.88rem', color: palette.textMuted, mt: 0.5}}>
                        {t('Model region, district, franchise, and store structure for org-aware visibility rules.')}
                    </Typography>
                </Box>

                <Button
                    variant="contained"
                    onClick={openCreate}
                    sx={{textTransform: 'none', borderRadius: 2.5}}
                >
                    {t('Add Org Unit')}
                </Button>
            </Stack>

            {error && !dialogOpen && (
                <Alert severity="error" sx={{mb: 2, borderRadius: 3}}>
                    {error}
                </Alert>
            )}

            <Box
                sx={{
                    display: 'grid',
                    gridTemplateColumns: {xs: '1fr 1fr', lg: 'repeat(4, minmax(0, 1fr))'},
                    gap: 1.5,
                    mb: 2.5,
                }}
            >
                {[
                    ['Regions', summary.regions],
                    ['Districts', summary.districts],
                    ['Franchises', summary.franchises],
                    ['Stores', summary.stores],
                ].map(([label, value]) => (
                    <Paper
                        key={label}
                        elevation={0}
                        sx={{
                            p: 2,
                            borderRadius: '20px',
                            border: `1px solid ${palette.border}`,
                            backgroundColor: palette.paperBg,
                        }}
                    >
                        <Typography sx={{fontSize: '0.75rem', color: palette.textMuted, textTransform: 'uppercase'}}>
                            {t(label)}
                        </Typography>
                        <Typography sx={{fontSize: '1.45rem', fontWeight: 800, color: palette.text, mt: 0.6}}>
                            {value}
                        </Typography>
                    </Paper>
                ))}
            </Box>

            <Paper
                elevation={0}
                sx={{
                    border: `1px solid ${palette.border}`,
                    borderRadius: '24px',
                    overflow: 'hidden',
                    backgroundColor: palette.paperBg,
                }}
            >
                <TableContainer>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                {['Unit', 'Type', 'Parent', 'Store', 'Users', 'Status', ''].map((label) => (
                                    <TableCell
                                        key={label}
                                        sx={{
                                            fontWeight: 700,
                                            fontSize: '0.76rem',
                                            color: palette.textMuted,
                                            backgroundColor: palette.paperBgAlt,
                                            borderBottom: `1px solid ${palette.border}`,
                                            textTransform: 'uppercase',
                                            letterSpacing: '0.4px',
                                        }}
                                    >
                                        {t(label)}
                                    </TableCell>
                                ))}
                            </TableRow>
                        </TableHead>

                        <TableBody>
                            {loading ? (
                                <TableRow>
                                    <TableCell colSpan={7} sx={{py: 4, color: palette.textMuted}}>
                                        {t('Loading organization hierarchy...')}
                                    </TableCell>
                                </TableRow>
                            ) : units.length === 0 ? (
                                <TableRow>
                                    <TableCell colSpan={7} sx={{py: 4, color: palette.textMuted}}>
                                        {t('No organization units found')}
                                    </TableCell>
                                </TableRow>
                            ) : (
                                units.map((unit) => (
                                    <TableRow key={unit.id} hover>
                                        <TableCell sx={{color: palette.text, fontWeight: 700}}>
                                            <Stack spacing={0.25}>
                                                <Typography sx={{fontSize: '0.9rem', fontWeight: 700}}>
                                                    {unit.unitName}
                                                </Typography>
                                                <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                                    {unit.unitKey}
                                                </Typography>
                                            </Stack>
                                        </TableCell>
                                        <TableCell sx={{color: palette.text}}>
                                            <Chip
                                                size="small"
                                                label={unit.unitType}
                                                sx={{
                                                    backgroundColor: palette.chipBlueBg,
                                                    color: palette.chipBlueText,
                                                    fontWeight: 700,
                                                }}
                                            />
                                        </TableCell>
                                        <TableCell sx={{color: palette.textMuted}}>
                                            {unit.parentUnitName || '-'}
                                        </TableCell>
                                        <TableCell sx={{color: palette.textMuted}}>
                                            {unit.storeId || '-'}
                                        </TableCell>
                                        <TableCell sx={{color: palette.textMuted}}>
                                            {unit.assignedUserCount || 0}
                                        </TableCell>
                                        <TableCell>
                                            <Chip
                                                size="small"
                                                label={unit.active ? t('Active') : t('Inactive')}
                                                sx={{
                                                    backgroundColor: unit.active
                                                        ? palette.chipGreenBg
                                                        : palette.chipRedBg,
                                                    color: unit.active
                                                        ? palette.chipGreenText
                                                        : palette.chipRedText,
                                                    fontWeight: 700,
                                                }}
                                            />
                                        </TableCell>
                                        <TableCell align="right">
                                            <Button
                                                size="small"
                                                onClick={() => openEdit(unit)}
                                                sx={{textTransform: 'none'}}
                                            >
                                                {t('Edit')}
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Paper>

            <Dialog
                open={dialogOpen}
                onClose={closeDialog}
                maxWidth="sm"
                fullWidth
                PaperProps={{
                    sx: {
                        borderRadius: '24px',
                        border: `1px solid ${palette.border}`,
                        background: palette.dialogBg,
                    },
                }}
            >
                <DialogTitle sx={{fontWeight: 800, color: palette.text}}>
                    {editingUnit ? t('Edit Organization Unit') : t('Create Organization Unit')}
                </DialogTitle>

                <DialogContent sx={{display: 'grid', gap: 2, pt: '16px !important'}}>
                    {error && (
                        <Alert severity="error" sx={{borderRadius: 2}}>
                            {error}
                        </Alert>
                    )}

                    <TextField
                        label={t('Unit Key')}
                        value={form.unitKey}
                        onChange={(event) => setForm((current) => ({...current, unitKey: event.target.value}))}
                        size="small"
                        fullWidth
                    />
                    <TextField
                        label={t('Unit Name')}
                        value={form.unitName}
                        onChange={(event) => setForm((current) => ({...current, unitName: event.target.value}))}
                        size="small"
                        fullWidth
                    />
                    <TextField
                        select
                        label={t('Unit Type')}
                        value={form.unitType}
                        onChange={(event) => setForm((current) => ({...current, unitType: event.target.value}))}
                        size="small"
                        fullWidth
                    >
                        {UNIT_TYPES.map((type) => (
                            <MenuItem key={type} value={type}>
                                {type}
                            </MenuItem>
                        ))}
                    </TextField>
                    <TextField
                        select
                        label={t('Parent Unit')}
                        value={form.parentUnitId}
                        onChange={(event) => setForm((current) => ({...current, parentUnitId: event.target.value}))}
                        size="small"
                        fullWidth
                    >
                        <MenuItem value="">{t('None / Root')}</MenuItem>
                        {units
                            .filter((unit) => unit.active && unit.id !== editingUnit?.id)
                            .map((unit) => (
                                <MenuItem key={unit.id} value={unit.id}>
                                    {unit.unitName} ({unit.unitType})
                                </MenuItem>
                            ))}
                    </TextField>
                    {form.unitType === 'STORE' && (
                        <TextField
                            label={t('Store Id')}
                            value={form.storeId}
                            onChange={(event) => setForm((current) => ({...current, storeId: event.target.value}))}
                            size="small"
                            fullWidth
                        />
                    )}
                    <TextField
                        label={t('Sort Order')}
                        type="number"
                        value={form.sortOrder}
                        onChange={(event) => setForm((current) => ({...current, sortOrder: Number(event.target.value || 0)}))}
                        size="small"
                        fullWidth
                    />
                </DialogContent>

                <DialogActions sx={{px: 3, pb: 3}}>
                    <Button onClick={closeDialog} sx={{textTransform: 'none', color: palette.textMuted}}>
                        {t('Cancel')}
                    </Button>
                    <Button
                        variant="contained"
                        onClick={handleSave}
                        disabled={saving}
                        sx={{textTransform: 'none', borderRadius: 2.5}}
                    >
                        {saving ? t('Saving...') : t('Save')}
                    </Button>
                </DialogActions>
            </Dialog>
        </Box>
    )
}
