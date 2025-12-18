# Writer/Reader DataSource

## 概要

`idp-server` は、**Writer/Reader DataSourceの自動分岐**により、読み取り負荷分散とパフォーマンス最適化を実現しています。

### 2つのデータソース選択機能

1. **DB種別の選択**: PostgreSQL または MySQL（アプリケーション単位）
2. **Writer/Readerの選択**: 主従レプリケーションでの自動分岐（@Transaction(readOnly)による）

`@Transaction(readOnly=true)` アノテーションに基づいて、自動的にWriter（主）またはReader（従）DataSourceを選択します。また、`ApplicationDatabaseTypeProvider`により、アプリケーション単位でPostgreSQL/MySQLを切り替えることができます。

`Spring` などのFWに頼らず、JDK ProxyとThreadLocalによる明示的制御で実装することで、OSSとしての拡張性・ポータビリティを高めています。

ここでは、Writer/Reader DataSourceの分岐の仕組みと、それを支える各コンポーネントの責務について説明します。また、`Spring` との比較も記載します。

**関連ドキュメント**: トランザクション分離レベルとRead Your Own Writesについては [トランザクション](./transaction.md#7-トランザクション分離レベル) を参照してください。

## アーキテクチャ

### レイヤー構成

```
┌─────────────────────────────────────────────────────┐
│ Application層                                        │
│   IdpServerApplication                              │
│     └─ EntryService実装をProxy経由で取得             │
└─────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────┐
│ Platform層 (Proxy)                                   │
│   TenantAwareEntryServiceProxy                      │
│     ├─ @Transactionアノテーション検出                 │
│     ├─ readOnly属性チェック                          │
│     └─ OperationType決定 (READ/WRITE)               │
└─────────────────────────────────────────────────────┘
                          ↓
        ┌─────────────────┴──────────────────┐
        │                                    │
    readOnly=false                      readOnly=true
    (デフォルト)                           (明示的)
        │                                    │
        ↓                                    ↓
┌──────────────────┐              ┌──────────────────┐
│ OperationType    │              │ OperationType    │
│   = WRITE        │              │   = READ         │
└──────────────────┘              └──────────────────┘
        ↓                                    ↓
┌──────────────────┐              ┌──────────────────┐
│ TransactionManager│              │ TransactionManager│
│ .beginTransaction│              │ .createConnection│
└──────────────────┘              └──────────────────┘
        ↓                                    ↓
┌──────────────────┐              ┌──────────────────┐
│DbConnectionProvider│            │DbConnectionProvider│
│.getWriterConnection│            │.getReaderConnection│
└──────────────────┘              └──────────────────┘
        ↓                                    ↓
┌──────────────────┐              ┌──────────────────┐
│ Writer DataSource│              │ Reader DataSource│
│   (主DB)         │              │   (従DB/Replica) │
│ INSERT/UPDATE/   │              │ SELECT only      │
│ DELETE可能       │              │ 読み取り負荷分散  │
└──────────────────┘              └──────────────────┘
```

### 主要コンポーネント

| コンポーネント | 実装クラス | 役割 |
|--------------|----------|------|
| **Proxy** | [TenantAwareEntryServiceProxy.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/TenantAwareEntryServiceProxy.java) | @Transactionアノテーション検出、Writer/Reader分岐 |
| **TransactionManager** | [TransactionManager.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/TransactionManager.java) | トランザクション開始・コミット・ロールバック |
| **OperationContext** | OperationContext | READ/WRITE判定保持（ThreadLocal） |
| **ApplicationDatabaseTypeProvider** | [ApplicationDatabaseTypeProvider.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/ApplicationDatabaseTypeProvider.java) | **アプリケーション単位**でDB種別解決（PostgreSQL/MySQL） |
| **DbConnectionProvider** | [DbConnectionProvider.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/DbConnectionProvider.java) | Writer/ReaderからConnection供給 |

### ApplicationDatabaseTypeProviderの役割

**アプリケーション単位でDB種別（PostgreSQL/MySQL）を指定**します。

```java
public interface ApplicationDatabaseTypeProvider {
  DatabaseType provide();  // 引数なし: アプリケーション全体で共通
}
```

**特徴**:
- テナントごとではなく、**アプリケーション全体**で共通のDB種別を使用
- 環境変数または設定ファイルでPostgreSQL/MySQLを切り替え
- 起動時に決定され、実行中は変更されない

**用途**:
- 開発環境: PostgreSQL
- 本番環境: MySQL（またはPostgreSQL）
- Repository実装でDB種別に応じたSQL文を選択（例: `RETURNING` vs `LAST_INSERT_ID()`）

**注意**: テナントごとに異なるDB種別を使用する場合は、別途`DialectProvider`を使用します（現在は未実装）。

### 処理フロー

1. **TenantAwareEntryServiceProxy**: @Transactionアノテーション検出、readOnly属性チェック
2. **OperationType決定**: readOnly=false → WRITE、readOnly=true → READ
3. **TransactionManager**: OperationTypeに基づいてトランザクション開始またはConnection作成
4. **DbConnectionProvider**: Writer/Reader DataSourceからConnection取得
5. **EntryService**: ビジネスロジック実行

## Spring との比較

| 機能カテゴリ                | Spring Framework                                        | idp-server                                                                |
|-----------------------|---------------------------------------------------------|---------------------------------------------------------------------------|
| **AOPによる横断処理**        | `@Transactional` → AOP                                  | `TenantAwareEntryServiceProxy`（JDK Proxy + `invoke()`）で制御                 |
| **Txの開始/終了**          | `PlatformTransactionManager`が制御                         | `TransactionManager`が　`begin/commit/rollback` を制御                         |
| **データソースの選択**         | ルーティングを独自実装する必要あり                                       | `ApplicationDatabaseTypeProvider.provide()` でDB種別を解決                                 |
| **DataSourceContext** | `ThreadLocal`: `RoutingContextHolder`                   | `TransactionManager`が　`OperationContext`と `DbConnectionProvider` を利用し解決する |
| **Writer/Reader分岐**   | `@Transactional(readOnly=true)` などを利用しルーティングを独自実装する必要あり | `@Transaction(readOnly = true)` で自動制御                                     |

---

## Writer/Reader分岐の詳細

### TenantAwareEntryServiceProxyによる自動分岐

`@Transaction`アノテーションの`readOnly`属性に基づいて、自動的にWriter/Readerを選択します。

#### 書き込み操作（デフォルト）

```java
@Transaction  // readOnly = false（デフォルト）
public class ClientManagementEntryService implements ClientManagementApi {

    public ClientManagementResponse create(...) {
        // ✅ Proxyが自動的にWriter DataSourceを選択
        // ✅ TransactionManager.beginTransaction()でWRITEモード
        // ✅ OperationType.WRITE → DbConnectionProvider.getWriterConnection()
        // ✅ Read Your Own Writes: 同一トランザクション内で更新後のデータを再取得可能
    }
}
```

**Writer DataSourceの特性**:
- INSERT/UPDATE/DELETEが可能
- トランザクション分離レベル: READ COMMITTED
- Read Your Own Writes: 更新後即座に再取得可能
- DB関数（now()等）の値も取得可能

詳細: [トランザクション - Read Your Own Writes](./transaction.md#8-read-your-own-writes-パターン)

#### 読み取り専用操作

```java
public class ClientManagementEntryService implements ClientManagementApi {

    @Transaction(readOnly = true)  // ✅ 読み取り専用
    public ClientManagementResponse findList(...) {
        // ✅ Proxyが自動的にReader DataSourceを選択
        // ✅ TransactionManager.createConnection()でREADモード
        // ✅ OperationType.READ → DbConnectionProvider.getReaderConnection()
        // ✅ 読み取り専用のため更新不可
    }
}
```

**Reader DataSourceの特性**:
- SELECTのみ可能（INSERT/UPDATE/DELETE不可）
- 主従レプリケーションの従（Replica）に接続
- 読み取り負荷を分散
- Read Your Own Writesは無関係（更新操作がないため）

### 分岐フロー

```text
TenantAwareEntryServiceProxy
    ↓
@Transactionアノテーション検出
    ↓
readOnly属性チェック
    ↓
┌─────────────────┬──────────────────┐
│ readOnly=false  │ readOnly=true    │
│ (デフォルト)      │                  │
└─────────────────┴──────────────────┘
    ↓                    ↓
OperationType.WRITE  OperationType.READ
    ↓                    ↓
Writer DataSource    Reader DataSource
    ↓                    ↓
beginTransaction()   createConnection()
    ↓                    ↓
INSERT/UPDATE/DELETE    SELECT
```

**詳細**: [AI開発者向け - Transaction管理](../content_10_ai_developer/ai-12-platform.md#datasourceトランザクションproxy)

---

## TenantAwareEntryServiceProxy 実装詳細

**情報源**: [TenantAwareEntryServiceProxy.java:29-64](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/proxy/TenantAwareEntryServiceProxy.java#L29-L64)

### invoke()メソッド - トランザクション制御の核心

```java
public class TenantAwareEntryServiceProxy implements InvocationHandler {

  protected final Object target;  // 実際のEntryService
  private final ApplicationDatabaseTypeProvider applicationDatabaseTypeProvider;

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // 1. @Transactionアノテーション検出
    boolean isTransactional =
        method.isAnnotationPresent(Transaction.class)
            || target.getClass().isAnnotationPresent(Transaction.class);

    // 2. readOnly属性を取得
    Transaction tx = method.getAnnotation(Transaction.class);
    if (tx == null) {
      tx = target.getClass().getAnnotation(Transaction.class);
    }
    boolean readOnly = tx != null && tx.readOnly();

    // 3. OperationType決定
    OperationType operationType = readOnly ? OperationType.READ : OperationType.WRITE;

    // 4. READ操作の処理
    if (isTransactional && operationType == OperationType.READ) {
      OperationContext.set(operationType);  // ThreadLocalに設定
      TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);
      TenantLoggingContext.setTenant(tenantIdentifier);

      // Connection作成（READ専用）
      // ...
    }

    // 5. WRITE操作の処理
    if (isTransactional && operationType == OperationType.WRITE) {
      OperationContext.set(operationType);  // ThreadLocalに設定
      TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);
      TenantLoggingContext.setTenant(tenantIdentifier);

      // Transaction開始（WRITE）
      TransactionManager.beginTransaction(...);
      // ...
    }
  }
}
```

**重要ポイント**:
- ✅ **JDK Proxy**: `InvocationHandler`実装でSpring AOPなしで横断処理
- ✅ **アノテーション検出**: メソッドレベル→クラスレベルの順で`@Transaction`を検索
- ✅ **readOnly自動判定**: `@Transaction(readOnly=true)` → READ、なし → WRITE
- ✅ **OperationContext**: ThreadLocalでREAD/WRITE状態を保持
- ✅ **TenantLoggingContext**: ログにtenantId/clientIdを自動付与

---

---

**情報源**:
- [TenantAwareEntryServiceProxy.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/TenantAwareEntryServiceProxy.java)
- [TransactionManager.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/TransactionManager.java)
- [ApplicationDatabaseTypeProvider.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/ApplicationDatabaseTypeProvider.java)
- [DbConnectionProvider.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/datasource/DbConnectionProvider.java)

**最終更新**: 2025-12-18
