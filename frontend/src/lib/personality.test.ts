import { copy, errorVoice, transferVoice } from './personality'

describe('personality layer', () => {
  it('keeps the exact server wording (amounts!) for money errors', () => {
    const server = 'Insufficient balance. Your available balance is ₦8,500.00.'
    const voice = errorVoice('INSUFFICIENT_BALANCE', server)
    expect(voice.title).toContain('your balance no reach')
    expect(voice.detail).toBe(server)

    const limit = 'Daily transfer limit of ₦1,000,000.00 would be exceeded. You have ₦50,000.00 left today.'
    expect(errorVoice('LIMIT_EXCEEDED', limit).detail).toBe(limit)
  })

  it('never turns a bad PIN into a joke without the remaining attempts', () => {
    const voice = errorVoice('INVALID_PIN', 'Invalid transaction PIN. 2 attempt(s) remaining.')
    expect(voice.detail).toContain('2 attempt(s) remaining')
  })

  it('has a friendly fallback for unknown codes and still shows the server message', () => {
    const voice = errorVoice('SOMETHING_NEW', 'Detailed reason')
    expect(voice.title).toBe('Ah. E no go through. Try again, abeg.')
    expect(voice.detail).toBe('Detailed reason')
  })

  it('tells people a network retry is safe', () => {
    expect(errorVoice('NETWORK_ERROR', '').detail).toMatch(/safe to press Send again/i)
  })

  it('has the headline copy from the brand brief', () => {
    expect(transferVoice('SUCCESSFUL').title).toBe('E don land! 💸')
    expect(transferVoice('FLAGGED').title).toBe('Omo… hold up. 🧐')
    expect(copy.emptyTransactions).toBe('Nothing dey here yet.')
    expect(copy.tagline).toBe('Banking, but make am.')
  })
})
