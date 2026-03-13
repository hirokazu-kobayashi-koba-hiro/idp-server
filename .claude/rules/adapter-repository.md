---
paths:
  - "libs/idp-server-core-adapter/src/**/*.java"
---

# Adapter層（永続化実装）のルール

## Tenant第一引数（必須）
- 全てのRepository/DataSourceメソッドは `Tenant tenant` を第一引数にすること
- **例外**: `OrganizationRepository` のみTenant不要
- マルチテナント分離を保証するための必須ルール

```java
// OK
public User find(Tenant tenant, UserIdentifier userId) { ... }

// NG - Tenant引数がない
public User find(UserIdentifier userId) { ... }
```

## 両DB実装（必須）
- `SqlExecutor` を実装する場合、PostgreSQL用とMySQL用の**両方**を実装すること
- `SqlExecutors`（複数形）クラスで `SqlExecutor.type()` により切り替える

```java
// SqlExecutors パターン
public class XxxSqlExecutors implements XxxSqlExecutor {
    Map<String, XxxSqlExecutor> executors; // "postgresql", "mysql"

    XxxSqlExecutors() {
        this.executors = Map.of(
            "postgresql", new XxxPostgresqlSqlExecutor(),
            "mysql", new XxxMysqlSqlExecutor()
        );
    }
}
```

## Adapter層にビジネスロジック禁止
- Adapter層はCRUD操作のみ
- 条件分岐・バリデーション・計算ロジックはCore層で行う
