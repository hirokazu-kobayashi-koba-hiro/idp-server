---
sidebar_position: 4
---

# FIDO2 登録フローとインターフェース詳細

---

## 概要

このドキュメントは、**W3C WebAuthn Level 2仕様のFigure 1 Registration Flow**に基づいて、FIDO2登録フローの各インターフェース（①〜⑥）とパラメータを詳細に解説します。

**情報源**: [W3C WebAuthn Level 2 - Figure 1 Registration Flow](https://www.w3.org/TR/webauthn-2/#sctn-api)

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
        ① challenge,       ⑤ clientDataJSON,
           user info,         attestationObject
           relying party info
                │                  │
┌───────────────▼──────────────────┴──────────────────────┐
│         RP JavaScript Application                       │
│         (Webブラウザー内で実行)                          │
├─────────────────────────────────────────────────────────┤
│         Browser (User Agent)                            │
│         WebAuthn API実装                                │
└───────────────┬──────────────────▲──────────────────────┘
                │                  │
        ② relying party id,  ④ new public key,
           user info,            credential id,
           relying party info,   attestation
           clientDataHash
                │                  │
┌───────────────▼──────────────────┴──────────────────────┐
│         Authenticator                                   │
│         ③ user verification,                            │
│            new keypair,                                 │
│            attestation                                  │
└─────────────────────────────────────────────────────────┘
```

### W3C WebAuthn仕様の標準化範囲

W3C WebAuthn仕様は、全てのインターフェースを標準化しているわけではありません。標準化の範囲を理解することが重要です。

#### ✅ W3C WebAuthn仕様が標準化しているもの

| 項目 | 説明 | 標準化の目的 |
|------|------|-------------|
| **JavaScript API（②、④）** | `navigator.credentials.create()` / `get()` のインターフェース | Browserの動作を統一（相互運用性） |
| **データ構造** | `attestationObject`、`authData`、`clientDataJSON` の構造 | RP ↔ Browser ↔ Authenticator間のデータ交換を統一 |
| **検証手順（⑥）** | RPが実行すべき検証ステップ（Section 7.1, 7.2） | セキュリティ要件の明確化 |
| **型定義** | `PublicKeyCredentialCreationOptions` 等の TypeScript/IDL 定義 | API仕様の明確化 |

**標準化の範囲**:
```
Browser（User Agent）の実装 = 完全に標準化
  ↓
・navigator.credentials.create() の動作
・attestationObject の生成方法
・clientDataJSON の構造
・Authenticator との通信プロトコル（CTAP）
```

#### ❌ W3C WebAuthn仕様が標準化していないもの

| 項目 | 説明 | 理由 |
|------|------|------|
| **RP Server ↔ RP JavaScript間の通信（①、⑤）** | HTTPエンドポイント、リクエスト/レスポンス構造 | 各RPが独自のバックエンドAPI設計を採用できるようにするため |
| **パラメータ名** | `username` / `user_name` / `email` 等 | RP内部の設計自由度を保つため |
| **エンドポイントURL** | `/fido2-registration-challenge` 等 | RESTful設計やURL設計はRP次第 |
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
POST /fido2-registration-challenge
Content-Type: application/json

{
  "username": "user@example.com"
}
```

### 一般的なレスポンス

```json
{
  "challenge": "Y2hhbGxlbmdl...",
  "rp": {
    "id": "example.com",
    "name": "Example Service"
  },
  "user": {
    "id": "dXNlcjEyMw",
    "name": "user@example.com",
    "displayName": "User Name"
  },
  "pubKeyCredParams": [
    {"type": "public-key", "alg": -7},
    {"type": "public-key", "alg": -257}
  ],
  "timeout": 60000,
  "authenticatorSelection": {
    "authenticatorAttachment": "platform",
    "residentKey": "required",
    "userVerification": "required"
  },
  "attestation": "none"
}
```

### 主要パラメータ

| パラメータ | 型 | 説明 | 例 |
|-----------|---|------|---|
| `challenge` | Base64URL | ランダムチャレンジ（32バイト以上推奨） | `"Y2hhbGxlbmdl..."` |
| `rp.id` | String | RPのドメイン名（フィッシング対策の要） | `"example.com"` |
| `rp.name` | String | RPの表示名 | `"Example Service"` |
| `user.id` | Base64URL | ユーザーID（バイナリ、ユーザー識別の要） | `"dXNlcjEyMw"` |
| `user.name` | String | ユーザー識別子 | `"user@example.com"` |
| `user.displayName` | String | ユーザー表示名 | `"User Name"` |
| `pubKeyCredParams` | Array | 許可する署名アルゴリズム | ES256(-7), RS256(-257) |
| `timeout` | Number | タイムアウト（ミリ秒） | `60000` |
| `authenticatorSelection.authenticatorAttachment` | String | 認証器タイプ制約 | `"platform"` / `"cross-platform"` / null |
| `authenticatorSelection.residentKey` | String | Resident Key要件 | `"required"` / `"preferred"` / `"discouraged"` |
| `authenticatorSelection.userVerification` | String | User Verification要件 | `"required"` / `"preferred"` / `"discouraged"` |
| `attestation` | String | Attestation要件 | `"none"` / `"indirect"` / `"direct"` |

### セキュリティ要件

- ✅ `challenge`は暗号学的に安全なランダム値（32バイト以上推奨）
- ✅ サーバー側でチャレンジを一時保存（検証時に使用、1回のみ有効）
- ✅ `user.id`はユーザーごとに一意（個人識別情報を含まない推奨）
- ✅ チャレンジの有効期限を設定（例: 2分）

---

## ② Browser → Authenticator: WebAuthn API呼び出し

**通信**: WebAuthn API（W3C標準）

### JavaScriptコード

```javascript
// ① で取得したレスポンスを変換
const publicKeyOptions = {
  challenge: base64UrlToBuffer(serverResponse.challenge),
  rp: serverResponse.rp,
  user: {
    id: base64UrlToBuffer(serverResponse.user.id),
    name: serverResponse.user.name,
    displayName: serverResponse.user.displayName
  },
  pubKeyCredParams: serverResponse.pubKeyCredParams,
  timeout: serverResponse.timeout,
  authenticatorSelection: serverResponse.authenticatorSelection,
  attestation: serverResponse.attestation
};

// WebAuthn API呼び出し
const credential = await navigator.credentials.create({
  publicKey: publicKeyOptions
});
```

### Browserから認証器へ渡されるデータ

| データ | 説明 | 由来 |
|--------|------|------|
| `rp.id` | RPのドメイン名 | サーバーから受領 |
| `rp.name` | RPの表示名 | サーバーから受領 |
| `user.id` | ユーザーID（バイナリ） | サーバーから受領 |
| `user.name` | ユーザー識別子 | サーバーから受領 |
| `user.displayName` | ユーザー表示名 | サーバーから受領 |
| `clientDataHash` | clientDataJSONのSHA-256ハッシュ | Browser内部で生成 |
| `authenticatorSelection` | 認証器選択基準 | サーバーから受領 |

### clientDataJSONの内容

```json
{
  "type": "webauthn.create",
  "challenge": "Y2hhbGxlbmdl...",
  "origin": "https://example.com",
  "crossOrigin": false
}
```

**重要**: Browserは`clientDataJSON`を自動生成し、そのSHA-256ハッシュを認証器に渡します。開発者は`clientDataJSON`を直接構築する必要はありません。

---

## ③ Authenticator内部: 鍵ペア生成とAttestation作成

**認証器の処理** (FIDO CTAP仕様準拠):

### 1. ユーザー検証（User Verification）

| 設定値 | 動作 |
|--------|------|
| `userVerification="required"` | 生体認証またはPIN入力を**必須**とする |
| `userVerification="preferred"` | 可能なら検証、不可能ならスキップ |
| `userVerification="discouraged"` | 検証なし（タップのみ） |

### 2. 鍵ペア生成

- `pubKeyCredParams`で指定されたアルゴリズムで鍵ペア生成（例: ES256）
- 秘密鍵は認証器内部のSecure Elementに安全に保管
- 公開鍵はRP Serverに送信

### 3. Credential ID生成

- ランダムな一意識別子を生成
- 認証時にCredentialを特定するために使用

### 4. Resident Key（Discoverable Credential）処理

| 設定値 | 動作 |
|--------|------|
| `residentKey="required"` | `user.id`、`user.name`、`rpId`を認証器内部に**保存** |
| `residentKey="preferred"` | 可能なら保存、不可能ならスキップ |
| `residentKey="discouraged"` | 保存しない |

**用途**: `residentKey="required"`の場合、パスワードレスログイン（ユーザー名入力不要）が可能になります。

### 5. Attestation Statement作成

| 設定値 | 動作 |
|--------|------|
| `attestation="none"` | Attestation Statementを省略（最も一般的） |
| `attestation="indirect"` | 匿名化されたAttestation |
| `attestation="direct"` | 認証器の証明書チェーン付きAttestation |

---

## ④ Authenticator → Browser: Attestation Response返却

**認証器がBrowserに返すデータ**:

```javascript
// credential.response の内容
{
  attestationObject: ArrayBuffer,  // CBOR形式のバイナリ
  clientDataJSON: ArrayBuffer      // JSON文字列のバイナリ
}
```

### attestationObject の内容（CBOR形式）

```
{
  "fmt": "none",
  "authData": <バイナリデータ>,
  "attStmt": {}
}
```

### authData の構造（37バイト以上）

| フィールド | サイズ | 説明 |
|-----------|--------|------|
| rpIdHash | 32バイト | rpIdのSHA-256ハッシュ（フィッシング検出に使用） |
| flags | 1バイト | UP, UV, AT, BE, BS等のフラグ |
| signCount | 4バイト | 署名カウンター（クローン検出に使用） |
| attestedCredentialData | 可変長 | aaguid + credentialIdLength + credentialId + credentialPublicKey |

### flags（1バイト）の内訳

| ビット | 名称 | 説明 |
|-------|------|------|
| bit 0 | UP (User Present) | ユーザーがタップした（物理的存在確認） |
| bit 2 | UV (User Verified) | 生体認証またはPIN入力が完了 |
| bit 6 | AT (Attested Credential Data) | attestedCredentialDataが含まれる（登録時は常に1） |
| bit 3 | BE (Backup Eligibility) | バックアップ可能（Level 3で追加） |
| bit 4 | BS (Backup State) | バックアップ済み（Level 3で追加） |

### attestedCredentialData の構造

```
aaguid (16 bytes)
  ↓ 認証器モデル識別子

credentialIdLength (2 bytes)
  ↓

credentialId (credentialIdLength bytes)
  ↓ Credential一意識別子

credentialPublicKey (COSE形式、可変長)
  ↓ 公開鍵（ES256の場合は約77バイト）
```

### 主要パラメータ

| パラメータ | 説明 | 用途 |
|-----------|------|------|
| `credentialId` | Credential一意識別子 | 認証時の指定に使用 |
| `credentialPublicKey` | 公開鍵（COSE形式） | 署名検証に使用 |
| `flags.UP` | User Present（タップ確認） | ユーザーの物理的存在確認 |
| `flags.UV` | User Verified（生体認証/PIN） | ユーザー本人確認 |
| `flags.AT` | Attested Credential Data存在フラグ | 登録時は常に1 |
| `aaguid` | 認証器モデル識別子 | 認証器の種類識別 |

---

## ⑤ RP JavaScript → RP Server: Attestation送信

**通信**: HTTP（各実装が自由に設計）

### 一般的なリクエスト

```http
POST /fido2-registration
Content-Type: application/json

{
  "id": "credential_id_base64url",
  "rawId": "credential_id_base64url",
  "type": "public-key",
  "response": {
    "clientDataJSON": "eyJ0eXBlIjoid2ViYXV0aG4uY3JlYXRlIi...",
    "attestationObject": "o2NmbXRkbm9uZWdhdHRTdG10oGhhdXRoRGF0YVi..."
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

// Credential送信
const response = await fetch('/fido2-registration', {
  method: 'POST',
  headers: {'Content-Type': 'application/json'},
  body: JSON.stringify({
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    response: {
      clientDataJSON: bufferToBase64Url(credential.response.clientDataJSON),
      attestationObject: bufferToBase64Url(credential.response.attestationObject)
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
| `response.attestationObject` | String | attestationObjectのBase64URL |

---

## ⑥ RP Server内部: サーバー側検証

**検証項目**（W3C仕様 Section 7.1準拠）:

### 1. clientDataJSON検証

```
✅ type が "webauthn.create" であること
✅ challenge が保存済みチャレンジと一致すること
✅ origin が許可リストに含まれること
✅ crossOrigin が false であること（Same Origin検証）
```

### 2. attestationObject検証

```
✅ authData.rpIdHash が rpId のSHA-256ハッシュと一致すること
✅ authData.flags.UP が 1（User Present）であること
✅ authData.flags.UV が要求通り（userVerification="required"の場合）
✅ authData.flags.AT が 1（Attested Credential Data存在）
```

**rpIdHash検証の重要性**:
- フィッシング攻撃防止の要
- 攻撃者が別ドメインで登録した鍵をRPに送信しても、rpIdHashが一致しないため検証失敗

### 3. 公開鍵抽出と保存

```
✅ attestedCredentialData から credentialId と credentialPublicKey を抽出
✅ データベースに保存（user_id, credential_id, public_key, sign_count等）
```

### 4. Attestation Statement検証（`attestation != "none"`の場合）

```
✅ 認証器の証明書チェーン検証
✅ FIDO Metadata Serviceとの照合
✅ 信頼できる認証器かどうかの判定
```

**注意**: `attestation="none"`が最も一般的で、多くのRPはAttestation検証を省略しています。

### レスポンス例

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "status": "success",
  "credential_id": "credential_id_base64url"
}
```

---

## インターフェース標準化状況まとめ

| IF | 通信 | 標準化状況 | 備考 |
|----|------|----------|------|
| **① RP Server → RP JavaScript** | HTTP | ❌ 標準化なし | 各RP実装が自由に設計 |
| **② Browser → Authenticator** | WebAuthn API | ✅ W3C標準 | `navigator.credentials.create()` |
| **③ Authenticator内部処理** | - | ✅ FIDO CTAP仕様 | 鍵ペア生成、User Verification |
| **④ Authenticator → Browser** | WebAuthn API | ✅ W3C標準 | AuthenticatorAttestationResponse |
| **⑤ RP JavaScript → RP Server** | HTTP | ❌ 標準化なし | 各RP実装が自由に設計 |
| **⑥ RP Server内部検証** | - | ✅ W3C標準（検証手順） | Section 7.1で手順規定 |

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
   - ✅ 検証手順（⑥）: W3C WebAuthn標準（Section 7.1）
   - ❌ RP ServerとRP JavaScript間の通信（①、⑤）: 標準化なし

2. **セキュリティの要**
   - `challenge`: 暗号学的に安全なランダム値（32バイト以上）
   - `rpIdHash`: フィッシング攻撃防止
   - `origin`: Same Origin検証
   - `flags.UP`: ユーザーの物理的存在確認

3. **データの流れ**
   - サーバー → ブラウザー: challenge, rp, user, pubKeyCredParams
   - ブラウザー → 認証器: rp, user, clientDataHash
   - 認証器 → ブラウザー: attestationObject, clientDataJSON
   - ブラウザー → サーバー: id, type, response

4. **実装の自由度**
   - ①、⑤のインターフェースはRP実装ごとに異なる
   - エンドポイント名、パラメータ名、HTTPメソッド等は自由
   - WebAuthn4j等のライブラリで検証を簡素化可能

---

## 参考リソース

### W3C WebAuthn Level 2仕様
- **[Section 5. Web Authentication API](https://www.w3.org/TR/webauthn-2/#sctn-api)**: Figure 1 Registration Flow
- **[Section 7.1 Registering a New Credential](https://www.w3.org/TR/webauthn-2/#sctn-registering-a-new-credential)**: 検証手順詳細
- **[Section 6.5 Authenticator Data](https://www.w3.org/TR/webauthn-2/#sctn-authenticator-data)**: authData構造

### FIDO仕様
- **[FIDO CTAP2.1](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html)**: 認証器プロトコル

### 関連ドキュメント
- **[basic-16: FIDO2・WebAuthn パスワードレス認証](basic-16-fido2-webauthn-passwordless.md)**: FIDO2/WebAuthn基礎概念
- **[basic-17: FIDO2・パスキー・Discoverable Credential](basic-17-fido2-passkey-discoverable-credential.md)**: Discoverable CredentialとConditional UI
- **[basic-18: FIDO2アーキテクチャ - RP・Webブラウザー・認証器の関係](basic-18-fido2-architecture-rp-browser-authenticator.md)**: 4つのコンポーネント概要
- **[basic-20: FIDO2 認証フローとインターフェース詳細](basic-20-fido2-authentication-flow-interface.md)**: 認証フロー詳細
- **[basic-21: FIDO2・WebAuthn仕様の変遷](basic-21-fido2-webauthn-level-specification-evolution.md)**: Level 1 → 2 → 3の変遷

---

**このドキュメントは、W3C WebAuthn Level 2仕様に基づいて作成されています。**
