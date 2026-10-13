# Deploying NepOpay (Neon + Render + Vercel)

```
Browser ──► Vercel (website + /api rewrite) ──► Render (Spring Boot API) ──► Neon (PostgreSQL)
```
The browser only ever talks to the Vercel address. Vercel forwards `/api/*` to Render, so the login cookie is first-party
(a cookie from onrender.com would not be sent from a vercel.app page).

## 1. Neon (database)
- Use the **direct** connection (turn "Connection pooling" off; the host must NOT contain `-pooler`), because Flyway migrations do not work reliably through a pooler.
- `DATABASE_URL` is the JDBC form WITHOUT user and password, and WITHOUT `channel_binding`:
  `jdbc:postgresql://ep-xxxx.<region>.aws.neon.tech/neondb?sslmode=require`

## 2. Render (backend, Docker web service) environment variables
| Variable | Value |
|---|---|
| `DATABASE_URL` | the JDBC URL above |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | the Neon role and its password |
| `DB_POOL_SIZE` | `5` |
| `JWT_SECRET` | a long random string (32+ chars). Never reuse it anywhere else |
| `SPRING_PROFILES_ACTIVE` | `prod` (the app then refuses to start with the development JWT secret) |
| `COOKIE_SECURE` | `true` |
| `COOKIE_SAME_SITE` | `Lax` |
| `FORWARD_HEADERS` | `native` |
| `ALLOWED_ORIGINS` | your Vercel address, e.g. `https://nepopay-8zia.vercel.app` (**required**: Vercel forwards the browser's Origin header and the API checks it) |
| `ADMIN_EMAIL` | the admin's email |
| `ADMIN_INITIAL_PASSWORD` | 12+ characters. Change it in the admin console right after the first login |
| `NEPOPAY_RATELIMIT_LOGINIPPERMINUTE` | `1000` |
| `NEPOPAY_RATELIMIT_REGISTERPERHOUR` | `1000` |
| `NEPOPAY_RATELIMIT_REFRESHPERMINUTE` | `1000` |

Why the three rate-limit settings: every visitor reaches the API through Vercel's servers, so per-address limits would treat
everyone as the same few clients. The per-account and per-user limits keep protecting logins, searches and transfers.

## 3. Vercel (frontend)
- Project root directory: `frontend`. Framework: Vite. Build: `npm run build`. Output: `dist`.
- **Do not set any `VITE_*` variable.** The site calls relative `/api/...` URLs, and `frontend/vercel.json` forwards them to Render.
- If your Render address changes, edit the `destination` in `frontend/vercel.json`, commit and push.

## 4. Check it works
1. `https://<render>/actuator/health` shows `{"status":"UP"}` (wake the free service first; it sleeps after 15 idle minutes).
2. `https://<vercel>/api/v1/users/me` shows JSON containing `"errorCode":"UNAUTHORIZED"`. That proves the rewrite works.
3. Register on the Vercel site, press F5 (you must stay logged in), send money to a second account.
4. Log in as the admin, open **My account**, change the password.

## Known limits of free hosting
- The API sleeps after ~15 minutes idle and needs about a minute to wake. The first request after sleep through Vercel can time out
  (a 504 or "Unexpected response"). Open `/actuator/health` a couple of minutes before a demo.
- Free-tier terms change: check Render's and Neon's current limits before relying on them.
