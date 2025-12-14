# Authentication Device Data Migration Scripts

Issue #964: Data migration for authentication device table.

## Overview

These scripts migrate data from `idp_user.authentication_devices` (JSONB/JSON) to `idp_user_authentication_devices` table.

**Table creation is handled by Flyway migration.** These scripts are for data migration only.

## Scripts

| Script | Database | Description |
|--------|----------|-------------|
| `migrate-authentication-devices.sh` | PostgreSQL | Data migration and verification |
| `migrate-authentication-devices-mysql.sh` | MySQL | Data migration and verification |

## Features

- **Idempotent**: Can be run multiple times safely
- **Verification**: Check data consistency between JSONB and table
- **Dry-run**: Preview what would be done without changes
- **Differential sync**: Migrate only missing records

## Usage

### PostgreSQL

```bash
export DB_OWNER_PASSWORD=your_password

# Verify data consistency
./scripts/migration/migrate-authentication-devices.sh --verify

# Dry run (show what would be done)
./scripts/migration/migrate-authentication-devices.sh --dry-run

# Migrate data (idempotent)
./scripts/migration/migrate-authentication-devices.sh

# Or explicitly
./scripts/migration/migrate-authentication-devices.sh --sync
```

### MySQL

```bash
export DB_PASSWORD=your_password

# Verify data consistency
./scripts/migration/migrate-authentication-devices-mysql.sh --verify

# Migrate data (idempotent)
./scripts/migration/migrate-authentication-devices-mysql.sh
```

## Options

| Option | Description |
|--------|-------------|
| `--verify` | Only verify data consistency |
| `--dry-run` | Show what would be done without changes |
| `--sync` | Sync missing records (default behavior) |
| `--help` | Show help message |

## Workflow

### Zero-downtime Migration

```
1. Run Flyway migration (creates table with initial data)
   - V0_9_21_4 for PostgreSQL
   - V0_10_1 for MySQL

2. Deploy new application (rolling deployment)
   - New app writes to both JSONB and table
   - New app reads from table

3. Sync differential data (if any)
   ./migrate-authentication-devices.sh --sync

4. Verify data consistency
   ./migrate-authentication-devices.sh --verify
```

### Output Example

```
==========================================
 Authentication Device Data Migration
 PostgreSQL - Issue #964
==========================================

[INFO] Database: localhost:5432/idp
[INFO] User: idp_owner

[INFO] Verifying data consistency...

┌────────────────────────────────────────┐
│       Data Consistency Report          │
├────────────────────────────────────────┤
│ JSONB devices:               1100370 │
│ Table devices:               1100370 │
│ Missing in table:                  0 │
└────────────────────────────────────────┘

[OK] All data is synchronized

[OK] Done!
```

## Environment Variables

### PostgreSQL

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | Database host |
| `DB_PORT` | 5432 | Database port |
| `DB_NAME` | idp | Database name |
| `DB_OWNER_USER` | idp_owner | Database owner user |
| `DB_OWNER_PASSWORD` | (required) | Database owner password |

### MySQL

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | Database host |
| `DB_PORT` | 3306 | Database port |
| `DB_NAME` | idp | Database name |
| `DB_USER` | idp_owner | Database user |
| `DB_PASSWORD` | (required) | Database password |
