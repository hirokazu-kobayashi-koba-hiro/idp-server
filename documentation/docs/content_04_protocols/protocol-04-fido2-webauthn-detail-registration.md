# FIDO2 / WebAuthn 登録フロー詳細ガイド

---

## このドキュメントについて

このドキュメントは、[FIDO2 / WebAuthn 認証フロー](protocol-04-fido2-webauthn.md)の**登録フロー**に特化した詳細ガイドです。

### 対象読者

- WebAuthn登録機能を実装する開発者
- 登録時のパラメータ設定を理解したいシステム管理者
- 登録時のトラブルシューティングが必要な運用担当者

### 前提知識

- [FIDO2 / WebAuthn 認証フロー](protocol-04-fido2-webauthn.md) - プロトコルの基本シーケンス
- WebAuthnの基本概念（公開鍵認証、認証器、Credential）

---

## このドキュメントで学べること

### 1. 登録チャレンジのパラメータと挙動

サーバーがブラウザに返す登録チャレンジ（`PublicKeyCredentialCreationOptions`）の各パラメータが、登録時のユーザー体験と保存されるCredentialにどう影響するかを説明します。

**対象パラメータ**: `authenticatorSelection`配下の`residentKey`、`userVerification`、`authenticatorAttachment`等

### 2. 登録フローの詳細ステップ

チャレンジ生成から、認証器での鍵ペア生成、サーバー検証、データベース保存までの各ステップを詳細に説明します。

### 3. 登録時のトラブルシューティング

よくある登録エラーの原因と解決策を提供します。

### 4. データベース保存内容

登録時に保存される情報（公開鍵、transports、rk、cred_protect等）の意味と用途を説明します。

---

## 1. 登録チャレンジのパラメータと挙動

サーバーが返す`PublicKeyCredentialCreationOptions`（登録チャレンジレスポンス）に含まれる各パラメータの説明です。

**登録チャレンジレスポンスの例**:
```json
{
  "challenge": "...",
  "rp": {...},
  "user": {...},
  "authenticatorSelection": {
    "residentKey": "required",        // ← 1.1で説明
    "userVerification": "required",   // ← 1.2で説明
    "authenticatorAttachment": "platform"  // ← 1.3で説明
  },
  "attestation": "none"  // ← 1.5で説明
}
```

これらのパラメータが、登録時のブラウザ動作・認証器の挙動・保存されるCredentialに影響します。

### 1.1 Resident Key (residentKey)

Resident Keyは、認証器がCredential IDとユーザー情報を内部保存するかどうかを決定します。

#### `required` の場合

**登録時の挙動**:
- 認証器に以下を保存:
  - Credential ID
  - User ID (user.id)
  - User Display Name
  - 秘密鍵
- 認証器が対応していない場合、登録失敗

**データベース保存値**:
- `rk` カラム: `true`

**認証時の動作**:
- ユーザー名入力不要（認証器がユーザー情報を保持）
- `allowCredentials=[]` で認証可能（Discoverable Credential）

**制約**:
- 認証器のストレージを消費（セキュリティキーは通常25-100個まで）
- Platform認証器（TouchID/FaceID）は実質無制限

---

#### `discouraged` の場合

**登録時の挙動**:
- 認証器には秘密鍵のみ保存（Credential IDやユーザー情報は保存しない）
- ストレージ節約

**データベース保存値**:
- `rk` カラム: `false`

**認証時の動作**:
- ユーザー特定が必要（ユーザー名入力またはパスワード認証後）
- `allowCredentials=[...]` で認証（サーバーがCredential IDを指定）

---

#### `preferred` の場合

**登録時の挙動**:
- 認証器の能力に依存
- Resident Key対応認証器 → `rk=true`
- 非対応認証器 → `rk=false`

**データベース保存値**:
- `rk` カラム: 認証器の決定に依存

**用途**: 柔軟な登録（デバイス依存を許容）

---

### 1.2 User Verification (userVerification)

User Verificationは、登録時にユーザー本人確認（生体認証やPIN）を要求するかを制御します。

#### `required` の場合

