# FIDO2/WebAuthn 実装改善バックログ

## 概要

FIDO2/WebAuthn実装の現状分析と残課題をまとめたドキュメント。
現在の実装は基本機能が完成しており、本番運用可能な状態だが、いくつかの改善点が存在する。

## 現在の実装状況

### 完成済み機能

| 機能 | 状態 | 備考 |
|------|------|------|
| 登録チャレンジ生成 | ✅ | `WebAuthn4jRegistrationChallengeExecutor` |
| 登録検証・保存 | ✅ | `WebAuthn4jRegistrationExecutor` |
| 認証チャレンジ生成 | ✅ | `WebAuthn4jAuthenticationChallengeExecutor` |
| 認証検証 | ✅ | `WebAuthn4jAuthenticationExecutor` |
| マルチクレデンシャル対応 | ✅ | 1ユーザーで複数パスキー登録可能 |
| マルチテナント対応 | ✅ | 全クエリで `tenant_id` 使用 |
| PostgreSQL対応 | ✅ | `PostgresqlExecutor` |
| MySQL対応 | ✅ | `MysqlExecutor` |
| signCount検証（クローン検出） | ✅ | 認証時にカウンタ検証 |
| transports保存 | ✅ | UX最適化用 |
| credProtect保存 | ✅ | 保護レベル |
| Resident Key (Discoverable Credential) | ✅ | `rk` フラグ保存 |

### アーキテクチャ

