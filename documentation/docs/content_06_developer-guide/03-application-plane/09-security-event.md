# SecurityEvent 実装ガイド

## このドキュメントの目的

**SecurityEvent**の仕組みとリトライ戦略を理解することが目標です。

### 所要時間
⏱️ **約15分**

### 前提知識
- [04. Authentication実装](./04-authentication.md)
- Spring Frameworkの`@EventListener`の基礎知識

---

## SecurityEventとは

**目的**: 「何が起きたか」を記録・通知

```
認証成功 → SecurityEvent(password_success) → 監査ログに記録 → 外部サービスに通知
```

**特徴**:
- ✅ **記録中心**: security_eventテーブルに永久保存
- ✅ **監視・通知**: Security Event Hooksで外部サービスに送信
- ❌ **状態変更しない**: ユーザー・トークン等は変更しない

---

## 処理フロー概要

SecurityEventの処理は以下の流れで行われます：

1. **イベント発行**: EntryServiceが `eventPublisher.publish()` で発行（同期）
2. **非同期処理**: Spring `@EventListener` がイベントを受信し、ThreadPoolに投入
3. **イベント処理**: `SecurityEventHandler` がDBへの記録とHook送信を実行
4. **リトライ**: ThreadPool満杯時は `SecurityEventRetryScheduler` が後で再実行

```
EntryService → publish() → ThreadPool → SecurityEventHandler → DB記録 + Hook送信
                              ↓ (満杯時)
                     RetryScheduler → 60秒後に再実行（最大3回）
```

**ポイント**: イベント発行は同期だが、処理は非同期。API呼び出しはイベント処理完了を待たずに返却される。

---

## アーキテクチャ

```
Application Plane API（認証・認可・トークン発行等）
    ↓
EntryService - eventPublisher.publish()
    ↓ (同期)
┌─────────────────────────────────────────────────────┐
│ SecurityEventPublisher（インターフェース）             │
├─────────────────────────────────────────────────────┤
│  SecurityEventPublisherService（Adapter層）          │
│    → applicationEventPublisher.publishEvent()       │
│       (Spring ApplicationEventPublisher)           │
└─────────────────────────────────────────────────────┘
    ↓ (同期で即座に返却)
EntryService処理完了 → HTTPレスポンス返却
    ↓
    ↓ (非同期 - Spring @EventListener)
    ↓
┌─────────────────────────────────────────────────────┐
│ SecurityEventListener（Spring Bean）                │
├─────────────────────────────────────────────────────┤
│  @EventListener                                     │
│  handleSecurityEvent(SecurityEvent event)           │
│    ↓                                                │
│  SecurityEventRunnable作成                          │
│    - TenantLoggingContext設定                       │
│    - SecurityEventHandler呼び出し                    │
│    ↓                                                │
│  securityEventTaskExecutor.execute(runnable)        │
│    → ThreadPoolに投入                                │
└─────────────────────────────────────────────────────┘
    ↓ (ThreadPoolで非同期実行)
    ├─ 正常時: 別スレッドで実行
    └─ ThreadPool満杯時: RejectedExecutionHandler
        ↓
    ┌─────────────────────────────────────────────────┐
    │ RejectedExecutionHandler                        │
    ├─────────────────────────────────────────────────┤
    │  SecurityEventRetryScheduler.enqueue()          │
    │    → retryQueueに追加                            │
    └─────────────────────────────────────────────────┘
    ↓ (別スレッド - 正常実行時)
┌─────────────────────────────────────────────────────┐
│ SecurityEventHandler（Platform層）                  │
├─────────────────────────────────────────────────────┤
│  1. SecurityEventLogService.logEvent()              │
│     → security_event テーブルに記録                  │
│                                                     │
│  2. updateStatistics() ※statistics_enabled時のみ    │
│     → テナント統計データを更新（DAU/MAU/YAU等）        │
│                                                     │
│  3. SecurityEventHookConfiguration取得               │
│     → 設定されたHookを取得                            │
│                                                     │
│  4. SecurityEventHook.shouldExecute()               │
│     → イベントタイプフィルタリング                      │
│                                                     │
│  5. SecurityEventHook.execute()                     │
│     → 外部サービスに送信（Webhook/Slack/SIEM）        │
│                                                     │
│  6. SecurityEventHookResult保存                      │
│     → security_event_hook_results テーブル           │
└─────────────────────────────────────────────────────┘
```