**登録時の挙動**:
- 認証器が以下のいずれかを要求:
  - 生体認証（TouchID/FaceID/指紋センサー）
  - PIN入力
  - パスワード入力（Windows Hello）
- 認証器が対応していない場合、登録失敗

**データベース保存値**:
- AuthenticatorDataの `UV flag` (0x04) が1にセットされたことを検証

**用途**: 高セキュリティ環境（FAPI準拠等）

---

#### `preferred` の場合

**登録時の挙動**:
- UV対応認証器 → PIN/生体認証を実行
- UV非対応認証器 → タップのみ（登録は成功）

**用途**: 汎用的な登録（デバイス依存を許容）

---

#### `discouraged` の場合

**登録時の挙動**:
- 認証器はUser Presenceのみ確認（タップ検出）
- PIN/生体認証は要求されない

**用途**: UX優先のサービス

---

### 1.3 Authenticator Attachment (authenticatorAttachment)

認証器のタイプを制限します。

#### `platform` の場合

**登録時の挙動**:
- ブラウザがPlatform認証器のみ提示
- TouchID/FaceID/Windows Hello
- 外部セキュリティキーは選択肢に表示されない

**データベース保存値**:
- `transports` カラム: `["internal"]`

**ユーザー体験**:
- "このデバイスでサインイン" プロンプト即表示

**用途**: デバイス内蔵認証優先のサービス

---

#### `cross-platform` の場合

**登録時の挙動**:
- ブラウザが外部認証器のみ提示
- USBセキュリティキー（YubiKey等）、NFCセキュリティキー
- Platform認証器は選択肢に表示されない

**データベース保存値**:
- `transports` カラム: `["usb"]`, `["nfc"]`, `["ble"]` 等

**ユーザー体験**:
- "セキュリティキーを挿入してください" プロンプト表示

**用途**: セキュリティキー必須のポリシー

---

#### 未指定（null）の場合

**登録時の挙動**:
- ブラウザが全ての認証器を提示
- ユーザーが選択可能

**データベース保存値**:
- `transports` カラム: 認証器の種類により異なる

**用途**: 多様なデバイス対応

---

### 1.4 Credential Protection (credProtect)

認証時にUser Verificationを要求するレベルを定義します（CTAP2.1 Extension）。

**重要**: クライアント側（JavaScript）で設定し、認証器が最終決定します。サーバー側では設定できません。

#### Level 1 (`userVerificationOptional`)

**登録時の設定**:
```javascript
navigator.credentials.create({
  publicKey: {
    extensions: {
      credProtect: 1,
      enforceCredentialProtectionPolicy: false
    }
  }
})
```

**データベース保存値**:
- `cred_protect` カラム: `1`

**認証時の動作**:
- User Verification不要（タップのみで認証可能）

---

#### Level 2 (`userVerificationOptionalWithCredentialIDList`)

**登録時の設定**:
```javascript
navigator.credentials.create({
  publicKey: {
    extensions: {
      credProtect: 2,
      enforceCredentialProtectionPolicy: false
    }
  }
})
```

**データベース保存値**:
- `cred_protect` カラム: `2`

**認証時の動作**:
- `allowCredentials` 指定あり → UV不要
- `allowCredentials` 空配列 → UV必須（パスワードレス時）

---

#### Level 3 (`userVerificationRequired`)

**登録時の設定**:
```javascript
navigator.credentials.create({
  publicKey: {
    extensions: {
      credProtect: 3,
      enforceCredentialProtectionPolicy: false
    }
  }
})
```

**データベース保存値**:
- `cred_protect` カラム: `3`

**認証時の動作**:
- 常にUser Verification必須（最高セキュリティ）

---

#### enforceCredentialProtectionPolicy

**`true` の場合**:
- 認証器が要求レベルをサポートしない場合、登録失敗

**`false` の場合**:
- 認証器が可能な範囲でダウングレード（Level 2 → Level 1）
- 登録は成功

---

### 1.5 Attestation

Attestation形式により、認証器の信頼性評価が可能になります（企業向け機能）。

#### `none` の場合（デフォルト）

