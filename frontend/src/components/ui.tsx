import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ComponentProps, type ReactNode } from 'react'
import type { AlertStatus, RiskLevel, TxStatus } from '../api/types'

export function cx(...parts: (string | false | null | undefined)[]): string {
  return parts.filter(Boolean).join(' ')
}

// ---------- buttons ----------
type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'success'

const variants: Record<Variant, string> = {
  primary: 'bg-jollof text-white hover:bg-jollof-dark',
  secondary: 'bg-ink text-cream hover:bg-black',
  ghost: 'border border-ink/15 bg-white text-ink hover:bg-sand/60',
  danger: 'bg-red-600 text-white hover:bg-red-700',
  success: 'bg-mint text-white hover:bg-emerald-700',
}

export function Button({
  variant = 'primary', loading = false, disabled, className, children, type = 'button', ...rest
}: ComponentProps<'button'> & { variant?: Variant; loading?: boolean }) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cx(
        'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl px-4 py-2.5 text-sm font-semibold transition',
        'disabled:cursor-not-allowed disabled:opacity-55',
        variants[variant],
        className,
      )}
      {...rest}
    >
      {loading && <Spinner size={16} />}
      {children}
    </button>
  )
}

export function Spinner({ size = 20 }: { size?: number }) {
  return (
    <span
      aria-hidden="true"
      style={{ width: size, height: size }}
      className="inline-block animate-spin rounded-full border-2 border-current border-t-transparent"
    />
  )
}

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden="true" className={cx('animate-pulse rounded-xl bg-sand', className)} />
}

// ---------- surfaces ----------
export function Card({ className, children, ...rest }: ComponentProps<'div'>) {
  return (
    <div className={cx('rounded-2xl border border-ink/10 bg-white p-4 shadow-sm sm:p-5', className)} {...rest}>
      {children}
    </div>
  )
}

export function PageTitle({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-ink/65">{subtitle}</p>}
      </div>
      {action}
    </div>
  )
}

export function EmptyState({ emoji = '🫙', title, hint, action }: { emoji?: string; title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 rounded-2xl border border-dashed border-ink/20 bg-white/60 px-6 py-10 text-center">
      <div className="text-4xl" aria-hidden="true">{emoji}</div>
      <p className="font-bold">{title}</p>
      {hint && <p className="max-w-sm text-sm text-ink/65">{hint}</p>}
      {action}
    </div>
  )
}

type AlertKind = 'error' | 'info' | 'success' | 'warning'
const alertStyles: Record<AlertKind, string> = {
  error: 'border-red-200 bg-red-50 text-red-900',
  info: 'border-sky-200 bg-sky-50 text-sky-900',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
  warning: 'border-amber-300 bg-amber-50 text-amber-900',
}

export function Alert({ kind = 'info', title, children }: { kind?: AlertKind; title?: string; children?: ReactNode }) {
  return (
    <div role={kind === 'error' ? 'alert' : 'status'} className={cx('rounded-xl border px-4 py-3 text-sm', alertStyles[kind])}>
      {title && <p className="font-bold">{title}</p>}
      {children && <div className={title ? 'mt-0.5' : ''}>{children}</div>}
    </div>
  )
}

// ---------- forms ----------
export function Field({ label, error, hint, htmlFor, children }: {
  label: string; error?: string; hint?: string; htmlFor: string; children: ReactNode
}) {
  return (
    <div className="space-y-1">
      <label htmlFor={htmlFor} className="block text-sm font-semibold">{label}</label>
      {children}
      {hint && !error && <p className="text-xs text-ink/60">{hint}</p>}
      {error && <p role="alert" className="text-xs font-semibold text-red-700">{error}</p>}
    </div>
  )
}

export function Input({ className, invalid, ...rest }: ComponentProps<'input'> & { invalid?: boolean }) {
  return (
    <input
      aria-invalid={invalid || undefined}
      className={cx(
        'block min-h-11 w-full rounded-xl border bg-white px-3.5 py-2.5 text-base placeholder:text-ink/35 sm:text-sm',
        invalid ? 'border-red-500' : 'border-ink/20 focus:border-jollof',
        className,
      )}
      {...rest}
    />
  )
}

