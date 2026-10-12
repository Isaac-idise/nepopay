/** A tiny in-memory stand-in for the Spring backend, enough to drive the UI through real flows. */
export interface FakeState {
  loggedInAs: 'user' | 'admin' | null       // what a valid refresh cookie represents
  balanceKobo: number
  transferBehaviour: 'ok' | 'flagged' | 'wrongPin' | 'insufficient'
  transferCalls: { key: string | null; body: Record<string, unknown> }[]
  transactions: unknown[]
}

export function createFakeApi(overrides: Partial<FakeState> = {}) {
  const state: FakeState = {
    loggedInAs: null, balanceKobo: 10_000_000, transferBehaviour: 'ok', transferCalls: [], transactions: [], ...overrides,
  }
  const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status })
  const ok = (data: unknown) => json(200, { success: true, data, timestamp: 't' })
  const err = (status: number, errorCode: string, message: string) => json(status, { success: false, errorCode, message, timestamp: 't' })
  const page = (content: unknown[]) => ({ content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 })

  const tokenFor = (who: 'user' | 'admin') => ({
    accessToken: `token-${who}`, tokenType: 'Bearer', expiresInSeconds: 900,
    username: who === 'admin' ? 'admin' : 'ada_o', displayName: who === 'admin' ? 'NepOpay Admin' : 'Ada Obi',
    role: who === 'admin' ? 'ADMIN' : 'USER',
  })

  const handler = async (input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> => {
    const url = new URL(String(input), 'http://localhost')
    const path = url.pathname.replace('/api/v1', '')
    const method = (init.method ?? 'GET').toUpperCase()
    const headers = (init.headers ?? {}) as Record<string, string>
    const body = init.body ? JSON.parse(String(init.body)) : undefined
    const authed = headers.Authorization?.startsWith('Bearer token-')

    if (path === '/auth/refresh') {
      return state.loggedInAs ? ok(tokenFor(state.loggedInAs)) : err(401, 'SESSION_EXPIRED', 'Your session has expired. Please log in again.')
    }
    if (path === '/auth/login') {
      if (body.password === 'Passw0rdTest') {
        state.loggedInAs = body.identifier === 'admin' ? 'admin' : 'user'
        return ok(tokenFor(state.loggedInAs))
      }
      return err(401, 'INVALID_CREDENTIALS', 'Invalid username or password.')
    }
    if (path === '/auth/logout') {
      state.loggedInAs = null
      return ok(null)
    }
    if (!authed) return err(401, 'UNAUTHORIZED', 'Authentication required.')

    if (path === '/accounts/me') return ok({ accountNumber: '9912345676', balanceKobo: state.balanceKobo, currency: 'NGN', status: 'ACTIVE' })
    if (path === '/notifications/unread-count') return ok({ unread: 2 })
    if (path === '/transactions' && method === 'GET') return ok(page(state.transactions))
    if (path === '/beneficiaries') return ok([])
    if (path === '/users/search') {
      const q = (url.searchParams.get('q') ?? '').toLowerCase()
      return ok(page(q.startsWith('dan') ? [{ username: 'daniel', displayName: 'Daniel Okafor', accountLast4: '4821' }] : []))
    }
    if (path === '/transfers' && method === 'POST') {
      state.transferCalls.push({ key: headers['Idempotency-Key'] ?? null, body })
      if (state.transferBehaviour === 'wrongPin') {
        state.transferBehaviour = 'ok' // the next attempt will use the right PIN
        return err(422, 'INVALID_PIN', 'Invalid transaction PIN. 2 attempt(s) remaining.')
      }
      if (state.transferBehaviour === 'insufficient') return err(422, 'INSUFFICIENT_BALANCE', 'Insufficient balance. Your available balance is ₦100,000.00.')
      const flagged = state.transferBehaviour === 'flagged'
      return json(flagged ? 202 : 201, {
        success: true, timestamp: 't',
        data: {
          reference: 'NPY-20261005-ABCD2345', status: flagged ? 'FLAGGED' : 'SUCCESSFUL', amountKobo: body.amountKobo,
          recipientUsername: 'daniel', recipientDisplayName: 'Daniel Okafor', description: null,
          createdAt: '2026-10-05T10:00:00Z', idempotentReplay: false,
          message: flagged ? 'Your transfer is on hold for a quick safety review.' : 'Transfer successful.',
        },
      })
    }
    return err(404, 'NOT_FOUND', 'Not found.')
  }
  return { state, handler }
}
