import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/endpoints'
import { Alert, Button, Card, EmptyState, Field, Input, PageTitle, Skeleton } from '../../components/ui'
import { formatDateTime } from '../../lib/format'

interface Filters { action: string; username: string; from: string; to: string }
const empty: Filters = { action: '', username: '', from: '', to: '' }

export default function AdminAudit() {
  const [draft, setDraft] = useState<Filters>(empty)
  const [applied, setApplied] = useState<Filters>(empty)
  const [page, setPage] = useState(0)
  const params = { action: applied.action.trim() || undefined, username: applied.username.trim() || undefined, from: applied.from || undefined, to: applied.to || undefined, page }
  const query = useQuery({ queryKey: ['admin', 'audit', params], queryFn: () => api.admin.audit(params) })
  const set = (k: keyof Filters) => (e: { target: { value: string } }) => setDraft((d) => ({ ...d, [k]: e.target.value }))

  return (
    <div className="space-y-4">
      <PageTitle title="Audit log" subtitle="An append-only record of important actions. It cannot be edited or deleted." />
      <Card>
        <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" onSubmit={(e) => { e.preventDefault(); setApplied(draft); setPage(0) }}>
          <Field label="Action" htmlFor="a-action"><Input id="a-action" placeholder="e.g. TRANSFER" autoCapitalize="characters" value={draft.action} onChange={set('action')} /></Field>
          <Field label="Username" htmlFor="a-user"><Input id="a-user" autoCapitalize="none" value={draft.username} onChange={set('username')} /></Field>
          <Field label="From date" htmlFor="a-from"><Input id="a-from" type="date" value={draft.from} onChange={set('from')} /></Field>
          <Field label="To date" htmlFor="a-to"><Input id="a-to" type="date" value={draft.to} onChange={set('to')} /></Field>
          <div className="flex gap-2 sm:col-span-2 lg:col-span-4">
            <Button type="submit">Apply filters</Button>
            <Button variant="ghost" onClick={() => { setDraft(empty); setApplied(empty); setPage(0) }}>Clear</Button>
          </div>
        </form>
      </Card>

      {query.isPending ? <Skeleton className="h-48" /> : query.isError ? <Alert kind="error" title="Couldn’t load the audit log" /> :
        query.data.content.length === 0 ? <EmptyState emoji="📜" title="No entries match" /> : (
          <Card className="overflow-x-auto !p-0">
            <table className="w-full min-w-[680px] text-left text-sm">
              <thead className="bg-sand/60 text-xs uppercase text-ink/60"><tr><th className="p-3">When</th><th className="p-3">Action</th><th className="p-3">User</th><th className="p-3">Target</th><th className="p-3">Details</th></tr></thead>
              <tbody>
                {query.data.content.map((a) => (
                  <tr key={a.id} className="border-t border-ink/5 align-top">
                    <td className="p-3 text-xs">{formatDateTime(a.createdAt)}</td>
                    <td className="p-3 font-mono text-xs font-bold">{a.action}</td>
                    <td className="p-3">{a.username ? `@${a.username}` : '—'}</td>
                    <td className="p-3 text-xs">{a.entityType ?? '—'}{a.entityId ? ` · ${a.entityId}` : ''}</td>
                    <td className="p-3 font-mono text-xs text-ink/70">{a.metadata ? JSON.stringify(a.metadata) : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="flex items-center justify-between border-t border-ink/5 p-3 text-sm">
              <Button variant="ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Newer</Button>
              <span className="text-ink/65">{query.data.totalElements} entries · page {query.data.page + 1} of {Math.max(1, query.data.totalPages)}</span>
              <Button variant="ghost" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage((p) => p + 1)}>Older →</Button>
            </div>
          </Card>
        )}
    </div>
  )
}
