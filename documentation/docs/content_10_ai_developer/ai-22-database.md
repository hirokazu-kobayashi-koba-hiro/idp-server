# idp-server-database

## モジュール概要

**情報源**: `libs/idp-server-database/`
**確認日**: 2025-10-12

### 責務

データベーススキーマ定義とマイグレーション（Flyway）。

- **DDL**: `CREATE TABLE`文
- **マイグレーション**: Flywayによるバージョン管理
- **初期データ**: 基本設定の投入
- **RLS**: Row Level Security（PostgreSQL）

## ディレクトリ構成

```
libs/idp-server-database/
├── postgresql/
│   ├── V1_0_0__init_lib.sql          # 初期スキーマ
│   ├── V1_0_1__add_column.sql        # マイグレーション例
│   └── operation/
│       └── app_user.sql              # アプリケーションユーザー作成
├── mysql/
│   └── (PostgreSQLと同様の構成)
└── README.md                          # PostgreSQL→MySQL DDL変換ルール
```

**情報源**: `find libs/idp-server-database -name "*.sql"`

## Flyway マイグレーション

**情報源**: [README.md](../../libs/idp-server-database/README.md)

### マイグレーション実行

```bash
# PostgreSQL
DB_TYPE=postgresql ./gradlew flywayClean flywayMigrate

# MySQL
DB_TYPE=mysql ./gradlew flywayClean flywayMigrate

# カスタムURL
DB_TYPE=postgresql DB_URL=jdbc:postgresql://localhost:5432/custom_db ./gradlew flywayMigrate
```

### PostgreSQL → MySQL DDL変換ルール

| PostgreSQL | MySQL | 備考 |
|-----------|-------|------|
| `BOOLEAN` | `TINYINT(1)` | 1=TRUE, 0=FALSE |
| `TIMESTAMP DEFAULT now()` | `DATETIME DEFAULT CURRENT_TIMESTAMP` | タイムスタンプデフォルト |
| `JSONB` | `JSON` | MySQL 5.7+ |
| `INET` | `VARCHAR(45)` | IPv6対応IP格納 |
| `SERIAL` | `INT AUTO_INCREMENT` | 自動増分 |
| `gen_random_uuid()` | `UUID()` | UUID生成 |
| `UUID` type | `CHAR(36)` | 文字列として格納 |
| `UNIQUE (...) WHERE ...` | Not supported | トリガーで代替 |

**情報源**: [README.md:24-46](../../libs/idp-server-database/README.md#L24-L46)

### Docker Flyway Migrator

```bash
# Docker イメージビルド
docker build -f ./Dockerfile-flyway -t idp-flyway-migrator:latest .

# コンテナ実行
docker run --rm \
  -e DB_TYPE=postgresql \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/idpserver \
  -e DB_USER=idp_app_user \
  -e DB_PASSWORD=secret \
  idp-flyway-migrator:latest migrate
```

## 主要テーブル

```sql
-- テナント
CREATE TABLE tenant (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(50) NOT NULL,
  attributes JSONB
);

-- ユーザー
CREATE TABLE idp_user (
  id UUID PRIMARY KEY,
  sub VARCHAR(255) NOT NULL,
  tenant_id UUID NOT NULL REFERENCES tenant(id)
);

-- クライアント設定
CREATE TABLE client_configuration (
  id UUID PRIMARY KEY,
  client_id VARCHAR(255) NOT NULL,
  tenant_id UUID NOT NULL REFERENCES tenant(id)
);

-- 組織
CREATE TABLE organization (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL
);

-- 組織-テナント関係
CREATE TABLE organization_tenants (
  organization_id UUID REFERENCES organization(id),
  tenant_id UUID REFERENCES tenant(id),
  PRIMARY KEY (organization_id, tenant_id)
);
```

**情報源**: `libs/idp-server-database/postgresql/V1_0_0__init_lib.sql`

## Row Level Security (RLS)

PostgreSQLでマルチテナント分離を実現。

```sql
-- RLS有効化
ALTER TABLE client_configuration ENABLE ROW LEVEL SECURITY;

-- ポリシー作成
CREATE POLICY tenant_isolation ON client_configuration
  USING (tenant_id = current_setting('app.tenant_id')::UUID);
```

**動作**:
1. TransactionManagerで`SET LOCAL app.tenant_id = 'xxx'`を実行
2. RLSポリシーが自動適用
3. テナントIDが一致する行のみアクセス可能

**情報源**: Issue #672（SQL Injection修正）、[deployment.md](../content_08_ops/ops-02-deployment.md)

## 関連ドキュメント

- [Adapter層統合ドキュメント](./ai-20-adapters.md) - databaseを含む全アダプターモジュール
- [idp-server-core-adapter](./ai-21-core-adapter.md) - Repository実装
- [idp-server-springboot-adapter](./ai-23-springboot-adapter.md) - Spring Boot統合

---

**情報源**:
- `libs/idp-server-database/postgresql/V1_0_0__init_lib.sql`
- [README.md](../../libs/idp-server-database/README.md)
- Issue #672（SQL Injection修正）

**最終更新**: 2025-10-12
