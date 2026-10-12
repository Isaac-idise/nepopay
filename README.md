# NepOpay

Simulated Nigerian digital banking sandbox. **100% simulated. ₦0 real money involved.**

```
backend/    Java 21 + Spring Boot + PostgreSQL  (REST API, ledger, fraud engine, admin)
frontend/   React + TypeScript + Vite           (customer app + admin console)
```

## Run everything locally
1. **Database:** `docker compose up -d`  (PostgreSQL on port 5432; Docker Desktop must be running)
2. **Backend:** open `backend/` in IntelliJ and run `NepOpayApplication`
   (or `mvn spring-boot:run`). Health check: http://localhost:8080/actuator/health , API docs: http://localhost:8080/swagger-ui.html
   - Optional admin account: set environment variables `ADMIN_EMAIL` and `ADMIN_INITIAL_PASSWORD` (12+ characters) on the run configuration.
     The admin username defaults to `admin`.
3. **Frontend:** in `frontend/` run `npm install` then `npm run dev`, and open http://localhost:5173

## Tests
- Backend: `mvn test` in `backend/` (needs Docker running; uses a throwaway PostgreSQL via Testcontainers)
- Frontend: `npm test` in `frontend/`

## Configuration (environment variables)
See `.env.example`. Never commit a real `.env`. Production must set `JWT_SECRET` (32+ characters).
