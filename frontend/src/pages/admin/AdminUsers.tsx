import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { ApiError } from '../../api/client'
import { api } from '../../api/endpoints'
import type { AdminUser } from '../../api/types'
import { Alert, Button, Card, cx, EmptyState, Field, Input, Modal, PageTitle, Select, Skeleton, useToast } from '../../components/ui'
import { formatDate } from '../../lib/format'
import { formatNaira } from '../../lib/money'
import { errorVoice } from '../../lib/personality'

export default function AdminUsers() {
  const [q, setQ] = useState('')
  const [debounced, setDebounced] = useState('')
  const [status, setStatus] = useState('ALL')
  const [page, setPage] = useState(0)
  const [target, setTarget] = useState<AdminUser | null>(null)

  useEffect(() => {
    const id = setTimeout(() => { setDebounced(q.trim()); setPage(0) }, 300)
    return () => clearTimeout(id)
  }, [q])

  const query = useQuery({ queryKey: ['admin', 'users', debounced, status, page], queryFn: () => api.admin.users({ q: debounced, status, page }) })

  return (
    <div className="space-y-4">
      <PageTitle title="Users" />
      <Card className="flex flex-wrap gap-3">
        <Input aria-label="Search users" placeholder="Search name, username or email" className="max-w-sm flex-1" value={q} onChange={(e) => setQ(e.target.value)} />
        <Select aria-label="Status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0) }}>
          <option value="ALL">All statuses</option><option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option>
        </Select>
      </Card>

      {query.isPending ? <Skeleton className="h-48" /> : query.isError ? <Alert kind="error" title="Couldn’t load users" /> :
        query.data.content.length === 0 ? <EmptyState emoji="🔎" title="No users match" /> : (
          <Card className="overflow-x-auto !p-0">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead className="bg-sand/60 text-xs uppercase text-ink/60">
                <tr><th className="p-3">User</th><th className="p-3">Contact</th><th className="p-3">Account</th><th className="p-3">Balance</th><th className="p-3">Status</th><th className="p-3" /></tr>
              </thead>
              <tbody>
                {query.data.content.map((u) => (
                  <tr key={u.username} className="border-t border-ink/5">
                    <td className="p-3"><p className="font-bold">{u.displayName}</p><p className="text-xs text-ink/60">@{u.username} · joined {formatDate(u.createdAt)}</p></td>
                    <td className="p-3 text-xs"><p>{u.email}</p><p className="text-ink/60">{u.phone}</p></td>
                    <td className="p-3 font-mono text-xs">{u.accountNumber ?? '—'}</td>
                    <td className="p-3 font-semibold">{u.balanceKobo === null ? '—' : formatNaira(u.balanceKobo)}</td>
                    <td className="p-3">
                      <span className={cx('rounded-full px-2 py-0.5 text-xs font-bold', u.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-800' : 'bg-red-100 text-red-800')}>{u.status}</span>
                      {u.role === 'ADMIN' && <span className="ml-1 rounded-full bg-ink px-2 py-0.5 text-xs font-bold text-cream">ADMIN</span>}
                    </td>
                    <td className="p-3 text-right">
                      {u.role === 'USER' && (
                        <Button variant={u.status === 'ACTIVE' ? 'danger' : 'success'} className="!min-h-9 !px-3 !py-1.5" onClick={() => setTarget(u)}>
                          {u.status === 'ACTIVE' ? 'Suspend' : 'Reactivate'}
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="flex items-center justify-between border-t border-ink/5 p-3 text-sm">
              <Button variant="ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Prev</Button>
              <span className="text-ink/65">{query.data.totalElements} users · page {query.data.page + 1} of {Math.max(1, query.data.totalPages)}</span>
              <Button variant="ghost" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage((p) => p + 1)}>Next →</Button>
            </div>
          </Card>
        )}

      {target && <StatusModal key={target.username} user={target} onClose={() => setTarget(null)} />}
    </div>
  )
}

function StatusModal({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const [reason, setReason] = useState('')
  const toast = useToast()
  const client = useQueryClient()
  const suspending = user.status === 'ACTIVE'
  const run = useMutation({
    mutationFn: () => api.admin.setUserStatus(user.username, suspending ? 'SUSPENDED' : 'ACTIVE', reason.trim()),
    onSuccess: () => { toast(suspending ? 'User suspended' : 'User reactivated', 'success'); void client.invalidateQueries({ queryKey: ['admin'] }); onClose() },
  })
  const voice = run.error instanceof ApiError ? errorVoice(run.error.code, run.error.message) : null
  return (
    <Modal open onClose={onClose} title={`${suspending ? 'Suspend' : 'Reactivate'} @${user.username}?`}>
      <div className="space-y-3">
        <p className="text-sm text-ink/75">
          {suspending ? 'They will be signed out, unable to log in, and unable to send or receive money.' : 'They will be able to log in and use their account again.'}
        </p>
        {voice && <Alert kind="error" title={voice.title}>{voice.detail}</Alert>}
        <Field label={suspending ? 'Reason (required)' : 'Note (optional)'} htmlFor="reason">
          <Input id="reason" maxLength={200} value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
        </Field>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant={suspending ? 'danger' : 'success'} className="flex-1" loading={run.isPending}
            disabled={suspending && reason.trim() === ''} onClick={() => run.mutate()}>
            {suspending ? 'Suspend' : 'Reactivate'}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