**登録時の挙動**:
- Attestation検証をスキップ
- すべての認証器を許可

**データベース保存値**:
- `attestation_type` カラム: `"none"`

**用途**: 一般的なサービス

---

#### `indirect` の場合

**登録時の挙動**:
- 認証器がAttestation情報を匿名化して返却
- サーバーはAttestation形式を検証

**データベース保存値**:
- `attestation_type` カラム: `"packed"`, `"fido-u2f"` 等

**用途**: デバイス種別の識別が必要なサービス

---

#### `direct` の場合

**登録時の挙動**:
- 認証器が詳細なAttestation情報を返却
- サーバーはAAGUIDと証明書を検証

**データベース保存値**:
- `attestation_type` カラム: `"packed"`, `"fido-u2f"` 等
- `aaguid` カラム: 認証器の固有ID

**用途**: FIDO認定デバイスのみ許可するポリシー

**セキュリティ考慮事項**:
- ユーザープライバシーへの影響（デバイストラッキング可能）

---

## 2. 登録フローの詳細ステップ

### フェーズ1: チャレンジ取得（クライアント → サーバー）

**クライアント側処理**:
1. ユーザーが "パスキーを登録" ボタンをクリック
2. JavaScriptがサーバーに登録チャレンジをリクエスト

**HTTPリクエスト例**:
```http
POST /{tenant-id}/v1/authentications/{transaction-id}/fido2-registration-challenge
Content-Type: application/json
Authorization: Bearer {access_token}

{
  "username": "user@example.com"
}
```

**サーバー側処理**:
1. 32バイト以上のランダムチャレンジを生成
2. セッションに保存（1回のみ有効）
3. ユーザー情報（user.id, user.name, user.displayName）を準備
4. パラメータ（residentKey, userVerification等）を設定

**HTTPレスポンス例**:
```json
{
  "challenge": "random-base64url-string",
  "rp": {
    "name": "IDP Server",
    "id": "example.com"
  },
  "user": {
    "id": "dXNlcjEyMw",
    "name": "user@example.com",
    "displayName": "User Name"
  },
  "pubKeyCredParams": [
    {"type": "public-key", "alg": -7},   // ES256
    {"type": "public-key", "alg": -257}  // RS256
  ],
  "timeout": 120000,
  "authenticatorSelection": {
    "residentKey": "required",
    "userVerification": "required",
    "authenticatorAttachment": "platform"
  },
  "attestation": "none"
}
```

---

### フェーズ2: 認証器操作（ブラウザ → 認証器）

**JavaScriptによる呼び出し**:
```javascript
const credential = await navigator.credentials.create({
  publicKey: challengeResponse
});
```

**ブラウザの処理**:
1. `authenticatorAttachment` に基づいて認証器をフィルタリング
2. 利用可能な認証器にCTAP2コマンド送信
3. 認証器の応答を待機

**認証器の処理**:
1. **ユーザー検証** (userVerification="required"の場合):
   - TouchID/FaceID/Windows Helloプロンプト表示
   - PIN入力要求（セキュリティキー）
2. **鍵ペア生成**:
   - 楕円曲線暗号鍵ペア生成（ES256: P-256曲線）
   - Credential ID生成（ランダム32バイト）
   - 秘密鍵を認証器内部に安全保存（抽出不可）
3. **Resident Key保存** (residentKey="required"の場合):
   - Credential ID + User ID + User Display Name を保存
4. **Attestation作成**:
   - AuthenticatorData構築
   - Attestation Statement作成
5. **署名カウンタ初期化**:
   - signCount=0

**Attestation Response構造**:
```javascript
{
  id: "base64url-credential-id",
  rawId: ArrayBuffer,
  type: "public-key",
  response: {
    attestationObject: ArrayBuffer,  // CBOR形式
    clientDataJSON: ArrayBuffer      // JSON形式
  }
}
```

---

### フェーズ3: サーバー検証・保存（クライアント → サーバー）

