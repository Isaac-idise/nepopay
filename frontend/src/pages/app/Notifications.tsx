import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../../api/endpoints'
import { keys, useNotifications } from '../../api/hooks'
import type { NotificationType, NotificationView } from '../../api/types'
import { Alert, Button, cx, EmptyState, PageTitle, Skeleton } from '../../components/ui'
import { formatDateTime } from '../../lib/format'
import { copy } from '../../lib/personality'

const icons: Record<NotificationType, string> = {
  WELCOME: '🎉', TRANSFER_SENT: '💸', TRANSFER_RECEIVED: '💰', SECURITY: '🔐', FRAUD_ALERT: '🧐', SYSTEM: '📣',
}

export default function Notifications() {
  const [page, setPage] = useState(0)
  const query = useNotifications(page, 15)
  const client = useQueryClient()
  const refresh = () => {
    void client.invalidateQueries({ queryKey: ['notifications'] })
    void client.invalidateQueries({ queryKey: keys.unread })
  }
  const markRead = useMutation({ mutationFn: (id: string) => api.markRead(id), onSuccess: refresh })
  const markAll = useMutation({ mutationFn: () => api.markAllRead(), onSuccess: refresh })

  const unreadOnPage = query.data?.content.filter((n) => !n.read).length ?? 0

  return (
    <div>
      <PageTitle title="Alerts" subtitle="Money in, money out, and anything that needs your attention."
        action={<Button variant="ghost" onClick={() => markAll.mutate()} loading={markAll.isPending} disabled={unreadOnPage === 0}>Mark all read</Button>} />
      {query.isPending ? (
        <div className="space-y-3">{[0, 1, 2].map((i) => <Skeleton key={i} className="h-20" />)}</div>
      ) : query.isError ? (
        <Alert kind="error" title="Couldn’t load your alerts"><Button variant="ghost" className="mt-2" onClick={() => void query.refetch()}>Try again</Button></Alert>
      ) : query.data.content.length === 0 ? (
        <EmptyState emoji="🔕" title={copy.emptyNotifications} />
      ) : (
        <>
          <ul className="space-y-2">
            {query.data.content.map((n) => <Item key={n.id} n={n} onOpen={() => !n.read && markRead.mutate(n.id)} />)}
          </ul>
          <div className="mt-4 flex items-center justify-between text-sm">
            <Button variant="ghost" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>← Newer</Button>
            <span className="text-ink/65">Page {query.data.page + 1} of {Math.max(1, query.data.totalPages)}</span>
            <Button variant="ghost" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage((p) => p + 1)}>Older →</Button>
          </div>
        </>
      )}
    </div>
  )
}

function Item({ n, onOpen }: { n: NotificationView; onOpen: () => void }) {
  return (
    <li>
      <button type="button" onClick={onOpen}
        className={cx('flex w-full gap-3 rounded-2xl border p-4 text-left', n.read ? 'border-ink/10 bg-white' : 'border-jollof/40 bg-orange-50')}>
        <span className="text-2xl" aria-hidden="true">{icons[n.type]}</span>
        <span className="min-w-0 flex-1">
          <span className="flex items-center gap-2">
            <span className="font-bold">{n.title}</span>
            {!n.read && <span className="size-2 rounded-full bg-jollof" aria-label="Unread" />}
          </span>
          <span className="mt-0.5 block text-sm text-ink/80">{n.message}</span>
          <span className="mt-1 block text-xs text-ink/55">{formatDateTime(n.createdAt)}</span>
        </span>
      </button>
    </li>
  )
}
