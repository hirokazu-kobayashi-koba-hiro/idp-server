# 📈 Performance Test Guide for idp-server

This guide provides comprehensive steps and configurations to perform testing on the `idp-server`
using [k6](https://k6.io/), PostgreSQL performance analytics, and synthetic test data.

> **Note**: For MySQL, see [README-mysql.md](./README-mysql.md)

---

## 📊 Performance Test Types

To ensure the idp-server performs reliably under various conditions, different types of performance tests should be
conducted. Each test type targets a specific system behavior:

| Test Type    | Description                                                                                                                                                      |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ✅ **Load**   | Identify the system's maximum sustainable throughput under expected usage (e.g., 500 RPS). Focuses on **steady-state behavior**.                                 |
| ✅ **Stress** | Push beyond the expected load to observe **failure modes**, **error rates**, and **graceful degradation**.                                                       |
| **Spike**    | Test sudden and extreme load increases (e.g., from 0 to 1000 RPS instantly) to measure the system’s **burst tolerance**.                                         |
| **Soak**     | Run the system under a typical load for an extended period (1 hour or more) to detect **memory leaks**, **GC issues**, or **performance degradation over time**. |

* Running various performance test scenarios with k6
* Analyzing database performance using `pg_stat_statements`

## Test Data Preparation

### 🗃️ User Data Generation

Use `generate_users.py` to create test user data. This script generates:
- User TSV file for `idp_user` table
- Device TSV file for `idp_user_authentication_devices` table
- Test users JSON file for k6 CIBA tests

#### Recommended: Combined Setup (1M + 9x100K)

This configuration supports both large-scale single-tenant tests and multi-tenant tests:

```shell
# 1. Register 10 tenants
./performance-test/scripts/register-tenants.sh -n 10

# 2. Generate: first tenant 1M users, other 9 tenants 100K each
python3 ./performance-test/scripts/generate_users.py \
  --tenants-file ./performance-test/data/performance-test-tenant.json \
  --users 100000 \
  --first-tenant-users 1000000

# 3. Import to PostgreSQL
./performance-test/scripts/import_users.sh multi_tenant_1m+9x100k

# 4. Setup for k6 tests
cp ./performance-test/data/multi_tenant_1m+9x100k_test_users.json \
   ./performance-test/data/performance-test-multi-tenant-users.json
```

**Result:**
- Tenant 1: 1,000,000 users (for large-scale tests)
- Tenant 2-10: 100,000 users each (for multi-tenant tests)
- Total: 1,900,000 users

#### Alternative: Uniform Multi-Tenant (10 x 100K)

```shell
python3 ./performance-test/scripts/generate_users.py --users 100000 \
  --tenants-file ./performance-test/data/performance-test-tenant.json
./performance-test/scripts/import_users.sh multi_tenant_10x100k
```

#### Alternative: Single Tenant Only

```shell
python3 ./performance-test/scripts/generate_users.py --users 1000000
./performance-test/scripts/import_users.sh single_tenant_1m
```

## k6

### install

```shell
brew install k6
```

### set env

#### local

```shell
export BASE_URL=http://localhost:8080
export TENANT_ID=67e7eae6-62b0-4500-9eff-87459f63fc66
export CLIENT_ID=clientSecretPost
export CLIENT_SECRET=clientSecretPostPassword1234567890123456789012345678901234567890123456789012345678901234567890
export REDIRECT_URI=https://www.certification.openid.net/test/a/idp_oidc_basic/callback
export ACCESS_TOKEN=eyJhbGciOiJSUzI1NiIsInR5cCI6ImF0K2p3dCIsImtpZCI6ImlkX3Rva2VuX25leHRhdXRoIn0.eyJzdWIiOiI5MmU2ZmEwMy02NjgwLTQ3NDItOGQzOC0xYjU4NTFjMmJlYzciLCJzY29wZSI6InBob25lIG1hbmFnZW1lbnQgb3BlbmlkIHRyYW5zZmVycyBwcm9maWxlIGVtYWlsIGFjY291bnQiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgwODAvNjdlN2VhZTYtNjJiMC00NTAwLTllZmYtODc0NTlmNjNmYzY2IiwiZXhwIjoxNzQ5OTE5ODM1LCJpYXQiOjE3NDk5MTYyMzUsImNsaWVudF9pZCI6ImNsaWVudFNlY3JldFBvc3QiLCJqdGkiOiJmMzQ0Yjc0ZC1iNWJlLTQ4MjgtOWU3OS00YjVmNTRmMTFkNjkifQ.TWVEVEO172iHaBf13xr5Spcmh8wDcTY6HZlhnmpZkI8YI93L5kvpfJKtTrwxJqguYCaWXEkNKk9MlbOp0fF-keIyq1JS2ikfRkUSrYRg0SYt5Fsmvqf2re4YpxbPKlAOtD-DvNz6WQ0mQESMOTN5oYbd9togIIrqB7ReI1YYDntC6IQZKup4heYkbm6z4zn_2GjAnbOzF-gmaZ7Jm2iOhHjgvQLHSXykkUMHOb_JA3q_CachHNUh0mMhRk-3qpJlOxxCnlr6U5Q-QZS60DcKqp0ovmz6DTPZJy9aMRsDuqNwmbHpohBQz3Jzo-QG6nLsz40NGC00Plo4uaXsXTcJvA
```

### run

### mkdir

```shell
mkdir -p performance-test/result/stress
mkdir -p performance-test/result/load
```

#### stress test

```shell
k6 run --summary-export=./performance-test/result/stress/scenario-1-authorization-request.json ./performance-test/stress/scenario-1-authorization-request.js
```

```shell
k6 run --summary-export=./performance-test/result/stress/scenario-2-bc.json ./performance-test/stress/scenario-2-bc.js
```

```shell
# CIBA scenarios (login_hint patterns)
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-device.json ./performance-test/stress/scenario-3-ciba-device.js
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-sub.json ./performance-test/stress/scenario-3-ciba-sub.js
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-email.json ./performance-test/stress/scenario-3-ciba-email.js
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-phone.json ./performance-test/stress/scenario-3-ciba-phone.js
k6 run --summary-export=./performance-test/result/stress/scenario-3-ciba-ex-sub.json ./performance-test/stress/scenario-3-ciba-ex-sub.js
```

```shell
k6 run --summary-export=./performance-test/result/stress/scenario-4-token-password.json ./performance-test/stress/scenario-4-token-password.js
```

```shell
k6 run --summary-export=./performance-test/result/stress/scenario-5-token-client-credentials.json ./performance-test/stress/scenario-5-token-client-credentials.js
```

```shell
k6 run --summary-export=./performance-test/result/stress/scenario-6-jwks.json ./performance-test/stress/scenario-6-jwks.js
```

```shell
k6 run --summary-export=./performance-test/result/stress/scenario-7-token-introspection.json ./performance-test/stress/scenario-7-token-introspection.js
```

```shell
k6 run --summary-export=./performance-test/result/stress/scenario-8-authentication-device.json ./performance-test/stress/scenario-8-authentication-device.js
```

```shell
k6 run --summary-export=./performance-test/result/stress/scenario-9-identity-verification-application.json ./performance-test/stress/scenario-9-identity-verification-application.js
```

#### load test

```shell
k6 run ./performance-test/load/scenario-1-ciba-login.js
```

```shell
k6 run ./performance-test/load/scenario-2-multi-ciba-login.js
```

```shell
# Admin API credentials required for deleteExpiredData scenario
# Get these from your idp-server admin configuration
export IDP_SERVER_API_KEY=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
export IDP_SERVER_API_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
k6 run ./performance-test/load/scenario-3-peak-login.js
```

```shell
k6 run ./performance-test/load/scenario-4-authorization-code.js
```


## CPU Memory

```shell
docker stats $(docker compose ps -q idp-server)
```

## 📄 App Logging

If you analyze execution time at each step, enable file logging in your application.yaml:

```yaml
logging:
  level:
    root: info
    web: info
  file:
    name: logs/idp-server.log
    pattern:
      file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```


## 📊 analyze

### 📈 k6 Result Example

You can expect an output like the following:

```
checks_total.......................: 13585  444.5355/s
checks_succeeded...................: 99.85% 13565 out of 13585
checks_failed......................: 0.14%  20 out of 13585
http_req_duration..................: avg=223.44ms p(95)=512.14ms
iterations.........................: 2717   88.91/s
```

* `checks_total`: Total number of `check()` calls
* `http_req_duration`: Time it took for HTTP requests to complete
* `iterations`: Total number of full scenario iterations
* `tps` (Throughput): Derived from `checks_total` per second or iterations per second depending on measurement point

Interpret the metrics in context of test goal, such as max TPS, latency, or error rate.

---

### 🐘 PostgreSQL: Using `pg_stat_statements`

`pg_stat_statements` is a powerful PostgreSQL extension that helps analyze SQL performance by tracking execution
statistics of all queries.

---

#### 🔧 1. Enable the Extension

To enable `pg_stat_statements`, it must be preloaded by PostgreSQL. You can set it in `postgresql.conf`:

```conf
shared_preload_libraries = 'pg_stat_statements'
```

If you are using Docker (e.g., Docker Compose), you can pass it as a command-line argument:

```yaml
command: [ "postgres", "-c", "shared_preload_libraries=pg_stat_statements" ]
```

---

#### 💥 2. Create the Extension (One-Time Setup)

After the database is up and running, connect using `psql` and run:

```sql
CREATE
EXTENSION pg_stat_statements;
```

This only needs to be done once per database.

---

#### 🔍 3. View Query Statistics

To check query performance:

```sql
SELECT query,
       calls,
       total_exec_time,
       mean_exec_time, rows, shared_blks_hit, shared_blks_read
FROM
    pg_stat_statements
ORDER BY
    total_exec_time DESC
    LIMIT 20;
```

#### 📌 Column Descriptions

| Column             | Description                                         |
|--------------------|-----------------------------------------------------|
| `query`            | Normalized SQL (literals are replaced with `?`)     |
| `calls`            | Number of times the query was executed              |
| `total_exec_time`  | Total execution time in milliseconds                |
| `mean_exec_time`   | Average execution time per call (ms)                |
| `rows`             | Total number of rows returned                       |
| `shared_blks_hit`  | Cache hits (higher is better)                       |
| `shared_blks_read` | Disk reads (higher may indicate slower performance) |

---

#### 🧼 4. Reset Statistics (Optional)

To reset statistics before a performance test or benchmark:

```sql
SELECT pg_stat_statements_reset();
```

---

#### 📦 Docker Compose Example

```yaml
services:
  postgresql:
    image: postgres:16
    environment:
      POSTGRES_PASSWORD: idpserver
    command: [ "postgres", "-c", "shared_preload_libraries=pg_stat_statements" ]
```

This ensures that `pg_stat_statements` is enabled when PostgreSQL starts inside a Docker container.

## Tips

* confirm disk

```shell
docker system df
```

* delete data at docker

```shell
docker system prune -a
docker volume prune
docker builder prune
```

