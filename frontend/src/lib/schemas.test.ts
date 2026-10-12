import { changePasswordSchema, changePinSchema, loginSchema, registerSchema } from './schemas'

const valid = {
  firstName: 'Ada', lastName: 'Obi', email: 'ada@example.com', phone: '08031234567',
  username: 'ada_o', password: 'Passw0rdTest', pin: '1234',
}

describe('registerSchema (mirrors the server rules)', () => {
  it('accepts a good registration', () => {
    expect(registerSchema.safeParse(valid).success).toBe(true)
  })

  it('accepts +234 phone numbers', () => {
    expect(registerSchema.safeParse({ ...valid, phone: '+2348031234567' }).success).toBe(true)
  })

  it.each([
    ['phone', '12345'], ['phone', '0603123456'], ['email', 'not-an-email'], ['username', 'ab'],
    ['username', 'has space'], ['password', 'short1'], ['password', 'onlyletters'], ['pin', '123'], ['pin', '12ab'],
  ])('rejects a bad %s: %s', (field, value) => {
    expect(registerSchema.safeParse({ ...valid, [field]: value }).success).toBe(false)
  })
})

describe('other forms', () => {
  it('login needs both fields', () => {
    expect(loginSchema.safeParse({ identifier: 'ada', password: '' }).success).toBe(false)
    expect(loginSchema.safeParse({ identifier: 'ada', password: 'x' }).success).toBe(true)
  })

  it('PIN change must pick a different PIN', () => {
    expect(changePinSchema.safeParse({ currentPin: '1234', newPin: '1234' }).success).toBe(false)
    expect(changePinSchema.safeParse({ currentPin: '1234', newPin: '5678' }).success).toBe(true)
  })

  it('password change needs matching confirmation', () => {
    const base = { currentPassword: 'Passw0rdTest', newPassword: 'NewPassw0rd9', confirm: 'NewPassw0rd9' }
    expect(changePasswordSchema.safeParse(base).success).toBe(true)
    expect(changePasswordSchema.safeParse({ ...base, confirm: 'different1' }).success).toBe(false)
  })
})
