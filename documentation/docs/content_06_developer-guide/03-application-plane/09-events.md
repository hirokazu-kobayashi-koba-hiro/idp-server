# イベント処理実装ガイド

## このドキュメントの目的

**SecurityEventとUserLifecycleEvent**の仕組みを理解することが目標です。

### 所要時間
⏱️ **約20分**

### 前提知識
- [04. Authentication実装](./04-authentication.md)
- Spring Frameworkの`@EventListener`の基礎知識（[補足セクション](#補足-spring-framework-applicationeventpublisher)参照）

---

## イベントシステムとは

idp-serverの各種操作（認証・認可・ユーザー管理）で発生するイベントを記録・通知する仕組み。

**2種類のイベント**:

| イベントタイプ | 目的 | 例 |
|--------------|------|---|
| **SecurityEvent** | 「何が起きたか」を記録・通知 | 認証成功/失敗、トークン発行 |
| **UserLifecycleEvent** | 「ユーザー状態を変更する」アクション | アカウントロック、ユーザー削除 |

**使い分け**: SecurityEventは「監視」、UserLifecycleEventは「アクション」

---

## アーキテクチャ全体像

### SecurityEventのアーキテクチャ

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
│  2. SecurityEventHookConfiguration取得               │
│     → 設定されたHookを取得                            │
│                                                     │
│  3. SecurityEventHook.shouldExecute()               │
│     → イベントタイプフィルタリング                      │
│                                                     │
│  4. SecurityEventHook.execute()                     │
│     → 外部サービスに送信（Webhook/Slack/SIEM）        │
│                                                     │
│  5. SecurityEventHookResult保存                      │
│     → security_event_hook_results テーブル           │
└─────────────────────────────────────────────────────┘
    ↓ (ThreadPool満杯でリトライキューに入った場合)
┌─────────────────────────────────────────────────────┐
│ SecurityEventRetryScheduler（Spring Scheduler）     │
├─────────────────────────────────────────────────────┤
│  @Scheduled(fixedDelay = 60_000)  ← 60秒ごと         │
│  resendFailedEvents()                               │
│    - retryQueueから取得                             │
│    - securityEventApi.handle()で再実行               │
│    - 失敗 → retryQueueに戻す                        │
└─────────────────────────────────────────────────────┘
```

**実装**:
- Publisher: [SecurityEventPublisherService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventPublisherService.java)
- Runnable: [SecurityEventRunnable.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventRunnable.java)
- Handler: [SecurityEventHandler.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/handler/SecurityEventHandler.java)
- Retry Scheduler: [SecurityEventRetryScheduler.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventRetryScheduler.java)
- ThreadPool設定: [AsyncConfig.java:46-69](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/AsyncConfig.java#L46-L69)

**ThreadPool設定**:
- **CorePoolSize**: 5スレッド
- **MaxPoolSize**: 10スレッド
- **QueueCapacity**: 50イベント
- **RejectedExecutionHandler**: ThreadPool満杯時にSecurityEventRetryScheduler.enqueue()

**重要**: SecurityEventRetrySchedulerは**Hook送信失敗時ではなく、ThreadPool満杯時**に使われる

---

### UserLifecycleEventのアーキテクチャ

SecurityEventと同じThreadPool + RejectedExecutionHandlerの仕組み：

```
EntryService - userLifecycleEventPublisher.publish()
    ↓ (同期)
┌─────────────────────────────────────────────────────┐
│ UserLifecycleEventPublisher（インターフェース）       │
├─────────────────────────────────────────────────────┤
│  UserLifecycleEventPublisherService（Adapter層）    │
│    → applicationEventPublisher.publishEvent()       │
│       (Spring ApplicationEventPublisher)           │
└─────────────────────────────────────────────────────┘
    ↓ (同期で即座に返却)
EntryService処理完了 → HTTPレスポンス返却
    ↓
    ↓ (非同期 - Spring @EventListener)
    ↓
┌─────────────────────────────────────────────────────┐
│ UserLifecycleEventListener（Spring Bean）          │
├─────────────────────────────────────────────────────┤
│  @EventListener                                     │
│  handleUserLifecycleEvent(UserLifecycleEvent event) │
│    ↓                                                │
│  UserLifecycleEventRunnable作成                     │
│    - TenantLoggingContext設定                       │
│    - UserLifecycleEventHandler呼び出し              │
│    ↓                                                │
│  userLifecycleEventTaskExecutor.execute(runnable)   │
│    → ThreadPoolに投入                                │
└─────────────────────────────────────────────────────┘
    ↓ (ThreadPoolで非同期実行)
    ├─ 正常時: 別スレッドで実行
    └─ ThreadPool満杯時: RejectedExecutionHandler
        ↓
    ┌─────────────────────────────────────────────────┐
    │ RejectedExecutionHandler                        │
    ├─────────────────────────────────────────────────┤
    │  UserLifecycleEventRetryScheduler.enqueue()     │
    │    → retryQueueに追加                            │
    └─────────────────────────────────────────────────┘
    ↓ (別スレッド - 正常実行時)
┌─────────────────────────────────────────────────────┐
│ UserLifecycleEventHandler（Platform層）             │
├─────────────────────────────────────────────────────┤
│  UserLifecycleType.LOCK の場合:                      │
│    1. User.status = LOCKED                          │
│    2. 全OAuthToken削除                               │
│    3. SecurityEvent(user_locked)発行                 │
│                                                     │
│  UserLifecycleType.DELETE の場合:                    │
│    1. 関連データ削除（12ステップ）                     │
│    2. 外部サービスに通知（FIDO/VC等）                  │
│    3. SecurityEvent(user_deleted)発行                │
└─────────────────────────────────────────────────────┘
    ↓ (ThreadPool満杯でリトライキューに入った場合)
┌─────────────────────────────────────────────────────┐
│ UserLifecycleEventRetryScheduler（Spring Scheduler）│
├─────────────────────────────────────────────────────┤
│  @Scheduled(fixedDelay = 60_000)  ← 60秒ごと         │
│  resendFailedEvents()                               │
│    - retryQueueから取得                             │
│    - userLifecycleEventApi.handle()で再実行          │
│    - 失敗 → retryQueueに戻す                        │
└─────────────────────────────────────────────────────┘
```

**実装**:
- Publisher: [UserLifecycleEventPublisherService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventPublisherService.java)
- Runnable: [UserLifecycleEventRunnable.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventRunnable.java)
- Handler: Platform層（イベントタイプ別に処理）
- Retry Scheduler: [UserLifecycleEventRetryScheduler.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventRetryScheduler.java)
- ThreadPool設定: [AsyncConfig.java:71-94](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/AsyncConfig.java#L71-L94)

**ThreadPool設定**:
- **CorePoolSize**: 5スレッド
- **MaxPoolSize**: 10スレッド
- **QueueCapacity**: 50イベント
- **RejectedExecutionHandler**: ThreadPool満杯時にUserLifecycleEventRetryScheduler.enqueue()

---

### 主要クラスの責務

| クラス | 層 | 役割 | 実装 |
|--------|---|------|------|
| **SecurityEventPublisher** | Interface | イベント発行インターフェース | Platform |
| **SecurityEventPublisherService** | Adapter | Spring ApplicationEventPublisherへの委譲 | [SecurityEventPublisherService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventPublisherService.java) |
| **SecurityEventRunnable** | Adapter | TenantLoggingContext設定＋Handler実行 | [SecurityEventRunnable.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventRunnable.java) |
| **SecurityEventHandler** | Platform | イベント処理・Hook実行（5ステップ） | [SecurityEventHandler.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/handler/SecurityEventHandler.java) |
| **SecurityEventRetryScheduler** | Adapter | ThreadPool満杯時の再実行（60秒ごと） | [SecurityEventRetryScheduler.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/SecurityEventRetryScheduler.java) |
| **AsyncConfig** | Adapter | ThreadPool設定（5-10スレッド、Queue50） | [AsyncConfig.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/AsyncConfig.java) |
| **SecurityEventHook** | Plugin | 外部サービス送信（Webhook等） | Platform |
| **UserLifecycleEventPublisher** | Interface | ライフサイクルイベント発行 | Core |
| **UserLifecycleEventPublisherService** | Adapter | Spring ApplicationEventPublisherへの委譲 | [UserLifecycleEventPublisherService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventPublisherService.java) |
| **UserLifecycleEventRunnable** | Adapter | TenantLoggingContext設定＋Handler実行 | [UserLifecycleEventRunnable.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventRunnable.java) |
| **UserLifecycleEventRetryScheduler** | Adapter | ThreadPool満杯時の再実行（60秒ごと） | [UserLifecycleEventRetryScheduler.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventRetryScheduler.java) |

---

### Spring ApplicationEventPublisherの利用

idp-serverは**Spring ApplicationEventPublisher**を活用して非同期イベント処理を実現：

**Publisher側（同期）**:
```java
@Service
public class SecurityEventPublisherService implements SecurityEventPublisher {
  ApplicationEventPublisher applicationEventPublisher;

  @Override
  public void publish(SecurityEvent securityEvent) {
    applicationEventPublisher.publishEvent(securityEvent);  // 同期で発行
  }
}
```

**Handler側（非同期）**:
```java
@Component
public class SecurityEventListener {

  @EventListener
  @Async  // 非同期実行
  public void handleSecurityEvent(SecurityEvent event) {
    securityEventHandler.handle(event.tenant(), event);
  }
}
```

**メリット**:
- ✅ EntryServiceはイベント発行後すぐに返却（パフォーマンス）
- ✅ Hook送信失敗がAPI呼び出しに影響しない
- ✅ Spring標準機能で非同期処理

---

## SecurityEvent詳細

### 特徴

**目的**: 「何が起きたか」を記録・通知

```
認証成功 → SecurityEvent(password_success) → 監査ログに記録 → 外部サービスに通知
```

**特徴**:
- ✅ **記録中心**: security_eventテーブルに永久保存
- ✅ **監視・通知**: Security Event Hooksで外部サービスに送信
- ❌ **状態変更しない**: ユーザー・トークン等は変更しない

---

### 主要なイベントタイプ

Application Planeで発行されるSecurityEvent：

| イベントタイプ | 発行タイミング | 実装箇所 |
|--------------|--------------|---------|
| `password_success` | パスワード認証成功 | [OAuthFlowEntryService.java:210](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L210) |
| `password_failure` | パスワード認証失敗 | 同上 |
| `oauth_authorize` | Authorization Code発行 | [OAuthFlowEntryService.java:330-335](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L330-L335) |
| `token_request_success` | トークン発行成功 | TokenEntryService |
| `userinfo_success` | UserInfo取得成功 | UserinfoEntryService |
| `backchannel_authentication_request_success` | CIBA認証リクエスト成功 | CibaFlowEntryService |

**完全なリスト**: [DefaultSecurityEventType.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/event/DefaultSecurityEventType.java)

### イベント発行の実装

**04-authentication.mdで見た例**:

```java
// 10. イベント発行（Security Event）
eventPublisher.publish(
    tenant,
    authorizationRequest,
    result.user(),
    result.eventType(),  // password_success or password_failure
    requestAttributes);
```

### SecurityEventの保存先

```sql
-- security_eventテーブル
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

## UserLifecycleEvent詳細

### 特徴

**目的**: 「ユーザー状態を変更する」アクション

```
認証失敗5回 → UserLifecycleEvent(LOCK) → User.status = LOCKED → トークン全削除
```

**特徴**:
- ✅ **状態変更**: ユーザーステータス・トークン・関連データを変更
- ✅ **非同期処理**: 別スレッドでデータ削除・外部サービス連携
- ✅ **副作用あり**: SecurityEvent(user_locked)等を再発行

---

### ライフサイクルタイプ

| ライフサイクルタイプ | 発行タイミング | 用途 |
|-----------------|--------------|------|
| `LOCK` | アカウントロック | ユーザーステータス更新、トークン失効 |
| `UNLOCK` | アカウントロック解除 | ユーザーステータス更新 |
| `DELETE` | ユーザー削除 | 関連データ削除、外部サービス連携 |
| `SUSPEND` | アカウント停止 | ユーザーステータス更新 |
| `ACTIVATE` | アカウント有効化 | ユーザーステータス更新 |
| `INVITE_COMPLETE` | 招待完了 | 招待ステータス更新 |

### イベント発行の実装

**04-authentication.mdで見た例**:

```java
// 9. ロック処理（失敗回数超過時）
if (updatedTransaction.isLocked()) {
  UserLifecycleEvent userLifecycleEvent =
      new UserLifecycleEvent(tenant, updatedTransaction.user(), UserLifecycleType.LOCK);
  userLifecycleEventPublisher.publish(userLifecycleEvent);
}
```

### UserLifecycleEventの処理

**非同期処理**: イベント発行後、別スレッドで処理

```
UserLifecycleEvent発行
    ↓
UserLifecycleEventHandler（非同期）
    ↓
┌─────────────────────────────────────────┐
│ UserLifecycleType.LOCK                  │
├─────────────────────────────────────────┤
│  - User.status = LOCKED                 │
│  - OAuthToken全削除                      │
│  - SecurityEvent(user_locked)発行        │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│ UserLifecycleType.DELETE                │
├─────────────────────────────────────────┤
│  - 関連データ削除（12ステップ）            │
│  - 外部サービスに通知（FIDO/VC等）         │
│  - SecurityEvent(user_deleted)発行       │
└─────────────────────────────────────────┘
```

---

## 2種類のイベントの使い分け

### パスワード認証失敗のフロー

2つのイベントがどう連携するかの具体例：

```
1. パスワード認証失敗
   → SecurityEvent(password_failure)発行  ← 監視・記録

2. 失敗回数が5回に到達
   → UserLifecycleEvent(LOCK)発行  ← 状態変更トリガー

3. UserLifecycleEventHandler（非同期）
   ├─ User.status = LOCKED  ← 状態変更
   ├─ 全OAuthToken削除  ← 状態変更
   └─ SecurityEvent(user_locked)発行  ← 記録・通知
```

**ポイント**:
- SecurityEvent: 監視・記録のみ（状態変更しない）
- UserLifecycleEvent: 状態変更のトリガー
- 1つのUserLifecycleEventが複数のSecurityEventを発生させることもある

---

## アカウントロックフロー（詳細）

認証失敗が一定回数を超えた場合の自動ロック：

```
[パスワード認証失敗 x5]
    ↓
AuthenticationTransaction.isLocked() = true
    ↓
UserLifecycleEvent(type=LOCK)発行
    ↓
UserLifecycleEventHandler（非同期処理）
    ├─ User.status = LOCKED
    ├─ 全OAuthToken削除
    └─ SecurityEvent(user_locked)発行
    ↓
次回認証試行時
    ↓
{
  "error": "account_locked",
  "error_description": "Account has been locked due to too many failed attempts"
}
```

**実装**: [OAuthFlowEntryService.java:204-208](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java#L204-L208)

---

## ユーザー削除戦略

### 削除方針

| カテゴリ | ストラテジー | 対象テーブル |
|----------|------------|------------|
| **コアデータ** | 物理削除 | `idp_user`, `idp_user_roles`, `oauth_token`, `authorization_code_grant`, `authentication_transaction`, `ciba_grant`, `federation_sso_session` |
| **ログ・履歴** | 論理削除/保持 | `authorization_granted`（revoked_at設定）, `identity_verification_application`（status=deleted）, `security_event`（保持） |
| **外部サービス** | 非同期削除 | FIDO-UAFデバイス、Verifiable Credentials |

### 削除シーケンス（推奨順序）

```
UserLifecycleEvent(type=DELETE)発行
    ↓
UserLifecycleEventHandler
    ↓
1. authentication_interactions 削除
2. authentication_transaction 削除
3. idp_user_roles 削除
4. idp_user_permission_override 削除
5. oauth_token 削除
6. authorization_code_grant 削除
7. ciba_grant 削除
8. federation_sso_session 削除
9. authorization_granted 論理削除（revoked_at設定）
10. identity_verification_application 論理削除（status=deleted）
11. idp_user 物理削除
12. security_event 監査エントリ追加
13. 外部サービスに delete_account イベント送信（非同期）
```

### 監査ログは削除しない

**重要**: `security_event`テーブルは削除せず、永久保存

**理由**:
- 法的要件（監査証跡の保持義務）
- セキュリティ分析（過去の不正アクセス調査）
- コンプライアンス（GDPR等の例外規定）

**対応**: ユーザー削除後も、security_eventは`user_id`を保持（または匿名化）

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

### リトライ戦略

Hook送信失敗時は自動リトライ：

- **max_retries**: 3回（デフォルト）
- **backoff**: 1秒 → 2秒 → 4秒
- **retryable_status_codes**: 502, 503, 504

**詳細**: [実装ガイド: Security Event Hooks](../04-implementation-guides/impl-15-security-event-hooks.md)

---

## よくある質問

### Q1: SecurityEventとUserLifecycleEventの使い分けは？

**SecurityEvent**:
- 監視・監査が目的
- 外部サービスへの通知
- ユーザー状態は変更しない

**UserLifecycleEvent**:
- ユーザー状態変更が目的
- データ削除・更新
- 内部処理のトリガー

**例**: パスワード失敗
```
1. SecurityEvent(password_failure) → 監査ログに記録
2. 5回失敗 → UserLifecycleEvent(LOCK) → ユーザーステータス更新
3. SecurityEvent(user_locked) → 外部に通知
```

---

### Q2: イベントは同期？非同期？

**SecurityEvent発行**: 同期（eventPublisher.publish()）
**SecurityEvent保存**: 同期（DBに即座に記録）
**Security Event Hooks送信**: 非同期（別スレッド）

**UserLifecycleEvent発行**: 同期（userLifecycleEventPublisher.publish()）
**UserLifecycleEvent処理**: 非同期（別スレッド）

**理由**:
- イベント記録は即座に完了（監査証跡）
- 外部サービス通知は非同期（パフォーマンス影響を避ける）

---

### Q3: イベント発行失敗時は？

**SecurityEvent発行失敗**:
- トランザクションロールバック
- API呼び出し自体が失敗

**Hook送信失敗**:
- リトライ（3回）
- 最終的に失敗 → security_event_hook_results テーブルに記録
- API呼び出しは成功（非同期のため影響なし）

---

## 補足: Spring Framework ApplicationEventPublisher

idp-serverのイベントシステムは**Spring Framework**の標準機能を活用しています。

### ApplicationEventPublisherとは

Spring Frameworkが提供するイベント駆動アーキテクチャの基盤コンポーネント。

**公式ドキュメント**:
- [Spring Framework Reference - Standard and Custom Events](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events)
- [Spring Framework Reference - Annotation-driven Event Listeners](https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation)

### 基本的な使い方

```java
// イベント発行側
@Service
public class EventPublisher {
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    public void doSomething() {
        // 同期でイベント発行
        applicationEventPublisher.publishEvent(new CustomEvent(this, "data"));
    }
}

// イベント受信側
@Component
public class EventListener {

    @EventListener
    @Async  // 非同期実行
    public void handleEvent(CustomEvent event) {
        // イベント処理
    }
}
```

### idp-serverでの活用ポイント

1. **同期発行 + 非同期処理**: イベント発行は同期だが、`@Async`で処理は非同期
2. **疎結合**: EntryServiceはイベント処理の詳細を知らない
3. **スレッドプール制御**: `AsyncConfig`でThreadPool設定を細かく制御

### 学習リソース

- [Baeldung - Spring Events](https://www.baeldung.com/spring-events) - 実践的な使い方
- [Spring Framework Documentation - Application Events](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#context-functionality-events) - 最新の公式リファレンス

---

## 次のステップ

✅ イベント処理の仕組みを理解した！

### 📖 詳細情報

- [実装ガイド: Security Event Hooks](../04-implementation-guides/impl-15-security-event-hooks.md) - Hook実装詳細
- [AI開発者向け: Security Event](../../content_10_ai_developer/ai-51-notification-security-event.md#security-event)

---

**情報源**:
- [OAuthFlowEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java)
- [SecurityEventPublisher.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/event/SecurityEventPublisher.java)
- [UserLifecycleEventPublisher.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/event/UserLifecycleEventPublisher.java)

**最終更新**: 2025-10-13
