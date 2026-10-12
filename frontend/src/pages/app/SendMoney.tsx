import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { useAccount, useBeneficiaries, useInvalidateMoney, useUserSearch } from '../../api/hooks'
import { api } from '../../api/endpoints'
import type { TransferResponse } from '../../api/types'
import { Alert, Button, Card, EmptyState, Field, Input, PageTitle, Skeleton, Spinner } from '../../components/ui'
import { copyText } from '../../lib/format'
import { formatNaira, nairaToKobo } from '../../lib/money'
import { errorVoice, transferVoice, type Voice } from '../../lib/personality'

interface Recipient {
  username: string
  displayName: string
  hint?: string
}

type Step = 'recipient' | 'amount' | 'confirm' | 'done'

const MIN_KOBO = 100
const QUICK_AMOUNTS = [1_000, 5_000, 10_000, 20_000]
/** Shown as a demo tip only. The real thresholds are enforced (and configurable) on the server. */
const DEMO_HOLD_TIP_KOBO = 5_000_000

function useDebounced(value: string, ms = 300): string {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), ms)
    return () => clearTimeout(id)
  }, [value, ms])
  return debounced
}

export default function SendMoney() {
  const [params] = useSearchParams()
  const [step, setStep] = useState<Step>('recipient')
  const [recipient, setRecipient] = useState<Recipient | null>(null)
  const [amountText, setAmountText] = useState('')
  const [note, setNote] = useState('')
  const [pin, setPin] = useState('')
  const [error, setError] = useState<Voice | null>(null)
  const [result, setResult] = useState<TransferResponse | null>(null)
  const invalidateMoney = useInvalidateMoney()

  // One idempotency key per distinct request. Retrying the SAME request (wrong PIN, bad network) reuses the key,
  // so even a double-tap or a retry after a timeout can never send twice. Changing anything creates a new key.
  const keyRef = useRef<{ fingerprint: string; key: string } | null>(null)

  const account = useAccount()
  const kobo = nairaToKobo(amountText)

  const submit = useMutation({
    mutationFn: () => {
      const fingerprint = `${recipient!.username}|${kobo}|${note.trim()}`
      if (keyRef.current?.fingerprint !== fingerprint) {
        keyRef.current = { fingerprint, key: crypto.randomUUID() }
      }
      return api.transfer(
        { recipientUsername: recipient!.username, amountKobo: kobo!, description: note.trim() || undefined, pin },
        keyRef.current.key,
      )
    },
    onSuccess: (response) => {
      setResult(response)
      setStep('done')
      setPin('')
      keyRef.current = null
      void invalidateMoney()
    },
    onError: (e) => {
      const voice = e instanceof ApiError ? errorVoice(e.code, e.message) : errorVoice('UNEXPECTED', '')
      setError(voice)
      if (e instanceof ApiError && (e.code === 'INVALID_PIN' || e.code === 'PIN_LOCKED')) setPin('')
    },
  })

  const reset = () => {
    setStep('recipient'); setRecipient(null); setAmountText(''); setNote(''); setPin(''); setError(null); setResult(null)
    keyRef.current = null
  }

  // /app/send?to=username pre-selects a person (used by "Saved" and "Send again")
  const preset = params.get('to')
  const presetSearch = useUserSearch(preset ?? '')
  useEffect(() => {
    if (!preset || recipient || step !== 'recipient') return
    const match = presetSearch.data?.find((u) => u.username.toLowerCase() === preset.toLowerCase())
    if (match) {
      setRecipient({ username: match.username, displayName: match.displayName, hint: `•••• ${match.accountLast4}` })
      setStep('amount')
    }
  }, [preset, presetSearch.data, recipient, step])

  const balance = account.data?.balanceKobo
  const amountError =
    amountText === '' ? null
      : kobo === null ? 'Enter a valid amount, e.g. 5000 or 1250.50'
      : kobo < MIN_KOBO ? `Minimum is ${formatNaira(MIN_KOBO)}`
      : balance !== undefined && kobo > balance ? `That is more than your balance of ${formatNaira(balance)}`
      : null

  const stepTitles: Record<Step, string> = { recipient: 'Who are you sending to?', amount: 'How much?', confirm: 'Confirm and enter your PIN', done: '' }

  return (
    <div>
      <PageTitle title="Send money" subtitle={step !== 'done' ? stepTitles[step] : undefined} />

      {step !== 'done' && (
        <ol className="mb-5 flex items-center gap-2 text-xs font-bold" aria-label="Progress">
          {(['recipient', 'amount', 'confirm'] as const).map((s, i) => (
            <li key={s} className="flex flex-1 items-center gap-2">
              <span className={`grid size-6 place-items-center rounded-full ${step === s || (['recipient', 'amount', 'confirm'].indexOf(step) > i) ? 'bg-jollof text-white' : 'bg-sand'}`}>{i + 1}</span>
              <span className="hidden sm:inline">{['Recipient', 'Amount', 'Confirm'][i]}</span>
              <span className="h-px flex-1 bg-ink/15" />
            </li>
          ))}
        </ol>
      )}

      {step === 'recipient' && (
        <RecipientStep
          onPick={(r) => { setRecipient(r); setStep('amount'); setError(null) }}
        />
      )}

      {step === 'amount' && recipient && (
        <Card className="space-y-4">
          <RecipientChip recipient={recipient} onChange={() => setStep('recipient')} />
          <Field label="Amount (₦)" htmlFor="amount" error={amountError ?? undefined}
            hint={balance !== undefined ? `Available: ${formatNaira(balance)}` : undefined}>
            <Input id="amount" inputMode="decimal" placeholder="0.00" autoFocus value={amountText}
              onChange={(e) => setAmountText(e.target.value)} invalid={!!amountError} />
          </Field>
          <div className="flex flex-wrap gap-2" aria-label="Quick amounts">
            {QUICK_AMOUNTS.map((n) => (
              <button key={n} type="button" onClick={() => setAmountText(String(n))}
                className="rounded-full border border-ink/15 bg-white px-3 py-1.5 text-sm font-semibold hover:bg-sand">
                {formatNaira(n * 100)}
              </button>
            ))}
          </div>
          <Field label="Note (optional)" htmlFor="note">
            <Input id="note" maxLength={140} placeholder="e.g. jollof money" value={note} onChange={(e) => setNote(e.target.value)} />
          </Field>
          {kobo !== null && kobo >= DEMO_HOLD_TIP_KOBO && (
            <Alert kind="info" title="Demo tip">
              Large transfers to someone new can be held for a quick safety review. You’ll see exactly what happened.
            </Alert>
          )}
          <Button className="w-full" disabled={kobo === null || !!amountError} onClick={() => { setStep('confirm'); setError(null) }}>
            Continue
          </Button>
        </Card>
      )}

      {step === 'confirm' && recipient && kobo !== null && (
        <Card className="space-y-4">
          <dl className="space-y-2 text-sm">
            <Row label="To" value={`${recipient.displayName} (@${recipient.username})`} />
            <Row label="Amount" value={formatNaira(kobo)} strong />
            {note.trim() && <Row label="Note" value={note.trim()} />}
          </dl>
          {error && <Alert kind="error" title={error.title}>{error.detail}</Alert>}
          <Field label="Transaction PIN" htmlFor="pin">
            <Input id="pin" type="password" inputMode="numeric" maxLength={4} autoComplete="off" autoFocus
              value={pin} onChange={(e) => setPin(e.target.value.replace(/\D/g, ''))} />
          </Field>
          <div className="flex gap-2">
            <Button variant="ghost" onClick={() => setStep('amount')} disabled={submit.isPending}>Back</Button>
            <Button className="flex-1" loading={submit.isPending} disabled={pin.length !== 4}
              onClick={() => { setError(null); submit.mutate() }}>
              Send {formatNaira(kobo)}
            </Button>
          </div>
        </Card>
      )}

      {step === 'done' && result && <Receipt result={result} onAgain={reset} />}
    </div>
  )
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex justify-between gap-4 border-b border-ink/5 pb-2 last:border-0">
      <dt className="text-ink/65">{label}</dt>
      <dd className={strong ? 'text-lg font-black' : 'text-right font-semibold'}>{value}</dd>
    </div>
  )
}

