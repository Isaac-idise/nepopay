import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { ApiError } from '../../api/client'
import { api } from '../../api/endpoints'
import { keys, useBeneficiaries } from '../../api/hooks'
import { Alert, Button, Card, EmptyState, Field, Input, PageTitle, Skeleton, useToast } from '../../components/ui'
import { errorVoice, copy } from '../../lib/personality'
import { beneficiarySchema, type BeneficiaryForm } from '../../lib/schemas'
import { Avatar } from './SendMoney'

export default function Beneficiaries() {
  const toast = useToast()
  const client = useQueryClient()
  const list = useBeneficiaries()
  const { register, handleSubmit, reset, formState: { errors } } = useForm<BeneficiaryForm>({ resolver: zodResolver(beneficiarySchema) })

  const add = useMutation({
    mutationFn: (v: BeneficiaryForm) => api.addBeneficiary(v.username.replace(/^@/, ''), v.nickname),
    onSuccess: (b) => { toast(`${b.displayName} saved ✅`, 'success'); reset(); void client.invalidateQueries({ queryKey: keys.beneficiaries }) },
  })
  const remove = useMutation({
    mutationFn: (username: string) => api.removeBeneficiary(username),
    onSuccess: () => { toast('Removed'); void client.invalidateQueries({ queryKey: keys.beneficiaries }) },
    onError: () => toast('Could not remove. Try again.', 'error'),
  })
  const voice = add.error instanceof ApiError ? errorVoice(add.error.code, add.error.message) : add.error ? errorVoice('UNEXPECTED', '') : null

  return (
    <div className="space-y-5">
      <PageTitle title="Saved people" subtitle="Quick access to the people you pay often." />
      <Card>
        <form onSubmit={handleSubmit((v) => add.mutate(v))} className="space-y-3" noValidate>
          {voice && <Alert kind="error" title={voice.title}>{voice.detail}</Alert>}
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Username" htmlFor="b-username" error={errors.username?.message}>
              <Input id="b-username" autoCapitalize="none" placeholder="@daniel" invalid={!!errors.username} {...register('username')} />
            </Field>
            <Field label="Nickname (optional)" htmlFor="b-nick" error={errors.nickname?.message}>
              <Input id="b-nick" placeholder="e.g. Mum" {...register('nickname')} />
            </Field>
          </div>
          <Button type="submit" loading={add.isPending}>Save person</Button>
        </form>
      </Card>

      <Card>
        {list.isPending ? <Skeleton className="h-16" /> : list.isError ? (
          <Alert kind="error" title="Couldn’t load your saved people" />
        ) : list.data.length === 0 ? (
          <EmptyState emoji="⭐" title={copy.emptyBeneficiaries} hint="Saving someone also makes future transfers to them look more familiar to our safety checks." />
        ) : (
          <ul className="divide-y divide-ink/5">
            {list.data.map((b) => (
              <li key={b.username} className="flex items-center gap-3 py-3">
                <Avatar name={b.displayName} />
                <div className="min-w-0 flex-1">
                  <p className="truncate font-bold">{b.nickname ?? b.displayName}</p>
                  <p className="truncate text-xs text-ink/65">{b.displayName} · @{b.username} · •••• {b.accountLast4}</p>
                </div>
                <Link to={`/app/send?to=${encodeURIComponent(b.username)}`}><Button className="!min-h-9 !px-3 !py-1.5">Send</Button></Link>
                <Button variant="ghost" className="!min-h-9 !px-3 !py-1.5" onClick={() => remove.mutate(b.username)}
                  loading={remove.isPending && remove.variables === b.username} aria-label={`Remove ${b.displayName}`}>✕</Button>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
