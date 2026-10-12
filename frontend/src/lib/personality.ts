/**
 * The NepOpay personality layer.
 *
 * Rule: personality is only a coat of paint. The server sends professional text and stable error codes;
 * this file turns them into Naija-flavoured headlines. Anything involving money keeps the server's exact
 * wording (amounts, limits, balances) as a plain "detail" line underneath, so numbers are never joked about.
 */
export interface Voice {
  title: string
  detail?: string
}

const KEEP_SERVER_DETAIL = (message: string): Voice['detail'] => (message ? message : undefined)

export function errorVoice(code: string, serverMessage: string): Voice {
  switch (code) {
    case 'INSUFFICIENT_BALANCE':
      return { title: 'Omo, your balance no reach 😭', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'LIMIT_EXCEEDED':
      return { title: 'You don hit the limit 🚧', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'INVALID_PIN':
      return { title: 'That PIN no correct 🔒', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'PIN_LOCKED':
      return { title: 'Easy! Too many wrong PINs 🛑', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'RECIPIENT_UNAVAILABLE':
      return { title: 'We no fit find that person 🤷🏽', detail: 'Check the username and try again.' }
    case 'SELF_TRANSFER':
      return { title: 'You wan send money to yourself? 😂', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'INVALID_CREDENTIALS':
      return { title: 'Wahala… those details no match 🙈', detail: 'Check your username or email and your password.' }
    case 'INVALID_PASSWORD':
      return { title: 'That password no correct 🔒', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'ACCOUNT_SUSPENDED':
      return { title: 'This account dey on pause ⏸️', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'RATE_LIMITED':
      return { title: 'Abeg, slow down small 🐢', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'SESSION_EXPIRED':
    case 'UNAUTHORIZED':
      return { title: 'Your session don expire ⏳', detail: 'Log in again, abeg.' }
    case 'DUPLICATE_REQUEST':
      return { title: 'Hold on, e still dey process ⏳', detail: 'Give it a few seconds, then press Send again. It will not send twice.' }
    case 'IDEMPOTENCY_CONFLICT':
      return { title: 'Something changed 🔄', detail: 'Go back and start this transfer again.' }
    case 'USERNAME_TAKEN':
      return { title: 'Eh! Somebody don take that 😅', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'EMAIL_TAKEN':
    case 'PHONE_TAKEN':
    case 'USER_ALREADY_EXISTS':
      return { title: 'We don see those details before 👀', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'BENEFICIARY_EXISTS':
      return { title: 'Person don already dey your list ✅', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'VALIDATION_ERROR':
      return { title: 'Small correction needed ✍🏽', detail: KEEP_SERVER_DETAIL(serverMessage) }
    case 'NETWORK_ERROR':
      return {
        title: 'Network wahala 📡',
        detail: 'Check your connection. If you were sending money, it is safe to press Send again: it cannot send twice.',
      }
    case 'ALERT_ALREADY_REVIEWED':
      return { title: 'Somebody don already decide this one', detail: KEEP_SERVER_DETAIL(serverMessage) }
    default:
      return { title: 'Ah. E no go through. Try again, abeg.', detail: KEEP_SERVER_DETAIL(serverMessage) }
  }
}

/** Headline for the result screen after a transfer request. */
export function transferVoice(status: string): Voice {
  if (status === 'FLAGGED') return { title: 'Omo… hold up. 🧐' }
  return { title: 'E don land! 💸' }
}

export const copy = {
  tagline: 'Banking, but make am.',
  welcome: 'Welcome to NepOpay 🇳🇬',
  emptyTransactions: 'Nothing dey here yet.',
  emptyNotifications: 'No gist yet. Come back later.',
  emptyBeneficiaries: 'Nobody dey your list yet.',
  demoLine: '100% simulated. ₦0 real money involved.',
}
