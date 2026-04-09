import {expect, test} from '@playwright/test'

test.use({
    screenshot: 'off',
    trace: 'off',
    video: 'off',
})

const credentials = {
    tenantId: process.env.SMOKE_TENANT_ID || 'tenant-india',
    username: process.env.SMOKE_USERNAME || 'admin',
    password: process.env.SMOKE_PASSWORD || 'Admin123!',
}

const sidebarGroups = [
    'Alerts',
    'Exceptions',
    'SLA & Aging',
    'Integration Hub',
    'Operations',
    'Audit & Activity',
    'Configurations',
    'Reports',
    'Security',
]

test('every visible sidebar tab opens without blanking the shell', async ({page}) => {
    const pageErrors = []
    page.on('pageerror', (error) => {
        pageErrors.push(error.message)
    })

    await page.goto('/login')

    await page.getByTestId('login-tenant').fill(credentials.tenantId)
    await page.getByTestId('login-username').fill(credentials.username)
    await page.getByTestId('login-password').fill(credentials.password)
    await page.getByTestId('login-submit').click()

    await expect(page.getByText('Welcome to RetailINQ')).toBeVisible()

    for (const group of sidebarGroups) {
        const groupButton = page.getByRole('button', {
            name: new RegExp(`^${group}$`),
        })
        if (await groupButton.count()) {
            await groupButton.first().click()
        }
    }

    const tabIds = await page
        .locator('[data-testid^="sidebar-item-"]')
        .evaluateAll((items) =>
            items
                .map((item) => item.getAttribute('data-testid'))
                .filter(Boolean)
        )

    expect(tabIds.length).toBeGreaterThan(0)

    for (const testId of tabIds) {
        await page.getByTestId(testId).first().click({force: true})

        await expect(page.locator('header')).toBeVisible()
        await expect(page.getByTestId(testId).first()).toBeVisible()
        await expect(page.getByText('Unable to load this module')).toHaveCount(0)
        expect((await page.locator('main').innerText()).trim().length).toBeGreaterThan(0)

        const tabId = testId.replace('sidebar-item-', '')
        await expect(page.getByTestId(`dashboard-tab-${tabId}`)).toBeVisible()
        await page.getByTestId(`dashboard-tab-close-${tabId}`).click()
    }

    expect(pageErrors).toEqual([])
})
