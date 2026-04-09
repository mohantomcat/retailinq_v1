import {
    Box,
    Chip,
    Divider,
    Grid,
    Paper,
    Stack,
    Typography,
} from '@mui/material'

const summaryCards = [
    {label: 'Total Transactions', value: '354', tone: 'neutral'},
    {label: 'Matched', value: '119', tone: 'success'},
    {label: 'Missing in Target', value: '235', tone: 'danger'},
    {label: 'Awaiting Target', value: '14', tone: 'warning'},
]

const issueCards = [
    {label: 'Missing Items', value: '42', tone: 'danger'},
    {label: 'Quantity Mismatch', value: '13', tone: 'warning'},
    {label: 'Transaction Total Mismatch', value: '17', tone: 'warning'},
    {label: 'Duplicate in Target', value: '8', tone: 'danger'},
]

const healthCards = [
    {label: 'Processing Pending', value: '26', tone: 'info'},
    {label: 'Published Source Transactions', value: '354', tone: 'neutral'},
    {label: 'Last Sync Delay', value: '6 min', tone: 'info'},
]

function toneColors(tone) {
    switch (tone) {
        case 'success':
            return {bg: '#ECFDF3', text: '#166534', accent: '#16A34A'}
        case 'danger':
            return {bg: '#FEF2F2', text: '#B91C1C', accent: '#EF4444'}
        case 'warning':
            return {bg: '#FFF7ED', text: '#B45309', accent: '#D97706'}
        case 'info':
            return {bg: '#EFF6FF', text: '#1D4ED8', accent: '#2563EB'}
        case 'neutral':
        default:
            return {bg: '#F8FAFC', text: '#334155', accent: '#0F7C86'}
    }
}

function MetricCard({label, value, tone = 'neutral', compact = false}) {
    const colors = toneColors(tone)

    return (
        <Paper
            elevation={0}
            sx={{
                p: compact ? 2 : 2.5,
                borderRadius: 4,
                border: '1px solid #E2E8F0',
                backgroundColor: '#FFFFFF',
                position: 'relative',
                overflow: 'hidden',
                minHeight: compact ? 108 : 128,
            }}
        >
            <Box
                sx={{
                    position: 'absolute',
                    inset: 0,
                    background:
                        'radial-gradient(circle at top right, rgba(37,99,235,0.08), transparent 42%)',
                    pointerEvents: 'none',
                }}
            />
            <Stack spacing={1.5} sx={{position: 'relative'}}>
                <Chip
                    label={label}
                    size="small"
                    sx={{
                        alignSelf: 'flex-start',
                        backgroundColor: colors.bg,
                        color: colors.text,
                        fontWeight: 700,
                        borderRadius: 999,
                    }}
                />
                <Typography
                    sx={{
                        fontSize: compact ? '2rem' : '2.3rem',
                        lineHeight: 1,
                        fontWeight: 800,
                        color: '#0F172A',
                    }}
                >
                    {value}
                </Typography>
                <Box
                    sx={{
                        width: compact ? 56 : 72,
                        height: 4,
                        borderRadius: 999,
                        backgroundColor: colors.accent,
                    }}
                />
            </Stack>
        </Paper>
    )
}

function SectionHeader({eyebrow, title, description}) {
    return (
        <Stack spacing={0.75}>
            <Chip
                label={eyebrow}
                size="small"
                sx={{
                    alignSelf: 'flex-start',
                    backgroundColor: '#DBEAFE',
                    color: '#1D4ED8',
                    fontWeight: 800,
                }}
            />
            <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: '#0F172A'}}>
                {title}
            </Typography>
            <Typography sx={{fontSize: '0.95rem', color: '#64748B', maxWidth: 720}}>
                {description}
            </Typography>
        </Stack>
    )
}

function OptionFrame({title, subtitle, children, recommended = false}) {
    return (
        <Paper
            elevation={0}
            sx={{
                p: {xs: 2.5, md: 3},
                borderRadius: 5,
                border: recommended ? '1px solid #93C5FD' : '1px solid #E2E8F0',
                background:
                    'linear-gradient(180deg, rgba(255,255,255,1) 0%, rgba(248,250,252,1) 100%)',
                boxShadow: recommended
                    ? '0 18px 40px rgba(37,99,235,0.10)'
                    : '0 10px 28px rgba(15,23,42,0.05)',
            }}
        >
            <Stack spacing={2.5}>
                <Stack direction="row" spacing={1.5} alignItems="center" flexWrap="wrap">
                    <Typography sx={{fontSize: '1.1rem', fontWeight: 800, color: '#0F172A'}}>
                        {title}
                    </Typography>
                    {recommended && (
                        <Chip
                            label="Recommended"
                            size="small"
                            sx={{
                                backgroundColor: '#0F7C86',
                                color: '#FFFFFF',
                                fontWeight: 800,
                            }}
                        />
                    )}
                </Stack>
                <Typography sx={{fontSize: '0.95rem', color: '#64748B'}}>
                    {subtitle}
                </Typography>
                {children}
            </Stack>
        </Paper>
    )
}