function RecipientChip({ recipient, onChange }: { recipient: Recipient; onChange: () => void }) {
  return (
    <div className="flex items-center gap-3 rounded-xl bg-sand/60 p-3">
      <Avatar name={recipient.displayName} />
      <div className="min-w-0 flex-1">
        <p className="truncate font-bold">{recipient.displayName}</p>
        <p className="text-xs text-ink/65">@{recipient.username}{recipient.hint ? ` · ${recipient.hint}` : ''}</p>
      </div>
      <button type="button" onClick={onChange} className="text-sm font-bold text-jollof">Change</button>
    </div>
  )
}

export function Avatar({ name }: { name: string }) {
  return (
    <span aria-hidden="true" className="grid size-10 shrink-0 place-items-center rounded-full bg-ink text-sm font-extrabold text-cream">
      {name.trim().charAt(0).toUpperCase() || '?'}
    </span>
  )
}

function RecipientStep({ onPick }: { onPick: (r: Recipient) => void }) {
  const [term, setTerm] = useState('')
  const debounced = useDebounced(term)
  const results = useUserSearch(debounced)
  const saved = useBeneficiaries()
  const searching = debounced.trim().length >= 2

  return (
    <div className="space-y-4">
      <Card>
        <Field label="Search by name or @username" htmlFor="search" hint="Type at least 2 letters.">
          <Input id="search" autoFocus autoCapitalize="none" autoComplete="off" placeholder="e.g. daniel" value={term}
            onChange={(e) => setTerm(e.target.value)} />
        </Field>
        <div className="mt-3" aria-live="polite">
          {searching && results.isFetching && <p className="flex items-center gap-2 py-3 text-sm text-ink/60"><Spinner size={14} /> Searching…</p>}
          {searching && results.isError && (
            <Alert kind="error" title={results.error instanceof ApiError ? errorVoice(results.error.code, results.error.message).title : 'Search failed'} />
          )}
          {searching && results.data && results.data.length === 0 && !results.isFetching && (
            <EmptyState emoji="🔎" title="Nobody found" hint="Check the spelling, or ask your friend to create an account first." />
          )}
          <ul className="divide-y divide-ink/5">
            {results.data?.map((u) => (
              <li key={u.username}>
                <button type="button" className="flex w-full items-center gap-3 rounded-xl px-1 py-3 text-left hover:bg-sand/60"
                  onClick={() => onPick({ username: u.username, displayName: u.displayName, hint: `•••• ${u.accountLast4}` })}>
                  <Avatar name={u.displayName} />
                  <span className="flex-1">
                    <span className="block font-bold">{u.displayName}</span>
                    <span className="block text-xs text-ink/65">@{u.username} · account ending {u.accountLast4}</span>
                  </span>
                  <span aria-hidden="true">›</span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </Card>

      {!searching && (
        <Card>
          <h2 className="mb-2 font-extrabold">Saved people</h2>
          {saved.isPending ? <Skeleton className="h-12" /> : saved.data && saved.data.length > 0 ? (
            <ul className="flex flex-wrap gap-2">
              {saved.data.map((b) => (
                <li key={b.username}>
                  <button type="button" onClick={() => onPick({ username: b.username, displayName: b.displayName, hint: `•••• ${b.accountLast4}` })}
                    className="flex items-center gap-2 rounded-full border border-ink/15 bg-white py-1 pl-1 pr-3 text-sm font-semibold hover:bg-sand">
                    <Avatar name={b.displayName} /> {b.nickname ?? b.displayName}
                  </button>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-ink/65">Nobody saved yet. You can add people from <Link to="/app/beneficiaries" className="font-bold text-jollof">Saved</Link>.</p>
          )}
        </Card>
      )}
    </div>
  )
}

function Receipt({ result, onAgain }: { result: TransferResponse; onAgain: () => void }) {
  const held = result.status === 'FLAGGED'
  const voice = transferVoice(result.status)
  return (
    <Card className="space-y-4 text-center">
      <div className="pop text-6xl" aria-hidden="true">{held ? '🧐' : '💸'}</div>
      <h2 className="text-2xl font-black" role="status">{voice.title}</h2>
      {held ? (
        <Alert kind="warning" title="Your transfer is on hold for a quick safety review">
          {result.message} Your money is safe and reserved: it will either be delivered or returned to you.
        </Alert>
      ) : (
        <p className="text-ink/75">
          {formatNaira(result.amountKobo)} sent to <strong>{result.recipientDisplayName}</strong> (@{result.recipientUsername}).
        </p>
      )}
      {result.idempotentReplay && <p className="text-xs text-ink/60">This was already sent earlier. We did not send it again.</p>}
      <div className="rounded-xl bg-sand/60 p-3 text-sm">
        <p className="text-ink/65">Reference</p>
        <button type="button" className="font-mono font-bold" onClick={() => void copyText(result.reference)} title="Copy reference">
          {result.reference} ⧉
        </button>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row">
        <Link to={`/app/transactions/${encodeURIComponent(result.reference)}`} className="flex-1">
          <Button variant="ghost" className="w-full">View receipt</Button>
        </Link>
        <Button className="flex-1" onClick={onAgain}>Send another</Button>
      </div>
    </Card>
  )
}
