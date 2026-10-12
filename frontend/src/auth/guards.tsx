import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Spinner } from '../components/ui'
import { useAuth } from './AuthContext'

function FullScreenSpinner() {
  return (
    <div className="flex min-h-screen items-center justify-center" role="status" aria-label="Loading">
      <Spinner size={36} />
    </div>
  )
}

/** Customers only. Admins have no bank account, so they are sent to the admin area. */
export function RequireCustomer() {
  const { status, session } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <FullScreenSpinner />
  if (!session) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (session.role === 'ADMIN') return <Navigate to="/admin" replace />
  return <Outlet />
}

export function RequireAdmin() {
  const { status, session } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <FullScreenSpinner />
  if (!session) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (session.role !== 'ADMIN') return <Navigate to="/app" replace />
  return <Outlet />
}

/** Login/register pages: if you are already signed in, go straight to your home. */
export function RedirectIfSignedIn() {
  const { status, session } = useAuth()
  if (status === 'loading') return <FullScreenSpinner />
  if (session) return <Navigate to={session.role === 'ADMIN' ? '/admin' : '/app'} replace />
  return <Outlet />
}
