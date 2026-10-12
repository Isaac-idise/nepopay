import type { ApiEnvelope, TokenResponse } from './types'

/** Every failed call becomes one of these, with the server's stable error code. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const API_ROOT = `${import.meta.env.VITE_API_BASE_URL ?? ''}/api/v1`

/**
 * The short-lived access token lives ONLY in this variable (memory): it is gone on refresh/close and
 * cannot be read from localStorage by injected scripts. The long-lived refresh token is an httpOnly
 * cookie the browser manages; JavaScript can never see it.
 */
let accessToken: string | null = null
let authLostHandler: (() => void) | null = null

export const tokenStore = {
  get: () => accessToken,
  set: (token: string | null) => {
    accessToken = token
  },
}

export function onAuthLost(handler: (() => void) | null): void {
  authLostHandler = handler
}

interface Options {
  method?: string
  body?: unknown
  headers?: Record<string, string>
  /** false = do not attach the token and never try to refresh (login, register, refresh, logout) */
  auth?: boolean
}

async function send<T>(path: string, options: Options): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.auth !== false && accessToken) headers.Authorization = `Bearer ${accessToken}`

  let response: Response
  try {
    response = await fetch(`${API_ROOT}${path}`, {
      method: options.method ?? 'GET',
      headers,
      credentials: 'include',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'Could not reach the server.')
  }

  let envelope: ApiEnvelope<T> | null = null
  try {
    envelope = (await response.json()) as ApiEnvelope<T>
  } catch {
    envelope = null // not JSON (for example an error page from a proxy)
  }

  if (!response.ok || !envelope || envelope.success === false) {
    throw new ApiError(
      response.status,
      envelope?.errorCode ?? 'UNEXPECTED',
      envelope?.message ?? `Unexpected response (${response.status}).`,
    )
  }
  return envelope.data as T
}

/**
 * Refresh tokens ROTATE: each one works once. If two refreshes with the same cookie ran at the same time
 * (React StrictMode, or several requests failing together), the second would look like token theft and
 * the server would end the whole session. So every caller shares ONE in-flight refresh.
 */
let inFlightRefresh: Promise<TokenResponse> | null = null

export function refreshSession(): Promise<TokenResponse> {
  if (!inFlightRefresh) {
    inFlightRefresh = send<TokenResponse>('/auth/refresh', { method: 'POST', auth: false })
      .then((token) => {
        accessToken = token.accessToken
        return token
      })
      .finally(() => {
        inFlightRefresh = null
      })
  }
  return inFlightRefresh
}

export async function request<T>(path: string, options: Options = {}): Promise<T> {
  try {
    return await send<T>(path, options)
  } catch (error) {
    const expired =
      error instanceof ApiError &&
      error.status === 401 &&
      options.auth !== false &&
      !path.startsWith('/auth/') &&
      accessToken !== null
    if (!expired) throw error

    try {
      await refreshSession()
    } catch {
      accessToken = null
      authLostHandler?.()
      throw error
    }
    return send<T>(path, options) // one retry with the fresh token
  }
}

export function qs(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}
