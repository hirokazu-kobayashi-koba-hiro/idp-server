# イベント処理実装ガイド

## このドキュメントの目的

**SecurityEventとUserLifecycleEvent**の概要を理解し、詳細ドキュメントへ誘導します。

### 所要時間
⏱️ **約5分**（概要のみ）

### 前提知識
- [04. Authentication実装](./04-authentication.md)
- Spring Frameworkの`@EventListener`の基礎知識（[補足セクション](#補足-spring-framework-applicationeventpublisher)参照）

---

## イベントシステムとは

idp-serverの各種操作（認証・認可・ユーザー管理）で発生するイベントを記録・通知する仕組み。

**2種類のイベント**:

| イベントタイプ | 目的 | 例 | 詳細 |
|--------------|------|---|------|
| **SecurityEvent** | 「何が起きたか」を記録・通知 | 認証成功/失敗、トークン発行 | [詳細ガイド](./09-security-event.md) |
| **UserLifecycleEvent** | 「ユーザー状態を変更する」アクション | アカウントロック、ユーザー削除 | [詳細ガイド](./09-user-lifecycle-event.md) |

**使い分け**: SecurityEventは「監視」、UserLifecycleEventは「アクション」

---

## アーキテクチャ概要

両イベントとも同じアーキテクチャパターンを採用：

```
EntryService - eventPublisher.publish()
    ↓ (同期)
Spring ApplicationEventPublisher
    ↓ (同期で即座に返却)
EntryService処理完了 → HTTPレスポンス返却
    ↓
    ↓ (非同期 - Spring @EventListener)
    ↓
EventListener → ThreadPool → EventHandler
    ↓
    └─ ThreadPool満杯時: RetryScheduler.enqueue()
```

### 共通のThreadPool設定

| 設定 | 値 | 説明 |
|------|-----|------|
| **CorePoolSize** | 5 | 常駐スレッド数 |
| **MaxPoolSize** | 10 | 最大スレッド数 |
| **QueueCapacity** | 50 | キュー待機数 |
| **RejectedExecutionHandler** | カスタム | 満杯時にRetrySchedulerへ |

### 共通のリトライ戦略

| 設定 | 値 |
|------|-----|
| **最大リトライ** | 3回 |
| **間隔** | 60秒 |
| **超過時** | ログ出力して破棄 |

---

## 詳細ドキュメント

### SecurityEvent

**目的**: 監視・監査（状態変更しない）

```
認証成功 → SecurityEvent(password_success) → 監査ログに記録 → 外部サービスに通知
```

**内容**:
- 2層のリトライ戦略（イベント処理層 + HTTP層）
- 主要なイベントタイプ
- Security Event Hooks
- データベーススキーマ

👉 **[SecurityEvent実装ガイド](./09-security-event.md)**

---

### UserLifecycleEvent

**目的**: ユーザー状態変更のトリガー

```
認証失敗5回 → UserLifecycleEvent(LOCK) → User.status = LOCKED → トークン全削除
```

**内容**:
- ライフサイクルタイプ（LOCK/UNLOCK/DELETE等）
- アカウントロックフロー
- ユーザー削除戦略（12ステップ）
- SecurityEventとの連携

👉 **[UserLifecycleEvent実装ガイド](./09-user-lifecycle-event.md)**

---

## 2種類のイベントの使い分け

### パスワード認証失敗の例

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

イベント処理の概要を理解したら、詳細ドキュメントへ進んでください：

- 👉 **[SecurityEvent実装ガイド](./09-security-event.md)** - 監視・監査イベント
- 👉 **[UserLifecycleEvent実装ガイド](./09-user-lifecycle-event.md)** - ユーザー状態変更イベント

---

**情報源**:
- [OAuthFlowEntryService.java](../../../../libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/OAuthFlowEntryService.java)
- [SecurityEventPublisher.java](../../../../libs/idp-server-platform/src/main/java/org/idp/server/platform/security/event/SecurityEventPublisher.java)
- [UserLifecycleEventPublisher.java](../../../../libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/event/UserLifecycleEventPublisher.java)

**最終更新**: 2025-12-13
