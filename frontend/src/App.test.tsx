import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import AppRoutes from './App'
import { tokenStore } from './api/client'
import { Providers } from './providers'
import { createFakeApi, type FakeState } from './test/fakeApi'

function setup(path: string, state: Partial<FakeState> = {}) {
  const fake = createFakeApi(state)
  const fetchMock = vi.fn(fake.handler)
  vi.stubGlobal('fetch', fetchMock)
  render(
    <MemoryRouter initialEntries={[path]}>
      <Providers>
        <AppRoutes />
      </Providers>
    </MemoryRouter>,
  )
  return { ...fake, fetchMock, user: userEvent.setup() }
}

beforeEach(() => {
  tokenStore.set(null)
  localStorage.clear()
})
afterEach(() => vi.unstubAllGlobals())

describe('public pages', () => {
  it('shows the brand, the tagline and the DEMO MODE notice', async () => {
    setup('/')
    expect(await screen.findByRole('heading', { level: 1, name: /banking, but make am/i })).toBeInTheDocument()
    expect(screen.getAllByText(/DEMO MODE/).length).toBeGreaterThan(0)
    expect(screen.getAllByText(/₦0 real money involved/).length).toBeGreaterThan(0)
  })

  it('sends signed-out visitors away from private pages', async () => {
    setup('/app')
    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument()
  })

  it('shows field errors before talking to the server', async () => {
    const { user, fetchMock } = setup('/register')
    // the header also has a "Create account" link-button, so look inside the page body
    const form = within(await screen.findByRole('main'))
    await user.click(await form.findByRole('button', { name: /^create account$/i }))
    expect(await screen.findByText(/enter your first name/i)).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/auth/register'))).toBe(false)
  })
})

