import { useQueryClient } from '@tanstack/react-query'
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../api/endpoints'
import { onAuthLost, refreshSession, tokenStore } from '../api/client'
import type { RegisterRequest, Role, TokenResponse } from '../api/types'

export interface Session {
  username: string
  displayName: string
  role: Role
}

type Status = 'loading' | 'authenticated' | 'anonymous'

interface AuthValue {
  status: Status
  session: Session | null
  login: (identifier: string, password: string) => Promise<Session>
  register: (values: RegisterRequest) => Promise<Session>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthValue | null>(null)

const toSession = (t: TokenResponse): Session => ({ username: t.username, displayName: t.displayName, role: t.role })

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [status, setStatus] = useState<Status>('loading')
  const [session, setSession] = useState<Session | null>(null)

  const clear = useCallback(() => {
    tokenStore.set(null)
    setSession(null)
    setStatus('anonymous')
    queryClient.clear() // never leave one person's data in memory for the next
  }, [queryClient])

  // On page load the access token is gone (it lives in memory), but the httpOnly refresh cookie may still be valid.
  useEffect(() => {
    let cancelled = false
    refreshSession()
      .then((t) => {
        if (cancelled) return
        setSession(toSession(t))
        setStatus('authenticated')
      })
      .catch(() => {
        if (!cancelled) setStatus('anonymous')
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    onAuthLost(clear)
    return () => onAuthLost(null)
  }, [clear])

  const login = useCallback(async (identifier: string, password: string) => {
    const token = await api.auth.login(identifier, password)
    tokenStore.set(token.accessToken)
    const next = toSession(token)
    setSession(next)
    setStatus('authenticated')
    return next
  }, [])

  const register = useCallback(
    async (values: RegisterRequest) => {
      await api.auth.register(values)
      return login(values.username, values.password)
    },
    [login],
  )

  const logout = useCallback(async () => {
    try {
      await api.auth.logout()
    } catch {
      // even if the server cannot be reached, sign out locally
    }
    clear()
  }, [clear])

  const value = useMemo(() => ({ status, session, login, register, logout }), [status, session, login, register, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>')
  return value
}
