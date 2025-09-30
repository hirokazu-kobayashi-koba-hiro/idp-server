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

## 🔐 FIDO-UAF ユーザーデータ削除

FIDO-UAF認証を使用しているユーザーが削除される場合、対応するFIDO-UAFデバイスキーも外部FIDOサーバーから削除する必要があります。

### `FidoUafUserDataDeletionExecutor`

このExecutorは、ユーザー削除時にFIDO-UAF関連データのクリーンアップを実行します。

| コンポーネント | 役割 |
|---------------|------|
| **トリガー** | `UserLifecycleEvent(DELETE)` + `user.enabledFidoUaf() == true` |
| **設定キー** | `"fido-uaf-deregistration"` (認証インタラクション設定) |
| **処理対象** | ユーザーが持つ全てのFIDO-UAF対応認証デバイス |
| **削除方式** | 外部FIDOサーバーへのHTTPリクエスト（デバイスごと） |

### 実装例

#### `shouldExecute` - 事前条件チェック

```java
@Override
public boolean shouldExecute(UserLifecycleEvent userLifecycleEvent) {
  // ライフサイクルタイプの確認
  if (userLifecycleEvent.lifecycleType() != UserLifecycleType.DELETE) {
    return false;
  }

  // FIDO-UAF設定の存在確認
  Tenant tenant = userLifecycleEvent.tenant();
  AuthenticationConfiguration authConfig =
      configurationQueryRepository.find(tenant, "fido-uaf");

  if (!authConfig.exists()) {
    log.info("Authentication config 'fido-uaf' not found");
    return false;
  }

  // デバイス削除設定の存在確認
  AuthenticationInteractionConfig interactionConfig =
      authConfig.getAuthenticationConfig("fido-uaf-deregistration");

  if (interactionConfig == null) {
    log.info("Authentication interaction config 'fido-uaf-deregistration' not found");
    return false;
  }

  // ユーザーのFIDO-UAF有効状態確認
  User user = userLifecycleEvent.user();
  return user.enabledFidoUaf();
}
```

#### `execute` - 実際の削除処理

```java
@Override
public UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent) {
  // shouldExecuteで事前チェック済みのため、安全に取得可能
  Tenant tenant = userLifecycleEvent.tenant();
  AuthenticationConfiguration config =
      configurationQueryRepository.get(tenant, "fido-uaf");
  AuthenticationInteractionConfig interactionConfig =
      config.getAuthenticationConfig("fido-uaf-deregistration");

  // 全デバイスに対して削除実行
  User user = userLifecycleEvent.user();
  for (AuthenticationDevice device : user.authenticationDevices()) {
    if (device.enabledFidoUaf()) {
      // デバイス固有の削除リクエスト
      Map<String, Object> request = Map.of("device_id", device.id());
      AuthenticationExecutionResult result = executor.execute(/* ... */);
      // 結果を集約
    }
  }
}
```

### 設定ファイル構造

`/authentication-config/fido-uaf/external.json`:

```json
{
  "interactions": {
    "fido-uaf-deregistration": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "http://localhost:4000/fido-uaf/delete-key",
          "method": "POST",
          "body_mapping_rules": [
            { "from": "$.request_body", "to": "*" }
          ]
        }
      }
    }
  }
}
```

### トラブルシューティング

#### ⚠️ 設定キー不一致エラー

**エラー**: `Cannot invoke 'AuthenticationInteractionConfig.execution()' because 'authenticationInteractionConfig' is null`

**原因**: コードと設定ファイルの設定キー不一致

| 問題のケース | コード側のキー | 設定ファイルのキー |
|-------------|---------------|-------------------|
| ❌ 不一致 | `"fido-uaf-delete-key"` | `"fido-uaf-deregistration"` |
| ✅ 正しい | `"fido-uaf-deregistration"` | `"fido-uaf-deregistration"` |

**解決法**:
1. 設定ファイルのキー名を確認
2. コード側の設定キーを一致させる
3. null安全性チェックを追加

```java
AuthenticationInteractionConfig config =
    authConfig.getAuthenticationConfig("fido-uaf-deregistration");

if (config == null) {
  log.warn("Authentication interaction config 'fido-uaf-deregistration' not found");
  return UserLifecycleEventResult.failure(name(),
      Map.of("message", "Configuration not found"));
}
```

### 設計指針

1. **責務の分離**: `shouldExecute`で事前条件をチェック、`execute`で実際の処理を実行
2. **安全な設定取得**: `find()`メソッドと`exists()`による存在確認を活用
3. **設定キーの一貫性**: コードと設定ファイルのキー名を必ず一致させる
4. **段階的チェック**: ライフサイクルタイプ → 設定存在 → インタラクション設定 → ユーザー状態の順で確認
5. **デバイス反復**: ユーザーが複数のFIDO-UAFデバイスを持つ場合を考慮
6. **結果集約**: 各デバイスの削除結果を適切に集約・報告
7. **ログ出力**: テナント情報はコンテキストで設定済みのため冗長な出力を避ける
8. **エラーハンドリング**: 設定不備の場合は実行対象から除外（例外ではなく`false`を返す）

---