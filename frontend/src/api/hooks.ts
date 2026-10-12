import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './endpoints'

export const keys = {
  account: ['account'] as const,
  unread: ['unread'] as const,
  beneficiaries: ['beneficiaries'] as const,
}

// Receiving money is picked up by polling (every 20s) and whenever the tab regains focus.
export const useAccount = () => useQuery({ queryKey: keys.account, queryFn: api.account, refetchInterval: 20_000 })

export const useUnreadCount = () =>
  useQuery({ queryKey: keys.unread, queryFn: api.unreadCount, refetchInterval: 15_000, select: (d) => d.unread })

export const useTransactions = (page: number, size: number) =>
  useQuery({ queryKey: ['transactions', page, size], queryFn: () => api.transactions(page, size), placeholderData: keepPreviousData })

export const useTransaction = (reference: string) =>
  useQuery({ queryKey: ['transaction', reference], queryFn: () => api.transaction(reference) })

export const useNotifications = (page: number, size: number) =>
  useQuery({ queryKey: ['notifications', page, size], queryFn: () => api.notifications(page, size), placeholderData: keepPreviousData })

export const useBeneficiaries = () => useQuery({ queryKey: keys.beneficiaries, queryFn: api.beneficiaries })

export const useUserSearch = (term: string) =>
  useQuery({
    queryKey: ['search', term],
    queryFn: () => api.searchUsers(term),
    enabled: term.trim().length >= 2,
    select: (page) => page.content,
  })

/** After money moves, everything that shows money, history or notifications is stale. */
export function useInvalidateMoney() {
  const client = useQueryClient()
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: keys.account }),
      client.invalidateQueries({ queryKey: ['transactions'] }),
      client.invalidateQueries({ queryKey: ['transaction'] }),
      client.invalidateQueries({ queryKey: ['notifications'] }),
      client.invalidateQueries({ queryKey: keys.unread }),
      client.invalidateQueries({ queryKey: keys.beneficiaries }),
    ])
}
