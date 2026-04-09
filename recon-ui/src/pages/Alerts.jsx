import {useEffect, useMemo, useState} from 'react'
import {
    Alert,
    Box,
    Button,
    Chip,
    CircularProgress,
    FormControl,
    Grid,
    InputLabel,
    MenuItem,
    Paper,
    Select,
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
import NotificationsActiveRoundedIcon from '@mui/icons-material/NotificationsActiveRounded'
import EmailOutlinedIcon from '@mui/icons-material/EmailOutlined'
import SettingsEthernetRoundedIcon from '@mui/icons-material/SettingsEthernetRounded'
import {alertApi} from '../services/alertApi'
import {useAuth} from '../context/AuthContext'
import {useReconModules} from '../hooks/useReconModules'

const METRIC_OPTIONS = [
    {value: 'MISSING_IN_TARGET', label: 'Missing in Target'},
    {value: 'DUPLICATE_TRANSACTIONS', label: 'Duplicate Transactions'},
    {value: 'MATCH_RATE', label: 'Match Rate'},
    {value: 'QUANTITY_MISMATCH', label: 'Quantity Mismatch'},
    {value: 'ITEM_MISSING', label: 'Item Missing'},
    {value: 'TOTAL_MISMATCH', label: 'Transaction Total Mismatch'},
    {value: 'TOTAL_TRANSACTIONS', label: 'Total Transactions'},
    {value: 'OPEN_EXCEPTIONS_7_PLUS', label: 'Open Exceptions 7+ Days'},
]

const EMPTY_RULE = {
    ruleName: '',
    reconView: '',
    metricKey: 'MISSING_IN_TARGET',
    operator: '>=',
    thresholdValue: '',
    severity: 'HIGH',
    storeId: '',
    wkstnId: '',
    lookbackDays: 1,
    cooldownMinutes: 60,
    active: true,
    description: '',
}

const EMPTY_SUBSCRIPTION = {
    subscriptionName: '',
    reconView: '',
    metricKey: '',
    severityThreshold: 'HIGH',
    recipientType: 'ROLE',
    recipientKey: '',
    storeId: '',
    wkstnId: '',
    active: true,
    description: '',
}

const EMPTY_WEBHOOK_SUBSCRIPTION = {
    subscriptionName: '',
    reconView: '',
    metricKey: '',
    severityThreshold: 'HIGH',
    channelType: 'GENERIC_WEBHOOK',
    endpointUrl: '',
    storeId: '',
    wkstnId: '',
    active: true,
    description: '',
}

const RECIPIENT_OPTIONS = [
    {value: 'ROLE', label: 'Role'},
    {value: 'USER', label: 'User'},
    {value: 'EMAIL', label: 'Email'},
]

const WEBHOOK_CHANNEL_OPTIONS = [
    {value: 'GENERIC_WEBHOOK', label: 'Generic Webhook'},
    {value: 'MICROSOFT_TEAMS', label: 'Microsoft Teams'},
    {value: 'SLACK', label: 'Slack'},
]

const PERSONAL_CHANNEL_OPTIONS = [
    {value: 'EMAIL', label: 'Email'},
    ...WEBHOOK_CHANNEL_OPTIONS,
]

const EMPTY_ESCALATION_POLICY = {
    policyName: '',
    reconView: '',
    metricKey: '',
    severityThreshold: 'HIGH',
    storeId: '',
    wkstnId: '',
    escalationAfterMinutes: 60,
    destinationType: 'ROLE',
    destinationKey: '',
    active: true,
    description: '',
}

const EMPTY_PERSONAL_SUBSCRIPTION = {
    reconView: '',
    metricKey: '',
    severityThreshold: 'HIGH',
    channelType: 'EMAIL',
    endpointUrl: '',
    storeId: '',
    wkstnId: '',
    active: true,
    description: '',
}

const EMPTY_DIGEST_SUBSCRIPTION = {
    digestName: '',
    reconView: '',
    scopeType: 'STORE',
    scopeKey: '',
    severityThreshold: 'HIGH',
    recipientType: 'ROLE',
    recipientKey: '',
    active: true,
    description: '',
}

const EMPTY_ANOMALY_RULE = {
    ruleName: '',
    reconView: '',
    metricKey: 'TOTAL_TRANSACTIONS',
    anomalyType: 'BOTH',
    percentChangeThreshold: 30,
    minBaselineValue: 1,
    lookbackDays: 7,
    cooldownMinutes: 180,
    severity: 'HIGH',
    storeId: '',
    active: true,
    description: '',
}

const EMPTY_SMS_SUBSCRIPTION = {
    subscriptionName: '',
    reconView: '',
    metricKey: '',
    severityThreshold: 'HIGH',
    phoneNumber: '',
    storeId: '',
    wkstnId: '',
    active: true,
    description: '',
}

const DIGEST_SCOPE_OPTIONS = [
    {value: 'ROLE', label: 'Role'},
    {value: 'STORE', label: 'Store'},
    {value: 'REGION', label: 'Region'},
]

const ANOMALY_TYPE_OPTIONS = [
    {value: 'BOTH', label: 'Spike or Drop'},
    {value: 'SPIKE', label: 'Spike Only'},
    {value: 'DROP', label: 'Drop Only'},
]

function SummaryCard({label, value, palette, tone}) {
    const colors = {
        blue: palette.blueChipText,
        teal: palette.tealChipText,
        red: '#DC2626',
        neutral: palette.neutralChipText,
        purple: '#7C3AED',
        orange: '#EA580C',
    }

    return (
        <Paper elevation={0} sx={{p: 2, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '0.8rem', fontWeight: 700, color: palette.textMuted}}>{label}</Typography>
            <Typography sx={{mt: 0.6, fontSize: '1.7rem', fontWeight: 800, color: colors[tone]}}>{value}</Typography>
        </Paper>
    )
}

function scopeLabel(item, t) {
    return `${item.storeId || t('All Stores')}${item.wkstnId ? ` / ${item.wkstnId}` : ''}`
}

function RuleTable({rules, palette, canEdit, onEdit, onDelete, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Alert Rules')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Rule')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Threshold')}</TableCell>
                            <TableCell>{t('Severity')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rules.length > 0 ? rules.map((rule) => (
                            <TableRow key={rule.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.85rem', fontWeight: 700, color: palette.text}}>{rule.ruleName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{rule.reconView} | {rule.metricKey}</Typography>
                                </TableCell>
                                <TableCell>{scopeLabel(rule, t)}</TableCell>
                                <TableCell>{rule.operator} {rule.thresholdValue}</TableCell>
                                <TableCell><Chip size="small" label={rule.severity} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        <Button size="small" onClick={() => onEdit(rule)} disabled={!canEdit || saving}>{t('Edit')}</Button>
                                        <Button size="small" color="error" onClick={() => onDelete(rule.id)} disabled={!canEdit || saving}>{t('Delete')}</Button>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No alert rules defined yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function EventTable({events, palette, canEdit, onStatus, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('In-App Alert Events')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Message')}</TableCell>
                            <TableCell>{t('Status')}</TableCell>
                            <TableCell>{t('Severity')}</TableCell>
                            <TableCell>{t('Metric')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {events.length > 0 ? events.map((event) => (
                            <TableRow key={event.id}>
                                <TableCell>
                                    <Stack direction="row" spacing={1} alignItems="center">
                                        <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{event.ruleName}</Typography>
                                        {event.detectionType === 'ANOMALY' ? <Chip size="small" label={event.anomalyDirection || 'ANOMALY'} color="warning" sx={{fontWeight: 700}}/> : null}
                                    </Stack>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{event.eventMessage}</Typography>
                                </TableCell>
                                <TableCell><Chip size="small" label={event.alertStatus} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell><Chip size="small" label={event.severity} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>{event.detectionType === 'ANOMALY' ? `${event.metricValue} vs ${event.baselineValue} (${event.deltaPercentage}%)` : `${event.metricValue} / ${event.thresholdValue}`}</TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        {event.alertStatus === 'OPEN' && <Button size="small" onClick={() => onStatus(event.id, 'ACKNOWLEDGED')} disabled={!canEdit || saving}>{t('Acknowledge')}</Button>}
                                        {event.alertStatus !== 'RESOLVED' && <Button size="small" color="success" onClick={() => onStatus(event.id, 'RESOLVED')} disabled={!canEdit || saving}>{t('Resolve')}</Button>}
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No alert events available.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function SubscriptionTable({subscriptions, palette, canEdit, onEdit, onDelete, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Email Subscriptions')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Subscription')}</TableCell>
                            <TableCell>{t('Recipient')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Severity')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {subscriptions.length > 0 ? subscriptions.map((subscription) => (
                            <TableRow key={subscription.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.85rem', fontWeight: 700, color: palette.text}}>{subscription.subscriptionName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                        {subscription.reconView} | {subscription.metricKey || t('All Metrics')}
                                    </Typography>
                                </TableCell>
                                <TableCell>{subscription.recipientType} | {subscription.recipientKey}</TableCell>
                                <TableCell>{scopeLabel(subscription, t)}</TableCell>
                                <TableCell>{subscription.severityThreshold || t('All')}</TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        <Button size="small" onClick={() => onEdit(subscription)} disabled={!canEdit || saving}>{t('Edit')}</Button>
                                        <Button size="small" color="error" onClick={() => onDelete(subscription.id)} disabled={!canEdit || saving}>{t('Delete')}</Button>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No email subscriptions configured yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function DeliveryTable({deliveries, palette, t}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Recent Email Deliveries')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Subject')}</TableCell>
                            <TableCell>{t('Recipient')}</TableCell>
                            <TableCell>{t('Status')}</TableCell>
                            <TableCell>{t('Delivered')}</TableCell>
                            <TableCell>{t('Error')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {deliveries.length > 0 ? deliveries.map((delivery) => (
                            <TableRow key={delivery.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{delivery.emailSubject}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{delivery.reconView}</Typography>
                                </TableCell>
                                <TableCell>{delivery.recipientEmail}</TableCell>
                                <TableCell><Chip size="small" label={delivery.deliveryStatus} color={delivery.deliveryStatus === 'FAILED' ? 'error' : delivery.deliveryStatus === 'SENT' ? 'success' : 'default'} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>{delivery.deliveredAt || delivery.lastAttemptAt || '-'}</TableCell>
                                <TableCell><Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{delivery.errorMessage || '-'}</Typography></TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No email delivery attempts available yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function WebhookSubscriptionTable({subscriptions, palette, canEdit, onEdit, onDelete, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Webhook Subscriptions')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Subscription')}</TableCell>
                            <TableCell>{t('Channel')}</TableCell>
                            <TableCell>{t('Endpoint')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {subscriptions.length > 0 ? subscriptions.map((subscription) => (
                            <TableRow key={subscription.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.85rem', fontWeight: 700, color: palette.text}}>{subscription.subscriptionName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>
                                        {subscription.reconView} | {subscription.metricKey || t('All Metrics')}
                                    </Typography>
                                </TableCell>
                                <TableCell>
                                    <Chip size="small" label={subscription.channelType} sx={{fontWeight: 700}}/>
                                    <Typography sx={{mt: 0.6, fontSize: '0.76rem', color: palette.textMuted}}>{subscription.severityThreshold || t('All Severities')}</Typography>
                                </TableCell>
                                <TableCell><Typography sx={{fontSize: '0.76rem', color: palette.textMuted, wordBreak: 'break-all'}}>{subscription.endpointUrl}</Typography></TableCell>
                                <TableCell>{scopeLabel(subscription, t)}</TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        <Button size="small" onClick={() => onEdit(subscription)} disabled={!canEdit || saving}>{t('Edit')}</Button>
                                        <Button size="small" color="error" onClick={() => onDelete(subscription.id)} disabled={!canEdit || saving}>{t('Delete')}</Button>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No webhook subscriptions configured yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function WebhookDeliveryTable({deliveries, palette, t}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Recent Webhook Deliveries')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Channel')}</TableCell>
                            <TableCell>{t('Endpoint')}</TableCell>
                            <TableCell>{t('Status')}</TableCell>
                            <TableCell>{t('HTTP')}</TableCell>
                            <TableCell>{t('Delivered')}</TableCell>
                            <TableCell>{t('Error')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {deliveries.length > 0 ? deliveries.map((delivery) => (
                            <TableRow key={delivery.id}>
                                <TableCell>
                                    <Chip size="small" label={delivery.channelType} sx={{fontWeight: 700}}/>
                                    <Typography sx={{mt: 0.6, fontSize: '0.76rem', color: palette.textMuted}}>{delivery.reconView}</Typography>
                                </TableCell>
                                <TableCell><Typography sx={{fontSize: '0.76rem', color: palette.textMuted, wordBreak: 'break-all'}}>{delivery.endpointUrl}</Typography></TableCell>
                                <TableCell><Chip size="small" label={delivery.deliveryStatus} color={delivery.deliveryStatus === 'FAILED' ? 'error' : delivery.deliveryStatus === 'SENT' ? 'success' : 'default'} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>{delivery.responseStatusCode || '-'}</TableCell>
                                <TableCell>{delivery.deliveredAt || delivery.lastAttemptAt || '-'}</TableCell>
                                <TableCell><Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{delivery.errorMessage || '-'}</Typography></TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={6}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No webhook delivery attempts available yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function EscalationPolicyTable({policies, palette, canEdit, onEdit, onDelete, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Escalation Policies')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Policy')}</TableCell>
                            <TableCell>{t('Threshold')}</TableCell>
                            <TableCell>{t('Destination')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {policies.length > 0 ? policies.map((policy) => (
                            <TableRow key={policy.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.85rem', fontWeight: 700, color: palette.text}}>{policy.policyName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{policy.reconView} | {policy.metricKey || t('All Metrics')}</Typography>
                                </TableCell>
                                <TableCell>{policy.severityThreshold || t('All')} | {policy.escalationAfterMinutes}m</TableCell>
                                <TableCell>
                                    <Chip size="small" label={policy.destinationType} sx={{fontWeight: 700}}/>
                                    <Typography sx={{mt: 0.6, fontSize: '0.76rem', color: palette.textMuted, wordBreak: 'break-all'}}>{policy.destinationKey}</Typography>
                                </TableCell>
                                <TableCell>{scopeLabel(policy, t)}</TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        <Button size="small" onClick={() => onEdit(policy)} disabled={!canEdit || saving}>{t('Edit')}</Button>
                                        <Button size="small" color="error" onClick={() => onDelete(policy.id)} disabled={!canEdit || saving}>{t('Delete')}</Button>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No escalation policies configured yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function EscalationHistoryTable({history, palette, t}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Escalation History')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Rule')}</TableCell>
                            <TableCell>{t('Destination')}</TableCell>
                            <TableCell>{t('Status')}</TableCell>
                            <TableCell>{t('Escalated')}</TableCell>
                            <TableCell>{t('Error')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {history.length > 0 ? history.map((item) => (
                            <TableRow key={item.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{item.ruleName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{item.reconView} | {item.severity}</Typography>
                                </TableCell>
                                <TableCell>
                                    <Chip size="small" label={item.destinationType} sx={{fontWeight: 700}}/>
                                    <Typography sx={{mt: 0.6, fontSize: '0.76rem', color: palette.textMuted, wordBreak: 'break-all'}}>{item.destinationKey}</Typography>
                                </TableCell>
                                <TableCell><Chip size="small" label={item.escalationStatus} color={item.escalationStatus === 'FAILED' ? 'error' : 'success'} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>{item.escalatedAt || '-'}</TableCell>
                                <TableCell><Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{item.errorMessage || '-'}</Typography></TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No escalations recorded yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function PersonalSubscriptionTable({subscriptions, palette, canEdit, onEdit, onDelete, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('My Alert Subscriptions')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Module')}</TableCell>
                            <TableCell>{t('Channel')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Threshold')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {subscriptions.length > 0 ? subscriptions.map((subscription) => (
                            <TableRow key={subscription.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{subscription.reconView}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{subscription.metricKey || t('All Metrics')}</Typography>
                                </TableCell>
                                <TableCell>
                                    <Chip size="small" label={subscription.channelType} sx={{fontWeight: 700}}/>
                                    {subscription.endpointUrl ? <Typography sx={{mt: 0.6, fontSize: '0.76rem', color: palette.textMuted, wordBreak: 'break-all'}}>{subscription.endpointUrl}</Typography> : null}
                                </TableCell>
                                <TableCell>{scopeLabel(subscription, t)}</TableCell>
                                <TableCell>{subscription.severityThreshold || t('All Severities')}</TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        <Button size="small" onClick={() => onEdit(subscription)} disabled={!canEdit || saving}>{t('Edit')}</Button>
                                        <Button size="small" color="error" onClick={() => onDelete(subscription.id)} disabled={!canEdit || saving}>{t('Delete')}</Button>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No personal subscriptions configured yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function DigestSubscriptionTable({subscriptions, palette, canEdit, onEdit, onDelete, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Daily Digests')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Digest')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Recipient')}</TableCell>
                            <TableCell>{t('Severity')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {subscriptions.length > 0 ? subscriptions.map((subscription) => (
                            <TableRow key={subscription.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.85rem', fontWeight: 700, color: palette.text}}>{subscription.digestName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{subscription.reconView}</Typography>
                                </TableCell>
                                <TableCell>{subscription.scopeType} | {subscription.scopeKey || t('All')}</TableCell>
                                <TableCell>{subscription.recipientType} | {subscription.recipientKey}</TableCell>
                                <TableCell>{subscription.severityThreshold || t('All')}</TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        <Button size="small" onClick={() => onEdit(subscription)} disabled={!canEdit || saving}>{t('Edit')}</Button>
                                        <Button size="small" color="error" onClick={() => onDelete(subscription.id)} disabled={!canEdit || saving}>{t('Delete')}</Button>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No daily digests configured yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function DigestRunTable({runs, palette, t}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Recent Digest Runs')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Subject')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Status')}</TableCell>
                            <TableCell>{t('Items')}</TableCell>
                            <TableCell>{t('Delivered')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {runs.length > 0 ? runs.map((run) => (
                            <TableRow key={run.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.84rem', fontWeight: 700, color: palette.text}}>{run.digestSubject}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{run.recipientSummary}</Typography>
                                </TableCell>
                                <TableCell>{run.scopeType} | {run.scopeKey || t('All')}</TableCell>
                                <TableCell><Chip size="small" label={run.runStatus} color={run.runStatus === 'FAILED' ? 'error' : run.runStatus === 'SENT' ? 'success' : 'default'} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>{run.itemCount}</TableCell>
                                <TableCell>{run.deliveredAt || run.createdAt || '-'}</TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No digest runs available yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function AnomalyRuleTable({rules, palette, canEdit, onEdit, onDelete, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Anomaly Rules')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Rule')}</TableCell>
                            <TableCell>{t('Pattern')}</TableCell>
                            <TableCell>{t('Baseline')}</TableCell>
                            <TableCell>{t('Severity')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {rules.length > 0 ? rules.map((rule) => (
                            <TableRow key={rule.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.85rem', fontWeight: 700, color: palette.text}}>{rule.ruleName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{rule.reconView} | {rule.metricKey}</Typography>
                                </TableCell>
                                <TableCell>{rule.anomalyType} | {rule.percentChangeThreshold}%</TableCell>
                                <TableCell>{t('Min')} {rule.minBaselineValue} | {rule.lookbackDays}d</TableCell>
                                <TableCell><Chip size="small" label={rule.severity} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        <Button size="small" onClick={() => onEdit(rule)} disabled={!canEdit || saving}>{t('Edit')}</Button>
                                        <Button size="small" color="error" onClick={() => onDelete(rule.id)} disabled={!canEdit || saving}>{t('Delete')}</Button>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No anomaly rules configured yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function SmsSubscriptionTable({subscriptions, palette, canEdit, onEdit, onDelete, t, saving}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('SMS Subscriptions')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Subscription')}</TableCell>
                            <TableCell>{t('Phone')}</TableCell>
                            <TableCell>{t('Scope')}</TableCell>
                            <TableCell>{t('Severity')}</TableCell>
                            <TableCell>{t('Actions')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {subscriptions.length > 0 ? subscriptions.map((subscription) => (
                            <TableRow key={subscription.id}>
                                <TableCell>
                                    <Typography sx={{fontSize: '0.85rem', fontWeight: 700, color: palette.text}}>{subscription.subscriptionName}</Typography>
                                    <Typography sx={{fontSize: '0.76rem', color: palette.textMuted}}>{subscription.reconView} | {subscription.metricKey || t('All Metrics')}</Typography>
                                </TableCell>
                                <TableCell>{subscription.phoneNumber}</TableCell>
                                <TableCell>{scopeLabel(subscription, t)}</TableCell>
                                <TableCell>{subscription.severityThreshold || t('All')}</TableCell>
                                <TableCell>
                                    <Stack direction="row" spacing={1}>
                                        <Button size="small" onClick={() => onEdit(subscription)} disabled={!canEdit || saving}>{t('Edit')}</Button>
                                        <Button size="small" color="error" onClick={() => onDelete(subscription.id)} disabled={!canEdit || saving}>{t('Delete')}</Button>
                                    </Stack>
                                </TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No SMS subscriptions configured yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function SmsDeliveryTable({deliveries, palette, t}) {
    return (
        <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
            <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>{t('Recent SMS Deliveries')}</Typography>
            <TableContainer>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>{t('Phone')}</TableCell>
                            <TableCell>{t('Provider')}</TableCell>
                            <TableCell>{t('Status')}</TableCell>
                            <TableCell>{t('HTTP')}</TableCell>
                            <TableCell>{t('Delivered')}</TableCell>
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {deliveries.length > 0 ? deliveries.map((delivery) => (
                            <TableRow key={delivery.id}>
                                <TableCell>{delivery.phoneNumber}</TableCell>
                                <TableCell>{delivery.providerName}</TableCell>
                                <TableCell><Chip size="small" label={delivery.deliveryStatus} color={delivery.deliveryStatus === 'FAILED' ? 'error' : delivery.deliveryStatus === 'SENT' ? 'success' : 'default'} sx={{fontWeight: 700}}/></TableCell>
                                <TableCell>{delivery.responseStatusCode || '-'}</TableCell>
                                <TableCell>{delivery.deliveredAt || delivery.lastAttemptAt || '-'}</TableCell>
                            </TableRow>
                        )) : <TableRow><TableCell colSpan={5}><Typography sx={{fontSize: '0.84rem', color: palette.textMuted}}>{t('No SMS delivery attempts available yet.')}</Typography></TableCell></TableRow>}
                    </TableBody>
                </Table>
            </TableContainer>
        </Paper>
    )
}

function SectionHeader({icon, title, subtitle, palette, tone = 'blue'}) {
    const colors = tone === 'purple'
        ? {bg: '#F3E8FF', color: '#7C3AED'}
        : {bg: palette.blueChipBg, color: palette.blueChipText}

    return (
        <Stack direction="row" spacing={1.25} alignItems="center" sx={{mb: 2}}>
            <Box sx={{width: 34, height: 34, borderRadius: 2.5, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: colors.bg, color: colors.color}}>
                {icon}
            </Box>
            <Box>
                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text}}>{title}</Typography>
                <Typography sx={{fontSize: '0.8rem', color: palette.textMuted}}>{subtitle}</Typography>
            </Box>
        </Stack>
    )
}

export default function Alerts({palette, t}) {
    const {hasPermission} = useAuth()
    const {moduleOptions: MODULE_OPTIONS} = useReconModules()
    const canEdit = hasPermission('ALERT_EDIT')
    const canManagePersonalSubscriptions = hasPermission('ALERT_VIEW')
    const [data, setData] = useState(null)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [feedback, setFeedback] = useState('')
    const [selectedReconView, setSelectedReconView] = useState('')
    const [saving, setSaving] = useState(false)
    const [editingRuleId, setEditingRuleId] = useState(null)
    const [editingSubscriptionId, setEditingSubscriptionId] = useState(null)
    const [editingWebhookSubscriptionId, setEditingWebhookSubscriptionId] = useState(null)
    const [editingEscalationPolicyId, setEditingEscalationPolicyId] = useState(null)
    const [editingPersonalSubscriptionId, setEditingPersonalSubscriptionId] = useState(null)
    const [editingDigestSubscriptionId, setEditingDigestSubscriptionId] = useState(null)
    const [editingAnomalyRuleId, setEditingAnomalyRuleId] = useState(null)
    const [editingSmsSubscriptionId, setEditingSmsSubscriptionId] = useState(null)
    const [ruleForm, setRuleForm] = useState(EMPTY_RULE)
    const [subscriptionForm, setSubscriptionForm] = useState(EMPTY_SUBSCRIPTION)
    const [webhookSubscriptionForm, setWebhookSubscriptionForm] = useState(EMPTY_WEBHOOK_SUBSCRIPTION)
    const [escalationPolicyForm, setEscalationPolicyForm] = useState(EMPTY_ESCALATION_POLICY)
    const [personalSubscriptionForm, setPersonalSubscriptionForm] = useState(EMPTY_PERSONAL_SUBSCRIPTION)
    const [digestSubscriptionForm, setDigestSubscriptionForm] = useState(EMPTY_DIGEST_SUBSCRIPTION)
    const [anomalyRuleForm, setAnomalyRuleForm] = useState(EMPTY_ANOMALY_RULE)
    const [smsSubscriptionForm, setSmsSubscriptionForm] = useState(EMPTY_SMS_SUBSCRIPTION)

    const filteredRules = useMemo(() => selectedReconView ? (data?.rules || []).filter((item) => item.reconView === selectedReconView) : (data?.rules || []), [data, selectedReconView])
    const filteredEvents = useMemo(() => selectedReconView ? (data?.events || []).filter((item) => item.reconView === selectedReconView) : (data?.events || []), [data, selectedReconView])
    const filteredSubscriptions = useMemo(() => selectedReconView ? (data?.subscriptions || []).filter((item) => item.reconView === selectedReconView) : (data?.subscriptions || []), [data, selectedReconView])
    const filteredDeliveries = useMemo(() => selectedReconView ? (data?.deliveries || []).filter((item) => item.reconView === selectedReconView) : (data?.deliveries || []), [data, selectedReconView])
    const filteredWebhookSubscriptions = useMemo(() => selectedReconView ? (data?.webhookSubscriptions || []).filter((item) => item.reconView === selectedReconView) : (data?.webhookSubscriptions || []), [data, selectedReconView])
    const filteredWebhookDeliveries = useMemo(() => selectedReconView ? (data?.webhookDeliveries || []).filter((item) => item.reconView === selectedReconView) : (data?.webhookDeliveries || []), [data, selectedReconView])
    const filteredEscalationPolicies = useMemo(() => selectedReconView ? (data?.escalationPolicies || []).filter((item) => item.reconView === selectedReconView) : (data?.escalationPolicies || []), [data, selectedReconView])
    const filteredEscalationHistory = useMemo(() => selectedReconView ? (data?.escalationHistory || []).filter((item) => item.reconView === selectedReconView) : (data?.escalationHistory || []), [data, selectedReconView])
    const filteredPersonalSubscriptions = useMemo(() => selectedReconView ? (data?.personalSubscriptions || []).filter((item) => item.reconView === selectedReconView) : (data?.personalSubscriptions || []), [data, selectedReconView])
    const filteredDigestSubscriptions = useMemo(() => selectedReconView ? (data?.digestSubscriptions || []).filter((item) => item.reconView === selectedReconView) : (data?.digestSubscriptions || []), [data, selectedReconView])
    const filteredDigestRuns = useMemo(() => selectedReconView ? (data?.digestRuns || []).filter((item) => item.reconView === selectedReconView) : (data?.digestRuns || []), [data, selectedReconView])
    const filteredAnomalyRules = useMemo(() => selectedReconView ? (data?.anomalyRules || []).filter((item) => item.reconView === selectedReconView) : (data?.anomalyRules || []), [data, selectedReconView])
    const filteredSmsSubscriptions = useMemo(() => selectedReconView ? (data?.smsSubscriptions || []).filter((item) => item.reconView === selectedReconView) : (data?.smsSubscriptions || []), [data, selectedReconView])
    const filteredSmsDeliveries = useMemo(() => selectedReconView ? (data?.smsDeliveries || []).filter((item) => item.reconView === selectedReconView) : (data?.smsDeliveries || []), [data, selectedReconView])

    const recipientHint = useMemo(() => {
        switch (subscriptionForm.recipientType) {
            case 'EMAIL':
                return t('Enter a direct email address such as ops@retailer.com')
            case 'USER':
                return t('Enter a RetailINQ username such as admin')
            default:
                return t('Enter a role name such as Admin')
        }
    }, [subscriptionForm.recipientType, t])

    const webhookHint = useMemo(() => {
        switch (webhookSubscriptionForm.channelType) {
            case 'MICROSOFT_TEAMS':
                return t('Enter the Microsoft Teams incoming webhook URL for the channel.')
            case 'SLACK':
                return t('Enter the Slack incoming webhook URL for the channel.')
            default:
                return t('Enter the target HTTPS endpoint that should receive RetailINQ alert JSON.')
        }
    }, [webhookSubscriptionForm.channelType, t])

    const personalSubscriptionHint = useMemo(() => {
        if (personalSubscriptionForm.channelType === 'EMAIL') {
            return t('Email subscriptions use your RetailINQ profile email address.')
        }
        if (personalSubscriptionForm.channelType === 'MICROSOFT_TEAMS') {
            return t('Enter your Microsoft Teams incoming webhook URL.')
        }
        if (personalSubscriptionForm.channelType === 'SLACK') {
            return t('Enter your Slack incoming webhook URL.')
        }
        return t('Enter the webhook endpoint that should receive your personal alert notifications.')
    }, [personalSubscriptionForm.channelType, t])

    const digestScopeHint = useMemo(() => {
        if (digestSubscriptionForm.scopeType === 'REGION') {
            return t('Enter a region or cluster key such as 10XX for store-prefix grouping.')
        }
        if (digestSubscriptionForm.scopeType === 'ROLE') {
            return t('Optional label for the audience, such as Store Ops or Regional Support.')
        }
        return t('Enter a store id, or leave blank to include all stores.')
    }, [digestSubscriptionForm.scopeType, t])

    const loadAlerts = async (reconView = '') => {
        try {
            setLoading(true)
            setError('')
            setData(await alertApi.getAlerts(reconView || null))
        } catch (err) {
            setError(err.message || 'Failed to load alerts')
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        loadAlerts('')
    }, [])

    const handleRuleForm = (key, value) => setRuleForm((current) => ({...current, [key]: value}))
    const handleSubscriptionForm = (key, value) => setSubscriptionForm((current) => ({...current, [key]: value}))
    const handleWebhookSubscriptionForm = (key, value) => setWebhookSubscriptionForm((current) => ({...current, [key]: value}))
    const handleEscalationPolicyForm = (key, value) => setEscalationPolicyForm((current) => ({...current, [key]: value}))
    const handlePersonalSubscriptionForm = (key, value) => setPersonalSubscriptionForm((current) => ({...current, [key]: value}))
    const handleDigestSubscriptionForm = (key, value) => setDigestSubscriptionForm((current) => ({...current, [key]: value}))
    const handleAnomalyRuleForm = (key, value) => setAnomalyRuleForm((current) => ({...current, [key]: value}))
    const handleSmsSubscriptionForm = (key, value) => setSmsSubscriptionForm((current) => ({...current, [key]: value}))

    const resetRuleForm = () => {
        setEditingRuleId(null)
        setRuleForm(EMPTY_RULE)
    }

    const resetSubscriptionForm = () => {
        setEditingSubscriptionId(null)
        setSubscriptionForm(EMPTY_SUBSCRIPTION)
    }

    const resetWebhookSubscriptionForm = () => {
        setEditingWebhookSubscriptionId(null)
        setWebhookSubscriptionForm(EMPTY_WEBHOOK_SUBSCRIPTION)
    }

    const resetEscalationPolicyForm = () => {
        setEditingEscalationPolicyId(null)
        setEscalationPolicyForm(EMPTY_ESCALATION_POLICY)
    }

    const resetPersonalSubscriptionForm = () => {
        setEditingPersonalSubscriptionId(null)
        setPersonalSubscriptionForm(EMPTY_PERSONAL_SUBSCRIPTION)
    }

    const resetDigestSubscriptionForm = () => {
        setEditingDigestSubscriptionId(null)
        setDigestSubscriptionForm(EMPTY_DIGEST_SUBSCRIPTION)
    }

    const resetAnomalyRuleForm = () => {
        setEditingAnomalyRuleId(null)
        setAnomalyRuleForm(EMPTY_ANOMALY_RULE)
    }

    const resetSmsSubscriptionForm = () => {
        setEditingSmsSubscriptionId(null)
        setSmsSubscriptionForm(EMPTY_SMS_SUBSCRIPTION)
    }

    const saveRule = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                ...ruleForm,
                thresholdValue: Number(ruleForm.thresholdValue),
                lookbackDays: Number(ruleForm.lookbackDays || 1),
                cooldownMinutes: Number(ruleForm.cooldownMinutes || 60),
            }
            const next = editingRuleId ? await alertApi.updateRule(editingRuleId, payload) : await alertApi.createRule(payload)
            setData(next)
            setFeedback(editingRuleId ? 'Alert rule updated.' : 'Alert rule created.')
            resetRuleForm()
        } catch (err) {
            setError(err.message || 'Failed to save alert rule')
        } finally {
            setSaving(false)
        }
    }

    const saveSubscription = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                ...subscriptionForm,
                metricKey: subscriptionForm.metricKey || null,
                severityThreshold: subscriptionForm.severityThreshold || null,
            }
            const next = editingSubscriptionId
                ? await alertApi.updateSubscription(editingSubscriptionId, payload)
                : await alertApi.createSubscription(payload)
            setData(next)
            setFeedback(editingSubscriptionId ? 'Email subscription updated.' : 'Email subscription created.')
            resetSubscriptionForm()
        } catch (err) {
            setError(err.message || 'Failed to save email subscription')
        } finally {
            setSaving(false)
        }
    }

    const saveWebhookSubscription = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                ...webhookSubscriptionForm,
                metricKey: webhookSubscriptionForm.metricKey || null,
                severityThreshold: webhookSubscriptionForm.severityThreshold || null,
            }
            const next = editingWebhookSubscriptionId
                ? await alertApi.updateWebhookSubscription(editingWebhookSubscriptionId, payload)
                : await alertApi.createWebhookSubscription(payload)
            setData(next)
            setFeedback(editingWebhookSubscriptionId ? 'Webhook subscription updated.' : 'Webhook subscription created.')
            resetWebhookSubscriptionForm()
        } catch (err) {
            setError(err.message || 'Failed to save webhook subscription')
        } finally {
            setSaving(false)
        }
    }

    const saveEscalationPolicy = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                ...escalationPolicyForm,
                metricKey: escalationPolicyForm.metricKey || null,
                severityThreshold: escalationPolicyForm.severityThreshold || null,
                escalationAfterMinutes: Number(escalationPolicyForm.escalationAfterMinutes || 60),
            }
            const next = editingEscalationPolicyId
                ? await alertApi.updateEscalationPolicy(editingEscalationPolicyId, payload)
                : await alertApi.createEscalationPolicy(payload)
            setData(next)
            setFeedback(editingEscalationPolicyId ? 'Escalation policy updated.' : 'Escalation policy created.')
            resetEscalationPolicyForm()
        } catch (err) {
            setError(err.message || 'Failed to save escalation policy')
        } finally {
            setSaving(false)
        }
    }

    const savePersonalSubscription = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                ...personalSubscriptionForm,
                metricKey: personalSubscriptionForm.metricKey || null,
                severityThreshold: personalSubscriptionForm.severityThreshold || null,
                endpointUrl: personalSubscriptionForm.channelType === 'EMAIL' ? null : (personalSubscriptionForm.endpointUrl || null),
            }
            const next = editingPersonalSubscriptionId
                ? await alertApi.updatePersonalSubscription(editingPersonalSubscriptionId, payload)
                : await alertApi.createPersonalSubscription(payload)
            setData(next)
            setFeedback(editingPersonalSubscriptionId ? 'Personal subscription updated.' : 'Personal subscription created.')
            resetPersonalSubscriptionForm()
        } catch (err) {
            setError(err.message || 'Failed to save personal subscription')
        } finally {
            setSaving(false)
        }
    }

    const saveDigestSubscription = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                ...digestSubscriptionForm,
                scopeKey: digestSubscriptionForm.scopeKey || null,
                severityThreshold: digestSubscriptionForm.severityThreshold || null,
            }
            const next = editingDigestSubscriptionId
                ? await alertApi.updateDigestSubscription(editingDigestSubscriptionId, payload)
                : await alertApi.createDigestSubscription(payload)
            setData(next)
            setFeedback(editingDigestSubscriptionId ? 'Daily digest updated.' : 'Daily digest created.')
            resetDigestSubscriptionForm()
        } catch (err) {
            setError(err.message || 'Failed to save daily digest')
        } finally {
            setSaving(false)
        }
    }

    const saveAnomalyRule = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                ...anomalyRuleForm,
                percentChangeThreshold: Number(anomalyRuleForm.percentChangeThreshold || 0),
                minBaselineValue: Number(anomalyRuleForm.minBaselineValue || 0),
                lookbackDays: Number(anomalyRuleForm.lookbackDays || 7),
                cooldownMinutes: Number(anomalyRuleForm.cooldownMinutes || 180),
                storeId: anomalyRuleForm.storeId || null,
            }
            const next = editingAnomalyRuleId
                ? await alertApi.updateAnomalyRule(editingAnomalyRuleId, payload)
                : await alertApi.createAnomalyRule(payload)
            setData(next)
            setFeedback(editingAnomalyRuleId ? 'Anomaly rule updated.' : 'Anomaly rule created.')
            resetAnomalyRuleForm()
        } catch (err) {
            setError(err.message || 'Failed to save anomaly rule')
        } finally {
            setSaving(false)
        }
    }

    const saveSmsSubscription = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            const payload = {
                ...smsSubscriptionForm,
                metricKey: smsSubscriptionForm.metricKey || null,
                severityThreshold: smsSubscriptionForm.severityThreshold || null,
            }
            const next = editingSmsSubscriptionId
                ? await alertApi.updateSmsSubscription(editingSmsSubscriptionId, payload)
                : await alertApi.createSmsSubscription(payload)
            setData(next)
            setFeedback(editingSmsSubscriptionId ? 'SMS subscription updated.' : 'SMS subscription created.')
            resetSmsSubscriptionForm()
        } catch (err) {
            setError(err.message || 'Failed to save SMS subscription')
        } finally {
            setSaving(false)
        }
    }

    const editRule = (rule) => {
        setEditingRuleId(rule.id)
        setRuleForm({...EMPTY_RULE, ...rule})
    }

    const editSubscription = (subscription) => {
        setEditingSubscriptionId(subscription.id)
        setSubscriptionForm({
            ...EMPTY_SUBSCRIPTION,
            ...subscription,
            metricKey: subscription.metricKey || '',
            severityThreshold: subscription.severityThreshold || '',
        })
    }

    const editWebhookSubscription = (subscription) => {
        setEditingWebhookSubscriptionId(subscription.id)
        setWebhookSubscriptionForm({
            ...EMPTY_WEBHOOK_SUBSCRIPTION,
            ...subscription,
            metricKey: subscription.metricKey || '',
            severityThreshold: subscription.severityThreshold || '',
        })
    }

    const editEscalationPolicy = (policy) => {
        setEditingEscalationPolicyId(policy.id)
        setEscalationPolicyForm({
            ...EMPTY_ESCALATION_POLICY,
            ...policy,
            metricKey: policy.metricKey || '',
            severityThreshold: policy.severityThreshold || '',
        })
    }

    const editPersonalSubscription = (subscription) => {
        setEditingPersonalSubscriptionId(subscription.id)
        setPersonalSubscriptionForm({
            ...EMPTY_PERSONAL_SUBSCRIPTION,
            ...subscription,
            metricKey: subscription.metricKey || '',
            severityThreshold: subscription.severityThreshold || '',
            endpointUrl: subscription.endpointUrl || '',
        })
    }

    const editDigestSubscription = (subscription) => {
        setEditingDigestSubscriptionId(subscription.id)
        setDigestSubscriptionForm({
            ...EMPTY_DIGEST_SUBSCRIPTION,
            ...subscription,
            scopeKey: subscription.scopeKey || '',
            severityThreshold: subscription.severityThreshold || '',
        })
    }

    const editAnomalyRule = (rule) => {
        setEditingAnomalyRuleId(rule.id)
        setAnomalyRuleForm({
            ...EMPTY_ANOMALY_RULE,
            ...rule,
            storeId: rule.storeId || '',
        })
    }

    const editSmsSubscription = (subscription) => {
        setEditingSmsSubscriptionId(subscription.id)
        setSmsSubscriptionForm({
            ...EMPTY_SMS_SUBSCRIPTION,
            ...subscription,
            metricKey: subscription.metricKey || '',
            severityThreshold: subscription.severityThreshold || '',
        })
    }

    const deleteRule = async (ruleId) => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.deleteRule(ruleId))
            setFeedback('Alert rule deleted.')
            if (editingRuleId === ruleId) {
                resetRuleForm()
            }
        } catch (err) {
            setError(err.message || 'Failed to delete alert rule')
        } finally {
            setSaving(false)
        }
    }

    const deleteSubscription = async (subscriptionId) => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.deleteSubscription(subscriptionId))
            setFeedback('Email subscription deleted.')
            if (editingSubscriptionId === subscriptionId) {
                resetSubscriptionForm()
            }
        } catch (err) {
            setError(err.message || 'Failed to delete email subscription')
        } finally {
            setSaving(false)
        }
    }

    const deleteWebhookSubscription = async (subscriptionId) => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.deleteWebhookSubscription(subscriptionId))
            setFeedback('Webhook subscription deleted.')
            if (editingWebhookSubscriptionId === subscriptionId) {
                resetWebhookSubscriptionForm()
            }
        } catch (err) {
            setError(err.message || 'Failed to delete webhook subscription')
        } finally {
            setSaving(false)
        }
    }

    const deleteEscalationPolicy = async (policyId) => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.deleteEscalationPolicy(policyId))
            setFeedback('Escalation policy deleted.')
            if (editingEscalationPolicyId === policyId) {
                resetEscalationPolicyForm()
            }
        } catch (err) {
            setError(err.message || 'Failed to delete escalation policy')
        } finally {
            setSaving(false)
        }
    }

    const deletePersonalSubscription = async (subscriptionId) => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.deletePersonalSubscription(subscriptionId))
            setFeedback('Personal subscription deleted.')
            if (editingPersonalSubscriptionId === subscriptionId) {
                resetPersonalSubscriptionForm()
            }
        } catch (err) {
            setError(err.message || 'Failed to delete personal subscription')
        } finally {
            setSaving(false)
        }
    }

    const deleteDigestSubscription = async (subscriptionId) => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.deleteDigestSubscription(subscriptionId))
            setFeedback('Daily digest deleted.')
            if (editingDigestSubscriptionId === subscriptionId) {
                resetDigestSubscriptionForm()
            }
        } catch (err) {
            setError(err.message || 'Failed to delete daily digest')
        } finally {
            setSaving(false)
        }
    }

    const deleteAnomalyRule = async (ruleId) => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.deleteAnomalyRule(ruleId))
            setFeedback('Anomaly rule deleted.')
            if (editingAnomalyRuleId === ruleId) {
                resetAnomalyRuleForm()
            }
        } catch (err) {
            setError(err.message || 'Failed to delete anomaly rule')
        } finally {
            setSaving(false)
        }
    }

    const deleteSmsSubscription = async (subscriptionId) => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.deleteSmsSubscription(subscriptionId))
            setFeedback('SMS subscription deleted.')
            if (editingSmsSubscriptionId === subscriptionId) {
                resetSmsSubscriptionForm()
            }
        } catch (err) {
            setError(err.message || 'Failed to delete SMS subscription')
        } finally {
            setSaving(false)
        }
    }

    const runDigestsNow = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.runDigestsNow())
            setFeedback('Daily digests executed.')
        } catch (err) {
            setError(err.message || 'Failed to run digests')
        } finally {
            setSaving(false)
        }
    }

    const runAnomalyDetectionNow = async () => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.runAnomalyDetectionNow())
            setFeedback('Anomaly scan executed.')
        } catch (err) {
            setError(err.message || 'Failed to run anomaly detection')
        } finally {
            setSaving(false)
        }
    }

    const updateEvent = async (eventId, status) => {
        try {
            setSaving(true)
            setError('')
            setFeedback('')
            setData(await alertApi.updateEventStatus(eventId, status))
            setFeedback(status === 'ACKNOWLEDGED' ? 'Alert acknowledged.' : 'Alert resolved.')
        } catch (err) {
            setError(err.message || 'Failed to update alert event')
        } finally {
            setSaving(false)
        }
    }

    return (
        <Box sx={{px: 1, py: 3}}>
            <Paper elevation={0} sx={{p: 3, mb: 3, borderRadius: '24px', border: `1px solid ${palette.border}`, background: palette.heroBg}}>
                <Box sx={{display: 'flex', alignItems: 'flex-start', gap: 1.5}}>
                    <Box sx={{mt: 0.3, width: 40, height: 40, borderRadius: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: '#FEF2F2', color: '#DC2626', flexShrink: 0}}>
                        <NotificationsActiveRoundedIcon sx={{fontSize: 20}}/>
                    </Box>
                    <Box>
                        <Typography sx={{fontSize: '1.35rem', fontWeight: 800, color: palette.text}}>{t('Alerts')}</Typography>
                        <Typography sx={{mt: 0.5, fontSize: '0.92rem', color: palette.textMuted, maxWidth: 920}}>
                            {t('Define threshold-based alerts, route them as in-app events, and deliver notifications through email, generic webhooks, Microsoft Teams, or Slack channels.')}
                        </Typography>
                    </Box>
                </Box>
            </Paper>

            {feedback ? <Alert severity="success" sx={{mb: 2}}>{feedback}</Alert> : null}
            {error ? <Alert severity="error" sx={{mb: 2}}>{error}</Alert> : null}

            {loading ? <Box sx={{display: 'flex', justifyContent: 'center', py: 8}}><CircularProgress/></Box> : (
                <>
                    <Grid container spacing={2} sx={{mb: 3}}>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Active Rules')} value={data?.summary?.activeRules || 0} tone="blue" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Open Alerts')} value={data?.summary?.openEvents || 0} tone="red" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Critical Alerts')} value={data?.summary?.criticalEvents || 0} tone="orange" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Open Anomalies')} value={data?.summary?.openAnomalyEvents || 0} tone="orange" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Daily Digests')} value={data?.summary?.activeDigestSubscriptions || 0} tone="teal" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('SMS Subscriptions')} value={data?.summary?.activeSmsSubscriptions || 0} tone="purple" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Escalation Policies')} value={data?.summary?.activeEscalationPolicies || 0} tone="purple" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Escalated Events')} value={data?.summary?.escalatedEvents || 0} tone="teal" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('My Subscriptions')} value={data?.summary?.activePersonalSubscriptions || 0} tone="blue" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Email Subscriptions')} value={data?.summary?.activeSubscriptions || 0} tone="purple" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Webhook Subscriptions')} value={data?.summary?.activeWebhookSubscriptions || 0} tone="teal" palette={palette}/></Grid>
                        <Grid size={{xs: 12, md: 6, xl: 2}}><SummaryCard label={t('Failed Notifications')} value={data?.summary?.failedNotificationDeliveries || 0} tone="red" palette={palette}/></Grid>
                    </Grid>

                    <Paper elevation={0} sx={{p: 2, mb: 3, borderRadius: '20px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                        <Stack direction={{xs: 'column', md: 'row'}} spacing={1.5} justifyContent="space-between">
                            <FormControl size="small" sx={{minWidth: 260}}>
                                <InputLabel>{t('Module')}</InputLabel>
                                <Select value={selectedReconView} label={t('Module')} onChange={(event) => setSelectedReconView(event.target.value)}>
                                    <MenuItem value="">{t('All Modules')}</MenuItem>
                                    {MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}
                                </Select>
                            </FormControl>
                            <Stack direction={{xs: 'column', sm: 'row'}} spacing={1}>
                                <Button variant="outlined" onClick={runDigestsNow} disabled={!canEdit || saving}>{t('Run Digests Now')}</Button>
                                <Button variant="outlined" onClick={runAnomalyDetectionNow} disabled={!canEdit || saving}>{t('Run Anomaly Scan')}</Button>
                            </Stack>
                        </Stack>
                    </Paper>

                    <Grid container spacing={2}>
                        <Grid size={{xs: 12, lg: 5}}>
                            <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <Typography sx={{fontSize: '1rem', fontWeight: 800, color: palette.text, mb: 2}}>
                                    {editingRuleId ? t('Edit Alert Rule') : t('Create Alert Rule')}
                                </Typography>
                                <Stack spacing={1.5}>
                                    <TextField label={t('Rule Name')} value={ruleForm.ruleName} onChange={(event) => handleRuleForm('ruleName', event.target.value)} disabled={!canEdit || saving}/>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Module')}</InputLabel><Select value={ruleForm.reconView} label={t('Module')} onChange={(event) => handleRuleForm('reconView', event.target.value)} disabled={!canEdit || saving}>{MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Metric')}</InputLabel><Select value={ruleForm.metricKey} label={t('Metric')} onChange={(event) => handleRuleForm('metricKey', event.target.value)} disabled={!canEdit || saving}>{METRIC_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Operator')}</InputLabel><Select value={ruleForm.operator} label={t('Operator')} onChange={(event) => handleRuleForm('operator', event.target.value)} disabled={!canEdit || saving}>{['>', '>=', '<', '<='].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                        <TextField fullWidth label={t('Threshold')} type="number" value={ruleForm.thresholdValue} onChange={(event) => handleRuleForm('thresholdValue', event.target.value)} disabled={!canEdit || saving}/>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Severity')}</InputLabel><Select value={ruleForm.severity} label={t('Severity')} onChange={(event) => handleRuleForm('severity', event.target.value)} disabled={!canEdit || saving}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                        <TextField fullWidth label={t('Lookback Days')} type="number" value={ruleForm.lookbackDays} onChange={(event) => handleRuleForm('lookbackDays', event.target.value)} disabled={!canEdit || saving}/>
                                    </Box>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <TextField fullWidth label={t('Store Id')} value={ruleForm.storeId} onChange={(event) => handleRuleForm('storeId', event.target.value)} disabled={!canEdit || saving}/>
                                        <TextField fullWidth label={t('Register Id')} value={ruleForm.wkstnId} onChange={(event) => handleRuleForm('wkstnId', event.target.value)} disabled={!canEdit || saving}/>
                                        <TextField fullWidth label={t('Cooldown Minutes')} type="number" value={ruleForm.cooldownMinutes} onChange={(event) => handleRuleForm('cooldownMinutes', event.target.value)} disabled={!canEdit || saving}/>
                                    </Box>
                                    <TextField label={t('Description')} multiline minRows={2} value={ruleForm.description} onChange={(event) => handleRuleForm('description', event.target.value)} disabled={!canEdit || saving}/>
                                    <FormControl fullWidth size="small"><InputLabel>{t('Status')}</InputLabel><Select value={ruleForm.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handleRuleForm('active', event.target.value === 'ACTIVE')} disabled={!canEdit || saving}><MenuItem value="ACTIVE">{t('Active')}</MenuItem><MenuItem value="INACTIVE">{t('Inactive')}</MenuItem></Select></FormControl>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={saveRule} disabled={!canEdit || saving || !String(ruleForm.ruleName).trim() || ruleForm.thresholdValue === ''}>{editingRuleId ? t('Update Rule') : t('Create Rule')}</Button>
                                        <Button variant="outlined" onClick={resetRuleForm} disabled={saving}>{t('Clear')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>

                            <Paper elevation={0} sx={{p: 2.5, mb: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <SectionHeader
                                    icon={<EmailOutlinedIcon sx={{fontSize: 18}}/>}
                                    title={editingSubscriptionId ? t('Edit Email Subscription') : t('Create Email Subscription')}
                                    subtitle={t('Send alert emails to a user, role, or direct email address when matching events open or repeat.')}
                                    palette={palette}
                                />
                                <Stack spacing={1.5}>
                                    <TextField label={t('Subscription Name')} value={subscriptionForm.subscriptionName} onChange={(event) => handleSubscriptionForm('subscriptionName', event.target.value)} disabled={!canEdit || saving}/>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Module')}</InputLabel><Select value={subscriptionForm.reconView} label={t('Module')} onChange={(event) => handleSubscriptionForm('reconView', event.target.value)} disabled={!canEdit || saving}>{MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Recipient Type')}</InputLabel><Select value={subscriptionForm.recipientType} label={t('Recipient Type')} onChange={(event) => handleSubscriptionForm('recipientType', event.target.value)} disabled={!canEdit || saving}>{RECIPIENT_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Metric')}</InputLabel><Select value={subscriptionForm.metricKey} label={t('Metric')} onChange={(event) => handleSubscriptionForm('metricKey', event.target.value)} disabled={!canEdit || saving}><MenuItem value="">{t('All Metrics')}</MenuItem>{METRIC_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Severity Threshold')}</InputLabel><Select value={subscriptionForm.severityThreshold} label={t('Severity Threshold')} onChange={(event) => handleSubscriptionForm('severityThreshold', event.target.value)} disabled={!canEdit || saving}><MenuItem value="">{t('All Severities')}</MenuItem>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                    </Box>
                                    <TextField label={t('Recipient Value')} value={subscriptionForm.recipientKey} onChange={(event) => handleSubscriptionForm('recipientKey', event.target.value)} disabled={!canEdit || saving} helperText={recipientHint}/>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <TextField fullWidth label={t('Store Id')} value={subscriptionForm.storeId} onChange={(event) => handleSubscriptionForm('storeId', event.target.value)} disabled={!canEdit || saving}/>
                                        <TextField fullWidth label={t('Register Id')} value={subscriptionForm.wkstnId} onChange={(event) => handleSubscriptionForm('wkstnId', event.target.value)} disabled={!canEdit || saving}/>
                                    </Box>
                                    <TextField label={t('Description')} multiline minRows={2} value={subscriptionForm.description} onChange={(event) => handleSubscriptionForm('description', event.target.value)} disabled={!canEdit || saving}/>
                                    <FormControl fullWidth size="small"><InputLabel>{t('Status')}</InputLabel><Select value={subscriptionForm.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handleSubscriptionForm('active', event.target.value === 'ACTIVE')} disabled={!canEdit || saving}><MenuItem value="ACTIVE">{t('Active')}</MenuItem><MenuItem value="INACTIVE">{t('Inactive')}</MenuItem></Select></FormControl>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={saveSubscription} disabled={!canEdit || saving || !String(subscriptionForm.subscriptionName).trim() || !String(subscriptionForm.recipientKey).trim()}>{editingSubscriptionId ? t('Update Subscription') : t('Create Subscription')}</Button>
                                        <Button variant="outlined" onClick={resetSubscriptionForm} disabled={saving}>{t('Clear')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>

                            <Paper elevation={0} sx={{p: 2.5, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <SectionHeader
                                    icon={<SettingsEthernetRoundedIcon sx={{fontSize: 18}}/>}
                                    title={editingWebhookSubscriptionId ? t('Edit Webhook Subscription') : t('Create Webhook Subscription')}
                                    subtitle={t('Route alerts to generic webhooks, Microsoft Teams, or Slack channels through inbound webhook endpoints.')}
                                    palette={palette}
                                    tone="purple"
                                />
                                <Stack spacing={1.5}>
                                    <TextField label={t('Subscription Name')} value={webhookSubscriptionForm.subscriptionName} onChange={(event) => handleWebhookSubscriptionForm('subscriptionName', event.target.value)} disabled={!canEdit || saving}/>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Module')}</InputLabel><Select value={webhookSubscriptionForm.reconView} label={t('Module')} onChange={(event) => handleWebhookSubscriptionForm('reconView', event.target.value)} disabled={!canEdit || saving}>{MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Channel')}</InputLabel><Select value={webhookSubscriptionForm.channelType} label={t('Channel')} onChange={(event) => handleWebhookSubscriptionForm('channelType', event.target.value)} disabled={!canEdit || saving}>{WEBHOOK_CHANNEL_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Metric')}</InputLabel><Select value={webhookSubscriptionForm.metricKey} label={t('Metric')} onChange={(event) => handleWebhookSubscriptionForm('metricKey', event.target.value)} disabled={!canEdit || saving}><MenuItem value="">{t('All Metrics')}</MenuItem>{METRIC_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Severity Threshold')}</InputLabel><Select value={webhookSubscriptionForm.severityThreshold} label={t('Severity Threshold')} onChange={(event) => handleWebhookSubscriptionForm('severityThreshold', event.target.value)} disabled={!canEdit || saving}><MenuItem value="">{t('All Severities')}</MenuItem>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                    </Box>
                                    <TextField label={t('Endpoint URL')} value={webhookSubscriptionForm.endpointUrl} onChange={(event) => handleWebhookSubscriptionForm('endpointUrl', event.target.value)} disabled={!canEdit || saving} helperText={webhookHint}/>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <TextField fullWidth label={t('Store Id')} value={webhookSubscriptionForm.storeId} onChange={(event) => handleWebhookSubscriptionForm('storeId', event.target.value)} disabled={!canEdit || saving}/>
                                        <TextField fullWidth label={t('Register Id')} value={webhookSubscriptionForm.wkstnId} onChange={(event) => handleWebhookSubscriptionForm('wkstnId', event.target.value)} disabled={!canEdit || saving}/>
                                    </Box>
                                    <TextField label={t('Description')} multiline minRows={2} value={webhookSubscriptionForm.description} onChange={(event) => handleWebhookSubscriptionForm('description', event.target.value)} disabled={!canEdit || saving}/>
                                    <FormControl fullWidth size="small"><InputLabel>{t('Status')}</InputLabel><Select value={webhookSubscriptionForm.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handleWebhookSubscriptionForm('active', event.target.value === 'ACTIVE')} disabled={!canEdit || saving}><MenuItem value="ACTIVE">{t('Active')}</MenuItem><MenuItem value="INACTIVE">{t('Inactive')}</MenuItem></Select></FormControl>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={saveWebhookSubscription} disabled={!canEdit || saving || !String(webhookSubscriptionForm.subscriptionName).trim() || !String(webhookSubscriptionForm.endpointUrl).trim()}>{editingWebhookSubscriptionId ? t('Update Subscription') : t('Create Subscription')}</Button>
                                        <Button variant="outlined" onClick={resetWebhookSubscriptionForm} disabled={saving}>{t('Clear')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>

                            <Paper elevation={0} sx={{p: 2.5, mt: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <SectionHeader
                                    icon={<NotificationsActiveRoundedIcon sx={{fontSize: 18}}/>}
                                    title={editingEscalationPolicyId ? t('Edit Escalation Policy') : t('Create Escalation Policy')}
                                    subtitle={t('Escalate unresolved open alerts to users, roles, email addresses, or webhook channels after a defined time threshold.')}
                                    palette={palette}
                                />
                                <Stack spacing={1.5}>
                                    <TextField label={t('Policy Name')} value={escalationPolicyForm.policyName} onChange={(event) => handleEscalationPolicyForm('policyName', event.target.value)} disabled={!canEdit || saving}/>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Module')}</InputLabel><Select value={escalationPolicyForm.reconView} label={t('Module')} onChange={(event) => handleEscalationPolicyForm('reconView', event.target.value)} disabled={!canEdit || saving}>{MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Metric')}</InputLabel><Select value={escalationPolicyForm.metricKey} label={t('Metric')} onChange={(event) => handleEscalationPolicyForm('metricKey', event.target.value)} disabled={!canEdit || saving}><MenuItem value="">{t('All Metrics')}</MenuItem>{METRIC_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Severity Threshold')}</InputLabel><Select value={escalationPolicyForm.severityThreshold} label={t('Severity Threshold')} onChange={(event) => handleEscalationPolicyForm('severityThreshold', event.target.value)} disabled={!canEdit || saving}><MenuItem value="">{t('All Severities')}</MenuItem>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                        <TextField fullWidth label={t('Escalate After (min)')} type="number" value={escalationPolicyForm.escalationAfterMinutes} onChange={(event) => handleEscalationPolicyForm('escalationAfterMinutes', event.target.value)} disabled={!canEdit || saving}/>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Destination Type')}</InputLabel><Select value={escalationPolicyForm.destinationType} label={t('Destination Type')} onChange={(event) => handleEscalationPolicyForm('destinationType', event.target.value)} disabled={!canEdit || saving}>{['USER', 'ROLE', 'EMAIL', 'GENERIC_WEBHOOK', 'MICROSOFT_TEAMS', 'SLACK'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                        <TextField fullWidth label={t('Destination Value')} value={escalationPolicyForm.destinationKey} onChange={(event) => handleEscalationPolicyForm('destinationKey', event.target.value)} disabled={!canEdit || saving}/>
                                    </Box>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <TextField fullWidth label={t('Store Id')} value={escalationPolicyForm.storeId} onChange={(event) => handleEscalationPolicyForm('storeId', event.target.value)} disabled={!canEdit || saving}/>
                                        <TextField fullWidth label={t('Register Id')} value={escalationPolicyForm.wkstnId} onChange={(event) => handleEscalationPolicyForm('wkstnId', event.target.value)} disabled={!canEdit || saving}/>
                                    </Box>
                                    <TextField label={t('Description')} multiline minRows={2} value={escalationPolicyForm.description} onChange={(event) => handleEscalationPolicyForm('description', event.target.value)} disabled={!canEdit || saving}/>
                                    <FormControl fullWidth size="small"><InputLabel>{t('Status')}</InputLabel><Select value={escalationPolicyForm.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handleEscalationPolicyForm('active', event.target.value === 'ACTIVE')} disabled={!canEdit || saving}><MenuItem value="ACTIVE">{t('Active')}</MenuItem><MenuItem value="INACTIVE">{t('Inactive')}</MenuItem></Select></FormControl>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={saveEscalationPolicy} disabled={!canEdit || saving || !String(escalationPolicyForm.policyName).trim() || !String(escalationPolicyForm.destinationKey).trim()}>{editingEscalationPolicyId ? t('Update Policy') : t('Create Policy')}</Button>
                                        <Button variant="outlined" onClick={resetEscalationPolicyForm} disabled={saving}>{t('Clear')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>

                            <Paper elevation={0} sx={{p: 2.5, mt: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <SectionHeader
                                    icon={<EmailOutlinedIcon sx={{fontSize: 18}}/>}
                                    title={editingPersonalSubscriptionId ? t('Edit My Subscription') : t('Create My Subscription')}
                                    subtitle={t('Create personal alert subscriptions for your own email or webhook channels without changing shared team-level notification rules.')}
                                    palette={palette}
                                    tone="purple"
                                />
                                <Stack spacing={1.5}>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Module')}</InputLabel><Select value={personalSubscriptionForm.reconView} label={t('Module')} onChange={(event) => handlePersonalSubscriptionForm('reconView', event.target.value)} disabled={!canManagePersonalSubscriptions || saving}>{MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Channel')}</InputLabel><Select value={personalSubscriptionForm.channelType} label={t('Channel')} onChange={(event) => handlePersonalSubscriptionForm('channelType', event.target.value)} disabled={!canManagePersonalSubscriptions || saving}>{PERSONAL_CHANNEL_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Metric')}</InputLabel><Select value={personalSubscriptionForm.metricKey} label={t('Metric')} onChange={(event) => handlePersonalSubscriptionForm('metricKey', event.target.value)} disabled={!canManagePersonalSubscriptions || saving}><MenuItem value="">{t('All Metrics')}</MenuItem>{METRIC_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Severity Threshold')}</InputLabel><Select value={personalSubscriptionForm.severityThreshold} label={t('Severity Threshold')} onChange={(event) => handlePersonalSubscriptionForm('severityThreshold', event.target.value)} disabled={!canManagePersonalSubscriptions || saving}><MenuItem value="">{t('All Severities')}</MenuItem>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                    </Box>
                                    {personalSubscriptionForm.channelType !== 'EMAIL' ? (
                                        <TextField label={t('Endpoint URL')} value={personalSubscriptionForm.endpointUrl} onChange={(event) => handlePersonalSubscriptionForm('endpointUrl', event.target.value)} disabled={!canManagePersonalSubscriptions || saving} helperText={personalSubscriptionHint}/>
                                    ) : (
                                        <Alert severity="info">{personalSubscriptionHint}</Alert>
                                    )}
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <TextField fullWidth label={t('Store Id')} value={personalSubscriptionForm.storeId} onChange={(event) => handlePersonalSubscriptionForm('storeId', event.target.value)} disabled={!canManagePersonalSubscriptions || saving}/>
                                        <TextField fullWidth label={t('Register Id')} value={personalSubscriptionForm.wkstnId} onChange={(event) => handlePersonalSubscriptionForm('wkstnId', event.target.value)} disabled={!canManagePersonalSubscriptions || saving}/>
                                    </Box>
                                    <TextField label={t('Description')} multiline minRows={2} value={personalSubscriptionForm.description} onChange={(event) => handlePersonalSubscriptionForm('description', event.target.value)} disabled={!canManagePersonalSubscriptions || saving}/>
                                    <FormControl fullWidth size="small"><InputLabel>{t('Status')}</InputLabel><Select value={personalSubscriptionForm.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handlePersonalSubscriptionForm('active', event.target.value === 'ACTIVE')} disabled={!canManagePersonalSubscriptions || saving}><MenuItem value="ACTIVE">{t('Active')}</MenuItem><MenuItem value="INACTIVE">{t('Inactive')}</MenuItem></Select></FormControl>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={savePersonalSubscription} disabled={!canManagePersonalSubscriptions || saving || (personalSubscriptionForm.channelType !== 'EMAIL' && !String(personalSubscriptionForm.endpointUrl).trim())}>{editingPersonalSubscriptionId ? t('Update Subscription') : t('Create Subscription')}</Button>
                                        <Button variant="outlined" onClick={resetPersonalSubscriptionForm} disabled={saving}>{t('Clear')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>

                            <Paper elevation={0} sx={{p: 2.5, mt: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <SectionHeader
                                    icon={<EmailOutlinedIcon sx={{fontSize: 18}}/>}
                                    title={editingDigestSubscriptionId ? t('Edit Daily Digest') : t('Create Daily Digest')}
                                    subtitle={t('Send a once-daily alert digest for a role, store, or region so teams start the day with the right risk summary.')}
                                    palette={palette}
                                />
                                <Stack spacing={1.5}>
                                    <TextField label={t('Digest Name')} value={digestSubscriptionForm.digestName} onChange={(event) => handleDigestSubscriptionForm('digestName', event.target.value)} disabled={!canEdit || saving}/>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Module')}</InputLabel><Select value={digestSubscriptionForm.reconView} label={t('Module')} onChange={(event) => handleDigestSubscriptionForm('reconView', event.target.value)} disabled={!canEdit || saving}>{MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Scope')}</InputLabel><Select value={digestSubscriptionForm.scopeType} label={t('Scope')} onChange={(event) => handleDigestSubscriptionForm('scopeType', event.target.value)} disabled={!canEdit || saving}>{DIGEST_SCOPE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Recipient Type')}</InputLabel><Select value={digestSubscriptionForm.recipientType} label={t('Recipient Type')} onChange={(event) => handleDigestSubscriptionForm('recipientType', event.target.value)} disabled={!canEdit || saving}>{RECIPIENT_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Severity Threshold')}</InputLabel><Select value={digestSubscriptionForm.severityThreshold} label={t('Severity Threshold')} onChange={(event) => handleDigestSubscriptionForm('severityThreshold', event.target.value)} disabled={!canEdit || saving}><MenuItem value="">{t('All Severities')}</MenuItem>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                    </Box>
                                    <TextField label={t('Scope Key')} value={digestSubscriptionForm.scopeKey} onChange={(event) => handleDigestSubscriptionForm('scopeKey', event.target.value)} disabled={!canEdit || saving} helperText={digestScopeHint}/>
                                    <TextField label={t('Recipient Value')} value={digestSubscriptionForm.recipientKey} onChange={(event) => handleDigestSubscriptionForm('recipientKey', event.target.value)} disabled={!canEdit || saving} helperText={recipientHint}/>
                                    <TextField label={t('Description')} multiline minRows={2} value={digestSubscriptionForm.description} onChange={(event) => handleDigestSubscriptionForm('description', event.target.value)} disabled={!canEdit || saving}/>
                                    <FormControl fullWidth size="small"><InputLabel>{t('Status')}</InputLabel><Select value={digestSubscriptionForm.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handleDigestSubscriptionForm('active', event.target.value === 'ACTIVE')} disabled={!canEdit || saving}><MenuItem value="ACTIVE">{t('Active')}</MenuItem><MenuItem value="INACTIVE">{t('Inactive')}</MenuItem></Select></FormControl>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={saveDigestSubscription} disabled={!canEdit || saving || !String(digestSubscriptionForm.digestName).trim() || !String(digestSubscriptionForm.recipientKey).trim()}>{editingDigestSubscriptionId ? t('Update Digest') : t('Create Digest')}</Button>
                                        <Button variant="outlined" onClick={resetDigestSubscriptionForm} disabled={saving}>{t('Clear')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>

                            <Paper elevation={0} sx={{p: 2.5, mt: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <SectionHeader
                                    icon={<NotificationsActiveRoundedIcon sx={{fontSize: 18}}/>}
                                    title={editingAnomalyRuleId ? t('Edit Anomaly Rule') : t('Create Anomaly Rule')}
                                    subtitle={t('Detect sudden spikes and drop-offs against recent operating baseline instead of only fixed thresholds.')}
                                    palette={palette}
                                />
                                <Stack spacing={1.5}>
                                    <TextField label={t('Rule Name')} value={anomalyRuleForm.ruleName} onChange={(event) => handleAnomalyRuleForm('ruleName', event.target.value)} disabled={!canEdit || saving}/>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Module')}</InputLabel><Select value={anomalyRuleForm.reconView} label={t('Module')} onChange={(event) => handleAnomalyRuleForm('reconView', event.target.value)} disabled={!canEdit || saving}>{MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Metric')}</InputLabel><Select value={anomalyRuleForm.metricKey} label={t('Metric')} onChange={(event) => handleAnomalyRuleForm('metricKey', event.target.value)} disabled={!canEdit || saving}>{METRIC_OPTIONS.filter((option) => option.value !== 'OPEN_EXCEPTIONS_7_PLUS').map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Pattern')}</InputLabel><Select value={anomalyRuleForm.anomalyType} label={t('Pattern')} onChange={(event) => handleAnomalyRuleForm('anomalyType', event.target.value)} disabled={!canEdit || saving}>{ANOMALY_TYPE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{t(option.label)}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Severity')}</InputLabel><Select value={anomalyRuleForm.severity} label={t('Severity')} onChange={(event) => handleAnomalyRuleForm('severity', event.target.value)} disabled={!canEdit || saving}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                        <TextField fullWidth label={t('Change % Threshold')} type="number" value={anomalyRuleForm.percentChangeThreshold} onChange={(event) => handleAnomalyRuleForm('percentChangeThreshold', event.target.value)} disabled={!canEdit || saving}/>
                                        <TextField fullWidth label={t('Min Baseline Value')} type="number" value={anomalyRuleForm.minBaselineValue} onChange={(event) => handleAnomalyRuleForm('minBaselineValue', event.target.value)} disabled={!canEdit || saving}/>
                                        <TextField fullWidth label={t('Lookback Days')} type="number" value={anomalyRuleForm.lookbackDays} onChange={(event) => handleAnomalyRuleForm('lookbackDays', event.target.value)} disabled={!canEdit || saving}/>
                                        <TextField fullWidth label={t('Cooldown Minutes')} type="number" value={anomalyRuleForm.cooldownMinutes} onChange={(event) => handleAnomalyRuleForm('cooldownMinutes', event.target.value)} disabled={!canEdit || saving}/>
                                    </Box>
                                    <TextField label={t('Store Id')} value={anomalyRuleForm.storeId} onChange={(event) => handleAnomalyRuleForm('storeId', event.target.value)} disabled={!canEdit || saving} helperText={t('Optional. Leave blank to scan all stores for the selected module.')}/>
                                    <TextField label={t('Description')} multiline minRows={2} value={anomalyRuleForm.description} onChange={(event) => handleAnomalyRuleForm('description', event.target.value)} disabled={!canEdit || saving}/>
                                    <FormControl fullWidth size="small"><InputLabel>{t('Status')}</InputLabel><Select value={anomalyRuleForm.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handleAnomalyRuleForm('active', event.target.value === 'ACTIVE')} disabled={!canEdit || saving}><MenuItem value="ACTIVE">{t('Active')}</MenuItem><MenuItem value="INACTIVE">{t('Inactive')}</MenuItem></Select></FormControl>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={saveAnomalyRule} disabled={!canEdit || saving || !String(anomalyRuleForm.ruleName).trim()}>{editingAnomalyRuleId ? t('Update Rule') : t('Create Rule')}</Button>
                                        <Button variant="outlined" onClick={resetAnomalyRuleForm} disabled={saving}>{t('Clear')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>

                            <Paper elevation={0} sx={{p: 2.5, mt: 2, borderRadius: '22px', border: `1px solid ${palette.border}`, backgroundColor: palette.cardBg}}>
                                <SectionHeader
                                    icon={<EmailOutlinedIcon sx={{fontSize: 18}}/>}
                                    title={editingSmsSubscriptionId ? t('Edit SMS Subscription') : t('Create SMS Subscription')}
                                    subtitle={t('Optional enterprise SMS channel for high-urgency alerts. Delivery stays disabled until an SMS provider endpoint is configured.') }
                                    palette={palette}
                                    tone="purple"
                                />
                                <Stack spacing={1.5}>
                                    <TextField label={t('Subscription Name')} value={smsSubscriptionForm.subscriptionName} onChange={(event) => handleSmsSubscriptionForm('subscriptionName', event.target.value)} disabled={!canEdit || saving}/>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Module')}</InputLabel><Select value={smsSubscriptionForm.reconView} label={t('Module')} onChange={(event) => handleSmsSubscriptionForm('reconView', event.target.value)} disabled={!canEdit || saving}>{MODULE_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Metric')}</InputLabel><Select value={smsSubscriptionForm.metricKey} label={t('Metric')} onChange={(event) => handleSmsSubscriptionForm('metricKey', event.target.value)} disabled={!canEdit || saving}><MenuItem value="">{t('All Metrics')}</MenuItem>{METRIC_OPTIONS.map((option) => <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>)}</Select></FormControl>
                                        <FormControl fullWidth size="small"><InputLabel>{t('Severity Threshold')}</InputLabel><Select value={smsSubscriptionForm.severityThreshold} label={t('Severity Threshold')} onChange={(event) => handleSmsSubscriptionForm('severityThreshold', event.target.value)} disabled={!canEdit || saving}><MenuItem value="">{t('All Severities')}</MenuItem>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((option) => <MenuItem key={option} value={option}>{option}</MenuItem>)}</Select></FormControl>
                                        <TextField fullWidth label={t('Phone Number')} value={smsSubscriptionForm.phoneNumber} onChange={(event) => handleSmsSubscriptionForm('phoneNumber', event.target.value)} disabled={!canEdit || saving}/>
                                    </Box>
                                    <Box sx={{display: 'grid', gridTemplateColumns: {xs: '1fr', xl: 'repeat(2, minmax(0, 1fr))'}, gap: 1.5}}>
                                        <TextField fullWidth label={t('Store Id')} value={smsSubscriptionForm.storeId} onChange={(event) => handleSmsSubscriptionForm('storeId', event.target.value)} disabled={!canEdit || saving}/>
                                        <TextField fullWidth label={t('Register Id')} value={smsSubscriptionForm.wkstnId} onChange={(event) => handleSmsSubscriptionForm('wkstnId', event.target.value)} disabled={!canEdit || saving}/>
                                    </Box>
                                    <TextField label={t('Description')} multiline minRows={2} value={smsSubscriptionForm.description} onChange={(event) => handleSmsSubscriptionForm('description', event.target.value)} disabled={!canEdit || saving}/>
                                    <FormControl fullWidth size="small"><InputLabel>{t('Status')}</InputLabel><Select value={smsSubscriptionForm.active ? 'ACTIVE' : 'INACTIVE'} label={t('Status')} onChange={(event) => handleSmsSubscriptionForm('active', event.target.value === 'ACTIVE')} disabled={!canEdit || saving}><MenuItem value="ACTIVE">{t('Active')}</MenuItem><MenuItem value="INACTIVE">{t('Inactive')}</MenuItem></Select></FormControl>
                                    <Stack direction="row" spacing={1}>
                                        <Button variant="contained" onClick={saveSmsSubscription} disabled={!canEdit || saving || !String(smsSubscriptionForm.subscriptionName).trim() || !String(smsSubscriptionForm.phoneNumber).trim()}>{editingSmsSubscriptionId ? t('Update Subscription') : t('Create Subscription')}</Button>
                                        <Button variant="outlined" onClick={resetSmsSubscriptionForm} disabled={saving}>{t('Clear')}</Button>
                                    </Stack>
                                </Stack>
                            </Paper>
                        </Grid>

                        <Grid size={{xs: 12, lg: 7}}>
                            <RuleTable rules={filteredRules} palette={palette} canEdit={canEdit} onEdit={editRule} onDelete={deleteRule} t={t} saving={saving}/>
                            <EventTable events={filteredEvents} palette={palette} canEdit={canEdit} onStatus={updateEvent} t={t} saving={saving}/>
                            <Box sx={{mt: 2}}>
                                <SubscriptionTable subscriptions={filteredSubscriptions} palette={palette} canEdit={canEdit} onEdit={editSubscription} onDelete={deleteSubscription} t={t} saving={saving}/>
                                <DeliveryTable deliveries={filteredDeliveries} palette={palette} t={t}/>
                            </Box>
                            <Box sx={{mt: 2}}>
                                <WebhookSubscriptionTable subscriptions={filteredWebhookSubscriptions} palette={palette} canEdit={canEdit} onEdit={editWebhookSubscription} onDelete={deleteWebhookSubscription} t={t} saving={saving}/>
                                <WebhookDeliveryTable deliveries={filteredWebhookDeliveries} palette={palette} t={t}/>
                            </Box>
                            <Box sx={{mt: 2}}>
                                <EscalationPolicyTable policies={filteredEscalationPolicies} palette={palette} canEdit={canEdit} onEdit={editEscalationPolicy} onDelete={deleteEscalationPolicy} t={t} saving={saving}/>
                                <EscalationHistoryTable history={filteredEscalationHistory} palette={palette} t={t}/>
                            </Box>
                            <Box sx={{mt: 2}}>
                                <PersonalSubscriptionTable subscriptions={filteredPersonalSubscriptions} palette={palette} canEdit={canManagePersonalSubscriptions} onEdit={editPersonalSubscription} onDelete={deletePersonalSubscription} t={t} saving={saving}/>
                            </Box>
                            <Box sx={{mt: 2}}>
                                <DigestSubscriptionTable subscriptions={filteredDigestSubscriptions} palette={palette} canEdit={canEdit} onEdit={editDigestSubscription} onDelete={deleteDigestSubscription} t={t} saving={saving}/>
                                <DigestRunTable runs={filteredDigestRuns} palette={palette} t={t}/>
                            </Box>
                            <Box sx={{mt: 2}}>
                                <AnomalyRuleTable rules={filteredAnomalyRules} palette={palette} canEdit={canEdit} onEdit={editAnomalyRule} onDelete={deleteAnomalyRule} t={t} saving={saving}/>
                            </Box>
                            <Box sx={{mt: 2}}>
                                <SmsSubscriptionTable subscriptions={filteredSmsSubscriptions} palette={palette} canEdit={canEdit} onEdit={editSmsSubscription} onDelete={deleteSmsSubscription} t={t} saving={saving}/>
                                <SmsDeliveryTable deliveries={filteredSmsDeliveries} palette={palette} t={t}/>
                            </Box>
                        </Grid>
                    </Grid>
                </>
            )}
        </Box>
    )
}
