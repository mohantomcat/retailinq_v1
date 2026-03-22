import {
    Box,
    Button,
    Chip,
    Paper,
    Stack,
    Typography,
} from '@mui/material'

function confidenceChipStyles(confidence, palette) {
    switch ((confidence || '').toUpperCase()) {
        case 'HIGH':
            return {backgroundColor: '#ECFDF5', color: '#047857'}
        case 'MEDIUM':
            return {backgroundColor: '#EFF6FF', color: '#2563EB'}
        default:
            return {backgroundColor: palette.cardBg, color: palette.textMuted, border: `1px solid ${palette.border}`}
    }
}

function InfoBlock({label, value, palette}) {
    if (!value) {
        return null
    }
    return (
        <Box>
            <Typography
                sx={{
                    fontSize: '0.74rem',
                    color: palette.textMuted,
                    textTransform: 'uppercase',
                    fontWeight: 700,
                    letterSpacing: '0.04em',
                }}
            >
                {label}
            </Typography>
            <Typography sx={{mt: 0.35, fontSize: '0.84rem', color: palette.text, lineHeight: 1.6}}>
                {value}
            </Typography>
        </Box>
    )
}

export default function KnownIssueGuidanceCard({
    match,
    palette,
    t,
    title,
    subtitle,
    onFeedback,
    feedbackSubmitting = false,
}) {
    if (!match) {
        return null
    }

    return (
        <Paper
            elevation={0}
            sx={{
                p: 2,
                borderRadius: 3,
                border: `1px solid ${palette.borderSoft}`,
                backgroundColor: palette.cardBg,
            }}
        >
            <Stack spacing={1.4}>
                <Box sx={{display: 'flex', justifyContent: 'space-between', gap: 1.25, alignItems: 'flex-start', flexWrap: 'wrap'}}>
                    <Box sx={{flex: '1 1 280px'}}>
                        <Typography sx={{fontSize: '0.82rem', color: palette.textMuted, textTransform: 'uppercase', fontWeight: 700}}>
                            {title || t('Guided Resolution')}
                        </Typography>
                        <Typography sx={{mt: 0.35, fontSize: '0.98rem', color: palette.text, fontWeight: 800}}>
                            {match.title}
                        </Typography>
                        <Typography sx={{mt: 0.35, fontSize: '0.8rem', color: palette.textMuted}}>
                            {subtitle || t('Matched guidance from the known-issue catalog for this exception context.')}
                        </Typography>
                    </Box>
                    <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                        {match.issueKey ? (
                            <Chip
                                size="small"
                                label={match.issueKey}
                                sx={{backgroundColor: palette.blueChipBg, color: palette.blueChipText, fontWeight: 700}}
                            />
                        ) : null}
                        <Chip
                            size="small"
                            label={`${t('Confidence')}: ${match.confidence || t('Low')}`}
                            sx={{fontWeight: 700, ...confidenceChipStyles(match.confidence, palette)}}
                        />
                    </Stack>
                </Box>

                {match.matchReason ? (
                    <Typography sx={{fontSize: '0.8rem', color: palette.blueChipText}}>
                        {match.matchReason}
                    </Typography>
                ) : null}

                <InfoBlock label={t('Issue Summary')} value={match.issueSummary} palette={palette}/>
                <InfoBlock label={t('Probable Cause')} value={match.probableCause} palette={palette}/>
                <InfoBlock label={t('Recommended Action')} value={match.recommendedAction} palette={palette}/>
                <InfoBlock label={t('Escalation Guidance')} value={match.escalationGuidance} palette={palette}/>
                <InfoBlock label={t('Resolver Notes')} value={match.resolverNotes} palette={palette}/>

                <Box
                    sx={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        gap: 1.25,
                        flexWrap: 'wrap',
                        pt: 0.4,
                    }}
                >
                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                        {t('Helpful')}: {match.helpfulCount || 0} | {t('Not Helpful')}: {match.notHelpfulCount || 0}
                    </Typography>
                    <Stack direction="row" spacing={1}>
                        <Button
                            size="small"
                            variant="outlined"
                            onClick={() => onFeedback?.(true)}
                            disabled={!onFeedback || feedbackSubmitting}
                            sx={{textTransform: 'none', fontWeight: 700}}
                        >
                            {feedbackSubmitting ? t('Saving...') : t('Helpful')}
                        </Button>
                        <Button
                            size="small"
                            variant="outlined"
                            color="inherit"
                            onClick={() => onFeedback?.(false)}
                            disabled={!onFeedback || feedbackSubmitting}
                            sx={{textTransform: 'none', fontWeight: 700}}
                        >
                            {t('Not Helpful')}
                        </Button>
                    </Stack>
                </Box>
            </Stack>
        </Paper>
    )
}
