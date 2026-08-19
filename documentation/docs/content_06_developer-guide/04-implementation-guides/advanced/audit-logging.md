# 監査ログ

## 📍 このドキュメントの位置づけ

**対象読者**: 監査ログの実装詳細を理解したい開発者

**このドキュメントで学べること**:
- 監査ログ（Audit Log）の構造
- AuditLogWriter プラグインの実装方法
- 非同期ログ処理の仕組み（AuditLogPublisher）
- データベースへの永続化
- カスタムログ出力先の実装（CloudWatch Logs、Splunk等）

**前提知識**:
- [impl-12: Plugin実装ガイド](./impl-12-plugin-implementation.md)の理解
- [impl-07: Multi-Tenancy](./impl-07-multi-tenancy.md)の理解

---

## 🏗️ 監査ログアーキテクチャ

idp-serverは、すべての重要な操作を**監査ログ（Audit Log）**として記録します。

### 監査ログフロー

```
┌─────────────────────────────────────────────────────────────┐
│ 1. API操作（Control Plane / Application Plane）             │
│    - ユーザー作成、設定変更、認証、トークン発行等            │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. AuditLogCreator                                           │
│    - AuditableContext から AuditLog を生成                   │
│    - UUID生成、タイムスタンプ追加                            │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. AuditLogPublisher                                         │
│    - 非同期イベント発行（Spring Events等）                   │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. AuditLogWriters                                           │
│    - 各 AuditLogWriter の shouldExecute() 判定               │
│    - 実行すべき Writer の write() 実行                       │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. AuditLogWriter 実装                                       │
│    - AuditLogDataBaseWriter: データベースに保存              │
│    - カスタムWriter: CloudWatch Logs、Splunk等に送信         │
└─────────────────────────────────────────────────────────────┘
```

---

## 📦 AuditLog モデル

監査ログは、以下の情報を含みます。

```java
public class AuditLog {
  String id;                        // 監査ログID（UUID）
  String type;                      // ログタイプ（例: "user.created"）
  String description;               // 説明
  String tenantId;                  // テナントID
  String clientId;                  // クライアントID（オプション）
  String userId;                    // ユーザーID（オプション）
  String externalUserId;            // 外部ユーザーID（オプション）
  JsonNodeWrapper userPayload;      // ユーザー情報
  String targetResource;            // 対象リソース（例: "user"）
  String targetResourceAction;      // 操作（例: "create"）
  JsonNodeWrapper request;          // リクエスト内容
  JsonNodeWrapper before;           // 変更前の状態
  JsonNodeWrapper after;            // 変更後の状態
  String outcomeResult;             // 結果（例: "success", "failure"）
  String outcomeReason;             // 理由
  String targetTenantId;            // 対象テナントID（マルチテナント操作時）
  String ipAddress;                 // IPアドレス
  String userAgent;                 // User-Agent
  JsonNodeWrapper attributes;       // 追加属性
  boolean dryRun;                   // Dry Run モード
  LocalDateTime createdAt;          // 作成日時
}
```

