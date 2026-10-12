import { qs, request } from './client'
import type {
  Account, AdminDashboard, AdminTransaction, AdminTransactionDetail, AdminUser, AlertStatus, AuditEntry,
  Beneficiary, DemoResetResponse, FraudAlert, Me, NotificationView, Page, Reconciliation, RegisterRequest,
  RegisterResponse, TokenResponse, TransactionDetail, TransactionView, TransferRequest, TransferResponse,
  UserSearchResult,
} from './types'

const post = <T>(path: string, body?: unknown, auth = true) => request<T>(path, { method: 'POST', body, auth })

export const api = {
  auth: {
    register: (body: RegisterRequest) => post<RegisterResponse>('/auth/register', body, false),
    login: (identifier: string, password: string) =>
      post<TokenResponse>('/auth/login', { identifier, password }, false),
    logout: () => post<void>('/auth/logout', undefined, false),
  },

  me: () => request<Me>('/users/me'),
  account: () => request<Account>('/accounts/me'),
  searchUsers: (q: string) => request<Page<UserSearchResult>>(`/users/search${qs({ q, size: 8 })}`),

  changePin: (currentPin: string, newPin: string) =>
    request<void>('/users/me/pin', { method: 'PATCH', body: { currentPin, newPin } }),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>('/users/me/password', { method: 'PATCH', body: { currentPassword, newPassword } }),

  transfer: (body: TransferRequest, idempotencyKey: string) =>
    request<TransferResponse>('/transfers', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } }),

  transactions: (page: number, size: number) =>
    request<Page<TransactionView>>(`/transactions${qs({ page, size })}`),
  transaction: (reference: string) =>
    request<TransactionDetail>(`/transactions/${encodeURIComponent(reference)}`),

  beneficiaries: () => request<Beneficiary[]>('/beneficiaries'),
  addBeneficiary: (username: string, nickname?: string) =>
    post<Beneficiary>('/beneficiaries', { username, nickname: nickname || undefined }),
  removeBeneficiary: (username: string) =>
    request<void>(`/beneficiaries/${encodeURIComponent(username)}`, { method: 'DELETE' }),

  notifications: (page: number, size: number) =>
    request<Page<NotificationView>>(`/notifications${qs({ page, size })}`),
  unreadCount: () => request<{ unread: number }>('/notifications/unread-count'),
  markRead: (id: string) => post<void>(`/notifications/${id}/read`),
  markAllRead: () => post<{ updated: number }>('/notifications/read-all'),

  resetDemo: (pin: string) => post<DemoResetResponse>('/demo/reset', { pin }),

  admin: {
    dashboard: () => request<AdminDashboard>('/admin/dashboard'),
    reconciliation: () => request<Reconciliation>('/admin/ledger/reconciliation'),
    users: (p: { q?: string; status?: string; page: number }) =>
      request<Page<AdminUser>>(`/admin/users${qs({ q: p.q, status: p.status, page: p.page, size: 15 })}`),
    setUserStatus: (username: string, status: 'ACTIVE' | 'SUSPENDED', reason?: string) =>
      request<AdminUser>(`/admin/users/${encodeURIComponent(username)}/status`, {
        method: 'PATCH', body: { status, reason: reason || undefined },
      }),
    transactions: (p: Record<string, string | number | undefined>) =>
      request<Page<AdminTransaction>>(`/admin/transactions${qs({ ...p, size: 15 })}`),
    transaction: (reference: string) =>
      request<AdminTransactionDetail>(`/admin/transactions/${encodeURIComponent(reference)}`),
    audit: (p: Record<string, string | number | undefined>) =>
      request<Page<AuditEntry>>(`/admin/audit-logs${qs({ ...p, size: 20 })}`),
    fraudAlerts: (status: AlertStatus | 'ALL', page: number) =>
      request<Page<FraudAlert>>(`/admin/fraud-alerts${qs({ status, page, size: 10 })}`),
    approve: (id: string, note?: string) =>
      post<FraudAlert>(`/admin/fraud-alerts/${id}/approve`, { note: note || undefined }),
    reject: (id: string, note: string) => post<FraudAlert>(`/admin/fraud-alerts/${id}/reject`, { note }),
  },
}