**HTTPリクエスト例**:
```http
POST /{tenant-id}/v1/authentications/{transaction-id}/fido2-registration
Content-Type: application/json
Authorization: Bearer {access_token}

{
  "response": {
    "attestationObject": "base64url-string",
    "clientDataJSON": "base64url-string"
  },
  "id": "credential-id"
}
```

**サーバー側検証処理**:

1. **clientDataJSON検証**:
   - Challenge一致確認（セッションの保存値と比較）
   - Origin検証（フィッシング対策）
   - Type確認（`webauthn.create`）

2. **AuthenticatorData検証**:
   - RP ID Hash検証（SHA-256(rpId)）
   - Flags確認（UP bit, UV bit, AT bit）
   - User Verification要件確認

3. **公開鍵抽出**:
   - AttestedCredentialDataから公開鍵を抽出
   - COSE形式（ES256: `{1: 2, 3: -7, -1: 1, -2: x, -3: y}`）

4. **Attestation検証**（attestation="direct"の場合）:
   - Attestation Statementの署名検証
   - AAGUID検証（許可リストと照合）

5. **Transports抽出**:
   - 認証器が返却したTransports情報を抽出

6. **credProtect抽出**（Extensions指定時）:
   - Authenticator Extensionsから抽出

**データベース保存**:

登録成功後、以下の情報がデータベースに保存されます：

| 項目 | 例 | 意味・用途 |
|------|-----|----------|
| `id` | `"abc123..."` | Credential ID（認証時に使用） |
| `rk` | `true` / `false` | ユーザー名入力の要否（true=不要） |
| `cred_protect` | `1` / `2` / `3` / `NULL` | 認証時のUV要求レベル |
| `transports` | `["internal"]` | ブラウザUI最適化用 |
| `sign_count` | `0` | クローン検出用（初期値0） |
| `attested_credential_data` | `"Base64URL..."` | 公開鍵を含むバイナリ（認証時に使用） |

**HTTPレスポンス例**:
```http
HTTP/1.1 201 Created
Content-Type: application/json

{
  "credentialId": "base64url-credential-id",
  "status": "registered"
}
```

---

## 3. 登録時のトラブルシューティング

### 3.1 "認証器が見つかりません"

#### 原因1: authenticatorAttachment制約

**症状**:
- ユーザーがPlatform認証器（TouchID）を持っているのに、"外部セキュリティキーを挿入" と表示

**確認方法**:
```json
// サーバーレスポンス確認
{
  "authenticatorSelection": {
    "authenticatorAttachment": "cross-platform"  // ← Platform認証器を除外
  }
}
```

**解決策**:
```json
// 制限を解除
{
  "authenticatorSelection": {
    "authenticatorAttachment": null  // または "platform"
  }
}
```

---

#### 原因2: Resident Key非対応認証器

**症状**:
- 古いセキュリティキーで登録時にエラー

**確認方法**:
```json
// サーバー設定確認
{
  "authenticatorSelection": {
    "residentKey": "required"  // ← 非対応認証器は登録不可
  }
}
```

**解決策**:
```json
// Resident Key要件を緩和
{
  "authenticatorSelection": {
    "residentKey": "preferred"  // または "discouraged"
  }
}
```

---

### 3.2 "ユーザー検証に失敗しました"

#### 原因: userVerification="required"だが認証器非対応

**症状**:
- UV非対応セキュリティキーで登録時にエラー

**確認方法**:
```json
// サーバー設定確認
{
  "authenticatorSelection": {
    "userVerification": "required"  // ← UV必須
  }
}
```

**解決策**:
```json
// UV要件を緩和
{
  "authenticatorSelection": {
    "userVerification": "preferred"  // または "discouraged"
  }
}
```

---

### 3.3 credProtectが保存されない

#### 原因: クライアント側Extension未設定

**症状**:
- データベースの`cred_protect`カラムが常にNULL

**確認方法**:
```sql
SELECT id, cred_protect
FROM webauthn_credentials
WHERE id = 'credential-id';

-- 結果: cred_protect = NULL
```

**解決策（JavaScript）**:
```javascript
navigator.credentials.create({
  publicKey: {
    ...challengeOptions,
    extensions: {
      credProtect: 2,
      enforceCredentialProtectionPolicy: false
    }
  }
})
```

