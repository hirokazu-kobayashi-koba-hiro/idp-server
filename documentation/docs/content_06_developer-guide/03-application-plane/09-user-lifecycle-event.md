# UserLifecycleEvent 実装ガイド

## このドキュメントの目的

**UserLifecycleEvent**の仕組みを理解することが目標です。

### 所要時間
⏱️ **約10分**

### 前提知識
- [04. Authentication実装](./04-authentication.md)
- [SecurityEvent実装ガイド](./09-security-event.md)

---

## UserLifecycleEventとは

**目的**: 「ユーザー状態を変更する」アクション

```
認証失敗5回 → UserLifecycleEvent(LOCK) → User.status = LOCKED → トークン全削除
```

**特徴**:
- ✅ **状態変更**: ユーザーステータス・トークン・関連データを変更
- ✅ **非同期処理**: 別スレッドでデータ削除・外部サービス連携
- ✅ **副作用あり**: SecurityEvent(user_locked)等を再発行

---

## SecurityEventとの違い

| 項目 | SecurityEvent | UserLifecycleEvent |
|------|--------------|-------------------|
| **目的** | 監視・監査 | 状態変更 |
| **状態変更** | しない | する |
| **用途** | ログ記録・外部通知 | ユーザー操作のトリガー |
| **例** | `password_failure` | `LOCK` |

**使い分け**: SecurityEventは「監視」、UserLifecycleEventは「アクション」

---

## アーキテクチャ

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
│    - 失敗 → retryQueueに戻す（最大3回）              │
└─────────────────────────────────────────────────────┘
```

**実装**:
- Publisher: [UserLifecycleEventPublisherService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventPublisherService.java)
- Runnable: [UserLifecycleEventRunnable.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventRunnable.java)
- Handler: Platform層（イベントタイプ別に処理）
- Retry Scheduler: [UserLifecycleEventRetryScheduler.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventRetryScheduler.java)
- ThreadPool設定: [AsyncConfig.java:71-94](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/AsyncConfig.java#L71-L94)

---

## ThreadPool設定

| 設定 | 値 | 説明 |
|------|-----|------|
| **CorePoolSize** | 5 | 常駐スレッド数 |
| **MaxPoolSize** | 10 | 最大スレッド数 |
| **QueueCapacity** | 50 | キュー待機数 |
| **RejectedExecutionHandler** | カスタム | 満杯時にRetrySchedulerへ |

---

## ライフサイクルタイプ

| ライフサイクルタイプ | 発行タイミング | 用途 |
|-----------------|--------------|------|
| `LOCK` | アカウントロック | ユーザーステータス更新、トークン失効 |
| `UNLOCK` | アカウントロック解除 | ユーザーステータス更新 |
| `DELETE` | ユーザー削除 | 関連データ削除、外部サービス連携 |
| `SUSPEND` | アカウント停止 | ユーザーステータス更新 |
| `ACTIVATE` | アカウント有効化 | ユーザーステータス更新 |
| `INVITE_COMPLETE` | 招待完了 | 招待ステータス更新 |

---

## イベント発行の実装

```java
// 9. ロック処理（失敗回数超過時）
if (updatedTransaction.isLocked()) {
  UserLifecycleEvent userLifecycleEvent =
      new UserLifecycleEvent(tenant, updatedTransaction.user(), UserLifecycleType.LOCK);
  userLifecycleEventPublisher.publish(userLifecycleEvent);
}
```

---

## 2種類のイベントの連携

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

## リトライ戦略

UserLifecycleEventのリトライはSecurityEventと同じ仕組みです。

| 設定 | 値 |
|------|-----|
| **最大リトライ** | 3回 |
| **間隔** | 60秒 |
| **超過時** | ログ出力して破棄 |

詳細は[SecurityEvent実装ガイド - リトライ戦略](./09-security-event.md#リトライ戦略)を参照してください。

---

## よくある質問

### Q1: UserLifecycleEvent処理失敗時は？

- ThreadPool満杯時: RetrySchedulerで最大3回リトライ
- 処理中エラー: ログ出力、イベントは破棄
- **重要**: 状態変更が部分的に完了する可能性あり（要監視）

### Q2: DELETEイベントが途中で失敗したら？

削除は順序依存があるため、途中失敗時は不整合が発生する可能性があります。

**対策**:
- 監視ログで失敗検知
- 手動での復旧対応
- 将来的にはSagaパターンの導入を検討

### Q3: SecurityEventとUserLifecycleEventどちらを使うべき？

| ユースケース | 使うべきイベント |
|-------------|----------------|
| ログインイベントを記録したい | SecurityEvent |
| ログイン失敗を外部に通知したい | SecurityEvent |
| アカウントをロックしたい | UserLifecycleEvent |
| ユーザーを削除したい | UserLifecycleEvent |
| ロック完了を通知したい | SecurityEvent（UserLifecycleEventHandlerが発行） |

---

## 関連ドキュメント

- [SecurityEvent実装ガイド](./09-security-event.md) - 監視・監査イベント
- [04. Authentication実装](./04-authentication.md) - 認証フローでのイベント発行

---

**情報源**:
- [UserLifecycleEventPublisher.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/event/UserLifecycleEventPublisher.java)
- [UserLifecycleEventRetryScheduler.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/event/UserLifecycleEventRetryScheduler.java)
- [OAuthFlowEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java)

**最終更新**: 2025-12-13
