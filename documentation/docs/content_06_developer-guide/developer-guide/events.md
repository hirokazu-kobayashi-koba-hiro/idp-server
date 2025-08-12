# イベント処理

このドキュメントでは、`idp-server` がユーザー関連イベントをどのように処理するかについて説明します。特に、
**SecurityEvents** と **UserLifecycleEvents** の関係、そしてこれらのイベントがアカウントロックなどのユーザー管理ロジックにどのように影響するかに焦点を当てます。

---

## 🎯 目的

以下の要素を明確に分離・連携させることで、堅牢なアイデンティティライフサイクル制御とセキュリティ監視を実現します。

* **セキュリティ監視** (例: パスワードエラー、不審なアクティビティ)
* **ライフサイクル操作** (例: 削除、ロック)

---

## 🔁 イベントシステムの概要

| イベントタイプ              | 目的                 | トリガーソース             | 影響                              |
|----------------------|--------------------|---------------------|---------------------------------|
| `SecurityEvent`      | 不審なアクションの検出と通知     | 認証フロー、ログイン、トークンアクセス | 通知、監査、ライフサイクルイベントへのトリガー (オプション) |
| `UserLifecycleEvent` | ユーザー状態の変更またはデータの削除 | 管理者アクション、自動ロック      | 内部ユーザーの更新、削除、伝播                 |

---

## 🔐 アカウントロックフロー（例：パスワード入力失敗 5 回）

```プレーンテキスト
[ログイン失敗 x5]
↓
SecurityEvent (type=password_failure)
↓
FailureCounter.increment(userId)
↓
IF failureCount >= 5:
↓
→ UserLifecycleEvent (operation=LOCK)
→ SecurityEvent (type=user_locked)
↓
ユーザーステータスが LO​​CKED に更新される
```

### 実装コンポーネント

* `SecurityEventHandler`: `password_failure` を監視し、カウンターをインクリメントする
* `FailureCounter`: Redis ベースのカウントトラッカー（ユーザーごと、有効期限あり）
* `UserLifecycleEventPublisher`: LOCK などのライフサイクルイベントを発行する
* `UserCommandRepository`: ユーザーステータスを更新する（例：LOCKED）
* `SecurityEventPublisher`: 監査/通知用の `user_locked` イベントを発行します。

---

## ✅ 責務

### `SecurityEvent`

* 実行時の動作（障害、異常なリクエスト）を監視します。
* アラートまたはイベントチェーンをトリガーします（例：ロック検出）
* 監査証跡とリアルタイム監視に使用されます。

### `UserLifecycleEvent`

* 明示的なユーザー状態の変更（LOCK、DELETE、SUSPEND）
* クリーンアップアクションをトリガーできます（例：資格情報の削除、ロールのクリア）
* リカバリワークフローをキューに追加できます（例：アカウントのロック解除）

---

## 🧩 複合ユースケース：ユーザーの削除

| ステップ | イベント         | アクション                                                   |
|------|--------------|---------------------------------------------------------|
| 1    | 管理者がユーザーを削除  | `UserLifecycleEvent(DELETE)`                            |
| 2    | ライフサイクルハンドラー | データ削除をトリガー (`UserRelatedDataDeletionExecutor`)          |
| 3    | システムに通知      | Webhook/Slack などで `SecurityEvent(type=user_delete)` を発行 |

---