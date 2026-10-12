import { Link, useParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { useTransaction } from '../../api/hooks'
import { Alert, Button, Card, PageTitle, Skeleton, StatusBadge, useToast } from '../../components/ui'
import { copyText, formatDateTime } from '../../lib/format'
import { formatNaira } from '../../lib/money'
import { describeTransaction } from '../../components/TransactionRow'

export default function TransactionDetail() {
  const { reference = '' } = useParams()
  const toast = useToast()
  const query = useTransaction(reference)

  if (query.isPending) return <Skeleton className="h-72" />
  if (query.isError) {
    const notFound = query.error instanceof ApiError && query.error.status === 404
    return (
      <Alert kind="error" title={notFound ? 'Transaction not found' : 'Couldn’t load this transaction'}>
        {notFound ? 'It may belong to someone else, or it was hidden by a demo reset.' : 'Please try again.'}
        <div className="mt-2"><Link to="/app/transactions" className="font-bold underline">Back to history</Link></div>
      </Alert>
    )
  }

  const t = query.data
  const credit = t.direction === 'CREDIT'
  return (
    <div className="space-y-4">
      <PageTitle title="Receipt" action={<StatusBadge status={t.status} />} />
      <Card className="space-y-4">
        <div className="text-center">
          <p className="text-sm text-ink/65">{describeTransaction(t)}</p>
          <p className={`mt-1 text-4xl font-black ${credit ? 'text-mint' : ''}`}>{credit ? '+' : '−'}{formatNaira(t.amountKobo)}</p>
        </div>
        <dl className="divide-y divide-ink/5 text-sm">
          <Line label="Date" value={formatDateTime(t.createdAt)} />
          <Line label="Type" value={t.type.replaceAll('_', ' ').toLowerCase()} />
          <Line label={credit ? 'From' : 'To'} value={t.counterparty.username ? `${t.counterparty.displayName} (@${t.counterparty.username})` : t.counterparty.displayName} />
          {t.description && <Line label="Note" value={t.description} />}
          <div className="flex items-center justify-between gap-3 py-2">
            <dt className="text-ink/65">Reference</dt>
            <dd>
              <button type="button" className="font-mono font-bold"
                onClick={async () => toast((await copyText(t.reference)) ? 'Reference copied' : 'Copy failed')}>
                {t.reference} ⧉
              </button>
            </dd>
          </div>
        </dl>
        {t.status === 'FLAGGED' && (
          <Alert kind="warning" title="Under review">
            This transfer is on hold for a quick safety check. The money is reserved and will be delivered or returned.
          </Alert>
        )}
        {t.type === 'P2P_TRANSFER' && !credit && t.counterparty.username && (
          <Link to={`/app/send?to=${encodeURIComponent(t.counterparty.username)}`}><Button variant="ghost" className="w-full">Send to them again</Button></Link>
        )}
      </Card>

      <Card>
        <h2 className="font-extrabold">Ledger entries</h2>
        <p className="mb-3 text-xs text-ink/65">The accounting records behind this transaction, for your account only.</p>
        {t.ledgerEntries.length === 0 ? (
          <p className="text-sm text-ink/65">No entries for your account yet.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="text-xs uppercase text-ink/60">
                <tr><th className="py-1 pr-3">Entry</th><th className="py-1 pr-3">Amount</th><th className="py-1">Balance after</th></tr>
              </thead>
              <tbody>
                {t.ledgerEntries.map((e, i) => (
                  <tr key={i} className="border-t border-ink/5">
                    <td className="py-2 pr-3 font-bold">{e.entryType}</td>
                    <td className="py-2 pr-3">{formatNaira(e.amountKobo)}</td>
                    <td className="py-2">{formatNaira(e.balanceAfterKobo)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}

function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 py-2">
      <dt className="text-ink/65">{label}</dt>
      <dd className="text-right font-semibold first-letter:uppercase">{value}</dd>
    </div>
  )
}
