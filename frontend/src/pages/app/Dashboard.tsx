import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAccount, useTransactions, useUnreadCount } from '../../api/hooks'
import { useAuth } from '../../auth/AuthContext'
import { Alert, Button, Card, EmptyState, Skeleton, useToast } from '../../components/ui'
import { TransactionRow } from '../../components/TransactionRow'
import { copyText } from '../../lib/format'
import { formatNaira } from '../../lib/money'
import { copy } from '../../lib/personality'

export default function Dashboard() {
  const { session } = useAuth()
  const toast = useToast()
  const [params] = useSearchParams()
  const account = useAccount()
  const recent = useTransactions(0, 5)
  const unread = useUnreadCount()
  const [hidden, setHidden] = useState(() => localStorage.getItem('nepopay.hideBalance') === '1')

  const toggle = () => {
    const next = !hidden
    setHidden(next)
    localStorage.setItem('nepopay.hideBalance', next ? '1' : '0')
  }

  return (
    <div className="space-y-5">
      {params.get('welcome') && (
        <Alert kind="success" title={copy.welcome}>
          Your ₦100,000 demo money don land. Find a friend’s username and send them something!
        </Alert>
      )}

      <section aria-label="Balance" className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-jollof to-jollof-dark p-5 text-white shadow-lg sm:p-6">
        <div aria-hidden="true" className="absolute -right-10 -top-10 size-40 rounded-full bg-plantain/40" />
        <div className="relative">
          <p className="text-sm font-semibold text-white/85">Hi {session?.displayName.split(' ')[0]} 👋🏽  ·  @{session?.username}</p>
          <p className="mt-3 text-xs font-bold uppercase tracking-wide text-white/80">Available balance</p>
          {account.isPending ? (
            <Skeleton className="mt-1 h-10 w-52 !bg-white/25" />
          ) : account.isError ? (
            <p className="mt-1 text-sm">Couldn’t load your balance. Pull to refresh or try again.</p>
          ) : (
            <p className="mt-1 text-4xl font-black tracking-tight" data-testid="balance">
              {hidden ? '₦ ••••••' : formatNaira(account.data.balanceKobo)}
            </p>
          )}
          <div className="mt-4 flex flex-wrap items-center gap-2 text-sm">
            {account.data && (
              <button
                type="button"
                className="rounded-lg bg-white/15 px-2.5 py-1 font-mono text-xs hover:bg-white/25"
                onClick={async () => toast((await copyText(account.data.accountNumber)) ? 'Account number copied' : 'Copy failed', 'info')}
                aria-label={`Account number ${account.data.accountNumber}. Press to copy.`}
              >
                {account.data.accountNumber} ⧉
              </button>
            )}
            <button type="button" className="rounded-lg bg-white/15 px-2.5 py-1 text-xs font-semibold hover:bg-white/25" onClick={toggle}>
              {hidden ? '👁 Show' : '🙈 Hide'}
            </button>
          </div>
        </div>
      </section>

      <div className="grid grid-cols-3 gap-3">
        <Link to="/app/send" className="rounded-2xl bg-ink p-3 text-center text-cream hover:bg-black">
          <span className="block text-2xl" aria-hidden="true">💸</span><span className="text-sm font-bold">Send</span>
        </Link>
        <Link to="/app/beneficiaries" className="rounded-2xl border border-ink/10 bg-white p-3 text-center hover:bg-sand/60">
          <span className="block text-2xl" aria-hidden="true">⭐</span><span className="text-sm font-bold">Saved</span>
        </Link>
        <Link to="/app/notifications" className="relative rounded-2xl border border-ink/10 bg-white p-3 text-center hover:bg-sand/60">
          <span className="block text-2xl" aria-hidden="true">🔔</span><span className="text-sm font-bold">Alerts</span>
          {!!unread.data && <span className="absolute right-2 top-2 rounded-full bg-jollof px-1.5 text-[10px] font-extrabold text-white">{unread.data}</span>}
        </Link>
      </div>

      <Card>
        <div className="mb-1 flex items-center justify-between">
          <h2 className="font-extrabold">Recent activity</h2>
          <Link to="/app/transactions" className="text-sm font-bold text-jollof">See all</Link>
        </div>
        {recent.isPending ? (
          <div className="space-y-3 py-2">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : recent.isError ? (
          <Alert kind="error" title="Couldn’t load your activity">
            <Button variant="ghost" className="mt-2" onClick={() => void recent.refetch()}>Try again</Button>
          </Alert>
        ) : recent.data.content.length === 0 ? (
          <EmptyState emoji="🫙" title={copy.emptyTransactions} hint="Send some money and it will show up here." />
        ) : (
          <div className="divide-y divide-ink/5">
            {recent.data.content.map((t) => <TransactionRow key={t.reference} tx={t} />)}
          </div>
        )}
      </Card>
    </div>
  )
}
