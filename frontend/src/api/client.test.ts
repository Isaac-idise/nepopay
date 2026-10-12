import { ApiError, onAuthLost, refreshSession, request, tokenStore } from './client'

const ok = (data: unknown) => new Response(JSON.stringify({ success: true, data, timestamp: 'now' }), { status: 200 })
const fail = (status: number, errorCode: string, message = 'msg') =>
  new Response(JSON.stringify({ success: false, errorCode, message, timestamp: 'now' }), { status: status })

const tokenBody = (accessToken: string) => ({ accessToken, tokenType: 'Bearer', expiresInSeconds: 900, username: 'u', displayName: 'U', role: 'USER' })

function install(handler: (url: string, init: RequestInit) => Response | Promise<Response>) {
  const mock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => handler(String(input), init))
  vi.stubGlobal('fetch', mock)
  return mock
}
const auth = (init: RequestInit) => (init.headers as Record<string, string>).Authorization

beforeEach(() => {
  tokenStore.set(null)
  onAuthLost(null)
})
afterEach(() => vi.unstubAllGlobals())

describe('api client', () => {
  it('unwraps the envelope and sends the bearer token', async () => {
    tokenStore.set('tok')
    const fetchMock = install((url, init) => {
      expect(url).toBe('/api/v1/accounts/me')
      expect(auth(init)).toBe('Bearer tok')
      expect(init.credentials).toBe('include')
      return ok({ balanceKobo: 10_000_000 })
    })
    await expect(request('/accounts/me')).resolves.toEqual({ balanceKobo: 10_000_000 })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('turns server errors into ApiError with the stable code', async () => {
    install(() => fail(422, 'INSUFFICIENT_BALANCE', 'Insufficient balance.'))
    const error = await request('/transfers', { method: 'POST', body: {} }).catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 422, code: 'INSUFFICIENT_BALANCE', message: 'Insufficient balance.' })
  })

  it('reports network failures distinctly', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('offline') }))
    await expect(request('/accounts/me')).rejects.toMatchObject({ code: 'NETWORK_ERROR', status: 0 })
  })

  it('survives a non-JSON error page', async () => {
    install(() => new Response('<html>Bad gateway</html>', { status: 502 }))
    await expect(request('/accounts/me')).rejects.toMatchObject({ code: 'UNEXPECTED', status: 502 })
  })

  it('refreshes an expired access token once, then retries the request', async () => {
    tokenStore.set('old')
    const calls: string[] = []
    install((url, init) => {
      calls.push(`${init.method ?? 'GET'} ${url}`)
      if (url.endsWith('/auth/refresh')) return ok(tokenBody('fresh'))
      return auth(init) === 'Bearer fresh' ? ok({ fine: true }) : fail(401, 'UNAUTHORIZED')
    })
    await expect(request('/accounts/me')).resolves.toEqual({ fine: true })
    expect(calls).toEqual(['GET /api/v1/accounts/me', 'POST /api/v1/auth/refresh', 'GET /api/v1/accounts/me'])
    expect(tokenStore.get()).toBe('fresh')
  })

  it('shares ONE refresh between simultaneous failing requests (rotation would treat a second as theft)', async () => {
    tokenStore.set('old')
    let refreshes = 0
    install(async (url, init) => {
      if (url.endsWith('/auth/refresh')) {
        refreshes++
        await new Promise((r) => setTimeout(r, 20))
        return ok(tokenBody('fresh'))
      }
      return auth(init) === 'Bearer fresh' ? ok({ url }) : fail(401, 'UNAUTHORIZED')
    })
    const results = await Promise.all([request('/accounts/me'), request('/notifications/unread-count'), request('/beneficiaries')])
    expect(results).toHaveLength(3)
    expect(refreshes).toBe(1)
  })

  it('shares one refresh between direct callers too (React StrictMode runs effects twice)', async () => {
    let refreshes = 0
    install(async () => { refreshes++; return ok(tokenBody('t')) })
    await Promise.all([refreshSession(), refreshSession()])
    expect(refreshes).toBe(1)
  })

  it('signs the user out when the refresh itself fails', async () => {
    tokenStore.set('old')
    const lost = vi.fn()
    onAuthLost(lost)
    install((url) => (url.endsWith('/auth/refresh') ? fail(401, 'SESSION_EXPIRED') : fail(401, 'UNAUTHORIZED')))
    await expect(request('/accounts/me')).rejects.toMatchObject({ status: 401 })
    expect(lost).toHaveBeenCalledTimes(1)
    expect(tokenStore.get()).toBeNull()
  })

  it('does not try to refresh for login errors or when nobody is signed in', async () => {
    const fetchMock = install(() => fail(401, 'INVALID_CREDENTIALS'))
    await expect(request('/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' })
    await expect(request('/accounts/me')).rejects.toMatchObject({ status: 401 }) // no token held
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('does not refresh for ordinary failures such as 422', async () => {
    tokenStore.set('tok')
    const fetchMock = install(() => fail(422, 'INVALID_PIN'))
    await expect(request('/transfers', { method: 'POST', body: {} })).rejects.toMatchObject({ code: 'INVALID_PIN' })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
