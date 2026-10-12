import { z } from 'zod'

/** These mirror the server's rules so people get instant feedback; the server still checks everything again. */
const pin = z.string().regex(/^\d{4}$/, 'PIN must be exactly 4 digits')
const password = z.string().regex(/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/, 'Use 8-72 characters with at least one letter and one number')

export const registerSchema = z.object({
  firstName: z.string().trim().min(1, 'Enter your first name').max(50),
  lastName: z.string().trim().min(1, 'Enter your last name').max(50),
  email: z.string().trim().regex(/^[^\s@]+@[^\s@]+\.[^\s@]+$/, 'Enter a valid email').max(254),
  phone: z.string().trim().regex(/^(\+234|0)[789][01]\d{8}$/, 'Use a Nigerian number like 08031234567 or +2348031234567'),
  username: z.string().trim().regex(/^[A-Za-z0-9_]{3,20}$/, '3-20 letters, numbers or underscores'),
  password,
  pin,
})
export type RegisterForm = z.infer<typeof registerSchema>

export const loginSchema = z.object({
  identifier: z.string().trim().min(1, 'Enter your username or email'),
  password: z.string().min(1, 'Enter your password'),
})
export type LoginForm = z.infer<typeof loginSchema>

export const changePinSchema = z.object({ currentPin: pin, newPin: pin }).refine((v) => v.currentPin !== v.newPin, {
  path: ['newPin'],
  message: 'New PIN must be different',
})
export type ChangePinForm = z.infer<typeof changePinSchema>

export const changePasswordSchema = z
  .object({ currentPassword: z.string().min(1, 'Enter your current password'), newPassword: password, confirm: z.string() })
  .refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Passwords do not match' })
export type ChangePasswordForm = z.infer<typeof changePasswordSchema>

export const pinOnlySchema = z.object({ pin })
export type PinOnlyForm = z.infer<typeof pinOnlySchema>

export const beneficiarySchema = z.object({
  username: z.string().trim().min(2, 'Enter a username'),
  nickname: z.string().trim().max(30, 'Nickname is too long').optional(),
})
export type BeneficiaryForm = z.infer<typeof beneficiarySchema>
