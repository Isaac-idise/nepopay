import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { ApiError } from '../../api/client'
import { api } from '../../api/endpoints'
import type { AlertStatus, FraudAlert } from '../../api/types'
import { Alert, AlertStatusBadge, Button, Card, cx, EmptyState, Field, Input, Modal, PageTitle, RiskBadge, Select, Skeleton, useToast } from '../../components/ui'
import { formatDateTime } from '../../lib/format'
import { formatNaira } from '../../lib/money'
import { errorVoice } from '../../lib/personality'

type Filter = AlertStatus | 'ALL'

export default function AdminFraud() {
  const [status, setStatus] = useState<Filter>('OPEN')
  const [page, setPage] = useState(0)
  const [deciding, setDeciding] = useState<{ alert: FraudAlert; action: 'approve' | 'reject' } | null>(null)
  const query = useQuery({ queryKey: ['admin', 'fraud', status, page], queryFn: () => api.admin.fraudAlerts(status, page) })

  return (
    <div className="space-y-4">
      <PageTitle title="Fraud review" subtitle="Held transfers wait here. Approve to deliver the money, reject to return it."
        action={
          <Select aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value as Filter); setPage(0) }}>
            <option value="OPEN">Open (needs review)</option><option value="APPROVED">Approved</option><option value="REJECTED">Rejected</option>
            <option value="INFO">Info (medium risk, delivered)</option><option value="ALL">All</option>
          </Select>
        } />

      {query.isPending ? <Skeleton className="h-40" /> : query.isError ? <Alert kind="error" title="Couldn’t load alerts" /> :
        query.data.content.length === 0 ? <EmptyState emoji="✅" title="Nothing to review" hint="No alerts match this filter." /> : (
          <>
            <ul className="space-y-3">
              {query.data.content.map((a) => <AlertCard key={a.id} alert={a} onDecide={(action) => setDeciding({ alert: a, action })} />)}
            </ul>
            <div className="flex items-center justify-between text-sm">
              <Button variant="ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Newer</Button>
              <span className="text-ink/65">Page {query.data.page + 1} of {Math.max(1, query.data.totalPages)}</span>
              <Button variant="ghost" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage((p) => p + 1)}>Older →</Button>
            </div>
          </>
        )}

      {deciding && <DecisionModal key={deciding.alert.id + deciding.action} {...deciding} onClose={() => setDeciding(null)} />}
    </div>
  )
}

function AlertCard({ alert: a, onDecide }: { alert: FraudAlert; onDecide: (a: 'approve' | 'reject') => void }) {
  const t = a.transaction
  return (
    <li>
      <Card className={cx(a.status === 'OPEN' && 'border-amber-300')}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="flex flex-wrap items-center gap-2">
            <RiskBadge level={a.riskLevel} score={a.riskScore} />
            <AlertStatusBadge status={a.status} />
          </div>
          <span className="text-xs text-ink/55">{formatDateTime(a.createdAt)}</span>
        </div>
        <p className="mt-3 text-lg font-black">
          {formatNaira(t.amountKobo)} <span className="text-sm font-semibold text-ink/65">from @{t.sender.username} to @{t.receiver.username}</span>
        </p>
        <p className="font-mono text-xs text-ink/60">{t.reference}</p>
        <h3 className="mt-3 text-xs font-bold uppercase tracking-wide text-ink/60">Why it was flagged</h3>
        <ul className="mt-1 space-y-1 text-sm">
          {a.reasons.map((r) => (
            <li key={r.rule} className="flex gap-2"><span className="w-9 shrink-0 font-black text-jollof">+{r.points}</span><span><span className="font-mono text-xs font-bold">{r.rule}</span> · {r.reason}</span></li>
          ))}
        </ul>
        {a.status === 'OPEN' ? (
          <div className="mt-4 flex gap-2">
            <Button variant="success" onClick={() => onDecide('approve')}>Approve & deliver</Button>
            <Button variant="danger" onClick={() => onDecide('reject')}>Reject & refund</Button>
          </div>
        ) : a.reviewedAt && (
          <p className="mt-3 text-xs text-ink/60">Reviewed {formatDateTime(a.reviewedAt)}{a.reviewNote ? ` · “${a.reviewNote}”` : ''}</p>
        )}
      </Card>
    </li>
  )
}

function DecisionModal({ alert, action, onClose }: { alert: FraudAlert; action: 'approve' | 'reject'; onClose: () => void }) {
  const [note, setNote] = useState('')
  const toast = useToast()
  const client = useQueryClient()
  const run = useMutation({
    mutationFn: () => (action === 'approve' ? api.admin.approve(alert.id, note.trim()) : api.admin.reject(alert.id, note.trim())),
    onSuccess: () => {
      toast(action === 'approve' ? 'Approved: money delivered' : 'Rejected: money returned', 'success')
      void client.invalidateQueries({ queryKey: ['admin'] })
      onClose()
    },
  })
  const voice = run.error instanceof ApiError ? errorVoice(run.error.code, run.error.message) : null
  const approve = action === 'approve'
  return (
    <Modal open onClose={onClose} title={approve ? 'Approve this transfer?' : 'Reject this transfer?'}>
      <div className="space-y-3">
        <p className="text-sm text-ink/75">
          {approve
            ? `${formatNaira(alert.transaction.amountKobo)} will be released to @${alert.transaction.receiver.username}.`
            : `${formatNaira(alert.transaction.amountKobo)} will be returned to @${alert.transaction.sender.username}.`}
        </p>
        {voice && <Alert kind="error" title={voice.title}>{voice.detail}</Alert>}
        <Field label={approve ? 'Note (optional)' : 'Reason (required)'} htmlFor="note">
          <Input id="note" maxLength={200} value={note} onChange={(e) => setNote(e.target.value)} autoFocus />
        </Field>
        <div className="flex gap-2">
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button variant={approve ? 'success' : 'danger'} className="flex-1" loading={run.isPending}
            disabled={!approve && note.trim() === ''} onClick={() => run.mutate()}>
            {approve ? 'Approve' : 'Reject'}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
