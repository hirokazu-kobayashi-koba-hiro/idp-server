---
sidebar_position: 5
---

# FIDO2 認証フローとインターフェース詳細

---

## 概要

このドキュメントは、**W3C WebAuthn Level 2仕様のFigure 2 Authentication Flow**に基づいて、FIDO2認証フローの各インターフェース（①〜⑥）とパラメータを詳細に解説します。

**情報源**: [W3C WebAuthn Level 2 - Figure 2 Authentication Flow](https://www.w3.org/TR/webauthn-2/#sctn-api)

**このドキュメントで学べること**:
- Relying Party ServerとRP JavaScript間の通信（①、⑤）
- BrowserとAuthenticator間のWebAuthn API（②、④）
- Authenticator内部処理（③）
- RP Serverでの検証処理（⑥）
- 各インターフェースの標準化状況

---

## アーキテクチャ図

```
┌─────────────────────────────────────────────────────────┐
│         Relying Party Server                            │
│                                       ⑥ server          │
│                                          validation     │
└───────────────┬──────────────────▲──────────────────────┘
                │                  │
        ① challenge        ⑤ clientDataJSON,
                              authenticatorData,
                              signature
                │                  │
┌───────────────▼──────────────────┴──────────────────────┐
│         RP JavaScript Application                       │
│         (Webブラウザー内で実行)                          │
├─────────────────────────────────────────────────────────┤
│         Browser (User Agent)                            │
│         WebAuthn API実装                                │
└───────────────┬──────────────────▲──────────────────────┘
                │                  │
        ② relying party id,  ④ authenticatorData,
           clientDataHash        signature
                │                  │
┌───────────────▼──────────────────┴──────────────────────┐
│         Authenticator                                   │
│         ③ user verification,                            │
│            create assertion                             │
└─────────────────────────────────────────────────────────┘
```

### W3C WebAuthn仕様の標準化範囲

W3C WebAuthn仕様は、全てのインターフェースを標準化しているわけではありません。標準化の範囲を理解することが重要です。

#### ✅ W3C WebAuthn仕様が標準化しているもの

| 項目 | 説明 | 標準化の目的 |
|------|------|-------------|
| **JavaScript API（②、④）** | `navigator.credentials.get()` のインターフェース | Browserの動作を統一（相互運用性） |
| **データ構造** | `authenticatorData`、`signature`、`clientDataJSON` の構造 | RP ↔ Browser ↔ Authenticator間のデータ交換を統一 |
| **検証手順（⑥）** | RPが実行すべき検証ステップ（Section 7.2） | セキュリティ要件の明確化 |
| **型定義** | `PublicKeyCredentialRequestOptions` 等の TypeScript/IDL 定義 | API仕様の明確化 |

**標準化の範囲**:
```
Browser（User Agent）の実装 = 完全に標準化
  ↓
・navigator.credentials.get() の動作
・authenticatorData の生成方法
・clientDataJSON の構造
・Authenticator との通信プロトコル（CTAP）
```

#### ❌ W3C WebAuthn仕様が標準化していないもの

| 項目 | 説明 | 理由 |
|------|------|------|
| **RP Server ↔ RP JavaScript間の通信（①、⑤）** | HTTPエンドポイント、リクエスト/レスポンス構造 | 各RPが独自のバックエンドAPI設計を採用できるようにするため |
| **パラメータ名** | `username` / `user_name` / `email` 等 | RP内部の設計自由度を保つため |
| **エンドポイントURL** | `/fido2-authentication-challenge` 等 | RESTful設計やURL設計はRP次第 |
| **認証フロー全体** | OAuth 2.0連携、セッション管理等 | RPごとに認証アーキテクチャが異なるため |

**非標準化の範囲**:
```
RP Server ↔ RP JavaScript の通信 = 標準化なし
  ↓
・HTTPエンドポイントURL
・リクエストのJSON構造
・レスポンスのJSON構造
・パラメータ名
・エラーレスポンス形式
```

#### 📖 W3C仕様の明確な記述

> **W3C WebAuthn Level 2 - Section 1.2 Conformance:**
> "This specification does not define a server-side API; it only defines the client-side API."

**日本語訳**: "この仕様はサーバー側APIを定義していません。クライアント側APIのみを定義します。"

**これの意味**:
- ✅ Browserの動作（JavaScript API、データ構造）は完全に標準化
- ❌ RPのバックエンドAPI（①、⑤）は各実装の自由

#### なぜこのような設計なのか？

| 観点 | 理由 |
|------|------|
| **相互運用性** | Browserの動作を統一すれば、どのRPでも同じJavaScript APIで実装可能 |
| **柔軟性** | RPごとに異なるバックエンドアーキテクチャ（Node.js、Java、Python等）に対応 |
| **進化可能性** | RPのバックエンドは自由に進化できる（新機能追加、パフォーマンス改善等） |
| **責任分離** | W3CはBrowser実装を標準化し、RPはセキュリティ要件（検証手順）のみ遵守 |

**実例**: idp-server、Google、GitHub、Microsoftは全て異なるバックエンドAPI設計ですが、全て同じWebAuthn APIで動作します。

---

## ① RP Server → RP JavaScript: チャレンジ取得

**通信**: HTTP（各実装が自由に設計）

### 一般的なリクエスト

```http
POST /fido2-authentication-challenge
Content-Type: application/json

{
  "username": "user@example.com"
}
```

### 一般的なレスポンス

```json
{
  "challenge": "Y2hhbGxlbmdl...",
  "rpId": "example.com",
  "allowCredentials": [
    {
      "type": "public-key",
      "id": "credential_id_base64url",
      "transports": ["internal"]
    }
  ],
  "timeout": 60000,
  "userVerification": "preferred"
}
```

### 主要パラメータ

| パラメータ | 型 | 説明 | 例 |
|-----------|---|------|---|
| `challenge` | Base64URL | ランダムチャレンジ（32バイト以上推奨） | `"Y2hhbGxlbmdl..."` |
| `rpId` | String | RPのドメイン名（省略時はcurrent origin） | `"example.com"` |
| `allowCredentials` | Array | 許可するCredential IDリスト | `[{type, id, transports}]` |
| `timeout` | Number | タイムアウト（ミリ秒） | `60000` |
| `userVerification` | String | User Verification要件 | `"required"` / `"preferred"` / `"discouraged"` |

### allowCredentialsの2つのパターン

| パターン | allowCredentials | ユーザー名入力 | 用途 |
|---------|-----------------|--------------|------|
| **ユーザー名入力あり** | RPがCredential IDを指定 | 必要 | 2要素認証、既存システムとの統合 |
| **パスワードレス** | 空配列 `[]` | 不要 | パスワードレスログイン（Discoverable Credential必須） |

**詳細**: [basic-17: FIDO2・パスキー・Discoverable Credential](basic-17-fido2-passkey-discoverable-credential.md)

### セキュリティ要件

- ✅ `challenge`は暗号学的に安全なランダム値（32バイト以上推奨）
- ✅ サーバー側でチャレンジを一時保存（検証時に使用、1回のみ有効）
- ✅ チャレンジの有効期限を設定（例: 2分）
- ✅ `allowCredentials`はユーザーに関連付けられたCredential IDのみ返す

---

## ② Browser → Authenticator: WebAuthn API呼び出し

**通信**: WebAuthn API（W3C標準）

### JavaScriptコード

```javascript
// ① で取得したレスポンスを変換
const publicKeyOptions = {
  challenge: base64UrlToBuffer(serverResponse.challenge),
  rpId: serverResponse.rpId,
  allowCredentials: serverResponse.allowCredentials.map(cred => ({
    type: cred.type,
    id: base64UrlToBuffer(cred.id),
    transports: cred.transports
  })),
  timeout: serverResponse.timeout,
  userVerification: serverResponse.userVerification
};

// WebAuthn API呼び出し
const assertion = await navigator.credentials.get({
  publicKey: publicKeyOptions
});
```

### Browserから認証器へ渡されるデータ

| データ | 説明 | 由来 |
|--------|------|------|
| `rpId` | RPのドメイン名 | サーバーから受領 |
| `allowCredentials` | 許可するCredential IDリスト | サーバーから受領 |
| `clientDataHash` | clientDataJSONのSHA-256ハッシュ | Browser内部で生成 |
| `userVerification` | User Verification要件 | サーバーから受領 |

### clientDataJSONの内容

```json
{
  "type": "webauthn.get",
  "challenge": "Y2hhbGxlbmdl...",
  "origin": "https://example.com",
  "crossOrigin": false
}
```

**重要**: Browserは`clientDataJSON`を自動生成し、そのSHA-256ハッシュを認証器に渡します。

---

## ③ Authenticator内部: 署名生成とAssertion作成

**認証器の処理** (FIDO CTAP仕様準拠):

### 1. Credentialの検索

| allowCredentials | 動作 |
|-----------------|------|
| **Credential ID指定あり** | 指定されたCredential IDに一致する秘密鍵を検索 |
| **空配列 `[]`** | Discoverable Credential（内部に保存済み）から検索 |

**Credential IDが見つからない場合**: エラー返却（`NotAllowedError`）

### 2. ユーザー検証（User Verification）

| 設定値 | 動作 |
|--------|------|
| `userVerification="required"` | 生体認証またはPIN入力を**必須**とする |
| `userVerification="preferred"` | 可能なら検証、不可能ならスキップ |
| `userVerification="discouraged"` | 検証なし（タップのみ） |

### 3. 署名生成

- 秘密鍵でAssertion（署名）を生成
- 署名対象: `authenticatorData || clientDataHash`
- `signCount`をインクリメント（クローン検出用）

---

## ④ Authenticator → Browser: Assertion Response返却

**認証器がBrowserに返すデータ**:

```javascript
// assertion.response の内容
{
  authenticatorData: ArrayBuffer,  // バイナリデータ
  clientDataJSON: ArrayBuffer,     // JSON文字列のバイナリ
  signature: ArrayBuffer,          // 署名データ
  userHandle: ArrayBuffer          // user.id（Discoverable Credentialの場合）
}
```

### authenticatorData の構造（37バイト以上）

| フィールド | サイズ | 説明 |
|-----------|--------|------|
| rpIdHash | 32バイト | rpIdのSHA-256ハッシュ |
| flags | 1バイト | UP(User Present), UV(User Verified), BE(Backup Eligibility), BS(Backup State) |
| signCount | 4バイト | 署名カウンター（クローン検出に使用） |

### flags（1バイト）の内訳

| ビット | 名称 | 説明 |
|-------|------|------|
| bit 0 | UP (User Present) | ユーザーがタップした（物理的存在確認） |
| bit 2 | UV (User Verified) | 生体認証またはPIN入力が完了 |
| bit 3 | BE (Backup Eligibility) | バックアップ可能（Level 3で追加） |
| bit 4 | BS (Backup State) | バックアップ済み（Level 3で追加） |

**注意**: 認証時は`AT`フラグ（Attested Credential Data）は含まれません（登録時のみ）

### 主要パラメータ

| パラメータ | 説明 | 用途 |
|-----------|------|------|
| `authenticatorData` | rpIdHash、flags、signCountを含むバイナリ | 検証に使用 |
| `signature` | 秘密鍵で生成された署名 | 公開鍵で検証 |
| `clientDataJSON` | Browserが生成したJSON | チャレンジ検証に使用 |
| `userHandle` | user.id（Discoverable Credentialの場合） | ユーザー識別 |

---

## ⑤ RP JavaScript → RP Server: Assertion送信

**通信**: HTTP（各実装が自由に設計）

### 一般的なリクエスト

```http
POST /fido2-authentication
Content-Type: application/json

{
  "id": "credential_id_base64url",
  "rawId": "credential_id_base64url",
  "type": "public-key",
  "response": {
    "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uZ2V0Ii...",
    "authenticatorData": "SZYN5YgOjGh0NBcPZHZgW4...",
    "signature": "MEUCIQDqV7Lzc...",
    "userHandle": "dXNlcjEyMw"
  }
}
```

### JavaScriptコード例

```javascript
// ArrayBufferをBase64URLに変換
function bufferToBase64Url(buffer) {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
}

// Assertion送信
const response = await fetch('/fido2-authentication', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({
    id: assertion.id,
    rawId: bufferToBase64Url(assertion.rawId),
    type: assertion.type,
    response: {
      clientDataJSON: bufferToBase64Url(assertion.response.clientDataJSON),
      authenticatorData: bufferToBase64Url(assertion.response.authenticatorData),
      signature: bufferToBase64Url(assertion.response.signature),
      userHandle: assertion.response.userHandle
        ? bufferToBase64Url(assertion.response.userHandle)
        : null
    }
  })
});
```

### 主要パラメータ

| パラメータ | 型 | 説明 |
|-----------|---|------|
| `id` | String | Credential ID（Base64URL） |
| `rawId` | String | Credential ID（Base64URL、idと同じ） |
| `type` | String | 常に `"public-key"` |
| `response.clientDataJSON` | String | clientDataJSONのBase64URL |
| `response.authenticatorData` | String | authenticatorDataのBase64URL |
| `response.signature` | String | 署名のBase64URL |
| `response.userHandle` | String | user.idのBase64URL（Discoverable Credentialの場合） |

---

## ⑥ RP Server内部: サーバー側検証

**検証項目**（W3C仕様 Section 7.2準拠）:

### 1. Credential ID検証

```
✅ Credential IDがデータベースに存在すること
✅ Credential IDとユーザーの関連付けが正しいこと
✅ 公開鍵をデータベースから取得
```

### 2. clientDataJSON検証

```
✅ type が "webauthn.get" であること
✅ challenge が保存済みチャレンジと一致すること
✅ origin が許可リストに含まれること
✅ crossOrigin が false であること（Same Origin検証）
```

### 3. authenticatorData検証

```
✅ authData.rpIdHash が rpId のSHA-256ハッシュと一致すること
✅ authData.flags.UP が 1（User Present）であること
✅ authData.flags.UV が要求通り（userVerification="required"の場合）
```

### 4. 署名検証

```
✅ signature が公開鍵で検証できること
✅ 署名対象: authenticatorData || sha256(clientDataJSON)
```

**署名検証の重要性**:
- 秘密鍵の所有証明
- 認証器が正当であることの確認
- 署名検証失敗 = 認証失敗

### 5. signCount検証（クローン検出）

```
✅ 現在のsignCountが前回保存値より大きいこと
✅ signCountが0の場合は検証スキップ（一部認証器は非対応）
✅ signCountが減少している場合はクローンの可能性
```

**クローン検出の重要性**:
- 認証器のクローン（不正コピー）を検出
- signCountが減少 = セキュリティアラートを発行

### レスポンス例

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "success",
  "user": {
    "id": "user123",
    "name": "user@example.com"
  }
}
```

---

## インターフェース標準化状況まとめ

| IF | 通信 | 標準化状況 | 備考 |
|----|------|----------|------|
| **① RP Server → RP JavaScript** | HTTP | ❌ 標準化なし | 各RP実装が自由に設計 |
| **② Browser → Authenticator** | WebAuthn API | ✅ W3C標準 | `navigator.credentials.get()` |
| **③ Authenticator内部処理** | - | ✅ FIDO CTAP仕様 | 署名生成、User Verification |
| **④ Authenticator → Browser** | WebAuthn API | ✅ W3C標準 | AuthenticatorAssertionResponse |
| **⑤ RP JavaScript → RP Server** | HTTP | ❌ 標準化なし | 各RP実装が自由に設計 |
| **⑥ RP Server内部検証** | - | ✅ W3C標準（検証手順） | Section 7.2で手順規定 |

**重要な結論**:
- W3C WebAuthn仕様は、**②、④のクライアント側APIと⑥の検証手順のみ標準化**
- **①、⑤のRP ServerとRP JavaScript間の通信は標準化されていない**
- 各RPが独自のAPI設計（エンドポイント、パラメータ名、データ構造）を採用可能
- idp-server、Google、GitHub等、各サービスでAPI設計が異なる

---

## まとめ

### 重要なポイント

1. **標準化の範囲**
   - ✅ クライアント側API（②、④）: W3C WebAuthn標準
   - ✅ 認証器処理（③）: FIDO CTAP標準
   - ✅ 検証手順（⑥）: W3C WebAuthn標準（Section 7.2）
   - ❌ RP ServerとRP JavaScript間の通信（①、⑤）: 標準化なし

2. **セキュリティの要**
   - `challenge`: 暗号学的に安全なランダム値（32バイト以上）
   - `rpIdHash`: フィッシング攻撃防止
   - `origin`: Same Origin検証
   - `signature`: 公開鍵で署名検証（秘密鍵の所有証明）
   - `signCount`: クローン検出

3. **データの流れ**
   - サーバー → ブラウザー: challenge, allowCredentials, userVerification
   - ブラウザー → 認証器: rpId, allowCredentials, clientDataHash
   - 認証器 → ブラウザー: authenticatorData, signature, clientDataJSON
   - ブラウザー → サーバー: id, type, response

4. **実装の自由度**
   - ①、⑤のインターフェースはRP実装ごとに異なる
   - エンドポイント名、パラメータ名、HTTPメソッド等は自由
   - allowCredentialsのパターン（指定あり/空配列）でUXが変わる

---

## 参考リソース

### W3C WebAuthn Level 2仕様
- **[Section 5. Web Authentication API](https://www.w3.org/TR/webauthn-2/#sctn-api)**: Figure 2 Authentication Flow
- **[Section 7.2 Verifying an Authentication Assertion](https://www.w3.org/TR/webauthn-2/#sctn-verifying-assertion)**: 検証手順詳細
- **[Section 6.5 Authenticator Data](https://www.w3.org/TR/webauthn-2/#sctn-authenticator-data)**: authenticatorData構造

### FIDO仕様
- **[FIDO CTAP2.1](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html)**: 認証器プロトコル

### 関連ドキュメント
- **[basic-16: FIDO2・WebAuthn パスワードレス認証](basic-16-fido2-webauthn-passwordless.md)**: FIDO2/WebAuthn基礎概念
- **[basic-17: FIDO2・パスキー・Discoverable Credential](basic-17-fido2-passkey-discoverable-credential.md)**: Discoverable CredentialとConditional UI
- **[basic-18: FIDO2アーキテクチャ - RP・Webブラウザー・認証器の関係](basic-18-fido2-architecture-rp-browser-authenticator.md)**: 4つのコンポーネント概要
- **[basic-19: FIDO2 登録フローとインターフェース詳細](basic-19-fido2-registration-flow-interface.md)**: 登録フロー詳細
- **[basic-21: FIDO2・WebAuthn仕様の変遷](basic-21-fido2-webauthn-level-specification-evolution.md)**: Level 1 → 2 → 3の変遷

---

**このドキュメントは、W3C WebAuthn Level 2仕様に基づいて作成されています。**
