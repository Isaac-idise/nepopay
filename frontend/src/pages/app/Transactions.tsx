import { useState } from 'react'
import { useTransactions } from '../../api/hooks'
import { TransactionRow } from '../../components/TransactionRow'
import { Alert, Button, Card, EmptyState, PageTitle, Skeleton } from '../../components/ui'
import { copy } from '../../lib/personality'

const PAGE_SIZE = 10

export default function Transactions() {
  const [page, setPage] = useState(0)
  const query = useTransactions(page, PAGE_SIZE)

  return (
    <div>
      <PageTitle title="History" subtitle="Everything that has moved in or out of your account." />
      <Card>
        {query.isPending ? (
          <div className="space-y-3">{[0, 1, 2, 3].map((i) => <Skeleton key={i} className="h-14" />)}</div>
        ) : query.isError ? (
          <Alert kind="error" title="Couldn’t load your history">
            <Button variant="ghost" className="mt-2" onClick={() => void query.refetch()}>Try again</Button>
          </Alert>
        ) : query.data.content.length === 0 ? (
          <EmptyState title={copy.emptyTransactions} hint="Your transfers will appear here." />
        ) : (
          <>
            <div className="divide-y divide-ink/5">
              {query.data.content.map((t) => <TransactionRow key={t.reference} tx={t} />)}
            </div>
            <div className="mt-4 flex items-center justify-between text-sm">
              <Button variant="ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Newer</Button>
              <span className="text-ink/65">Page {query.data.page + 1} of {Math.max(1, query.data.totalPages)}</span>
              <Button variant="ghost" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage((p) => p + 1)}>Older →</Button>
            </div>
          </>
        )}
      </Card>
    </div>
  )
}