**注意**: サーバー側では設定不可（クライアント側Extensionのため）

---

### 3.4 Transportsが保存されない

#### 原因: サーバー側でTransports抽出漏れ

**症状**:
- データベースの`transports`カラムがNULL

**確認方法**:
```sql
SELECT id, transports
FROM webauthn_credentials
WHERE id = 'credential-id';

-- 結果: transports = NULL
```

**解決策**:
- Transports抽出・保存の実装確認
- 既存Credentialの場合: 手動UPDATE
  ```sql
  UPDATE webauthn_credentials
  SET transports = '["internal"]'::jsonb
  WHERE id = 'credential-id';
  ```

---

### 3.5 Origin検証エラー

#### 原因: rpIdとOriginの不整合

**症状**:
```
OriginVerificationException: Origin mismatch
```

**確認方法**:
```json
// サーバー設定確認
{
  "rpId": "example.com",
  "origin": "https://example.com"
}

// ブラウザのOrigin確認
console.log(window.location.origin);
// 出力: "https://app.example.com"  // ← 不一致
```

**解決策**:
```json
// allowedOriginsに追加
{
  "allowedOrigins": [
    "https://example.com",
    "https://app.example.com"
  ]
}
```

---

## 4. 登録後の確認方法

### 4.1 登録されたCredentialの確認

**ユーザーの全Credential一覧**:
```sql
SELECT
  id,
  username,
  rk,
  cred_protect,
  transports,
  created_at
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123'
ORDER BY created_at DESC;
```

**期待される結果例**:
```
id: "abc123"
username: "user@example.com"
rk: true
cred_protect: 2
transports: ["internal"]
created_at: 2024-01-15 10:30:00
```

---

### 4.2 ユーザー名入力要否の確認

**確認方法**:
```sql
SELECT id, rk, username
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123';
```

**`rk`（Resident Key）の意味**:
- `true`: ユーザー名入力不要
  - 認証器がCredential ID + ユーザー情報を保存済み
  - `allowCredentials=[]`で認証可能（Discoverable Credential）
- `false`: ユーザー特定が必要
  - 認証時にユーザー名入力またはパスワード認証が必要
  - `allowCredentials=[...]`で認証

---

### 4.3 認証時のUV要求レベルの確認

**確認方法**:
```sql
SELECT id, cred_protect
FROM webauthn_credentials
WHERE id = 'credential-id';
```

**`cred_protect`の意味**:
- `1`: UV不要（タップのみで認証可能）
- `2`: 条件付きUV（パスワードレス時のみUV必須）
- `3`: 常にUV必須（最高セキュリティ）
- `NULL`: 未設定（Level 1相当）

**認証時の影響**:
- `cred_protect=3` → 認証時、常にPIN/生体認証が必要

---

### 4.4 ブラウザUX最適化の確認

**確認方法**:
```sql
SELECT id, transports
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123';
```

**`transports`の意味**:
- `["internal"]`: TouchID/FaceID/Windows Hello
- `["usb"]`: USBセキュリティキー
- `["nfc"]`: NFCセキュリティキー
- `["usb", "nfc"]`: USB/NFC両対応（YubiKey 5 NFC）
- `["hybrid"]`: スマートフォン連携（QRコード + Bluetooth）

**認証時の用途**:
- `["internal"]` → TouchID/FaceIDプロンプト即表示
- `["usb"]` → "USBキーを挿入" メッセージ表示

**期待される結果例**:
```
id: "abc123", transports: ["internal"]
id: "xyz789", transports: ["usb", "nfc"]
```

---

## 5. セキュリティ考慮事項

### 5.1 Origin検証

**目的**: フィッシング攻撃防止

**仕組み**:
1. ブラウザが`clientDataJSON.origin`を自動設定
2. サーバーが設定済み`allowedOrigins`と比較
3. 不一致の場合、登録拒否

