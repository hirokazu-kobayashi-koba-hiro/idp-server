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

### 🗃️ User

```shell
python3 ./performance-test/data/generate_users_100k.py
```

```shell
chmod +x ./performance-test/data/test-user.sh

# Generate test user JSON for all login_hint patterns
./performance-test/data/test-user.sh all

# Or generate for specific pattern:
# ./performance-test/data/test-user.sh device  # device:{deviceId} pattern (default)
# ./performance-test/data/test-user.sh sub     # sub:{subject} pattern
# ./performance-test/data/test-user.sh email   # email:{email} pattern
# ./performance-test/data/test-user.sh phone   # phone:{phone} pattern
# ./performance-test/data/test-user.sh ex-sub  # ex-sub:{externalSubject} pattern
```

* register user data

```shell
mysql -u root -p"$MYSQL_ROOT_PASSWORD" -h 127.0.0.1 -P 3306 idpserver --local-infile=1 -e "
LOAD DATA LOCAL INFILE './performance-test/data/generated_users_100k.tsv'
INTO TABLE idp_user
FIELDS TERMINATED BY '\t' OPTIONALLY ENCLOSED BY '\"' ESCAPED BY '\"'
LINES TERMINATED BY '\n'
(id, tenant_id, provider_id, external_user_id, name, email, email_verified, phone_number, phone_number_verified, preferred_username, status, authentication_devices);
"
```

* register authentication devices data (for optimized query performance)

```shell
mysql -u root -p"$MYSQL_ROOT_PASSWORD" -h 127.0.0.1 -P 3306 idpserver --local-infile=1 -e "
LOAD DATA LOCAL INFILE './performance-test/data/generated_user_devices_100k.tsv'
INTO TABLE idp_user_authentication_devices
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(id, tenant_id, user_id, os, model, platform, locale, app_name, priority, available_methods, notification_token, notification_channel);
"
```

### tenants

Register performance test tenants using the onboarding API. The script reads credentials from `.env` file.

**Prerequisites:**
- `.env` file in project root with the following variables:
  ```
  ADMIN_USER_EMAIL=admin@example.com
  ADMIN_USER_PASSWORD=your-password
  ADMIN_TENANT_ID=your-admin-tenant-id
  ADMIN_CLIENT_ID=your-client-id
  ADMIN_CLIENT_SECRET=your-client-secret
  AUTHORIZATION_SERVER_URL=http://localhost:8080
  ```

**Register tenants:**

```shell
# Register 5 tenants (credentials loaded from .env)
./performance-test/data/register-tenants.sh -n 5

# Dry run mode
./performance-test/data/register-tenants.sh -n 5 -d true

# Specify custom base URL
./performance-test/data/register-tenants.sh -n 5 -b http://localhost:8080
```

This creates:
- `performance-test/data/performance-test-tenant.json` - Tenant configuration for load tests

### Multi-tenant User Data (Optional)

For multi-tenant load tests with dedicated users per tenant:

```shell
# Generate multi-tenant user data (reads from performance-test-tenant.json)
python3 ./performance-test/data/generate_multi_tenant_users.py

# Import to MySQL
mysql -u root -p"$MYSQL_ROOT_PASSWORD" -h 127.0.0.1 -P 3306 idpserver --local-infile=1 -e "
LOAD DATA LOCAL INFILE './performance-test/data/multi_tenant_users.tsv'
INTO TABLE idp_user
FIELDS TERMINATED BY '\t' OPTIONALLY ENCLOSED BY '\"' ESCAPED BY '\"'
LINES TERMINATED BY '\n'
(id, tenant_id, provider_id, external_user_id, name, email, email_verified, phone_number, phone_number_verified, preferred_username, status, authentication_devices);
"

mysql -u root -p"$MYSQL_ROOT_PASSWORD" -h 127.0.0.1 -P 3306 idpserver --local-infile=1 -e "
LOAD DATA LOCAL INFILE './performance-test/data/multi_tenant_devices.tsv'
INTO TABLE idp_user_authentication_devices
FIELDS TERMINATED BY '\t'
LINES TERMINATED BY '\n'
(id, tenant_id, user_id, os, model, platform, locale, app_name, priority, available_methods, notification_token, notification_channel);
"
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
