import {expect, test} from '@playwright/test'

const credentials = {
    tenantId: process.env.SMOKE_TENANT_ID || 'tenant-india',
    username: process.env.SMOKE_USERNAME || 'admin',
    password: process.env.SMOKE_PASSWORD || 'Admin@123',
}

test('kpi drill-down opens the scoped transaction and exception queue flow', async ({page}) => {
    await page.goto('/login')

    await page.getByTestId('login-tenant').fill(credentials.tenantId)
    await page.getByTestId('login-username').fill(credentials.username)
    await page.getByTestId('login-password').fill(credentials.password)
    await page.getByTestId('login-submit').click()

    await expect(page.getByTestId('sidebar-item-xstore-sim')).toBeVisible()
    await page.getByTestId('sidebar-item-xstore-sim').click()

    await expect(page.getByTestId('kpi-card-total')).toBeVisible()
    await page.getByTestId('kpi-card-total').click()

    await expect(page.getByTestId('selected-record-summary')).toBeVisible()
    await page.getByTestId('selected-record-open-drilldown').click()

    const drilldownPage = page.getByTestId('transaction-drilldown-page')
    await expect(drilldownPage).toBeVisible()
    await expect(drilldownPage.getByRole('button', {name: /open in exception queues/i})).toBeVisible()
    await page.getByTestId('transaction-drilldown-open-exception-queue').click()

    const queuePage = page.getByTestId('exception-queues-page')
    await expect(queuePage).toBeVisible()
    await expect(queuePage).toContainText(/exception queues/i)
})
