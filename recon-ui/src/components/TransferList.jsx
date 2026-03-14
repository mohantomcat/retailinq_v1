import {useMemo, useState} from 'react'
import {
    Box,
    Button,
    Chip,
    InputAdornment,
    TextField,
    Typography,
} from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import {useAdminPalette} from './admin/theme'
import {useI18n} from '../context/I18nContext'

function PanelItem({item, checked, onToggle, palette}) {
    return (
        <Box
            onClick={() => onToggle(item.id)}
            sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                px: 1.5,
                py: 0.9,
                borderRadius: 1.5,
                cursor: 'pointer',
                backgroundColor: checked ? palette.selectedBg : 'transparent',
                border: '1px solid',
                borderColor: checked ? palette.primary : 'transparent',
                mb: 0.4,
                transition: 'all 0.12s ease',
                '&:hover': {
                    backgroundColor: checked
                        ? palette.selectedHoverBg
                        : palette.paperBgAlt,
                },
            }}
        >
            <Box
                sx={{
                    width: 16,
                    height: 16,
                    borderRadius: 0.5,
                    border: '2px solid',
                    borderColor: checked ? palette.primary : palette.border,
                    backgroundColor: checked ? palette.primary : 'transparent',
                    flexShrink: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}
            >
                {checked && (
                    <Box
                        sx={{
                            width: 8,
                            height: 5,
                            borderLeft: '2px solid #fff',
                            borderBottom: '2px solid #fff',
                            transform: 'rotate(-45deg)',
                            mt: '-2px',
                        }}
                    />
                )}
            </Box>

            <Typography
                sx={{
                    fontSize: '0.82rem',
                    color: checked ? palette.chipBlueText : palette.textMuted,
                    fontWeight: checked ? 500 : 400,
                    flex: 1,
                }}
            >
                {item.label}
            </Typography>

            {item.badge && (
                <Chip
                    label={item.badge}
                    size="small"
                    sx={{
                        height: 18,
                        fontSize: '0.62rem',
                        fontFamily: 'monospace',
                        backgroundColor: palette.chipNeutralBg,
                        color: palette.chipNeutralText,
                    }}
                />
            )}
        </Box>
    )
}

function Panel({
    title,
    items,
    selected,
    onToggle,
    headerColor,
    searchPlaceholder = 'Search...',
    emptyText = 'None',
    palette,
    t,
}) {
    const [search, setSearch] = useState('')

    const filteredItems = useMemo(() => {
        const q = search.trim().toLowerCase()
        if (!q) return items

        return items.filter((item) => {
            const label = String(item.label || '').toLowerCase()
            const badge = String(item.badge || '').toLowerCase()
            return label.includes(q) || badge.includes(q)
        })
    }, [items, search])

    return (
        <Box
            sx={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                border: `1px solid ${palette.border}`,
                borderRadius: '20px',
                overflow: 'hidden',
                minHeight: 380,
                maxHeight: 480,
                backgroundColor: palette.paperBg,
            }}
        >
            <Box
                sx={{
                    px: 2,
                    py: 1.25,
                    backgroundColor: headerColor || palette.paperBgAlt,
                    borderBottom: `1px solid ${palette.border}`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexShrink: 0,
                }}
            >
                <Typography
                    sx={{
                        fontWeight: 700,
                        fontSize: '0.86rem',
                        color: palette.textMuted,
                    }}
                >
                    {t(title)}
                </Typography>

                <Chip
                    label={items.length}
                    size="small"
                    sx={{
                        height: 20,
                        fontSize: '0.7rem',
                        backgroundColor: palette.chipBlueBg,
                        color: palette.chipBlueText,
                        fontWeight: 700,
                    }}
                />
            </Box>

            <Box sx={{px: 1.5, py: 1, flexShrink: 0}}>
                <TextField
                    size="small"
                    fullWidth
                    placeholder={t(searchPlaceholder)}
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    InputProps={{
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon
                                    sx={{
                                        fontSize: 16,
                                        color: palette.textSoft,
                                    }}
                                />
                            </InputAdornment>
                        ),
                        sx: {
                            fontSize: '0.82rem',
                            borderRadius: 999,
                            color: palette.text,
                            backgroundColor: palette.inputBg,
                        },
                    }}
                    sx={{
                        '& .MuiOutlinedInput-root': {
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
                    }}
                />
            </Box>

            <Box
                sx={{
                    flex: 1,
                    overflowY: 'auto',
                    px: 1,
                    pb: 1,
                    '&::-webkit-scrollbar': {width: 6},
                    '&::-webkit-scrollbar-track': {
                        backgroundColor: palette.scrollbarTrack,
                    },
                    '&::-webkit-scrollbar-thumb': {
                        backgroundColor: palette.scrollbarThumb,
                        borderRadius: 999,
                    },
                }}
            >
                {filteredItems.length === 0 ? (
                    <Typography
                        sx={{
                            fontSize: '0.8rem',
                            color: palette.textSoft,
                            textAlign: 'center',
                            py: 4,
                            fontStyle: 'italic',
                        }}
                    >
                        {t(emptyText)}
                    </Typography>
                ) : (
                    filteredItems.map((item) => (
                        <PanelItem
                            key={item.id}
                            item={item}
                            checked={selected.has(item.id)}
                            onToggle={onToggle}
                            palette={palette}
                        />
                    ))
                )}
            </Box>
        </Box>
    )
}

