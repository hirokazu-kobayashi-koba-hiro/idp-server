# 📈 Performance Test Guide for idp-server (MySQL)

This guide provides comprehensive steps and configurations to perform testing on the `idp-server`
using [k6](https://k6.io/), MySQL performance analytics, and synthetic test data.

> **Note**: For PostgreSQL, see [README.md](./README.md)

---

## 📊 Performance Test Types

To ensure the idp-server performs reliably under various conditions, different types of performance tests should be
conducted. Each test type targets a specific system behavior:

| Test Type    | Description                                                                                                                                                      |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| ✅ **Load**   | Identify the system's maximum sustainable throughput under expected usage (e.g., 500 RPS). Focuses on **steady-state behavior**.                                 |
| ✅ **Stress** | Push beyond the expected load to observe **failure modes**, **error rates**, and **graceful degradation**.                                                       |
| **Spike**    | Test sudden and extreme load increases (e.g., from 0 to 1000 RPS instantly) to measure the system's **burst tolerance**.                                         |
| **Soak**     | Run the system under a typical load for an extended period (1 hour or more) to detect **memory leaks**, **GC issues**, or **performance degradation over time**. |

* Running various performance test scenarios with k6
* Analyzing database performance using `performance_schema`

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

# 3. Import to MySQL
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

### 🐬 MySQL: Using `performance_schema`

MySQL provides `performance_schema` for analyzing SQL performance. It is enabled by default in MySQL 5.7+.

---

#### 🔐 1. Grant Permission (Required)

The `performance_schema` requires SELECT permission. Grant it to your user:

```shell
mysql -h 127.0.0.1 -P 3306 -u root -p < libs/idp-server-database/mysql/operation/grant-performance-schema.sql
```

Or manually:

```shell
mysql -h 127.0.0.1 -P 3306 -u root -p
```

```sql
-- Grant permission to idpserver user
GRANT SELECT ON performance_schema.* TO 'idpserver'@'%';
FLUSH PRIVILEGES;
```

---

#### 🔧 3. Enable Performance Schema (if disabled)

Check if `performance_schema` is enabled:

```sql
SHOW VARIABLES LIKE 'performance_schema';
```

If disabled, add to `my.cnf`:

```ini
[mysqld]
performance_schema = ON
```

For Docker, you can pass it as a command-line argument:

```yaml
command: ["mysqld", "--performance-schema=ON"]
```

---

#### 🔍 4. View Query Statistics

To check query performance:

```sql
SELECT
    DIGEST_TEXT AS query,
    COUNT_STAR AS calls,
    ROUND(SUM_TIMER_WAIT / 1000000000, 2) AS total_exec_time_ms,
    ROUND(AVG_TIMER_WAIT / 1000000000, 2) AS avg_exec_time_ms,
    SUM_ROWS_EXAMINED AS rows_examined,
    SUM_ROWS_SENT AS rows_sent
FROM performance_schema.events_statements_summary_by_digest
WHERE SCHEMA_NAME = 'idpserver'
ORDER BY SUM_TIMER_WAIT DESC
LIMIT 20;
```

#### 📌 Column Descriptions

| Column              | Description                                           |
|---------------------|-------------------------------------------------------|
| `DIGEST_TEXT`       | Normalized SQL (literals are replaced with `?`)       |
| `COUNT_STAR`        | Number of times the query was executed                |
| `SUM_TIMER_WAIT`    | Total execution time in picoseconds                   |
| `AVG_TIMER_WAIT`    | Average execution time per call (picoseconds)         |
| `SUM_ROWS_EXAMINED` | Total number of rows examined                         |
| `SUM_ROWS_SENT`     | Total number of rows returned                         |

---

#### 🧼 5. Reset Statistics (Optional)

To reset statistics before a performance test or benchmark:

```sql
TRUNCATE TABLE performance_schema.events_statements_summary_by_digest;
```

---

#### 📦 6. Docker Compose Example

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: idpserver
    command: ["mysqld", "--performance-schema=ON"]
```

This ensures that `performance_schema` is enabled when MySQL starts inside a Docker container.

---

#### 🔍 7. Check Index Usage

To verify indexes are being used:

```sql
-- Show indexes on idp_user table
SHOW INDEX FROM idp_user;

-- Explain a query to check index usage
EXPLAIN SELECT * FROM idp_user WHERE tenant_id = ? AND phone_number = ?;
```

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

* Enable local_infile for LOAD DATA

If you get "Loading local data is disabled" error:

```sql
SET GLOBAL local_infile = 1;
```

Or start MySQL with:

```yaml
command: ["mysqld", "--local-infile=1"]
```
