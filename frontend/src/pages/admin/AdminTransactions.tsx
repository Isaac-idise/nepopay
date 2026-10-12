import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/endpoints'
import { Alert, Button, Card, EmptyState, Field, Input, Modal, PageTitle, Select, Skeleton, StatusBadge } from '../../components/ui'
import { formatDateTime } from '../../lib/format'
import { formatNaira, nairaToKobo } from '../../lib/money'

interface Filters { status: string; type: string; user: string; from: string; to: string; minNaira: string; maxNaira: string }
const empty: Filters = { status: '', type: '', user: '', from: '', to: '', minNaira: '', maxNaira: '' }

export default function AdminTransactions() {
  const [draft, setDraft] = useState<Filters>(empty)
  const [applied, setApplied] = useState<Filters>(empty)
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState<string | null>(null)

  const params = {
    status: applied.status || undefined, type: applied.type || undefined, user: applied.user.trim() || undefined,
    from: applied.from || undefined, to: applied.to || undefined,
    minKobo: nairaToKobo(applied.minNaira) ?? undefined, maxKobo: nairaToKobo(applied.maxNaira) ?? undefined, page,
  }
  const query = useQuery({ queryKey: ['admin', 'transactions', params], queryFn: () => api.admin.transactions(params) })
  const set = (k: keyof Filters) => (e: { target: { value: string } }) => setDraft((d) => ({ ...d, [k]: e.target.value }))

  return (
    <div className="space-y-4">
      <PageTitle title="Transactions" subtitle="Every transaction on the platform, with its ledger entries." />
      <Card>
        <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" onSubmit={(e) => { e.preventDefault(); setApplied(draft); setPage(0) }}>
          <Field label="User" htmlFor="f-user"><Input id="f-user" placeholder="username" autoCapitalize="none" value={draft.user} onChange={set('user')} /></Field>
          <Field label="Status" htmlFor="f-status">
            <Select id="f-status" className="w-full" value={draft.status} onChange={set('status')}>
              <option value="">Any</option>
              {['SUCCESSFUL', 'FLAGGED', 'REVERSED', 'FAILED', 'PROCESSING', 'PENDING'].map((s) => <option key={s}>{s}</option>)}
            </Select>
          </Field>
          <Field label="Type" htmlFor="f-type">
            <Select id="f-type" className="w-full" value={draft.type} onChange={set('type')}>
              <option value="">Any</option>
              {['P2P_TRANSFER', 'DEMO_WELCOME_CREDIT', 'DEMO_RESET', 'REVERSAL', 'GATEWAY_PAYMENT'].map((s) => <option key={s}>{s}</option>)}
            </Select>
          </Field>
          <div />
          <Field label="From date" htmlFor="f-from"><Input id="f-from" type="date" value={draft.from} onChange={set('from')} /></Field>
          <Field label="To date" htmlFor="f-to"><Input id="f-to" type="date" value={draft.to} onChange={set('to')} /></Field>
          <Field label="Min amount (₦)" htmlFor="f-min"><Input id="f-min" inputMode="decimal" value={draft.minNaira} onChange={set('minNaira')} /></Field>
          <Field label="Max amount (₦)" htmlFor="f-max"><Input id="f-max" inputMode="decimal" value={draft.maxNaira} onChange={set('maxNaira')} /></Field>
          <div className="flex gap-2 sm:col-span-2 lg:col-span-4">
            <Button type="submit">Apply filters</Button>
            <Button variant="ghost" onClick={() => { setDraft(empty); setApplied(empty); setPage(0) }}>Clear</Button>
          </div>
        </form>
      </Card>

      {query.isPending ? <Skeleton className="h-48" /> : query.isError ? <Alert kind="error" title="Couldn’t load transactions" /> :
        query.data.content.length === 0 ? <EmptyState emoji="🔎" title="No transactions match" /> : (
          <Card className="overflow-x-auto !p-0">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="bg-sand/60 text-xs uppercase text-ink/60">
                <tr><th className="p-3">When</th><th className="p-3">Reference</th><th className="p-3">From → To</th><th className="p-3">Amount</th><th className="p-3">Risk</th><th className="p-3">Status</th></tr>
              </thead>
              <tbody>
                {query.data.content.map((t) => (
                  <tr key={t.reference} className="cursor-pointer border-t border-ink/5 hover:bg-sand/40" onClick={() => setOpen(t.reference)}>
                    <td className="p-3 text-xs">{formatDateTime(t.createdAt)}</td>
                    <td className="p-3 font-mono text-xs">{t.reference}</td>
                    <td className="p-3">{t.sender} → {t.receiver}<p className="text-xs text-ink/55">{t.type.replaceAll('_', ' ').toLowerCase()}</p></td>
                    <td className="p-3 font-semibold">{formatNaira(t.amountKobo)}</td>
                    <td className="p-3">{t.riskScore}</td>
                    <td className="p-3"><StatusBadge status={t.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="flex items-center justify-between border-t border-ink/5 p-3 text-sm">
              <Button variant="ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Newer</Button>
              <span className="text-ink/65">{query.data.totalElements} results · page {query.data.page + 1} of {Math.max(1, query.data.totalPages)}</span>
              <Button variant="ghost" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage((p) => p + 1)}>Older →</Button>
            </div>
          </Card>
        )}

      {open && <DetailModal reference={open} onClose={() => setOpen(null)} />}
    </div>
  )
}

function DetailModal({ reference, onClose }: { reference: string; onClose: () => void }) {
  const query = useQuery({ queryKey: ['admin', 'transaction', reference], queryFn: () => api.admin.transaction(reference) })
  return (
    <Modal open onClose={onClose} title="Transaction detail">
      {query.isPending ? <Skeleton className="h-40" /> : query.isError ? <Alert kind="error" title="Couldn’t load this transaction" /> : (
        <div className="space-y-3 text-sm">
          <p className="font-mono text-xs">{query.data.transaction.reference}</p>
          <p className="text-2xl font-black">{formatNaira(query.data.transaction.amountKobo)}</p>
          <p>{query.data.transaction.sender} → {query.data.transaction.receiver} · <StatusBadge status={query.data.transaction.status} /> · risk {query.data.transaction.riskScore}</p>
          <h3 className="pt-2 text-xs font-bold uppercase tracking-wide text-ink/60">Ledger entries</h3>
          <table className="w-full text-left">
            <thead className="text-xs text-ink/60"><tr><th className="py-1">Account</th><th>Entry</th><th>Amount</th><th>Balance after</th></tr></thead>
            <tbody>
              {query.data.ledgerEntries.map((e, i) => (
                <tr key={i} className="border-t border-ink/5"><td className="py-1.5 font-semibold">{e.account}</td><td>{e.entryType}</td><td>{formatNaira(e.amountKobo)}</td><td>{formatNaira(e.balanceAfterKobo)}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Modal>
  )
}