describe('login', () => {
  it('logs in and lands on the dashboard with the balance', async () => {
    const { user } = setup('/login')
    await user.type(await screen.findByLabelText(/username or email/i), 'ada_o')
    await user.type(screen.getByLabelText(/^password/i), 'Passw0rdTest')
    await user.click(screen.getByRole('button', { name: /^log in$/i }))
    expect(await screen.findByTestId('balance')).toHaveTextContent('₦100,000.00')
    expect(screen.getByText(/Nothing dey here yet\./)).toBeInTheDocument()
  })

  it('explains a wrong password in plain, friendly words', async () => {
    const { user } = setup('/login')
    await user.type(await screen.findByLabelText(/username or email/i), 'ada_o')
    await user.type(screen.getByLabelText(/^password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /^log in$/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/those details no match/i)
  })

  it('restores the session on page load from the refresh cookie', async () => {
    setup('/app', { loggedInAs: 'user' })
    expect(await screen.findByTestId('balance')).toHaveTextContent('₦100,000.00')
  })

  it('can hide the balance', async () => {
    const { user } = setup('/app', { loggedInAs: 'user' })
    await screen.findByTestId('balance')
    await user.click(screen.getByRole('button', { name: /hide/i }))
    expect(screen.getByTestId('balance')).toHaveTextContent('••••')
    expect(localStorage.getItem('nepopay.hideBalance')).toBe('1')
  })
})

describe('role guards', () => {
  it('keeps customers out of the admin area', async () => {
    setup('/admin', { loggedInAs: 'user' })
    expect(await screen.findByTestId('balance')).toBeInTheDocument() // bounced to /app
  })

  it('sends admins to the admin area instead of the customer app', async () => {
    setup('/app', { loggedInAs: 'admin' })
    await waitFor(() => expect(screen.getAllByText('ADMIN').length).toBeGreaterThan(0))
    expect(screen.queryByTestId('balance')).not.toBeInTheDocument()
  })
})

describe('send money', () => {
  async function toPinStep(ctx: ReturnType<typeof setup>, amount = '20000') {
    const { user } = ctx
    await user.type(await screen.findByLabelText(/search by name/i), 'dan')
    await user.click(await screen.findByRole('button', { name: /daniel okafor/i }))
    await user.type(await screen.findByLabelText(/^amount \(₦\)/i), amount)
    await user.click(screen.getByRole('button', { name: /^continue$/i }))
    return screen.findByLabelText(/transaction pin/i)
  }

  it('walks through recipient, amount and PIN, then celebrates', async () => {
    const ctx = setup('/app/send', { loggedInAs: 'user' })
    const pin = await toPinStep(ctx)
    expect(screen.getByText('₦20,000.00', { selector: 'dd' })).toBeInTheDocument()
    await ctx.user.type(pin, '1234')
    await ctx.user.click(screen.getByRole('button', { name: /send ₦20,000\.00/i }))

    expect(await screen.findByText('E don land! 💸')).toBeInTheDocument()
    expect(screen.getByText(/NPY-20261005-ABCD2345/)).toBeInTheDocument()

    expect(ctx.state.transferCalls).toHaveLength(1)
    expect(ctx.state.transferCalls[0].body).toMatchObject({ recipientUsername: 'daniel', amountKobo: 2_000_000, pin: '1234' })
    expect(ctx.state.transferCalls[0].key).toMatch(/^[0-9a-f-]{36}$/)
  })

  it('reuses the SAME idempotency key when the same request is retried after a wrong PIN', async () => {
    const ctx = setup('/app/send', { loggedInAs: 'user', transferBehaviour: 'wrongPin' })
    const pin = await toPinStep(ctx)
    await ctx.user.type(pin, '0000')
    await ctx.user.click(screen.getByRole('button', { name: /send ₦20,000\.00/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/that PIN no correct/i)
    expect(alert).toHaveTextContent(/2 attempt\(s\) remaining/)       // server detail stays visible

    const pinField = screen.getByLabelText(/transaction pin/i)
    expect(pinField).toHaveValue('')                                  // cleared for the retry
    await ctx.user.type(pinField, '1234')
    await ctx.user.click(screen.getByRole('button', { name: /send ₦20,000\.00/i }))
    expect(await screen.findByText('E don land! 💸')).toBeInTheDocument()

    const [first, second] = ctx.state.transferCalls
    expect(first.key).toBe(second.key)
  })

  it('uses a NEW key when the request itself changes', async () => {
    const ctx = setup('/app/send', { loggedInAs: 'user', transferBehaviour: 'wrongPin' })
    const pin = await toPinStep(ctx, '20000')
    await ctx.user.type(pin, '0000')
    await ctx.user.click(screen.getByRole('button', { name: /send ₦20,000\.00/i }))
    await screen.findByRole('alert')

    await ctx.user.click(screen.getByRole('button', { name: /back/i }))
    const amount = await screen.findByLabelText(/^amount \(₦\)/i)
    await ctx.user.clear(amount)
    await ctx.user.type(amount, '25000')
    await ctx.user.click(screen.getByRole('button', { name: /^continue$/i }))
    await ctx.user.type(await screen.findByLabelText(/transaction pin/i), '1234')
    await ctx.user.click(screen.getByRole('button', { name: /send ₦25,000\.00/i }))
    await screen.findByText('E don land! 💸')

    const [first, second] = ctx.state.transferCalls
    expect(first.key).not.toBe(second.key)
  })

  it('shows a held transfer honestly instead of pretending it landed', async () => {
    const ctx = setup('/app/send', { loggedInAs: 'user', transferBehaviour: 'flagged' })
    const pin = await toPinStep(ctx, '60000')
    await ctx.user.type(pin, '1234')
    await ctx.user.click(screen.getByRole('button', { name: /send ₦60,000\.00/i }))

    expect(await screen.findByText('Omo… hold up. 🧐')).toBeInTheDocument()
    expect(screen.getByText(/on hold for a quick safety review/i, { selector: 'p' })).toBeInTheDocument()
    expect(screen.queryByText('E don land! 💸')).not.toBeInTheDocument()
  })

  it('keeps the exact balance wording when funds are short', async () => {
    const ctx = setup('/app/send', { loggedInAs: 'user', transferBehaviour: 'insufficient' })
    const pin = await toPinStep(ctx, '5000')
    await ctx.user.type(pin, '1234')
    await ctx.user.click(screen.getByRole('button', { name: /send ₦5,000\.00/i }))
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText(/your balance no reach/i)).toBeInTheDocument()
    expect(alert).toHaveTextContent('Your available balance is ₦100,000.00.')
  })

  it('stops amounts above the balance and below the minimum before calling the server', async () => {
    const ctx = setup('/app/send', { loggedInAs: 'user' })
    await ctx.user.type(await screen.findByLabelText(/search by name/i), 'dan')
    await ctx.user.click(await screen.findByRole('button', { name: /daniel okafor/i }))
    const amount = await screen.findByLabelText(/^amount \(₦\)/i)

    await ctx.user.type(amount, '150000')
    expect(await screen.findByText(/more than your balance of ₦100,000\.00/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^continue$/i })).toBeDisabled()

    await ctx.user.clear(amount)
    await ctx.user.type(amount, '0.5')
    expect(await screen.findByText(/minimum is ₦1\.00/i)).toBeInTheDocument()
    expect(ctx.state.transferCalls).toHaveLength(0)
  })

  it('says so when nobody matches the search', async () => {
    const ctx = setup('/app/send', { loggedInAs: 'user' })
    await ctx.user.type(await screen.findByLabelText(/search by name/i), 'zzz')
    expect(await screen.findByText(/nobody found/i)).toBeInTheDocument()
  })
})

describe('admin account', () => {
  it('lets an admin change the initial password, then signs them out', async () => {
    const ctx = setup('/admin/account', { loggedInAs: 'admin' })
    expect(await screen.findByRole('heading', { name: /my account/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /my account/i })).toBeInTheDocument()

    await ctx.user.type(screen.getByLabelText(/current password/i), 'Passw0rdTest')
    await ctx.user.type(screen.getByLabelText(/^new password/i), 'BrandNewPass9')
    await ctx.user.type(screen.getByLabelText(/confirm new password/i), 'BrandNewPass9')
    await ctx.user.click(screen.getByRole('button', { name: /change password/i }))

    expect(await screen.findByRole('heading', { name: /welcome back/i })).toBeInTheDocument() // signed out
    expect(ctx.state.passwordChanges).toEqual([{ currentPassword: 'Passw0rdTest', newPassword: 'BrandNewPass9' }])
  })

  it('shows a clear message when the current password is wrong', async () => {
    const ctx = setup('/admin/account', { loggedInAs: 'admin' })
    await ctx.user.type(await screen.findByLabelText(/current password/i), 'not-it')
    await ctx.user.type(screen.getByLabelText(/^new password/i), 'BrandNewPass9')
    await ctx.user.type(screen.getByLabelText(/confirm new password/i), 'BrandNewPass9')
    await ctx.user.click(screen.getByRole('button', { name: /change password/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/that password no correct/i)
    expect(screen.queryByRole('heading', { name: /welcome back/i })).not.toBeInTheDocument() // still signed in
  })
})

