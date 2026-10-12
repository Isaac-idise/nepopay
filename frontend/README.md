# NepOpay frontend

React + TypeScript + Vite + Tailwind. **100% simulated. ₦0 real money involved.**

## Run it
Requires **Node 22 LTS** (https://nodejs.org). Check with `node -v`.

```
npm install
npm run dev          # http://localhost:5173
```
The backend must be running on http://localhost:8080. In development Vite forwards `/api` to it, so there is
no CORS to configure and the refresh cookie is first-party. Use `VITE_DEV_API` if your backend is elsewhere.

## Scripts
| Command | What it does |
|---|---|
| `npm run dev` | dev server with hot reload |
| `npm test` | unit + UI-flow tests (Vitest, Testing Library) against a fake backend |
| `npm run typecheck` | TypeScript check |
| `npm run build` | type-check, then production build into `dist/` |

## How it is organised
```
src/
  api/        client.ts (fetch wrapper, token refresh), endpoints.ts (one typed function per API call), types.ts, hooks.ts
  auth/       AuthContext (session state), guards (customer / admin / signed-out routes)
  components/ ui.tsx (buttons, cards, modal, toasts...), Layouts.tsx, TransactionRow.tsx
  lib/        money.ts (kobo <-> naira, no floats), personality.ts (Pidgin copy), schemas.ts (zod forms), format.ts
  pages/      Landing, Login, Register | app/ (customer screens) | admin/ (admin screens)
  test/       fake backend + setup
```

## Design rules worth knowing
- **Personality is only a coat of paint.** `lib/personality.ts` maps the server's stable error codes to Naija headlines,
  but any message about money keeps the server's exact wording underneath (amounts, limits, balances are never joked about).
- **The access token lives in memory only.** The refresh token is an httpOnly cookie JavaScript cannot read. On page load the app
  silently refreshes to restore the session.
- **One refresh at a time.** Refresh tokens rotate (each works once), so `refreshSession()` shares a single in-flight request.
  Two parallel refreshes would look like token theft to the server and end the session.
- **Safe to press Send twice.** Each distinct transfer gets one `Idempotency-Key`. Retrying the same request (wrong PIN, bad network)
  reuses it; changing the recipient, amount or note creates a new one.
- **Money is whole kobo** end to end. `nairaToKobo` parses typed text with string maths, never `parseFloat`.

## Deploying
The simplest production setup serves the built site and the API from the **same site**, so the refresh cookie stays first-party.
Host the `dist/` folder and proxy `/api` to the backend (for example with Vercel/Netlify rewrites). If you must call the API on another
domain, set `VITE_API_BASE_URL`, and on the backend set `COOKIE_SAME_SITE=None`, `COOKIE_SECURE=true` and `ALLOWED_ORIGINS`.
