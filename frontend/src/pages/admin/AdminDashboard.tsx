import { useMutation, useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { api } from '../../api/endpoints'
import { Alert, Button, Card, cx, PageTitle, Skeleton } from '../../components/ui'
import { formatDateTime } from '../../lib/format'
import { formatNaira } from '../../lib/money'
import { errorVoice } from '../../lib/personality'
import { Link } from 'react-router-dom'

function Stat({ label, value, hint, tone }: { label: string; value: string | number; hint?: string; tone?: 'warn' | 'good' }) {
  return (
    <Card className={cx(tone === 'warn' && 'border-amber-300 bg-amber-50')}>
      <p className="text-xs font-bold uppercase tracking-wide text-ink/60">{label}</p>
      <p className="mt-1 text-2xl font-black">{value}</p>
      {hint && <p className="text-xs text-ink/60">{hint}</p>}
    </Card>
  )
}

export default function AdminDashboard() {
  const dash = useQuery({ queryKey: ['admin', 'dashboard'], queryFn: api.admin.dashboard, refetchInterval: 30_000 })
  const recon = useMutation({ mutationFn: api.admin.reconciliation })

  if (dash.isPending) return <div className="grid gap-3 sm:grid-cols-3">{[0, 1, 2, 3, 4, 5].map((i) => <Skeleton key={i} className="h-24" />)}</div>
  if (dash.isError) {
    const v = dash.error instanceof ApiError ? errorVoice(dash.error.code, dash.error.message) : errorVoice('UNEXPECTED', '')
    return <Alert kind="error" title={v.title}>{v.detail}</Alert>
  }
  const d = dash.data
  const r = recon.data

  return (
    <div className="space-y-6">
      <PageTitle title="Overview" subtitle={`Updated ${formatDateTime(d.generatedAt)}`} />

      <section aria-label="Users" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Users" value={d.users.total} hint={`${d.users.registeredToday} joined today`} />
        <Stat label="Active" value={d.users.active} />
        <Stat label="Suspended" value={d.users.suspended} tone={d.users.suspended > 0 ? 'warn' : undefined} />
        <Stat label="Admins" value={d.users.admins} />
      </section>

      <section aria-label="Transfers" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Successful transfers" value={d.transfers.successful} hint={`of ${d.transfers.total} total`} />
        <Stat label="Held for review" value={d.transfers.flagged} tone={d.transfers.flagged > 0 ? 'warn' : undefined} />
        <Stat label="Reversed" value={d.transfers.reversed} />
        <Stat label="Failed attempts today" value={d.transfers.failedAttemptsToday} hint={`${d.transfers.failedAttemptsAllTime} all time`} />
      </section>

      <section aria-label="Volume and fraud" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="Volume today" value={formatNaira(d.volume.todayKobo)} />
        <Stat label="Volume, 7 days" value={formatNaira(d.volume.last7DaysKobo)} />
        <Stat label="Volume, all time" value={formatNaira(d.volume.allTimeKobo)} />
        <Link to="/admin/fraud" className="block">
          <Stat label="Open fraud alerts" value={d.fraud.open} tone={d.fraud.open > 0 ? 'warn' : 'good'} hint={`${formatNaira(d.fraud.heldKobo)} on hold`} />
        </Link>
      </section>

      <Card>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-extrabold">Ledger reconciliation</h2>
            <p className="text-sm text-ink/65">Re-checks the books from scratch: balances vs entries, debits vs credits, held money vs flagged transfers.</p>
          </div>
          <Button onClick={() => recon.mutate()} loading={recon.isPending}>Run books check</Button>
        </div>
        {recon.isError && <div className="mt-3"><Alert kind="error" title="Check failed to run" /></div>}
        {r && (
          <div className="mt-4 space-y-3 text-sm">
            <Alert kind={r.ok ? 'success' : 'error'} title={r.ok ? '✅ The books balance' : '🚨 Problems found'}>
              Checked {formatDateTime(r.checkedAt)}
            </Alert>
            <ul className="grid gap-1 sm:grid-cols-2">
              <Check ok={r.totalBalanceKobo === 0} text={`All balances add up to ${formatNaira(r.totalBalanceKobo)} (should be ₦0.00)`} />
              <Check ok={r.accountDrift.length === 0} text={`${r.accountDrift.length} account(s) disagree with their ledger entries`} />
              <Check ok={r.unbalancedTransactions.length === 0} text={`${r.unbalancedTransactions.length} transaction(s) with unequal debits and credits`} />
              <Check ok={r.transactionsWithoutEntries === 0} text={`${r.transactionsWithoutEntries} transaction(s) missing ledger entries`} />
              <Check ok={r.holdMatchesFlagged} text={`Held ${formatNaira(r.holdBalanceKobo)} vs flagged ${formatNaira(r.flaggedTotalKobo)}`} />
            </ul>
            {r.accountDrift.map((a) => (
              <p key={a.accountNumber} className="rounded-lg bg-red-50 px-3 py-2 text-red-900">
                {a.username ?? a.accountType} ({a.accountNumber}): balance {formatNaira(a.balanceKobo)}, ledger says {formatNaira(a.ledgerKobo)}
              </p>
            ))}
          </div>
        )}
      </Card>

      <Card>
        <h2 className="mb-2 font-extrabold">Recent activity</h2>
        <ul className="divide-y divide-ink/5 text-sm">
          {d.recentAudit.map((a) => (
            <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
              <span><span className="font-mono font-bold">{a.action}</span> {a.username && <span className="text-ink/65">by @{a.username}</span>}</span>
              <span className="text-xs text-ink/55">{formatDateTime(a.createdAt)}</span>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  )
}

function Check({ ok, text }: { ok: boolean; text: string }) {
  return <li className="flex gap-2"><span aria-hidden="true">{ok ? '✅' : '❌'}</span><span>{text}</span></li>
}
