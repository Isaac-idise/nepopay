import { Link } from 'react-router-dom'
import type { TransactionView, TxType } from '../api/types'
import { formatDateTime } from '../lib/format'
import { formatNaira } from '../lib/money'
import { cx, StatusBadge } from './ui'

const typeLabel: Record<TxType, string> = {
  P2P_TRANSFER: 'Transfer',
  DEMO_WELCOME_CREDIT: 'Welcome credit',
  DEMO_RESET: 'Demo reset',
  REVERSAL: 'Reversal',
  GATEWAY_PAYMENT: 'Payment',
}

export function describeTransaction(t: TransactionView): string {
  const who = t.counterparty.username ? `@${t.counterparty.username}` : t.counterparty.displayName
  if (t.type === 'P2P_TRANSFER') return t.direction === 'DEBIT' ? `To ${who}` : `From ${who}`
  return typeLabel[t.type]
}

export function TransactionRow({ tx }: { tx: TransactionView }) {
  const credit = tx.direction === 'CREDIT'
  return (
    <Link
      to={`/app/transactions/${encodeURIComponent(tx.reference)}`}
      className="flex items-center gap-3 rounded-xl px-2 py-3 hover:bg-sand/60"
    >
      <span
        aria-hidden="true"
        className={cx('grid size-10 shrink-0 place-items-center rounded-full text-lg', credit ? 'bg-emerald-100' : 'bg-orange-100')}
      >
        {credit ? '↓' : '↑'}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate font-semibold">{describeTransaction(tx)}</span>
        <span className="block truncate text-xs text-ink/60">
          {formatDateTime(tx.createdAt)}{tx.description ? ` · ${tx.description}` : ''}
        </span>
      </span>
      <span className="text-right">
        <span className={cx('block font-extrabold', credit ? 'text-mint' : 'text-ink')}>
          {credit ? '+' : '−'}{formatNaira(tx.amountKobo)}
        </span>
        {tx.status !== 'SUCCESSFUL' && <StatusBadge status={tx.status} />}
      </span>
    </Link>
  )
}