**参考実装**: [AuditLog.java:25](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLog.java#L25)

### AuditLog生成例

```java
public class AuditLogCreator {

  public static AuditLog create(AuditableContext context) {
    String id = UUID.randomUUID().toString();
    LocalDateTime createdAt = SystemDateTime.now();

    return new AuditLog(
        id,
        context.type(),                    // "user.created"
        context.description(),             // "User registration"
        context.tenantId(),
        context.clientId(),
        context.userId(),
        context.externalUserId(),
        JsonNodeWrapper.fromMap(context.userPayload()),
        context.targetResource(),          // "user"
        context.targetResourceAction(),    // "create"
        JsonNodeWrapper.fromMap(context.request()),
        JsonNodeWrapper.fromMap(context.before()),
        JsonNodeWrapper.fromMap(context.after()),
        context.outcomeResult(),           // "success"
        context.outcomeReason(),
        context.targetTenantId(),
        context.ipAddress(),
        context.userAgent(),
        JsonNodeWrapper.fromMap(context.attributes()),
        context.dryRun(),
        createdAt);
  }
}
```

**参考実装**: [AuditLogCreator.java:25](../../../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/base/AuditLogCreator.java#L25)

---

## 🔌 AuditLogWriter プラグイン

### インターフェース

```java
public interface AuditLogWriter {

  /**
   * このWriterを実行すべきか判定
   *
   * @param tenant テナント情報
   * @param auditLog 監査ログ
   * @return 実行する場合 true
   */
  default boolean shouldExecute(Tenant tenant, AuditLog auditLog) {
    return true;  // デフォルトは常に実行
  }

  /**
   * 監査ログを書き込む
   *
   * @param tenant テナント情報
   * @param auditLog 監査ログ
   */
  void write(Tenant tenant, AuditLog auditLog);
}
```

**参考実装**: [AuditLogWriter.java:21](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogWriter.java#L21)

### デフォルト実装: AuditLogDataBaseWriter

データベースに監査ログを保存します。

```java
public class AuditLogDataBaseWriter implements AuditLogWriter {

  AuditLogCommandRepository auditLogCommandRepository;

  public AuditLogDataBaseWriter(AuditLogCommandRepository auditLogCommandRepository) {
    this.auditLogCommandRepository = auditLogCommandRepository;
  }

  @Override
  public void write(Tenant tenant, AuditLog auditLog) {
    auditLogCommandRepository.register(tenant, auditLog);
  }
}
```

**参考実装**: [AuditLogDataBaseWriter.java:21](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogDataBaseWriter.java#L21)

### AuditLogWriters（複数Writer管理）

```java
public class AuditLogWriters {

  List<AuditLogWriter> writers;

  public AuditLogWriters(List<AuditLogWriter> writers) {
    this.writers = writers;
  }

  public void write(Tenant tenant, AuditLog auditLog) {
    for (AuditLogWriter writer : writers) {
      // shouldExecute() で判定
      if (writer.shouldExecute(tenant, auditLog)) {
        log.info(
            "TenantId {} AuditLogWriter execute: {}",
            tenant.identifierValue(),
            writer.getClass().getSimpleName());

        // 実行
        writer.write(tenant, auditLog);
      }
    }
  }
}
```

**参考実装**: [AuditLogWriters.java:23](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogWriters.java#L23)

**特徴**:
- 複数のWriterを登録可能（データベース + CloudWatch Logs等）
- `shouldExecute()` で条件判定（テナントごとに異なる出力先等）
- すべてのWriterを順次実行

---

## 🔄 非同期処理: AuditLogPublisher

監査ログは、**非同期**で処理されます。

### AuditLogPublisher インターフェース

```java
public interface AuditLogPublisher {

  /**
   * 監査ログイベントを非同期処理のために発行
   *
   * @param auditLog 非同期処理される監査ログ
   */
  void publish(AuditLog auditLog);
}
```

**参考実装**: [AuditLogPublisher.java:28](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogPublisher.java#L28)

### 実装パターン: Spring Events

```java
// 1. Publisher実装（Spring Events使用）
@Component
public class SpringAuditLogPublisher implements AuditLogPublisher {

  @Autowired
  private ApplicationEventPublisher eventPublisher;

  @Override
  public void publish(AuditLog auditLog) {
    // Spring Eventとして発行
    eventPublisher.publishEvent(new AuditLogEvent(auditLog));
  }
}

// 2. Event定義
public class AuditLogEvent {
  private final AuditLog auditLog;

  public AuditLogEvent(AuditLog auditLog) {
    this.auditLog = auditLog;
  }

  public AuditLog getAuditLog() {
    return auditLog;
  }
}

// 3. EventListener実装
@Component
public class AuditLogEventListener {

  @Autowired
  private AuditLogWriters auditLogWriters;

  @Autowired
  private TenantQueryRepository tenantRepository;

  @EventListener
  @Async  // 非同期実行
  public void handleAuditLogEvent(AuditLogEvent event) {
    AuditLog auditLog = event.getAuditLog();
    Tenant tenant = tenantRepository.get(auditLog.tenantIdentifier());

    // すべてのWriterを実行
    auditLogWriters.write(tenant, auditLog);
  }
}
```

**非同期処理のメリット**:
- **レスポンス速度向上**: API応答を待たずにログ処理
- **スケーラビリティ**: ログ処理の負荷をバックグラウンド化
- **耐障害性**: ログ出力エラーがAPIレスポンスに影響しない

---

## 🧩 カスタムAuditLogWriter実装例

### 例1: CloudWatch Logs Writer

```java
public class CloudWatchLogsAuditLogWriter implements AuditLogWriter {

  private final CloudWatchLogsClient cloudWatchClient;
  private final String logGroupName;
  private final String logStreamName;

  public CloudWatchLogsAuditLogWriter(
      CloudWatchLogsClient cloudWatchClient,
      String logGroupName,
      String logStreamName) {
    this.cloudWatchClient = cloudWatchClient;
    this.logGroupName = logGroupName;
    this.logStreamName = logStreamName;
  }

  @Override
  public boolean shouldExecute(Tenant tenant, AuditLog auditLog) {
    // 本番環境のテナントのみCloudWatch Logsに出力
    return tenant.type() == TenantType.PUBLIC
        && !auditLog.dryRun();
  }

  @Override
  public void write(Tenant tenant, AuditLog auditLog) {
    try {
      // AuditLog を JSON 形式に変換
      String logMessage = new ObjectMapper().writeValueAsString(auditLog.toMap());

      // CloudWatch Logs に送信
      InputLogEvent logEvent = InputLogEvent.builder()
          .message(logMessage)
          .timestamp(System.currentTimeMillis())
          .build();

      PutLogEventsRequest request = PutLogEventsRequest.builder()
          .logGroupName(logGroupName)
          .logStreamName(logStreamName)
          .logEvents(logEvent)
          .build();

      cloudWatchClient.putLogEvents(request);

    } catch (Exception e) {
      // ログ出力エラーをログ（メタログ）
      log.error("Failed to write audit log to CloudWatch Logs", e);
    }
  }
}
```

### 例2: Splunk Writer

```java
public class SplunkAuditLogWriter implements AuditLogWriter {

  private final HttpClient httpClient;
  private final String splunkUrl;
  private final String splunkToken;

  @Override
  public boolean shouldExecute(Tenant tenant, AuditLog auditLog) {
    // 特定タイプのログのみSplunkに送信
    return auditLog.type().startsWith("security.")
        || auditLog.outcomeResult().equals("failure");
  }

  @Override
  public void write(Tenant tenant, AuditLog auditLog) {
    try {
      // Splunk HEC (HTTP Event Collector) 形式
      Map<String, Object> event = Map.of(
          "time", System.currentTimeMillis() / 1000,
          "source", "idp-server",
          "sourcetype", "audit_log",
          "event", auditLog.toMap()
      );

      String json = new ObjectMapper().writeValueAsString(event);

      // Splunk HEC に送信
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(splunkUrl + "/services/collector"))
          .header("Authorization", "Splunk " + splunkToken)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json))
          .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        log.error("Failed to send audit log to Splunk: {}", response.body());
      }

    } catch (Exception e) {
      log.error("Failed to write audit log to Splunk", e);
    }
  }
}
```

### プラグイン登録

`META-INF/services/org.idp.server.platform.audit.AuditLogWriterProvider`

```
com.example.idp.audit.CloudWatchLogsAuditLogWriterProvider
com.example.idp.audit.SplunkAuditLogWriterProvider
```

**AuditLogWriterProvider 実装**:
```java
public class CloudWatchLogsAuditLogWriterProvider implements AuditLogWriterProvider {

  @Override
  public AuditLogWriter provide(ApplicationComponentContainer container) {
    CloudWatchLogsClient client = container.get(CloudWatchLogsClient.class);
    String logGroupName = System.getenv("CLOUDWATCH_LOG_GROUP");
    String logStreamName = System.getenv("CLOUDWATCH_LOG_STREAM");

    return new CloudWatchLogsAuditLogWriter(client, logGroupName, logStreamName);
  }
}
```

---

## 📋 監査ログのフィールド詳細

### 基本情報

| フィールド | 型 | 必須 | 説明 | 例 |
|----------|---|-----|------|-----|
| `id` | String | ✅ | 監査ログID（UUID） | "a1b2c3d4-..." |
| `type` | String | ✅ | ログタイプ | "user.created" |
| `description` | String | ✅ | 説明 | "User registration" |
| `tenantId` | String | ✅ | テナントID | "tenant-123" |
| `createdAt` | LocalDateTime | ✅ | 作成日時 | "2025-12-07T10:30:00" |

### 操作情報

| フィールド | 型 | 必須 | 説明 | 例 |
|----------|---|-----|------|-----|
| `targetResource` | String | ✅ | 対象リソース | "user" |
| `targetResourceAction` | String | ✅ | 操作 | "create", "update", "delete" |
| `request` | JsonNodeWrapper |  | リクエスト内容 | `{"username": "test"}` |
| `before` | JsonNodeWrapper |  | 変更前の状態 | `{"status": "active"}` |
| `after` | JsonNodeWrapper |  | 変更後の状態 | `{"status": "inactive"}` |

### 操作者情報

| フィールド | 型 | 必須 | 説明 | 例 |
|----------|---|-----|------|-----|
| `clientId` | String |  | クライアントID | "client-abc" |
| `userId` | String |  | ユーザーID | "user-xyz" |
| `externalUserId` | String |  | 外部ユーザーID | "external-123" |
| `userPayload` | JsonNodeWrapper |  | ユーザー情報 | `{"email": "test@example.com"}` |
| `ipAddress` | String |  | IPアドレス | "192.168.1.1" |
| `userAgent` | String |  | User-Agent | "Mozilla/5.0..." |

### 結果情報

| フィールド | 型 | 必須 | 説明 | 例 |
|----------|---|-----|------|-----|
| `outcomeResult` | String | ✅ | 結果 | "success", "failure" |
| `outcomeReason` | String |  | 理由 | "Invalid credentials" |
| `dryRun` | boolean | ✅ | Dry Runモード | false |

### 追加情報

| フィールド | 型 | 必須 | 説明 | 例 |
|----------|---|-----|------|-----|
| `targetTenantId` | String |  | 対象テナントID | "tenant-456" |
| `attributes` | JsonNodeWrapper |  | 追加属性 | `{"custom": "value"}` |

---

## 🧪 テスト実装例

### AuditLog生成テスト

```java
@Test
void testAuditLogCreation() {
  // 1. AuditableContext作成
  AuditableContext context = AuditableContext.builder()
      .type("user.created")
      .description("User registration")
      .tenantId("tenant-123")
      .userId("user-456")
      .targetResource("user")
      .targetResourceAction("create")
      .request(Map.of("username", "testuser"))
      .after(Map.of("username", "testuser", "status", "active"))
      .outcomeResult("success")
      .ipAddress("192.168.1.1")
      .userAgent("Mozilla/5.0")
      .dryRun(false)
      .build();

  // 2. AuditLog生成
  AuditLog auditLog = AuditLogCreator.create(context);

  // 3. 検証
  assertNotNull(auditLog.id());
  assertEquals("user.created", auditLog.type());
  assertEquals("tenant-123", auditLog.tenantId());
  assertEquals("user", auditLog.targetResource());
  assertEquals("create", auditLog.targetResourceAction());
  assertEquals("success", auditLog.outcomeResult());
}
```

### カスタムWriter テスト

```java
@Test
void testCustomAuditLogWriter() {
  // 1. カスタムWriter作成
  List<String> writtenLogs = new ArrayList<>();

  AuditLogWriter customWriter = new AuditLogWriter() {
    @Override
    public boolean shouldExecute(Tenant tenant, AuditLog auditLog) {
      return auditLog.type().startsWith("security.");
    }

    @Override
    public void write(Tenant tenant, AuditLog auditLog) {
      writtenLogs.add(auditLog.id());
    }
  };

  // 2. AuditLogWriters作成
  AuditLogWriters writers = new AuditLogWriters(List.of(customWriter));

  // 3. セキュリティログ書き込み
  AuditLog securityLog = new AuditLog(..., "security.login_failed", ...);
  writers.write(tenant, securityLog);

  // 4. 検証: セキュリティログは書き込まれる
  assertEquals(1, writtenLogs.size());

  // 5. 通常ログ書き込み
  AuditLog normalLog = new AuditLog(..., "user.created", ...);
  writers.write(tenant, normalLog);

  // 6. 検証: 通常ログは書き込まれない（shouldExecute=false）
  assertEquals(1, writtenLogs.size());
}
```

---

## 📋 実装チェックリスト

カスタムAuditLogWriterを実装する際のチェックリスト:

- [ ] **AuditLogWriter実装**:
  ```java
  public class MyAuditLogWriter implements AuditLogWriter {
    @Override
    public boolean shouldExecute(Tenant tenant, AuditLog auditLog) { ... }

    @Override
    public void write(Tenant tenant, AuditLog auditLog) { ... }
  }
  ```

- [ ] **shouldExecute判定**:
  - [ ] テナント条件（本番環境のみ等）
  - [ ] ログタイプ条件（`security.*`のみ等）
  - [ ] Dry Runモードの扱い

- [ ] **write実装**:
  - [ ] 外部システムへの送信ロジック
  - [ ] エラーハンドリング（送信失敗時）
  - [ ] タイムアウト設定

- [ ] **Provider実装**:
  ```java
  public class MyAuditLogWriterProvider implements AuditLogWriterProvider {
    @Override
    public AuditLogWriter provide(ApplicationComponentContainer container) {
      return new MyAuditLogWriter(...);
    }
  }
  ```

- [ ] **プラグイン登録**:
  ```
  META-INF/services/org.idp.server.platform.audit.AuditLogWriterProvider
  com.example.idp.audit.MyAuditLogWriterProvider
  ```

- [ ] **テスト作成**:
  - [ ] shouldExecute のテスト
  - [ ] write のテスト
  - [ ] エラー時の動作テスト

---

## 🚨 よくある間違い

### 1. 同期処理の実装

```java
// ❌ 誤り: write() で時間のかかる処理（APIレスポンス遅延）
@Override
public void write(Tenant tenant, AuditLog auditLog) {
  httpClient.send(request, ...);  // 同期送信（遅い）
}

// ✅ 正しい: AuditLogPublisher経由で非同期処理
auditLogPublisher.publish(auditLog);  // 非同期
```

### 2. エラーハンドリング不足

```java
// ❌ 誤り: エラー時に例外をthrow（他のWriterが実行されない）
@Override
public void write(Tenant tenant, AuditLog auditLog) {
  httpClient.send(request, ...);  // 例外が発生すると後続Writerが実行されない
}

// ✅ 正しい: try-catchでエラーをキャッチ
@Override
public void write(Tenant tenant, AuditLog auditLog) {
  try {
    httpClient.send(request, ...);
  } catch (Exception e) {
    log.error("Failed to write audit log", e);
    // 他のWriterは実行される
  }
}
```

### 3. テナント分離の考慮不足

```java
// ❌ 誤り: すべてのテナントを同じログストリームに出力
String logStreamName = "audit-logs";

// ✅ 正しい: テナントごとにログストリームを分離
String logStreamName = "audit-logs-" + tenant.identifierValue();
```

### 4. 機密情報のログ出力

```java
// ❌ 誤り: パスワードやトークンをログに含める
auditLog.request().put("password", "secret123");
auditLog.request().put("access_token", "xxx");

// ✅ 正しい: 機密情報はマスクまたは除外
auditLog.request().put("password", "***");  // マスク
// または password フィールド自体を含めない
```

---

## 🔗 関連ドキュメント

**概念・基礎**:
- [impl-07: Multi-Tenancy](./impl-07-multi-tenancy.md) - テナント分離の実装

**実装詳細**:
- [impl-12: Plugin実装ガイド](./impl-12-plugin-implementation.md) - プラグインシステムの詳細
- [impl-03: トランザクション管理](./impl-03-transaction.md) - データベース永続化

**参考実装クラス**:
- [AuditLog.java](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLog.java)
- [AuditLogWriter.java](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogWriter.java)
- [AuditLogWriterProvider.java](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogWriterProvider.java)
- [AuditLogPublisher.java](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogPublisher.java)
- [AuditLogCreator.java](../../../../../libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/base/AuditLogCreator.java)
- [AuditLogWriters.java](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogWriters.java)
- [AuditLogDataBaseWriter.java](../../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/audit/AuditLogDataBaseWriter.java)

---

**最終更新**: 2025-12-07
**難易度**: ⭐⭐⭐ (中級)
