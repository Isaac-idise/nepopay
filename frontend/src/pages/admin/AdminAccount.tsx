import { Alert, Card, PageTitle } from '../../components/ui'
import { useAuth } from '../../auth/AuthContext'
import { ChangePassword } from '../app/Profile'

export default function AdminAccount() {
  const { session } = useAuth()
  return (
    <div className="mx-auto max-w-xl space-y-5">
      <PageTitle title="My account" subtitle={`Signed in as @${session?.username}`} />
      <Alert kind="warning" title="Change the initial password">
        The first admin password was set on the server (ADMIN_INITIAL_PASSWORD). Choose a new one now, then remove or rotate that
        setting. Changing it signs you out of every device.
      </Alert>
      <ChangePassword />
      <Card>
        <p className="text-sm text-ink/70">
          Admin accounts have no bank account and no transaction PIN, so they can review and monitor but never move money.
        </p>
      </Card>
    </div>
  )
}
