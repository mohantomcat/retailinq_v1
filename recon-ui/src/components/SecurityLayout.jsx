import {Box, Typography} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import ManageUsers from '../pages/admin/ManageUsers'
import ManageRoles from '../pages/admin/ManageRoles'
import ManagePermissions from '../pages/admin/ManagePermissions'

const TAB_LABELS = {
    'manage-users': 'Manage Users',
    'manage-roles': 'Manage Roles',
    'manage-perms': 'Manage Permissions',
}

const TAB_CONTENT = {
    'manage-users': <ManageUsers/>,
    'manage-roles': <ManageRoles/>,
    'manage-perms': <ManagePermissions/>,
}

export default function SecurityLayout({
                                           openTabs,
                                           activeTab,
                                           setActiveTab,
                                           onCloseTab,
                                       }) {
    if (openTabs.length === 0) {
        return (
            <Box sx={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                height: '60vh',
                gap: 2,
            }}>
                <Box sx={{
                    width: 56, height: 56,
                    borderRadius: '50%',
                    backgroundColor: '#eff6ff',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}>
                    <Box sx={{
                        fontSize: 28,
                        lineHeight: 1,
                    }}>
                        🔐
                    </Box>
                </Box>
                <Typography sx={{
                    fontWeight: 600,
                    fontSize: '1rem',
                    color: '#374151',
                }}>
                    Security Management
                </Typography>
                <Typography sx={{
                    fontSize: '0.875rem',
                    color: '#9ca3af',
                    textAlign: 'center',
                    maxWidth: 320,
                }}>
                    Select Manage Users, Manage Roles or
                    Manage Permissions from the sidebar
                    to get started.
                </Typography>
            </Box>
        )
    }

    return (
        <Box sx={{
            display: 'flex',
            flexDirection: 'column',
            height: '100%',
        }}>
            {/* Security tab bar */}
            <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 0.5,
                borderBottom: '1px solid #e5e7eb',
                backgroundColor: '#ffffff',
                px: 2,
                pt: 1.5,
                flexWrap: 'nowrap',
                overflowX: 'auto',
                '&::-webkit-scrollbar': {height: 4},
                '&::-webkit-scrollbar-track': {
                    backgroundColor: '#f1f5f9',
                },
                '&::-webkit-scrollbar-thumb': {
                    backgroundColor: '#cbd5e1',
                    borderRadius: 2,
                },
            }}>
                {openTabs.map(tabId => {
                    const isActive = activeTab === tabId
                    return (
                        <Box
                            key={tabId}
                            onClick={() =>
                                setActiveTab(tabId)}
                            sx={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 1,
                                px: 2, py: 0.75,
                                borderRadius:
                                    '8px 8px 0 0',
                                cursor: 'pointer',
                                flexShrink: 0,
                                backgroundColor: isActive
                                    ? '#f5f7fa'
                                    : 'transparent',
                                borderBottom: isActive
                                    ? '2px solid #2563eb'
                                    : '2px solid transparent',
                                transition:
                                    'all 0.15s ease',
                                '&:hover': {
                                    backgroundColor:
                                        '#f8fafc',
                                },
                            }}
                        >
                            <Typography sx={{
                                fontSize: '0.82rem',
                                fontWeight: isActive
                                    ? 600 : 400,
                                color: isActive
                                    ? '#2563eb'
                                    : '#6b7280',
                                whiteSpace: 'nowrap',
                            }}>
                                {TAB_LABELS[tabId]}
                            </Typography>
                            <Box
                                onClick={e => {
                                    e.stopPropagation()
                                    onCloseTab(tabId)
                                }}
                                sx={{
                                    width: 18, height: 18,
                                    borderRadius: '50%',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent:
                                        'center',
                                    color: '#9ca3af',
                                    flexShrink: 0,
                                    '&:hover': {
                                        backgroundColor:
                                            '#e5e7eb',
                                        color: '#374151',
                                    },
                                }}
                            >
                                <CloseIcon
                                    sx={{fontSize: 12}}/>
                            </Box>
                        </Box>
                    )
                })}
            </Box>

            {/* Tab content */}
            <Box sx={{flex: 1, overflowY: 'auto'}}>
                {openTabs.map(tabId => (
                    <Box
                        key={tabId}
                        sx={{
                            display: activeTab === tabId
                                ? 'block' : 'none',
                        }}
                    >
                        {TAB_CONTENT[tabId]}
                    </Box>
                ))}
            </Box>
        </Box>
    )
}