export default function TransferList({
    items = [],
    value = [],
    onChange,
    leftTitle = 'Available',
    rightTitle = 'Assigned',
    leftSearchPlaceholder = 'Search available...',
    rightSearchPlaceholder = 'Search assigned...',
    leftEmptyText = 'No items available',
    rightEmptyText = 'No items assigned',
    addSelectedLabel = '>',
    addAllLabel = '>>',
    removeSelectedLabel = '<',
    removeAllLabel = '<<',
    addSelectedTitle = 'Add selected',
    addAllTitle = 'Add all',
    removeSelectedTitle = 'Remove selected',
    removeAllTitle = 'Remove all',
}) {
    const palette = useAdminPalette()
    const {t} = useI18n()
    const [selectedLeft, setSelectedLeft] = useState(new Set())
    const [selectedRight, setSelectedRight] = useState(new Set())
    const assignedIds = useMemo(() => new Set(value), [value])

    const availableItems = useMemo(
        () => items.filter((item) => !assignedIds.has(item.id)),
        [items, assignedIds]
    )

    const assignedItems = useMemo(
        () => items.filter((item) => assignedIds.has(item.id)),
        [items, assignedIds]
    )

    const availableCount = availableItems.length
    const assignedCount = assignedItems.length

    const emitChange = (nextAssignedIds) => {
        const next = Array.from(nextAssignedIds)
        onChange?.(next)
    }

    const toggleLeft = (id) =>
        setSelectedLeft((prev) => {
            const next = new Set(prev)
            next.has(id) ? next.delete(id) : next.add(id)
            return next
        })

    const toggleRight = (id) =>
        setSelectedRight((prev) => {
            const next = new Set(prev)
            next.has(id) ? next.delete(id) : next.add(id)
            return next
        })

    const moveToRight = () => {
        if (!selectedLeft.size) return

        const next = new Set(assignedIds)
        selectedLeft.forEach((id) => next.add(id))
        setSelectedLeft(new Set())
        emitChange(next)
    }

    const moveAllToRight = () => {
        const next = new Set(items.map((item) => item.id))
        setSelectedLeft(new Set())
        emitChange(next)
    }

    const moveToLeft = () => {
        if (!selectedRight.size) return

        const next = new Set(assignedIds)
        selectedRight.forEach((id) => next.delete(id))
        setSelectedRight(new Set())
        emitChange(next)
    }

    const moveAllToLeft = () => {
        setSelectedRight(new Set())
        emitChange(new Set())
    }

    const btnBase = {
        minWidth: 44,
        width: 44,
        height: 34,
        p: 0,
        borderRadius: 2.5,
        boxShadow: 'none',
        textTransform: 'none',
        fontWeight: 700,
        '&:hover': {boxShadow: 'none'},
    }

    return (
        <Box>
            <Box
                sx={{
                    display: 'flex',
                    alignItems: 'stretch',
                    gap: 1.5,
                    flexDirection: {xs: 'column', md: 'row'},
                }}
            >
                <Panel
                    title={`${leftTitle} (${availableCount})`}
                    items={availableItems}
                    selected={selectedLeft}
                    onToggle={toggleLeft}
                    headerColor={palette.paperBgAlt}
                    searchPlaceholder={leftSearchPlaceholder}
                    emptyText={t(leftEmptyText)}
                    palette={palette}
                    t={t}
                />

                <Box
                    sx={{
                        display: 'flex',
                        flexDirection: {xs: 'row', md: 'column'},
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 1,
                        px: {xs: 0, md: 0.5},
                        flexShrink: 0,
                    }}
                >
                    <Button
                        variant="outlined"
                        size="small"
                        onClick={moveToRight}
                        disabled={!selectedLeft.size}
                        title={t(addSelectedTitle)}
                        sx={{
                            ...btnBase,
                            borderColor: palette.border,
                            color: palette.primary,
                            '&:not(:disabled):hover': {
                                borderColor: palette.primary,
                                backgroundColor: palette.selectedBg,
                            },
                        }}
                    >
                        {addSelectedLabel}
                    </Button>

                    <Button
                        variant="outlined"
                        size="small"
                        onClick={moveAllToRight}
                        disabled={availableCount === 0}
                        title={t(addAllTitle)}
                        sx={{
                            ...btnBase,
                            borderColor: palette.border,
                            color: palette.primary,
                            '&:not(:disabled):hover': {
                                borderColor: palette.primary,
                                backgroundColor: palette.selectedBg,
                            },
                        }}
                    >
                        {addAllLabel}
                    </Button>

                    <Button
                        variant="outlined"
                        size="small"
                        onClick={moveToLeft}
                        disabled={!selectedRight.size}
                        title={t(removeSelectedTitle)}
                        sx={{
                            ...btnBase,
                            borderColor: palette.border,
                            color: palette.dangerText,
                            '&:not(:disabled):hover': {
                                borderColor: palette.dangerText,
                                backgroundColor: palette.dangerBg,
                            },
                        }}
                    >
                        {removeSelectedLabel}
                    </Button>

                    <Button
                        variant="outlined"
                        size="small"
                        onClick={moveAllToLeft}
                        disabled={assignedCount === 0}
                        title={t(removeAllTitle)}
                        sx={{
                            ...btnBase,
                            borderColor: palette.border,
                            color: palette.dangerText,
                            '&:not(:disabled):hover': {
                                borderColor: palette.dangerText,
                                backgroundColor: palette.dangerBg,
                            },
                        }}
                    >
                        {removeAllLabel}
                    </Button>
                </Box>

                <Panel
                    title={`${rightTitle} (${assignedCount})`}
                    items={assignedItems}
                    selected={selectedRight}
                    onToggle={toggleRight}
                    headerColor={palette.successBg}
                    searchPlaceholder={rightSearchPlaceholder}
                    emptyText={t(rightEmptyText)}
                    palette={palette}
                    t={t}
                />
            </Box>
        </Box>
    )
}