export default function KpiMockup() {
    return (
        <Box
            sx={{
                minHeight: '100vh',
                background:
                    'linear-gradient(180deg, #F8FAFC 0%, #EEF4FF 100%)',
                p: {xs: 2, md: 4},
            }}
        >
            <Stack spacing={4}>
                <SectionHeader
                    eyebrow="KPI Mockup"
                    title="Enterprise KPI Layout Directions"
                    description="This preview shows cleaner ways to present reconciliation KPIs as the metric count grows. The first option is the strongest fit for RetailINQ because it keeps the top operational signals prominent and groups the rest by issue type."
                />

                <OptionFrame
                    title="Option 1: Tiered KPI Header"
                    subtitle="Top-line metrics stay prominent, while secondary issue metrics are grouped into transaction and item sections. This is the most practical reconciliation layout."
                    recommended
                >
                    <Grid container spacing={2}>
                        {summaryCards.map((card) => (
                            <Grid size={{xs: 12, sm: 6, lg: 3}} key={card.label}>
                                <MetricCard {...card} />
                            </Grid>
                        ))}
                    </Grid>

                    <Divider sx={{borderColor: '#E2E8F0'}} />

                    <Stack spacing={1.5}>
                        <Typography sx={{fontSize: '0.92rem', fontWeight: 800, color: '#475569'}}>
                            Transaction Issues
                        </Typography>
                        <Grid container spacing={2}>
                            {issueCards.slice(2).map((card) => (
                                <Grid size={{xs: 12, md: 6}} key={card.label}>
                                    <MetricCard {...card} compact />
                                </Grid>
                            ))}
                        </Grid>
                    </Stack>

                    <Stack spacing={1.5}>
                        <Typography sx={{fontSize: '0.92rem', fontWeight: 800, color: '#475569'}}>
                            Item Issues
                        </Typography>
                        <Grid container spacing={2}>
                            {issueCards.slice(0, 2).map((card) => (
                                <Grid size={{xs: 12, md: 6}} key={card.label}>
                                    <MetricCard {...card} compact />
                                </Grid>
                            ))}
                        </Grid>
                    </Stack>
                </OptionFrame>

                <OptionFrame
                    title="Option 2: Executive Summary Rail"
                    subtitle="This pushes the primary totals into a single summary band and moves all exception metrics into a clean grid underneath. It feels more dashboard-like and less operational."
                >
                    <Paper
                        elevation={0}
                        sx={{
                            p: 3,
                            borderRadius: 4,
                            border: '1px solid #C7D2FE',
                            background:
                                'linear-gradient(135deg, #0F172A 0%, #1E3A8A 100%)',
                            color: '#FFFFFF',
                        }}
                    >
                        <Grid container spacing={2} alignItems="center">
                            <Grid size={{xs: 12, md: 4}}>
                                <Typography sx={{fontSize: '0.82rem', opacity: 0.76}}>
                                    Total Transactions
                                </Typography>
                                <Typography sx={{fontSize: '2.4rem', fontWeight: 800}}>
                                    354
                                </Typography>
                            </Grid>
                            <Grid size={{xs: 6, md: 4}}>
                                <Typography sx={{fontSize: '0.82rem', opacity: 0.76}}>
                                    Matched
                                </Typography>
                                <Typography sx={{fontSize: '1.7rem', fontWeight: 800}}>
                                    119
                                </Typography>
                            </Grid>
                            <Grid size={{xs: 6, md: 4}}>
                                <Typography sx={{fontSize: '0.82rem', opacity: 0.76}}>
                                    Exception Count
                                </Typography>
                                <Typography sx={{fontSize: '1.7rem', fontWeight: 800}}>
                                    235
                                </Typography>
                            </Grid>
                        </Grid>
                    </Paper>

                    <Grid container spacing={2}>
                        {[...issueCards, ...healthCards.slice(0, 2)].map((card) => (
                            <Grid size={{xs: 12, sm: 6, lg: 4}} key={card.label}>
                                <MetricCard {...card} compact />
                            </Grid>
                        ))}
                    </Grid>
                </OptionFrame>

                <OptionFrame
                    title="Option 3: Split Summary and Processing Health"
                    subtitle="This layout separates reconciliation outcome from pipeline health. It is useful if operations teams care equally about data quality and connector processing state."
                >
                    <Grid container spacing={2}>
                        <Grid size={{xs: 12, lg: 7}}>
                            <Paper
                                elevation={0}
                                sx={{
                                    p: 3,
                                    borderRadius: 4,
                                    border: '1px solid #E2E8F0',
                                    height: '100%',
                                }}
                            >
                                <Stack spacing={2}>
                                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: '#0F172A'}}>
                                        Reconciliation Summary
                                    </Typography>
                                    <Grid container spacing={2}>
                                        {summaryCards.slice(0, 3).map((card) => (
                                            <Grid size={{xs: 12, md: 4}} key={card.label}>
                                                <MetricCard {...card} compact />
                                            </Grid>
                                        ))}
                                    </Grid>
                                </Stack>
                            </Paper>
                        </Grid>
                        <Grid size={{xs: 12, lg: 5}}>
                            <Paper
                                elevation={0}
                                sx={{
                                    p: 3,
                                    borderRadius: 4,
                                    border: '1px solid #E2E8F0',
                                    height: '100%',
                                }}
                            >
                                <Stack spacing={2}>
                                    <Typography sx={{fontSize: '1rem', fontWeight: 800, color: '#0F172A'}}>
                                        Processing Health
                                    </Typography>
                                    <Grid container spacing={2}>
                                        {healthCards.map((card) => (
                                            <Grid size={{xs: 12}} key={card.label}>
                                                <MetricCard {...card} compact />
                                            </Grid>
                                        ))}
                                    </Grid>
                                </Stack>
                            </Paper>
                        </Grid>
                    </Grid>

                    <Grid container spacing={2}>
                        {issueCards.map((card) => (
                            <Grid size={{xs: 12, sm: 6, lg: 3}} key={card.label}>
                                <MetricCard {...card} compact />
                            </Grid>
                        ))}
                    </Grid>
                </OptionFrame>
            </Stack>
        </Box>
    )
}
