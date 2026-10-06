# NepOpay

Simulated Nigerian digital banking sandbox. **100% simulated. ₦0 real money involved.**

## Run locally
1. `docker compose up -d` (starts PostgreSQL)
2. `cd backend && ./mvnw spring-boot:run` (or `mvn spring-boot:run`)
3. Health: http://localhost:8080/actuator/health
4. Tests (needs Docker running): `mvn test`