export function Select({ className, ...rest }: ComponentProps<'select'>) {
  return (
    <select className={cx('min-h-11 rounded-xl border border-ink/20 bg-white px-3 py-2.5 text-base sm:text-sm', className)} {...rest} />
  )
}

// ---------- badges ----------
const statusStyle: Record<TxStatus, { label: string; cls: string }> = {
  SUCCESSFUL: { label: 'Successful', cls: 'bg-emerald-100 text-emerald-800' },
  FLAGGED: { label: 'Under review', cls: 'bg-amber-100 text-amber-900' },
  REVERSED: { label: 'Reversed', cls: 'bg-stone-200 text-stone-700' },
  FAILED: { label: 'Failed', cls: 'bg-red-100 text-red-800' },
  PENDING: { label: 'Pending', cls: 'bg-sky-100 text-sky-800' },
  PROCESSING: { label: 'Processing', cls: 'bg-sky-100 text-sky-800' },
}

export function StatusBadge({ status }: { status: TxStatus }) {
  const s = statusStyle[status]
  return <span className={cx('inline-block rounded-full px-2.5 py-0.5 text-xs font-bold', s.cls)}>{s.label}</span>
}

const riskStyle: Record<RiskLevel, string> = {
  LOW: 'bg-emerald-100 text-emerald-800',
  MEDIUM: 'bg-amber-100 text-amber-900',
  HIGH: 'bg-orange-200 text-orange-900',
  CRITICAL: 'bg-red-200 text-red-900',
}

export function RiskBadge({ level, score }: { level: RiskLevel; score: number }) {
  return <span className={cx('inline-block rounded-full px-2.5 py-0.5 text-xs font-bold', riskStyle[level])}>{level} · {score}</span>
}

const alertStatusStyle: Record<AlertStatus, string> = {
  OPEN: 'bg-amber-100 text-amber-900',
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-red-100 text-red-800',
  INFO: 'bg-sky-100 text-sky-800',
}

export function AlertStatusBadge({ status }: { status: AlertStatus }) {
  return <span className={cx('inline-block rounded-full px-2.5 py-0.5 text-xs font-bold', alertStatusStyle[status])}>{status}</span>
}

// ---------- modal ----------
export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: ReactNode }) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-ink/55 p-3 sm:items-center" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="max-h-[90vh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-5 shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-3 flex items-start justify-between gap-3">
          <h2 className="text-lg font-extrabold">{title}</h2>
          <button type="button" onClick={onClose} aria-label="Close" className="rounded-lg px-2 text-xl leading-none hover:bg-sand">×</button>
        </div>
        {children}
      </div>
    </div>
  )
}

// ---------- toasts ----------
type ToastKind = 'success' | 'error' | 'info'
interface ToastItem { id: number; kind: ToastKind; message: string }
const ToastContext = createContext<((message: string, kind?: ToastKind) => void) | null>(null)

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([])
  const push = useCallback((message: string, kind: ToastKind = 'info') => {
    const id = Date.now() + Math.random()
    setItems((list) => [...list, { id, kind, message }])
    setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), 4500)
  }, [])
  const value = useMemo(() => push, [push])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 bottom-20 z-[60] flex flex-col items-center gap-2 px-4 sm:bottom-6" aria-live="polite">
        {items.map((t) => (
          <div
            key={t.id}
            className={cx(
              'pointer-events-auto max-w-sm rounded-xl px-4 py-3 text-sm font-semibold text-white shadow-lg',
              t.kind === 'error' ? 'bg-red-700' : t.kind === 'success' ? 'bg-mint' : 'bg-ink',
            )}
          >
            {t.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const push = useContext(ToastContext)
  if (!push) throw new Error('useToast must be used inside <ToastProvider>')
  return push
}
