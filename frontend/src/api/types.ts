/** These mirror the backend's JSON exactly (money is always whole kobo). */
export interface ApiEnvelope<T> {
  success: boolean
  data?: T
  errorCode?: string
  message?: string
  timestamp: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type Role = 'USER' | 'ADMIN'
export type TxStatus = 'PENDING' | 'PROCESSING' | 'SUCCESSFUL' | 'FAILED' | 'REVERSED' | 'FLAGGED'
export type TxType = 'P2P_TRANSFER' | 'DEMO_WELCOME_CREDIT' | 'DEMO_RESET' | 'REVERSAL' | 'GATEWAY_PAYMENT'

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
  username: string
  displayName: string
  role: Role
}

export interface RegisterRequest {
  firstName: string
  lastName: string
  email: string
  phone: string
  username: string
  password: string
  pin: string
}

export interface RegisterResponse {
  username: string
  displayName: string
  accountNumber: string
  balanceKobo: number
  currency: string
  welcomeTransactionReference: string
}

export interface Me {
  username: string
  displayName: string
  email: string
  phone: string
  role: Role
  status: string
  createdAt: string
}

export interface Account {
  accountNumber: string
  balanceKobo: number
  currency: string
  status: string
}

export interface UserSearchResult {
  username: string
  displayName: string
  accountLast4: string
}

export interface TransferRequest {
  recipientUsername: string
  amountKobo: number
  description?: string
  pin: string
}

export interface TransferResponse {
  reference: string
  status: TxStatus
  amountKobo: number
  recipientUsername: string
  recipientDisplayName: string
  description: string | null
  createdAt: string
  idempotentReplay: boolean
  message: string | null
}

export interface Party {
  username: string | null
  displayName: string
}

export interface TransactionView {
  reference: string
  type: TxType
  status: TxStatus
  direction: 'DEBIT' | 'CREDIT'
  amountKobo: number
  counterparty: Party
  description: string | null
  createdAt: string
}

export interface EntryView {
  entryType: 'DEBIT' | 'CREDIT'
  amountKobo: number
  balanceAfterKobo: number
  createdAt: string
}

export interface TransactionDetail extends TransactionView {
  ledgerEntries: EntryView[]
}

export type NotificationType =
  | 'WELCOME' | 'TRANSFER_SENT' | 'TRANSFER_RECEIVED' | 'SECURITY' | 'FRAUD_ALERT' | 'SYSTEM'

export interface NotificationView {
  id: string
  title: string
  message: string
  type: NotificationType
  read: boolean
  createdAt: string
}

export interface Beneficiary {
  username: string
  displayName: string
  accountLast4: string
  nickname: string | null
  createdAt: string
}

export interface DemoResetResponse {
  balanceKobo: number
  adjustmentReference: string | null
}

// ---------------- admin ----------------
export interface AdminUser {
  username: string
  displayName: string
  email: string
  phone: string
  role: Role
  status: string
  createdAt: string
  accountNumber: string | null
  balanceKobo: number | null
}

export interface AuditEntry {
  id: number
  action: string
  username: string | null
  entityType: string | null
  entityId: string | null
  metadata: Record<string, unknown> | null
  createdAt: string
}

export interface AdminDashboard {
  users: { total: number; active: number; suspended: number; admins: number; registeredToday: number }
  transfers: {
    total: number; successful: number; flagged: number; reversed: number
    failedAttemptsToday: number; failedAttemptsAllTime: number
  }
  volume: { todayKobo: number; last7DaysKobo: number; allTimeKobo: number }
  fraud: { open: number; info: number; approved: number; rejected: number; heldKobo: number }
  recentAudit: AuditEntry[]
  generatedAt: string
}

export interface AdminTransaction {
  reference: string
  type: TxType
  status: TxStatus
  amountKobo: number
  riskScore: number
  sender: string
  receiver: string
  description: string | null
  createdAt: string
}

export interface AdminEntry {
  account: string
  entryType: 'DEBIT' | 'CREDIT'
  amountKobo: number
  balanceAfterKobo: number
  createdAt: string
}

export interface AdminTransactionDetail {
  transaction: AdminTransaction
  ledgerEntries: AdminEntry[]
}

export interface RuleHit {
  rule: string
  points: number
  reason: string
}

export type AlertStatus = 'OPEN' | 'APPROVED' | 'REJECTED' | 'INFO'
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface FraudAlert {
  id: string
  status: AlertStatus
  riskScore: number
  riskLevel: RiskLevel
  reasons: RuleHit[]
  transaction: {
    reference: string
    type: TxType
    status: TxStatus
    amountKobo: number
    description: string | null
    createdAt: string
    sender: { username: string | null; displayName: string }
    receiver: { username: string | null; displayName: string }
  }
  createdAt: string
  reviewedAt: string | null
  reviewNote: string | null
}

export interface Reconciliation {
  ok: boolean
  checkedAt: string
  totalBalanceKobo: number
  accountDrift: { accountNumber: string; accountType: string; username: string | null; balanceKobo: number; ledgerKobo: number }[]
  unbalancedTransactions: { reference: string; debitsKobo: number; creditsKobo: number }[]
  transactionsWithoutEntries: number
  holdBalanceKobo: number
  flaggedTotalKobo: number
  holdMatchesFlagged: boolean
  staleIdempotencyKeys: number
}
