import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { Alert, Button, Card, Field, Input } from '../components/ui'
import { errorVoice } from '../lib/personality'
import { loginSchema, type LoginForm } from '../lib/schemas'

export default function Login() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState<{ title: string; detail?: string } | null>(null)
  const {
    register, handleSubmit, formState: { errors, isSubmitting },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit(async (values) => {
    setError(null)
    try {
      const session = await login(values.identifier, values.password)
      const from = (location.state as { from?: string } | null)?.from
      navigate(session.role === 'ADMIN' ? '/admin' : from && from.startsWith('/app') ? from : '/app', { replace: true })
    } catch (e) {
      setError(e instanceof ApiError ? errorVoice(e.code, e.message) : errorVoice('UNEXPECTED', ''))
    }
  })

  return (
    <div className="mx-auto max-w-md pt-4">
      <h1 className="mb-1 text-3xl font-black tracking-tight">Welcome back 👋🏽</h1>
      <p className="mb-5 text-sm text-ink/70">Log in with your username or email.</p>
      <Card>
        <form onSubmit={onSubmit} className="space-y-4" noValidate>
          {error && <Alert kind="error" title={error.title}>{error.detail}</Alert>}
          <Field label="Username or email" htmlFor="identifier" error={errors.identifier?.message}>
            <Input id="identifier" autoComplete="username" autoCapitalize="none" invalid={!!errors.identifier} {...register('identifier')} />
          </Field>
          <Field label="Password" htmlFor="password" error={errors.password?.message}>
            <Input id="password" type="password" autoComplete="current-password" invalid={!!errors.password} {...register('password')} />
          </Field>
          <Button type="submit" loading={isSubmitting} className="w-full">Log in</Button>
        </form>
      </Card>
      <p className="mt-4 text-center text-sm">
        New here? <Link to="/register" className="font-bold text-jollof underline">Create an account</Link>
      </p>
    </div>
  )
}
