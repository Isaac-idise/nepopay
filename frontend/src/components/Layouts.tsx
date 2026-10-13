import { NavLink, Outlet, Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useUnreadCount } from '../api/hooks'
import { copy } from '../lib/personality'
import { Button, cx } from './ui'

export function DemoBanner() {
  return (
    <div className="bg-ink px-3 py-1.5 text-center text-xs font-semibold text-cream" role="note">
      <span className="mr-2 rounded bg-plantain px-1.5 py-0.5 font-extrabold text-ink">DEMO MODE</span>
      {copy.demoLine}
    </div>
  )
}

export function Logo({ to = '/' }: { to?: string }) {
  return (
    <Link to={to} className="flex items-center gap-2 text-xl font-black tracking-tight">
      <span aria-hidden="true" className="grid size-8 place-items-center rounded-xl bg-jollof text-base text-white">₦</span>
      NepOpay
    </Link>
  )
}

export function PublicLayout() {
  const { session } = useAuth()
  return (
    <div className="flex min-h-screen flex-col">
      <DemoBanner />
      <header className="mx-auto flex w-full max-w-5xl items-center justify-between px-4 py-4">
        <Logo />
        <nav className="flex items-center gap-2">
          {session ? (
            <Link to={session.role === 'ADMIN' ? '/admin' : '/app'}>
              <Button>Open app</Button>
            </Link>
          ) : (
            <>
              <Link to="/login" className="rounded-xl px-3 py-2 text-sm font-semibold hover:bg-sand">Log in</Link>
              <Link to="/register"><Button>Create account</Button></Link>
            </>
          )}
        </nav>
      </header>
      <main className="mx-auto w-full max-w-5xl flex-1 px-4 pb-12">
        <Outlet />
      </main>
      <footer className="border-t border-ink/10 px-4 py-6 text-center text-xs text-ink/60">
        NepOpay is a student project and a simulation. It is not a bank and it never touches real money.
      </footer>
    </div>
  )
}

const navLink = ({ isActive }: { isActive: boolean }) =>
  cx('rounded-xl px-3 py-2 text-sm font-semibold', isActive ? 'bg-ink text-cream' : 'hover:bg-sand')

const tabLink = ({ isActive }: { isActive: boolean }) =>
  cx('relative flex flex-1 flex-col items-center gap-0.5 py-2 text-[11px] font-semibold', isActive ? 'text-jollof' : 'text-ink/65')

function UnreadDot() {
  const { data } = useUnreadCount()
  if (!data) return null
  return (
    <span className="absolute right-1/4 top-0.5 grid min-w-4 place-items-center rounded-full bg-jollof px-1 text-[10px] font-extrabold text-white" aria-label={`${data} unread`}>
      {data > 9 ? '9+' : data}
    </span>
  )
}

export function AppLayout() {
  const { session, logout } = useAuth()
  const tabs = [
    { to: '/app', label: 'Home', icon: '🏠', end: true },
    { to: '/app/send', label: 'Send', icon: '💸' },
    { to: '/app/transactions', label: 'History', icon: '🧾' },
    { to: '/app/notifications', label: 'Alerts', icon: '🔔', dot: true },
    { to: '/app/profile', label: 'Me', icon: '👤' },
  ]
  return (
    <div className="min-h-screen pb-20 sm:pb-0">
      <DemoBanner />
      <header className="sticky top-0 z-30 border-b border-ink/10 bg-cream/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
          <Logo to="/app" />
          <nav className="hidden items-center gap-1 sm:flex" aria-label="Main">
            {tabs.map((t) => (
              <NavLink key={t.to} to={t.to} end={t.end} className={navLink}>
                {t.label}
              </NavLink>
            ))}
            <NavLink to="/app/beneficiaries" className={navLink}>Saved</NavLink>
          </nav>
          <div className="flex items-center gap-2">
            <span className="hidden text-sm font-semibold text-ink/70 sm:inline">@{session?.username}</span>
            <Button variant="ghost" onClick={() => void logout()} className="!min-h-9 !px-3 !py-1.5">Log out</Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-4 py-6">
        <Outlet />
      </main>
      <nav className="fixed inset-x-0 bottom-0 z-30 flex border-t border-ink/10 bg-white sm:hidden" aria-label="Main">
        {tabs.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end} className={tabLink}>
            <span className="text-lg" aria-hidden="true">{t.icon}</span>
            {t.label}
            {t.dot && <UnreadDot />}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

export function AdminLayout() {
  const { session, logout } = useAuth()
  const links = [
    { to: '/admin', label: 'Overview', end: true },
    { to: '/admin/fraud', label: 'Fraud' },
    { to: '/admin/users', label: 'Users' },
    { to: '/admin/transactions', label: 'Transactions' },
    { to: '/admin/audit', label: 'Audit log' },
    { to: '/admin/account', label: 'My account' },
  ]
  return (
    <div className="min-h-screen">
      <DemoBanner />
      <header className="border-b border-ink/10 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="flex items-center gap-3">
            <Logo to="/admin" />
            <span className="rounded-full bg-ink px-2.5 py-0.5 text-xs font-bold text-cream">ADMIN</span>
          </div>
          <nav className="flex flex-wrap items-center gap-1" aria-label="Admin">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to} end={l.end} className={navLink}>{l.label}</NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-ink/70">@{session?.username}</span>
            <Button variant="ghost" onClick={() => void logout()} className="!min-h-9 !px-3 !py-1.5">Log out</Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
