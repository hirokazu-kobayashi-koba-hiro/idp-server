# k6 Stress Test Results for idp-server

Date: June 2025
Target: `idp-server`
Tool: [k6](https://k6.io/) v1.0.0
Environment: Local environment via Docker Compose (PostgreSQL / MySQL / Redis)

---

## Configuration

* **Docker Compose Resource Limits**:

    * `cpus: 1.5`, `memory: 2g` (for idp-server container)
* **JVM Settings**:

    * `JAVA_TOOL_OPTIONS: -Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=100`
* **Tomcat Settings**:

    * `maxThreads: 300`, `minSpareThreads: 50`
* **Redis Cache Settings**:

    * Enabled (`CACHE_ENABLE: true`), TTL: 300 seconds
* **PostgreSQL**:

    * `pg_stat_statements` enabled

---

## Scenario Results

* Each scenario ran for 30 seconds

| Scenario                      | VUs | TPS   | p95 Latency (ms) | Error Rate (%) |
|-------------------------------|-----|-------|------------------|----------------|
| 1. Authorization Request      | 120 | 2,057 | 179.34           | 0.00           |
| 2. BC Request                 | 120 | 712   | 251.11           | 0.00           |
| 3. CIBA Flow All              | 120 | 712   | 386.31           | 0.17           |
| 4. Token (Password)           | 120 | 34    | 6400.00          | 60.47          |
| 5. Token (Client Credentials) | 120 | 614   | 298.70           | 0.00           |
| 6. JWKS                       | 120 | 2,205 | 107.20           | 0.00           |
| 7. Token Introspection        | 120 | 1,388 | 151.18           | 0.00           |
| 8. Authentication Device      | 120 | 2,054 | 148.43           | 0.00           |

---

## Observations

* ✅ **Endpoints responding stably under high load**:

    * `/jwks`, `/introspection`, and `/authentication-devices/{id}` show strong performance with minimal latency and
      high throughput.
    * `/authorizations` also performed reliably, even with ID token generation included.

* ⚠️ **Moderately heavy processing**:

    * `/backchannel/authentications` involves more steps (e.g., user lookup, device notifications), so its throughput is
      lower than basic auth, but the p95 latency remains under 400ms and is considered acceptable.

* ❌ **Problematic endpoint**:

    * `Resource Owner Password Credentials Grant` (scenario-4) is a clear bottleneck with a 60%+ failure rate and p95
      over 6 seconds.

        * Likely due to the cost of password verification using bcrypt.

---

This result was obtained from a local environment.
Revalidation is required in a production-like configuration.
