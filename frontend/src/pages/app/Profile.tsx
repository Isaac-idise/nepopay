import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { ApiError } from '../../api/client'
import { api } from '../../api/endpoints'
import { useInvalidateMoney } from '../../api/hooks'
import { useAuth } from '../../auth/AuthContext'
import { Alert, Button, Card, Field, Input, Modal, PageTitle, Skeleton, useToast } from '../../components/ui'
import { formatDate } from '../../lib/format'
import { formatNaira } from '../../lib/money'
import { errorVoice, type Voice } from '../../lib/personality'
import {
  changePasswordSchema, changePinSchema, pinOnlySchema,
  type ChangePasswordForm, type ChangePinForm, type PinOnlyForm,
} from '../../lib/schemas'

const toVoice = (e: unknown): Voice => (e instanceof ApiError ? errorVoice(e.code, e.message) : errorVoice('UNEXPECTED', ''))

export default function Profile() {
  const { logout } = useAuth()
  const me = useQuery({ queryKey: ['me'], queryFn: api.me })

  return (
    <div className="space-y-5">
      <PageTitle title="Me & security" />
      <Card>
        {me.isPending ? <Skeleton className="h-28" /> : me.isError ? <Alert kind="error" title="Couldn’t load your profile" /> : (
          <dl className="divide-y divide-ink/5 text-sm">
            <Item label="Name" value={me.data.displayName} />
            <Item label="Username" value={`@${me.data.username}`} />
            <Item label="Email" value={me.data.email} />
            <Item label="Phone" value={me.data.phone} />
            <Item label="Member since" value={formatDate(me.data.createdAt)} />
          </dl>
        )}
        <p className="mt-3 text-xs text-ink/60">Your email and phone are private. Other users only see your name, username and the last 4 digits of your account number.</p>
      </Card>

      <ChangePin />
      <ChangePassword />
      <ResetDemo />

      <Button variant="ghost" className="w-full" onClick={() => void logout()}>Log out</Button>
    </div>
  )
}

function Item({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-4 py-2"><dt className="text-ink/65">{label}</dt><dd className="break-all text-right font-semibold">{value}</dd></div>
  )
}

function ChangePin() {
  const toast = useToast()
  const { register, handleSubmit, reset, formState: { errors } } = useForm<ChangePinForm>({ resolver: zodResolver(changePinSchema) })
  const change = useMutation({
    mutationFn: (v: ChangePinForm) => api.changePin(v.currentPin, v.newPin),
    onSuccess: () => { toast('PIN changed 🔐', 'success'); reset() },
  })
  const voice = change.error ? toVoice(change.error) : null
  return (
    <Card>
      <h2 className="mb-3 font-extrabold">Change transaction PIN</h2>
      <form onSubmit={handleSubmit((v) => change.mutate(v))} className="space-y-3" noValidate>
        {voice && <Alert kind="error" title={voice.title}>{voice.detail}</Alert>}
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Current PIN" htmlFor="cur-pin" error={errors.currentPin?.message}>
            <Input id="cur-pin" type="password" inputMode="numeric" maxLength={4} autoComplete="off" invalid={!!errors.currentPin} {...register('currentPin')} />
          </Field>
          <Field label="New PIN" htmlFor="new-pin" error={errors.newPin?.message}>
            <Input id="new-pin" type="password" inputMode="numeric" maxLength={4} autoComplete="off" invalid={!!errors.newPin} {...register('newPin')} />
          </Field>
        </div>
        <Button type="submit" loading={change.isPending}>Change PIN</Button>
      </form>
    </Card>
  )
}

function ChangePassword() {
  const toast = useToast()
  const { logout } = useAuth()
  const { register, handleSubmit, reset, formState: { errors } } = useForm<ChangePasswordForm>({ resolver: zodResolver(changePasswordSchema) })
  const change = useMutation({
    mutationFn: (v: ChangePasswordForm) => api.changePassword(v.currentPassword, v.newPassword),
    onSuccess: async () => {
      reset()
      toast('Password changed. Please log in again.', 'success')
      await logout() // the server signed every device out
    },
  })
  const voice = change.error ? toVoice(change.error) : null
  return (
    <Card>
      <h2 className="mb-1 font-extrabold">Change password</h2>
      <p className="mb-3 text-xs text-ink/60">You’ll be signed out on all devices.</p>
      <form onSubmit={handleSubmit((v) => change.mutate(v))} className="space-y-3" noValidate>
        {voice && <Alert kind="error" title={voice.title}>{voice.detail}</Alert>}
        <Field label="Current password" htmlFor="cur-pw" error={errors.currentPassword?.message}>
          <Input id="cur-pw" type="password" autoComplete="current-password" invalid={!!errors.currentPassword} {...register('currentPassword')} />
        </Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="New password" htmlFor="new-pw" error={errors.newPassword?.message}>
            <Input id="new-pw" type="password" autoComplete="new-password" invalid={!!errors.newPassword} {...register('newPassword')} />
          </Field>
          <Field label="Confirm new password" htmlFor="confirm-pw" error={errors.confirm?.message}>
            <Input id="confirm-pw" type="password" autoComplete="new-password" invalid={!!errors.confirm} {...register('confirm')} />
          </Field>
        </div>
        <Button type="submit" loading={change.isPending}>Change password</Button>
      </form>
    </Card>
  )
}

function ResetDemo() {
  const [open, setOpen] = useState(false)
  const toast = useToast()
  const invalidate = useInvalidateMoney()
  const { register, handleSubmit, reset, formState: { errors } } = useForm<PinOnlyForm>({ resolver: zodResolver(pinOnlySchema) })
  const run = useMutation({
    mutationFn: (v: PinOnlyForm) => api.resetDemo(v.pin),
    onSuccess: (r) => {
      toast(`Demo account reset to ${formatNaira(r.balanceKobo)} ✅`, 'success')
      reset(); setOpen(false); void invalidate()
    },
  })
  const voice = run.error ? toVoice(run.error) : null
  return (
    <Card>
      <h2 className="font-extrabold">Reset demo account</h2>
      <p className="mt-1 text-sm text-ink/70">
        Put your balance back to ₦100,000 and clear your history, saved people and alerts. Nobody else’s history changes,
        and the books stay balanced.
      </p>
      <Button variant="danger" className="mt-3" onClick={() => { run.reset(); setOpen(true) }}>Reset my demo account</Button>
      <Modal open={open} onClose={() => setOpen(false)} title="Reset your demo account?">
        <form onSubmit={handleSubmit((v) => run.mutate(v))} className="space-y-3" noValidate>
          <p className="text-sm text-ink/75">This can’t be undone. Enter your PIN to confirm.</p>
          {voice && <Alert kind="error" title={voice.title}>{voice.detail}</Alert>}
          <Field label="Transaction PIN" htmlFor="reset-pin" error={errors.pin?.message}>
            <Input id="reset-pin" type="password" inputMode="numeric" maxLength={4} autoComplete="off" autoFocus invalid={!!errors.pin} {...register('pin')} />
          </Field>
          <div className="flex gap-2">
            <Button variant="ghost" onClick={() => setOpen(false)}>Cancel</Button>
            <Button type="submit" variant="danger" className="flex-1" loading={run.isPending}>Yes, reset</Button>
          </div>
        </form>
      </Modal>
    </Card>
  )
}
