# FIDO2 パスキー削除機能 実装サマリー

## 概要

ユーザーが登録済みのパスキー（FIDO2クレデンシャル）を削除できる機能を実装した。

## 実装した機能

### 1. 認証デバイス削除API

**エンドポイント**: `DELETE /{tenant-id}/v1/me/authentication-devices/{device-id}`

**必要なスコープ**: `claims:authentication_devices`

**レスポンス**:
| ステータス | 説明 |
|-----------|------|
| 204 No Content | 削除成功 |
| 403 Forbidden | スコープ不足 |
| 404 Not Found | デバイスが見つからない |

### 2. WebAuthn4jクレデンシャル削除の連動

認証デバイス削除時に、紐づいているWebAuthn4jクレデンシャルも同時に削除される。

### 3. sample-web UIの削除ボタン

ダッシュボードの各パスキー表示に削除ボタンを追加。確認ダイアログ付き。

## アーキテクチャ

### データモデルの関係

```
┌─────────────────────────────────────────────────────────────────┐
│ User                                                            │
│   └── authentication_devices[]                                  │
│         └── AuthenticationDevice                                │
│               ├── id: UUID (デバイスID)                         │
│               ├── platform, os, model (デバイス情報)            │
│               └── deviceCredentials[]                           │
│                     └── DeviceCredential                        │
│                           ├── type: "fido2"                     │
│                           └── typeSpecificData (FidoCredentialData)
│                                 ├── fido_server_id              │
│                                 ├── credential_id  ─────────┐   │
│                                 └── rp_id                   │   │
│                                                             │   │
│ WebAuthn4jCredential                                        │   │
│   ├── id ◄──────────────────────────────────────────────────┘   │
│   ├── userId                                                    │
│   ├── username                                                  │
│   ├── attestedCredentialData (公開鍵等)                         │
│   └── signCount                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 登録フロー

```
1. WebAuthn4jRegistrationExecutor
   └── credential_id を生成、WebAuthn4jCredentialRepository に保存

2. Fido2RegistrationInteractor
   └── AuthenticationDevice を作成
       └── DeviceCredential (type=fido2) を追加
           └── FidoCredentialData に credential_id を保存
   └── User.addAuthenticationDevice()
```

### 削除フロー

```
1. DELETE /v1/me/authentication-devices/{device-id}

2. UserOperationEntryService.deleteAuthenticationDevice()
   ├── スコープ検証 (claims:authentication_devices)
   ├── デバイス存在確認
   ├── FIDO2クレデンシャル削除
   │     └── device.deviceCredentials から credential_id 取得
   │     └── WebAuthn4jCredentialRepository.delete(credential_id)
   ├── User.removeAuthenticationDevice(device_id)
   └── セキュリティイベント発行
```

## 変更ファイル一覧

### サーバーサイド (Java)

| ファイル | 変更内容 |
|---------|---------|
| `libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/UserOperationApi.java` | `deleteAuthenticationDevice` メソッド追加 |
| `libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/io/UserOperationStatus.java` | `NOT_FOUND(404)` 追加 |
| `libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/io/UserOperationResponse.java` | `notFound()` メソッド追加 |
| `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/UserOperationEntryService.java` | `deleteAuthenticationDevice` 実装、WebAuthn4jCredentialRepository 依存追加 |
| `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/IdpServerApplication.java` | コンストラクタに WebAuthn4jCredentialRepository 追加 |
| `libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/restapi/me/UserV1Api.java` | DELETE エンドポイント追加 |
| `libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/fido2/Fido2RegistrationInteractor.java` | 登録時に credential_id を DeviceCredential に保存 |
| `libs/idp-server-platform/src/main/java/org/idp/server/platform/security/event/DefaultSecurityEventType.java` | `fido2_deregistration_success/failure` 追加済み |

### クライアントサイド (sample-web)

| ファイル | 変更内容 |
|---------|---------|
| `sample-web/src/app/api/authentication-devices/[id]/route.ts` | 新規: 削除API呼び出し |
| `sample-web/src/components/UserInfo.tsx` | 削除ボタン、確認ダイアログ追加 |

### 設定ファイル

| ファイル | 変更内容 |
|---------|---------|
| `config/examples/e2e/test-tenant/authentication-config/fido2/webauthn4j.json` | `fido2-deregistration` 追加 |
| `config/examples/subdomain-oidc-web-app/authentication-config/fido2/webauthn4j.json` | `fido2-deregistration` 追加 |

### SPI登録ファイル

| ファイル | 変更内容 |
|---------|---------|
| `libs/idp-server-webauthn4j-adapter/src/main/resources/META-INF/services/org.idp.server.core.openid.authentication.interaction.execution.AuthenticationExecutorFactory` | `WebAuthn4jDeregistrationExecutorFactory` 追加 |
| `libs/idp-server-authentication-interactors/src/main/resources/META-INF/services/org.idp.server.core.openid.authentication.plugin.AuthenticationInteractorFactory` | `Fido2DeregistrationInteractorFactory` 追加 |

## 注意事項

### WebAuthn APIの制限

- サーバー側でクレデンシャルを削除しても、**ブラウザ/デバイス側のパスキーは削除されない**
- ユーザーは手動でデバイス側のパスキーを削除する必要がある
- UIに注意メッセージを表示済み

### credential_id の紐づけ

登録時の `body_mapping_rules` 設定によっては、`credential_id` がレスポンスに含まれない可能性がある。

現在の設定:
```json
{
  "from": "$.execution_webauthn4j",
  "to": "*"
}
```

この設定では `credential_id` (フィールド名: `id`) が含まれる。

コードは `credential_id` が null の場合を考慮済み:
```java
if (credentialId != null && !credentialId.isEmpty()) {
  // 紐づけ処理
}
```

## 未対応・検討事項

1. **最後のパスキー保護**: ユーザーが最後のパスキーを削除しようとした場合の警告/ブロック
2. **デバイス名変更機能**: ユーザーがパスキーに名前をつけられる機能
3. **最終使用日時表示**: いつ最後に使用されたかを表示
4. **既存データの移行**: credential_id 紐づけなしで登録された既存パスキーへの対応

## 各IDサービスの参考情報

パスキー削除機能は主要IDサービスで以下のように実装されている:

| サービス | 管理者向け | ユーザー向け | API |
|---------|-----------|-------------|-----|
| Google | Admin Console | myaccount.google.com | - |
| Microsoft | Azure AD/Entra | mysignins.microsoft.com | - |
| Apple | - | Passwords アプリ | - |
| Okta | Admin Console + API | End-User Settings | MyAccount WebAuthn API |
| Auth0 | Dashboard | ユーザー設定画面 | Management API |
| AWS Cognito | - | アプリ内 | DeleteWebAuthnCredential API |

## ビルド・テスト

```bash
# ビルド
./gradlew spotlessApply && ./gradlew build -x test

# sample-web ビルド
cd sample-web && npm run build
```

## 関連Issue/PR

(該当するIssue/PR番号をここに記載)