```
┌─────────────────────────────────────────────────────────────────┐
│                    Application Plane API                        │
├─────────────────────────────────────────────────────────────────┤
│  Fido2RegistrationChallengeInteractor                          │
│  Fido2RegistrationInteractor                                    │
│  Fido2AuthenticationChallengeInteractor                        │
│  Fido2AuthenticationInteractor                                  │
├─────────────────────────────────────────────────────────────────┤
│                    WebAuthn4j Adapter                           │
├─────────────────────────────────────────────────────────────────┤
│  WebAuthn4jRegistrationChallengeExecutor                       │
│  WebAuthn4jRegistrationExecutor                                 │
│  WebAuthn4jAuthenticationChallengeExecutor                     │
│  WebAuthn4jAuthenticationExecutor                               │
├─────────────────────────────────────────────────────────────────┤
│  WebAuthn4jCredentialRepository (Interface)                    │
│    └── WebAuthn4jCredentialDataSource (Implementation)         │
│          ├── PostgresqlExecutor                                │
│          └── MysqlExecutor                                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 残課題一覧

### Phase 1: 高優先度

#### 1.1 エラーハンドリング改善

**課題**: クレデンシャル検索失敗時のエラーハンドリングが不適切

**現状**:
- `WebAuthn4jCredentialDataSource.get()` が `NotFoundException` をスロー
- `WebAuthn4jAuthenticationExecutor` で generic `Exception` としてキャッチ
- 結果: 500 Internal Server Error として返却

**対象ファイル**:
- `libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/WebAuthn4jAuthenticationExecutor.java:87`
- `libs/idp-server-webauthn4j-adapter/src/main/java/org/idp/server/authenticators/webauthn4j/datasource/credential/WebAuthn4jCredentialDataSource.java:75`

**改善案**:
```java
// WebAuthn4jAuthenticationExecutor.java
try {
    WebAuthn4jCredential credential = credentialRepository.get(tenant, id);
    // ...
} catch (NotFoundException e) {
    log.warn("Credential not found: id={}, tenant={}", id, tenant.identifier());
    return AuthenticationResult.clientError("credential_not_found", "Credential not found for this user");
} catch (WebAuthn4jBadRequestException e) {
    // existing handling
}
```

**期待される動作**:
- 404 Not Found レスポンス
- 明確なエラーメッセージ: `credential_not_found`
- ログ出力（警告レベル）

---

#### 1.2 クレデンシャル一覧API

**課題**: 登録済みパスキーの一覧取得APIが未実装

**バックエンド実装**: ✅ 完了
- `WebAuthn4jCredentialRepository.findAll(Tenant, String userId)`
- `WebAuthn4jCredentialRepository.findByUsername(Tenant, String username)`

**必要な作業**:
1. Control Plane API エンドポイント追加
   - `GET /v1/users/{userId}/credentials`
   - `GET /v1/credentials?username={username}`
2. OpenAPI定義追加
3. レスポンス形式定義

**レスポンス例**:
```json
{
  "credentials": [
    {
      "id": "abc123...",
      "created_at": "2024-01-15T10:30:00Z",
      "last_used_at": "2024-01-20T08:00:00Z",
      "device_name": "iPhone 15 Pro",
      "transports": ["internal"],
      "backup_eligible": true,
      "backup_state": true
    }
  ]
}
```

---

#### 1.3 クレデンシャル削除API

**課題**: パスキー削除APIが未実装

**バックエンド実装**: ✅ 完了
- `WebAuthn4jCredentialRepository.delete(Tenant, String credentialId)`

**必要な作業**:
1. Control Plane API エンドポイント追加
   - `DELETE /v1/credentials/{credentialId}`
2. 削除前の検証ロジック
   - 最後の1つを削除する場合の警告/拒否
3. セキュリティイベントログ出力

---

### Phase 2: 中優先度

#### 2.1 デバイス名フィールド追加

**課題**: パスキーに人間が読める名前を付けられない

**必要な作業**:
1. DBスキーマ変更
   ```sql
   ALTER TABLE webauthn_credentials ADD COLUMN device_name VARCHAR(128);
   ```
2. Flyway マイグレーション追加
3. `WebAuthn4jCredential` モデル更新
4. 登録時のデバイス名自動検出（User-Agent解析）

---

#### 2.2 クレデンシャル名変更API

**課題**: 登録後にパスキーの表示名を変更できない

**必要な作業**:
1. Control Plane API エンドポイント追加
   - `PUT /v1/credentials/{credentialId}`
   - Request body: `{ "device_name": "My MacBook" }`
2. Repository メソッド追加
   - `updateDeviceName(Tenant, String credentialId, String deviceName)`

---

#### 2.3 パスキー管理UI（sample-web）

**課題**: ユーザーがパスキーを管理するUIがない

**必要な作業**:
1. パスキー一覧コンポーネント
2. 削除確認ダイアログ
3. 名前変更機能
4. 最終使用日時表示

---

### Phase 3: 低優先度

#### 3.1 WebAuthn Level 3 Backup Flags対応

**課題**: バックアップ関連フラグが保存されていない

**現状**:
- `WebAuthn4jCredentialConverter.java:64-65` で `null` 設定

**対象フラグ**:
| フラグ | 説明 |
|--------|------|
| `backup_eligible` (BE) | iCloud Keychain等にバックアップ可能か |
| `backup_state` (BS) | 現在バックアップされているか |
| `uv_initialized` | User Verification初期化済みか |

**必要な作業**:
1. DBスキーマ変更
2. WebAuthn4jライブラリからフラグ抽出
3. 認証時の検証ロジック追加

---

#### 3.2 セキュリティイベントログ連携

**課題**: FIDO2関連イベントがセキュリティイベントログに出力されていない

**出力すべきイベント**:
| イベント | 重要度 |
|---------|--------|
| `fido2.credential.registered` | INFO |
| `fido2.credential.deleted` | WARNING |
| `fido2.authentication.success` | INFO |
| `fido2.authentication.failed` | WARNING |
| `fido2.cloned_credential_detected` | CRITICAL |

---

#### 3.3 レート制限

**課題**: 認証チャレンジ生成のレート制限がない

**リスク**:
- DoS攻撃によるリソース枯渇
- チャレンジ総当たり攻撃（理論上）

**対応案**:
- IPベースのレート制限
- ユーザーベースのレート制限
- Exponential backoff

---

#### 3.4 クレデンシャルメタデータ拡張

**課題**: セキュリティ監査用の情報が不足

**追加すべきフィールド**:
| フィールド | 用途 |
|-----------|------|
| `registered_ip` | 登録時IPアドレス |
| `registered_user_agent` | 登録時User-Agent |
| `last_used_ip` | 最終使用時IP |
| `last_used_user_agent` | 最終使用時User-Agent |

---

## 関連ファイル一覧

### コア実装

| ファイル | 説明 |
|---------|------|
| `WebAuthn4jCredential.java` | クレデンシャルモデル |
| `WebAuthn4jCredentialRepository.java` | リポジトリインターフェース |
| `WebAuthn4jCredentialDataSource.java` | リポジトリ実装 |
| `WebAuthn4jRegistrationChallengeExecutor.java` | 登録チャレンジ生成 |
| `WebAuthn4jRegistrationExecutor.java` | 登録検証 |
| `WebAuthn4jAuthenticationChallengeExecutor.java` | 認証チャレンジ生成 |
| `WebAuthn4jAuthenticationExecutor.java` | 認証検証 |
| `WebAuthn4jConfiguration.java` | 設定クラス |

### データベース

| ファイル | 説明 |
|---------|------|
| `V0_9_27_2__webauthn4j.sql` | PostgreSQLスキーマ |
| `V0_9_27_2__webauthn.mysql.sql` | MySQLスキーマ |
| `PostgresqlExecutor.java` | PostgreSQL実装 |
| `MysqlExecutor.java` | MySQL実装 |

### フロントエンド（app-view）

| ファイル | 説明 |
|---------|------|
| `src/pages/signup/fido2/index.tsx` | パスキー登録画面 |
| `src/pages/signin/fido2/index.tsx` | パスキー認証画面 |
| `src/pages/add-passkey/index.tsx` | パスキー追加（メール入力） |
| `src/pages/add-passkey/verify/index.tsx` | パスキー追加（コード検証） |

---

## 参考情報

### WebAuthn仕様

- [W3C Web Authentication Level 2](https://www.w3.org/TR/webauthn-2/)
- [W3C Web Authentication Level 3](https://www.w3.org/TR/webauthn-3/)
- [FIDO Alliance Specifications](https://fidoalliance.org/specifications/)

### 関連Issue/PR

- TBD: GitHub Issue作成時にリンク追加

---

## 更新履歴

| 日付 | 内容 |
|------|------|
| 2026-01-20 | 初版作成 |
