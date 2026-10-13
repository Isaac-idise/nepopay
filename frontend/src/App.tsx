import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { RedirectIfSignedIn, RequireAdmin, RequireCustomer } from './auth/guards'
import { AdminLayout, AppLayout, PublicLayout } from './components/Layouts'
import { Button } from './components/ui'
import Landing from './pages/Landing'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/app/Dashboard'
import SendMoney from './pages/app/SendMoney'
import Transactions from './pages/app/Transactions'
import TransactionDetail from './pages/app/TransactionDetail'
import Beneficiaries from './pages/app/Beneficiaries'
import Notifications from './pages/app/Notifications'
import Profile from './pages/app/Profile'
import AdminDashboard from './pages/admin/AdminDashboard'
import AdminFraud from './pages/admin/AdminFraud'
import AdminUsers from './pages/admin/AdminUsers'
import AdminTransactions from './pages/admin/AdminTransactions'
import AdminAudit from './pages/admin/AdminAudit'
import AdminAccount from './pages/admin/AdminAccount'

function NotFound() {
  return (
    <div className="mx-auto max-w-md py-20 text-center">
      <p className="text-6xl" aria-hidden="true">🧭</p>
      <h1 className="mt-3 text-2xl font-black">We no see this page</h1>
      <p className="mt-1 text-ink/70">The link may be wrong or the page moved.</p>
      <Link to="/" className="mt-4 inline-block"><Button>Go home</Button></Link>
    </div>
  )
}

export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        <Route index element={<Landing />} />
        <Route element={<RedirectIfSignedIn />}>
          <Route path="login" element={<Login />} />
          <Route path="register" element={<Register />} />
        </Route>
      </Route>

      <Route path="app" element={<RequireCustomer />}>
        <Route element={<AppLayout />}>
          <Route index element={<Dashboard />} />
          <Route path="send" element={<SendMoney />} />
          <Route path="transactions" element={<Transactions />} />
          <Route path="transactions/:reference" element={<TransactionDetail />} />
          <Route path="beneficiaries" element={<Beneficiaries />} />
          <Route path="notifications" element={<Notifications />} />
          <Route path="profile" element={<Profile />} />
        </Route>
      </Route>

      <Route path="admin" element={<RequireAdmin />}>
        <Route element={<AdminLayout />}>
          <Route index element={<AdminDashboard />} />
          <Route path="fraud" element={<AdminFraud />} />
          <Route path="users" element={<AdminUsers />} />
          <Route path="transactions" element={<AdminTransactions />} />
          <Route path="audit" element={<AdminAudit />} />
          <Route path="account" element={<AdminAccount />} />
        </Route>
      </Route>

      <Route path="home" element={<Navigate to="/" replace />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