**攻撃シナリオ**:
```
フィッシングサイト（https://evil.com）で登録を試みる
  ↓
ブラウザが clientDataJSON.origin = "https://evil.com" をセット
  ↓
サーバーが expectedOrigin = "https://example.com" と比較
  ↓
不一致 → 登録失敗
```

**重要**: ブラウザがOriginを設定するため、JavaScriptで改ざん不可

---

### 5.2 Challenge検証

**目的**: 再利用攻撃防止

**仕組み**:
1. サーバーが32バイト以上のランダムチャレンジを生成
2. セッションに保存（1回のみ有効）
3. 登録リクエスト受信時、`clientDataJSON.challenge`と比較
4. 検証成功後、セッションからチャレンジを削除

**有効期限**:
- デフォルト: 2分（120秒）
- FAPI準拠: 5分（300秒）推奨

---

### 5.3 Attestation検証（企業向け）

**目的**: デバイス信頼性評価、FIDO認定デバイスのみ許可

**検証項目**:
1. **Attestation形式確認**:
   - `none`: 検証スキップ
   - `packed`: Packed Attestation Statement検証
   - `fido-u2f`: FIDO U2F Attestation検証
2. **AAGUID検証**:
   - 許可リストと照合
   - 例: YubiKey 5 NFCのみ許可
3. **証明書チェーン検証**（direct attestationの場合）:
   - FIDO Metadata Serviceと照合

**実装例**:
```sql
-- YubiKey 5 NFCのAAGUID
-- cb69481e-8ff7-4039-93ec-0a2729a154a8

SELECT id, aaguid
FROM webauthn_credentials
WHERE id = 'credential-id';

-- AAGUIDが許可リストにない場合、登録拒否
```

**プライバシー考慮事項**:
- `attestation="direct"` は詳細なデバイス情報を含む
- ユーザートラッキング可能 → プライバシーポリシー明記必須

---

## 6. テナント設定例

### 6.1 ユーザー名入力不要のログイン向け設定

```json
{
  "rpId": "example.com",
  "origin": "https://example.com",
  "timeout": 120000,
  "authenticatorSelection": {
    "residentKey": "required",
    "userVerification": "required",
    "authenticatorAttachment": "platform"
  },
  "attestation": "none"
}
```

**期待される動作**:
- TouchID/FaceIDで登録
- 認証時にユーザー名入力不要（`allowCredentials=[]`で認証可能）
- 高セキュリティ（UV必須）

**補足**: いわゆる「パスワードレスログイン」の設定

---

### 6.2 2要素認証向け設定

```json
{
  "authenticatorSelection": {
    "residentKey": "discouraged",
    "userVerification": "discouraged",
    "authenticatorAttachment": "cross-platform"
  },
  "attestation": "none"
}
```

**期待される動作**:
- セキュリティキーで登録
- パスワード認証後にセキュリティキータップ
- ストレージ節約（Resident Key不要）

---

### 6.3 FAPI準拠向け設定

```json
{
  "authenticatorSelection": {
    "residentKey": "required",
    "userVerification": "required"
  },
  "attestation": "direct",
  "timeout": 300000
}
```

**追加実装**:
- AAGUID許可リスト管理
- Attestation Statement検証
- FIDO Metadata Service連携

---

## 参考資料

### 仕様書
- [W3C WebAuthn Level 2 Recommendation](https://www.w3.org/TR/webauthn-2/)
- [FIDO CTAP2.1 Specification](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html)
- [CTAP2.1 credProtect Extension](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html#sctn-credProtect-extension)

### ライブラリ
- [WebAuthn4j GitHub](https://github.com/webauthn4j/webauthn4j)
- [WebAuthn4j Documentation](https://webauthn4j.github.io/webauthn4j/en/)

### 関連ドキュメント
- [FIDO2 / WebAuthn プロトコル概要](protocol-04-fido2-webauthn.md) - プロトコルの基本フロー
- [FIDO2 / WebAuthn 詳細ガイド](protocol-04-fido2-webauthn-detail.md) - 登録・認証全体の詳細
- [認証設定ガイド](../content_06_developer-guide/05-configuration/authn/webauthn.md) - テナント設定方法