**実装**:
- Publisher: [SecurityEventPublisherService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventPublisherService.java)
- Runnable: [SecurityEventRunnable.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventRunnable.java)
- Handler: [SecurityEventHandler.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/handler/SecurityEventHandler.java)
- Retry Scheduler: [SecurityEventRetryScheduler.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventRetryScheduler.java)
- ThreadPool設定: [AsyncConfig.java:46-69](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/AsyncConfig.java#L46-L69)

---

## ThreadPool設定

| 設定 | 値 | 説明 |
|------|-----|------|
| **CorePoolSize** | 5 | 常駐スレッド数 |
| **MaxPoolSize** | 10 | 最大スレッド数 |
| **QueueCapacity** | 50 | キュー待機数 |
| **RejectedExecutionHandler** | カスタム | 満杯時にRetrySchedulerへ |

### 処理の流れ

```
新規イベント到着
    ↓
┌─────────────────────────────────────────────────────────┐
│ ThreadPool (securityEventTaskExecutor)                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  [Worker 1] [Worker 2] [Worker 3] [Worker 4] [Worker 5] │  ← CorePoolSize: 5
│                                                         │
│  ─────────────────────────────────────────────────────  │
│  負荷増加時に追加                                        │
│  [Worker 6] [Worker 7] [Worker 8] [Worker 9] [Worker 10]│  ← MaxPoolSize: 10
│                                                         │
│  ─────────────────────────────────────────────────────  │
│  全Worker稼働中は待機                                    │
│  [Queue: 最大50件まで待機可能]                            │  ← QueueCapacity: 50
│                                                         │
└─────────────────────────────────────────────────────────┘
    ↓ (Queue も満杯の場合)
┌─────────────────────────────────────────────────────────┐
│ RejectedExecutionHandler                                │
├─────────────────────────────────────────────────────────┤
│  SecurityEventRetryScheduler.enqueue(event)             │
│    → 60秒後にリトライ（最大3回）                          │
└─────────────────────────────────────────────────────────┘
```

### 設定の意味

- **CorePoolSize (5)**: 通常時に稼働するスレッド数。イベント処理の基本キャパシティ
- **MaxPoolSize (10)**: 負荷が高い時に増加できる最大スレッド数
- **QueueCapacity (50)**: 全スレッドが稼働中でも50件までキューで待機可能
- **RejectedExecutionHandler**: キューも満杯になった場合の処理。RetrySchedulerに委譲

### キャパシティ計算

同時に処理可能なイベント数：
- **即時処理**: 最大10件（MaxPoolSize）
- **待機可能**: 50件（QueueCapacity）
- **合計**: 60件まで受け入れ可能

61件目以降は `RejectedExecutionHandler` → `RetryScheduler` へ

### スレッド数設定の考え方

#### スレッド数の上限制約

スレッド数は以下のリソースによって制約されます：

| 制約 | 説明 | 確認方法 |
|------|------|---------|
| **DB接続プール** | スレッド数 > DB接続数だと接続待ちが発生 | HikariCPの`maximumPoolSize` |
| **メモリ（スタック）** | 1スレッドあたり約1MB消費（デフォルト） | `-Xss`オプション |
| **OS制限** | プロセスあたりのスレッド数上限 | `ulimit -u` |
| **外部API Rate Limit** | Hook送信先のレート制限 | 外部サービスの仕様 |

```
実効上限 = min(DB接続プール, 利用可能メモリ/スタックサイズ, OS制限, 外部API制限)
```

**例**: DB接続プール20、メモリ2GB、スタック1MBの場合
- メモリ上限: 2048MB / 1MB = 約2000スレッド（理論値）
- **実効上限: 20スレッド**（DB接続プールがボトルネック）

#### I/O待ち時間を考慮

SecurityEvent処理はI/Oバウンド（DB書き込み、HTTP送信）なので、CPUコア数より多めに設定可能。

```
推奨スレッド数 = CPUコア数 × (1 + I/O待ち時間 / CPU処理時間)
```

例：4コアCPU、I/O待ち時間がCPU処理時間の10倍の場合
```
4 × (1 + 10) = 44スレッド まで効果的
```

ただし、上記の上限制約を超えないこと。

#### 現在の設定値の根拠

| 設定 | 値 | 根拠 |
|------|-----|------|
| CorePoolSize | 5 | 通常負荷での安定動作。DB接続プールとのバランス |
| MaxPoolSize | 10 | ピーク時の2倍対応。過度なリソース消費を防止 |
| QueueCapacity | 50 | バースト的なイベント増加に対応。メモリ消費とのバランス |

#### 調整が必要なケース

| 状況 | 調整案 |
|------|-------|
| リトライが頻発する | MaxPoolSize / QueueCapacity を増加 |
| メモリ使用量が高い | QueueCapacity を減少 |
| DB接続エラーが発生 | CorePoolSize を DB接続プール以下に調整 |
| Hook送信が遅い | MaxPoolSize を増加（I/O待ち対策） |

#### 設定変更方法

環境変数で設定を変更できます：

| 環境変数 | 説明 | デフォルト値 |
|----------|------|-------------|
| `SECURITY_EVENT_CORE_POOL_SIZE` | コアスレッド数 | `5` |
| `SECURITY_EVENT_MAX_POOL_SIZE` | 最大スレッド数 | `20` |
| `SECURITY_EVENT_QUEUE_CAPACITY` | キュー容量 | `100` |

**application.yml での設定例**:

```yaml
idp:
  async:
    security-event:
      core-pool-size: ${SECURITY_EVENT_CORE_POOL_SIZE:5}
      max-pool-size: ${SECURITY_EVENT_MAX_POOL_SIZE:20}
      queue-capacity: ${SECURITY_EVENT_QUEUE_CAPACITY:100}
```

**実装**: [AsyncConfig.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/AsyncConfig.java), [AsyncProperties.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/AsyncProperties.java)

---

## リトライ戦略

ThreadPool満杯時のリトライは`SecurityEventRetryScheduler`が担当します。

| 設定 | 値 |
|------|-----|
| **最大リトライ** | 3回 |
| **間隔** | 60秒 |
| **超過時** | ログ出力して破棄 |

### リトライ実装

**リトライ回数管理**: Mapでイベント別にカウント

```java
@Component
public class SecurityEventRetryScheduler {

  private static final int MAX_RETRIES = 3;

  Queue<SecurityEvent> retryQueue = new ConcurrentLinkedQueue<>();
  Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();

  public void enqueue(SecurityEvent event) {
    retryQueue.add(event);
    retryCountMap.putIfAbsent(event.id(), 0);
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    while (!retryQueue.isEmpty()) {
      SecurityEvent event = retryQueue.poll();
      String eventId = event.id();

      try {
        log.info("retry event (attempt {}): {}",
            retryCountMap.get(eventId) + 1, eventId);
        securityEventApi.handle(event.tenantIdentifier(), event);
        retryCountMap.remove(eventId);  // 成功時はクリア
      } catch (Exception e) {
        int count = retryCountMap.merge(eventId, 1, Integer::sum);
        if (count < MAX_RETRIES) {
          log.warn("retry scheduled ({}/{}): {}", count, MAX_RETRIES, eventId);
          retryQueue.add(event);
        } else {
          log.error("max retries exceeded, dropping event: {}", event.toMap());
          retryCountMap.remove(eventId);
        }
      }
    }
  }
}
```

**ポイント**:
- `retryCountMap`: イベントID → リトライ回数のマッピング
- 成功時・上限到達時に`remove()`でメモリ解放
- 最大3回リトライ後は破棄（ログに記録）

---

## 主要なイベントタイプ

Application Planeで発行されるSecurityEvent：

| イベントタイプ | 発行タイミング | 実装箇所 |
|--------------|--------------|---------|
| `password_success` | パスワード認証成功 | [OAuthFlowEntryService.java:210](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L210) |
| `password_failure` | パスワード認証失敗 | 同上 |
| `oauth_authorize` | Authorization Code発行 | [OAuthFlowEntryService.java:330-335](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L330-L335) |
| `token_request_success` | トークン発行成功 | TokenEntryService |
| `userinfo_success` | UserInfo取得成功 | UserinfoEntryService |
| `backchannel_authentication_request_success` | CIBA認証リクエスト成功 | CibaFlowEntryService |
| `authentication_device_log` | 認証デバイスからのログ受信 | [AuthenticationDeviceLogEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/AuthenticationDeviceLogEntryService.java) |

**完全なリスト**: [DefaultSecurityEventType.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/event/DefaultSecurityEventType.java)

---

## Authentication Device Log

クライアント（モバイルアプリ等）から送信される認証デバイスログをセキュリティイベントとして記録します。

### エンドポイント

```
POST /{tenant-id}/v1/authentication-devices/logs
```

### リクエスト例

```json
{
  "device_id": "device-12345",
  "event": "fido2_authentication_attempt",
  "status": "success",
  "timestamp": "2025-12-26T10:00:00Z",
  "details": {
    "authenticator_type": "platform",
    "user_verification": true
  }
}
```

### 処理フロー

```
クライアント → POST /logs
    ↓
AuthenticationDeviceV1Api
    ↓ (ログ出力)
    log.info(requestBody)
    ↓
AuthenticationDeviceLogEntryService
    ↓
    1. device_id または user_id からユーザー検索
    2. ユーザーが見つかった場合のみセキュリティイベント発行
    ↓
SecurityEventPublisher.publish()
```

### ユーザー検索ロジック

1. `device_id` がリクエストに含まれる場合 → `UserQueryRepository.findByAuthenticationDevice()` で検索
2. `user_id` がリクエストに含まれる場合 → `UserQueryRepository.findById()` で検索
3. ユーザーが見つからない場合 → セキュリティイベントは発行されない（ノイズ防止）

### セキュリティイベント詳細

発行されるセキュリティイベントには以下の情報が含まれます：

| フィールド | 内容 |
|-----------|------|
| `event_type` | `authentication_device_log` |
| `tenant` | テナント情報（ID、issuer、name） |
| `user` | ユーザー情報（見つかった場合） |
| `execution_result` | リクエストボディ全体 |
| `ip_address` | クライアントIPアドレス |
| `user_agent` | User-Agent |

### 実装ファイル

- API: [AuthenticationDeviceV1Api.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/authentication/device/AuthenticationDeviceV1Api.java)
- EntryService: [AuthenticationDeviceLogEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/AuthenticationDeviceLogEntryService.java)
- EventPublisher: [AuthenticationDeviceLogEventPublisher.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/device/AuthenticationDeviceLogEventPublisher.java)
- EventCreator: [AuthenticationDeviceLogEventCreator.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/device/AuthenticationDeviceLogEventCreator.java)

---

## イベント発行の実装

```java
// 10. イベント発行（Security Event）
eventPublisher.publish(
    tenant,
    authorizationRequest,
    result.user(),
    result.eventType(),  // password_success or password_failure
    requestAttributes);
```

---

## 統計データ記録

SecurityEventの処理時に、テナント統計データ（DAU/MAU/YAU、イベントカウント等）を記録できます。

### 有効化設定

テナントの`security_event_log_config`で`statistics_enabled`を`true`に設定します：

```json
{
  "security_event_log_config": {
    "statistics_enabled": true
  }
}
```

| 設定 | デフォルト | 説明 |
|------|-----------|------|
| `statistics_enabled` | `false` | 統計データ記録を有効化 |

**注意**: 統計機能を有効にすると、セキュリティイベント発生時にデータベースへの書き込みが追加で発生します。

**関連ドキュメント**: [テナント統計管理](../../content_03_concepts/07-operations/concept-03-tenant-statistics.md)

---

## データベーススキーマ

### security_event テーブル

```sql
CREATE TABLE security_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_type VARCHAR(255) NOT NULL,  -- 'password_success' 等
    user_id UUID,
    client_id VARCHAR(255),
    ip_address VARCHAR(45),
    user_agent TEXT,
    event_data JSONB,
    created_at TIMESTAMP NOT NULL
);
```

**用途**:
- 監査ログとして永久保存
- Security Event Hooksで外部サービスに通知
- SIEM（Security Information and Event Management）連携

---

## Security Event Hooks

### Hooksとは

SecurityEventを外部サービス（Webhook/Slack/SIEM等）に通知する仕組み。

```
SecurityEvent発行
    ↓
SecurityEventHookExecutor（非同期）
    ↓
┌─────────────────────────────────────────┐
│ 設定されたHookエンドポイントに送信          │
├─────────────────────────────────────────┤
│  POST https://webhook.example.com/events │
│  {                                       │
│    "event_type": "password_failure",     │
│    "user_id": "user-12345",              │
│    "ip_address": "192.168.1.1",          │
│    "timestamp": "2025-10-13T10:00:00Z"   │
│  }                                       │
└─────────────────────────────────────────┘
    ↓
外部サービス（Slack/SIEM/監視ツール）
```

### Hook設定

**Management APIで設定**:

```json
{
  "id": "uuid",
  "event_types": ["password_failure", "user_locked", "token_request_success"],
  "endpoint": "https://webhook.example.com/events",
  "auth_type": "bearer",
  "auth_token": "secret-token",
  "enabled": true
}
```

**設定API**:
```
POST /v1/management/tenants/{tenant-id}/security-event-hooks
```

---

## よくある質問

### Q1: イベントは同期？非同期？

| 処理 | 同期/非同期 | 説明 |
|------|-----------|------|
| **イベント発行** | 同期 | `eventPublisher.publish()` |
| **イベント保存** | 同期 | DBに即座に記録 |
| **Hook送信** | 非同期 | 別スレッドで実行 |

**理由**:
- イベント記録は即座に完了（監査証跡）
- 外部サービス通知は非同期（パフォーマンス影響を避ける）

### Q2: イベント発行失敗時は？

**SecurityEvent発行失敗**:
- トランザクションロールバック
- API呼び出し自体が失敗

**Hook送信失敗**:
- HTTP層でリトライ（3回）
- 最終的に失敗 → security_event_hook_results テーブルに記録
- API呼び出しは成功（非同期のため影響なし）

### Q3: リトライ回数を変更するには？

`SecurityEventRetryScheduler`の`MAX_RETRIES`定数を変更します。

将来的には設定ファイル（`application.yml`）から読み込む形式への変更を検討中です。

---

## 関連ドキュメント

- [UserLifecycleEvent実装ガイド](./09-user-lifecycle-event.md) - ユーザー状態変更イベント
- [実装ガイド: Security Event Hooks](../04-implementation-guides/impl-15-security-event-hooks.md) - Hook実装詳細
- [HTTP Request Executor](../04-implementation-guides/impl-16-http-request-executor.md) - HTTP層のリトライ機構
- [AI開発者向け: Security Event](../../content_10_ai_developer/ai-50-notification-security-event.md)

---

**情報源**:
- [OAuthFlowEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java)
- [SecurityEventPublisher.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/event/SecurityEventPublisher.java)
- [SecurityEventRetryScheduler.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventRetryScheduler.java)

**最終更新**: 2025-12-26
