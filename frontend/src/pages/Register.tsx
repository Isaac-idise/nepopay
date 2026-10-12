import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert, Button, Card, Field, Input } from '../components/ui'
import { errorVoice } from '../lib/personality'
import { registerSchema, type RegisterForm } from '../lib/schemas'

export default function Register() {
  const { register: signUp } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<{ title: string; detail?: string } | null>(null)
  const {
    register, handleSubmit, setError: setFieldError, formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({ resolver: zodResolver(registerSchema), mode: 'onTouched' })

  const onSubmit = handleSubmit(async (values) => {
    setError(null)
    try {
      await signUp(values)
      navigate('/app?welcome=1', { replace: true })
    } catch (e) {
      if (e instanceof ApiError) {
        const field = { USERNAME_TAKEN: 'username', EMAIL_TAKEN: 'email', PHONE_TAKEN: 'phone' }[e.code] as
          'username' | 'email' | 'phone' | undefined
        if (field) {
          setFieldError(field, { message: e.message })
          return
        }
        setError(errorVoice(e.code, e.message))
      } else {
        setError(errorVoice('UNEXPECTED', ''))
      }
    }
  })

  return (
    <div className="mx-auto max-w-lg pt-4">
      <h1 className="mb-1 text-3xl font-black tracking-tight">Create your demo account</h1>
      <p className="mb-5 text-sm text-ink/70">You’ll get ₦100,000 of simulated money straight away.</p>
      <Card>
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {error && <Alert kind="error" title={error.title}>{error.detail}</Alert>}
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="First name" htmlFor="firstName" error={errors.firstName?.message}>
              <Input id="firstName" autoComplete="given-name" invalid={!!errors.firstName} {...register('firstName')} />
            </Field>
            <Field label="Last name" htmlFor="lastName" error={errors.lastName?.message}>
              <Input id="lastName" autoComplete="family-name" invalid={!!errors.lastName} {...register('lastName')} />
            </Field>
          </div>
          <Field label="Email" htmlFor="email" error={errors.email?.message} hint="Never shown to other users.">
            <Input id="email" type="email" autoComplete="email" invalid={!!errors.email} {...register('email')} />
          </Field>
          <Field label="Phone number" htmlFor="phone" error={errors.phone?.message} hint="Nigerian mobile. Never shown to other users.">
            <Input id="phone" type="tel" inputMode="tel" autoComplete="tel" placeholder="08031234567" invalid={!!errors.phone} {...register('phone')} />
          </Field>
          <Field label="Username" htmlFor="username" error={errors.username?.message} hint="This is how friends find and pay you.">
            <Input id="username" autoComplete="username" autoCapitalize="none" placeholder="e.g. tunde_o" invalid={!!errors.username} {...register('username')} />
          </Field>
          <Field label="Password" htmlFor="password" error={errors.password?.message} hint="8+ characters with a letter and a number.">
            <Input id="password" type="password" autoComplete="new-password" invalid={!!errors.password} {...register('password')} />
          </Field>
          <Field label="Transaction PIN" htmlFor="pin" error={errors.pin?.message} hint="4 digits. You’ll type it to confirm every transfer.">
            <Input id="pin" type="password" inputMode="numeric" maxLength={4} autoComplete="off" invalid={!!errors.pin} {...register('pin')} />
          </Field>
          <Alert kind="warning" title="Demo reminder">
            Don’t reuse a real password or PIN. This is a simulation.
          </Alert>
          <Button type="submit" loading={isSubmitting} className="w-full">Create account</Button>
        </form>
      </Card>
      <p className="mt-4 text-center text-sm">
        Already registered? <Link to="/login" className="font-bold text-jollof underline">Log in</Link>
      </p>
    </div>
  )
}
