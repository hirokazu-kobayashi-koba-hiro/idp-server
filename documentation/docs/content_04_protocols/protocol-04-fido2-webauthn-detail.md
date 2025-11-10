# FIDO2 / WebAuthn 詳細ガイド

---

## このドキュメントについて

このドキュメントは、[FIDO2 / WebAuthn 認証フロー](protocol-04-fido2-webauthn.md)の詳細ガイドです。

### 対象読者

- WebAuthn認証を導入・運用するシステム管理者
- パラメータ設定による挙動の違いを理解したい開発者
- トラブルシューティングが必要な運用担当者

### 前提知識

このドキュメントを読む前に、以下を理解していることを推奨します：

- [FIDO2 / WebAuthn 認証フロー](protocol-04-fido2-webauthn.md) - プロトコルの基本シーケンスとユースケース
- WebAuthnの基本概念（公開鍵認証、認証器、Credential）

---

## このドキュメントで学べること

### 1. パラメータごとの挙動の違い

設定値（`residentKey`、`userVerification`等）がユーザー体験やブラウザ動作にどう影響するかを詳細に説明します。

**例**: `residentKey: "required"` → ユーザー名入力不要、`residentKey: "discouraged"` → ユーザー名入力必須

### 2. リクエスト内容による動作変化

サーバーがレスポンスする`allowCredentials`の指定パターンにより、ブラウザの動作が変わります。

- **空配列 `[]` の場合**:
  - ユーザー特定前の認証（ユーザー名不明）
  - ブラウザが全認証器に問い合わせ、Discoverable Credentialを探索
  - **用途**: パスワードレスログイン

- **1個以上 `[...]` の場合**:
  - ユーザー特定済みの認証（ユーザー名判明済み）
  - サーバーが特定ユーザーのCredential IDを指定
  - **用途**: パスワード認証後のWebAuthn、またはユーザー名入力後のWebAuthn

**注意**: 2要素認証（パスワード + WebAuthn）かパスワードレスかは、認証フロー全体の設計によります。`allowCredentials`の指定パターンとは直接関係ありません。

### 3. 組み合わせパターン

実用的な設定例（パスワードレスログイン、FAPI準拠認証等）と期待される挙動を提示します。

### 4. セキュリティ機構の詳細

Origin検証、Challenge検証、signCount検証、ユーザー列挙攻撃対策の仕組みを説明します。

### 5. トラブルシューティング

よくある問題の原因・確認方法・解決策を提供します。

**例**: "認証器が見つかりません" → 原因3パターンとそれぞれの解決策

### 6. データベース確認方法

Credential情報や認証履歴をSQLで確認する方法を提供します。

### 7. テナント設定による挙動制御

`rpId`、`origin`、`timeout`、認証ポリシー連携の設定と挙動を説明します。

---

## ドキュメント構成

| セクション | 内容 |
|----------|------|
| **1. パラメータによる挙動の違い** | residentKey, userVerification, authenticatorAttachment, credProtect, transports |
| **2. リクエスト内容による挙動の違い** | allowCredentials, username, Extensions |
| **3. 組み合わせパターンとユーザー体験** | 4つの実用パターン |
| **4. セキュリティ関連の挙動** | Origin/Challenge/signCount検証、ユーザー列挙攻撃対策 |
| **5. トラブルシューティング** | 6つの典型的問題と解決策 |
| **6. データベース確認方法** | SQLクエリ例 |
| **7. テナント設定による挙動制御** | rpId, origin, timeout, 認証ポリシー |

---

## 1. パラメータによる挙動の違い

### 1.1 Resident Key (residentKey)

Resident Keyは、認証器がCredential IDとユーザー情報を内部保存するかどうかを決定します。

#### `required` の場合の挙動

**ユーザー体験**:
- ユーザー名入力が不要
- 認証器選択のみでログイン可能（パスワードレス）

**技術的な動作**:
- 認証器が以下を内部保存:
  - Credential ID
  - User ID (user.id)
  - User Display Name
  - 秘密鍵
- サーバーは `allowCredentials=[]` (空配列) でレスポンス
- ブラウザが全認証器に `rpId` で Discoverable Credential を問い合わせ
- 複数アカウントがある場合、ブラウザがアカウント選択UIを表示

**制約**:
- 認証器のストレージを消費（セキュリティキーは通常25-100個まで）
- Platform認証器（TouchID/FaceID）は実質無制限

**ユースケース**: パスワードレスログイン

---

#### `discouraged` の場合の挙動

**ユーザー体験**:
- ユーザー名入力が必須
- ユーザー名 + 認証器タップでログイン

**技術的な動作**:
- 認証器はCredential IDとユーザー情報を保存しない（秘密鍵のみ保存）
- サーバーは以下のフローを実行:
  1. ユーザー名からCredential ID一覧を取得
  2. `allowCredentials` に1個以上のCredential IDを列挙
  3. ブラウザが該当Credentialを持つ認証器を探索
- ストレージ節約（Credential ID保存不要）

**ユースケース**: 2要素認証（パスワード + セキュリティキー）

---

#### `preferred` の場合の挙動

**技術的な動作**:
- 認証器の能力に依存
- Resident Key対応認証器 → `rk=true` として動作
- 非対応認証器 → `rk=false` として動作
- サーバーは両方のパターンに対応する必要あり

**ユースケース**: 柔軟な認証（デバイス依存を許容）

---

### 1.2 User Verification (userVerification)

User Verificationは、認証器がユーザー本人確認（生体認証やPIN）を実行するかを制御します。

#### `required` の場合の挙動

**ユーザー体験**:
- 毎回PIN入力または生体認証が必要
- 認証器タップだけでは認証完了しない

**技術的な動作**:
- 認証器が以下のいずれかを実行:
  - 生体認証（TouchID/FaceID/指紋センサー）
  - PIN入力
  - パスワード入力（Windows Hello）
- AuthenticatorDataの `UV flag` (0x04) が1にセット
- サーバーはUV flagを検証し、0の場合は認証失敗

**仕様準拠**:
- FAPI 1.0 Advanced Section 5.2.2.2 要件
- WebAuthn Level 2 Section 5.8.6

**ユースケース**: 金融グレード認証、高セキュリティ環境

---

#### `preferred` の場合の挙動

**技術的な動作**:
- 認証器の能力に応じて動作:
  - UV対応認証器 → PIN/生体認証を実行
  - UV非対応認証器 → タップのみ（UP flagのみセット）
- サーバーはUV flagが0でも認証成功

**ユーザー体験**:
- デバイスによって体験が異なる
- TouchID搭載デバイス → 生体認証要求
- 古いセキュリティキー → タップのみ

**ユースケース**: 汎用的な認証（デバイス依存を許容）

---

#### `discouraged` の場合の挙動

**ユーザー体験**:
- 認証器タップのみで認証完了
- PIN/生体認証は要求されない

**技術的な動作**:
- 認証器はUser Presenceのみ確認（タップ検出）
- AuthenticatorDataの `UP flag` (0x01) のみセット
- UV flagは0のまま

**セキュリティトレードオフ**:
- 利便性向上（タップのみ）
- セキュリティ低下（デバイス盗難時のリスク）

**ユースケース**: 低リスク操作、UX優先のサービス

---

### 1.3 Authenticator Attachment (authenticatorAttachment)

認証器のタイプを制限します。

#### `platform` の場合の挙動

**ブラウザが提示する認証器**:
- TouchID（macOS/iOS）
- FaceID（iOS/iPadOS）
- Windows Hello（Windows）
- Android生体認証

**ユーザーに表示されるUI**:
- "このデバイスでサインイン"
- 即座に生体認証プロンプト表示
- 外部セキュリティキーは選択肢に表示されない

**技術的な動作**:
- `transports: ["internal"]` の認証器のみ探索
- Cross-platform認証器は無視

**ユースケース**: デバイス内蔵認証優先のサービス

---

#### `cross-platform` の場合の挙動

**ブラウザが提示する認証器**:
- USBセキュリティキー（YubiKey等）
- NFCセキュリティキー
- Bluetoothセキュリティキー

**ユーザーに表示されるUI**:
- "セキュリティキーを挿入してください"
- USBポートやNFCリーダーのアイコン
- Platform認証器は選択肢に表示されない

**技術的な動作**:
- `transports: ["usb", "nfc", "ble"]` の認証器のみ探索
- Platform認証器は無視

**ユースケース**: セキュリティキー必須のポリシー

---

#### 未指定（null）の場合の挙動

**ブラウザが提示する認証器**:
- 全ての認証器（Platform + Cross-platform）

**ユーザーに表示されるUI**:
- ブラウザが選択肢を提示:
  - "このデバイスを使用"
  - "セキュリティキー"
  - "スマートフォン"（Hybrid）

**ユーザー体験**:
- ユーザーが認証方法を選択可能
- 最も柔軟

**ユースケース**: 多様なデバイス対応（Microsoft、Google等）

---

### 1.4 Credential Protection (credProtect)

認証器がCredentialの使用時にUser Verificationを要求するレベルを定義します（CTAP2.1 Extension）。

**重要**: このパラメータはクライアント側で設定し、認証器が最終決定します。

#### Level 1 (`userVerificationOptional`) の挙動

**動作**:
- User Verification不要
- タップのみでCredentialアクセス可能

**ユースケース**: 低リスク操作

---

#### Level 2 (`userVerificationOptionalWithCredentialIDList`) の挙動

**動作**:
- `allowCredentials` に Credential ID 指定あり → UV不要
- `allowCredentials` が空配列 → UV必須（Discoverable Credential使用時）

**パスワードレス時の影響**:
- Resident Key使用時は必ずPIN/生体認証が要求される

**ユースケース**: バランス型セキュリティ

---

#### Level 3 (`userVerificationRequired`) の挙動

**動作**:
- 常にUser Verification必須
- タップのみでは認証完了しない

**ユーザー体験**:
- 2FA使用時も毎回PIN/生体認証が必要
- 最高セキュリティ

**ユースケース**: FAPI準拠、金融サービス

---

#### クライアント設定例

```javascript
navigator.credentials.create({
  publicKey: {
    extensions: {
      credProtect: 2,  // Level 2を要求
      enforceCredentialProtectionPolicy: false  // ダウングレード許可
    }
  }
})
```

**enforceCredentialProtectionPolicy**:
- `true`: 認証器が要求レベルをサポートしない場合、登録失敗
- `false`: 認証器が可能な範囲でダウングレード（Level 2 → Level 1）

---

### 1.5 Transports

認証器がサポートする通信方式を示します（認証器が自動設定）。

#### `["internal"]` の場合の挙動

**ブラウザの動作**:
- Platform認証器プロンプトを即座に表示
- TouchID/FaceID/Windows Helloを起動
- USB/NFCセキュリティキー探索をスキップ

**ユーザー体験**:
- 最速（認証器探索不要）
- デバイス生体認証のみ

---

#### `["usb"]` の場合の挙動

**ブラウザの動作**:
- "USBセキュリティキーを挿入してください" メッセージ表示
- USBポート監視を開始
- Platform認証器は無視

**ユーザー体験**:
- USBキー挿入 → タップ

---

#### `["hybrid"]` の場合の挙動

**ブラウザの動作**:
- QRコード表示
- Bluetooth Low Energy (BLE) リスニング開始
- スマートフォン連携待機

**ユーザー体験（初回）**:
1. PCでQRコード表示
2. スマホでQRコード読み取り
3. スマホで生体認証
4. PCでログイン完了

**ユーザー体験（2回目以降）**:
1. Bluetoothで自動接続（QRコード不要）
2. スマホで生体認証
3. PCでログイン完了

**仕様準拠**: CTAP 2.2 Hybrid Transport

---

#### 複数指定時の挙動 `["usb", "nfc"]`

**ブラウザの動作**:
- 並列探索:
  - USBポート監視
  - NFCリーダー監視
- 先に検出された認証器を使用

**ユーザー体験**:
- YubiKey 5 NFCの場合:
  - USB挿入でも認証可能
  - NFCタップでも認証可能

---

## 2. リクエスト内容による挙動の違い

### 2.1 allowCredentials指定パターン

#### パターン1: 1個以上のCredential ID指定

**サーバーレスポンス例**:
```json
{
  "challenge": "random-base64url-string",
  "allowCredentials": [
    {
      "type": "public-key",
      "id": "credential-id-abc",
      "transports": ["internal"]
    },
    {
      "type": "public-key",
      "id": "credential-id-xyz",
      "transports": ["usb", "nfc"]
    }
  ]
}
```

**ブラウザの認証器探索方法**:
1. 各Credential IDについて、対応する認証器を探索
2. `transports` でフィルタリング:
   - `["internal"]` → Platform認証器のみ
   - `["usb"]` → USB認証器のみ
3. Credential IDを持つ認証器を発見 → 即座に使用

**ユーザーに表示されるUI**:
- Credential IDが `["internal"]` → TouchID/FaceIDプロンプト即表示
- Credential IDが `["usb"]` → "USBキーを挿入" メッセージ
- 該当Credentialがない → "認証器が見つかりません" エラー

**ユーザー名入力の要否**:
- 必須（サーバーがCredential ID一覧を取得するため）

**フロー**:
```
1. ユーザーが "user@example.com" 入力
   ↓
2. サーバーがCredential ID一覧取得
   SELECT id, transports FROM webauthn_credentials WHERE user_id = ?
   ↓
3. allowCredentials構築してレスポンス
   ↓
4. ブラウザが該当Credentialを持つ認証器を探索
```

---

#### パターン2: 空配列 `[]`

**サーバーレスポンス例**:
```json
{
  "challenge": "random-base64url-string",
  "allowCredentials": []
}
```

**ブラウザの認証器探索方法**:
1. 全認証器に `rpId` でDiscoverable Credential問い合わせ
2. Platform認証器が保存済みCredential一覧を返却:
   ```javascript
   [
     {credentialId: "abc", userId: "user1", displayName: "User 1"},
     {credentialId: "xyz", userId: "user2", displayName: "User 2"}
   ]
   ```
3. ブラウザがアカウント選択UI表示

**ユーザーに表示されるUI**:
```
┌─────────────────────────────────────┐
│ ログインするアカウントを選択        │
├─────────────────────────────────────┤
│ ● user1@example.com (TouchID)      │
│ ○ user2@example.com (YubiKey)      │
│ ○ user3@example.com (iPhone)       │
└─────────────────────────────────────┘
```

**Resident Key必須の理由**:
- `allowCredentials=[]` の場合、認証器は Resident Key (rk=true) のCredentialのみ返却
- rk=false のCredentialは検出不可（Credential ID保存なしのため）

**ユーザー名入力の要否**:
- 不要（認証器がユーザー情報を保持）

**フロー**:
```
1. ユーザーが "パスキーでログイン" クリック
   ↓
2. サーバーが allowCredentials=[] でレスポンス
   ↓
3. ブラウザが全認証器に問い合わせ
   ↓
4. アカウント選択UI表示
   ↓
5. ユーザーがアカウント選択 + 生体認証
```

---

#### パターン3: 省略（null）

**動作**: パターン2（空配列）と同じ

---

### 2.2 username指定の有無

#### username指定あり

**サーバー側の処理フロー**:
1. ユーザー名からユーザーIDを取得
2. ユーザーのCredential一覧をデータベースから取得
3. allowCredentialsを構築してレスポンス

**ユーザー列挙攻撃対策**:
- **脆弱な実装**: ユーザー不在時に空配列を返却 → ユーザー存在判定が可能
- **安全な実装**: ユーザー不在時もダミーCredentialを返却 → 存在判定不可

**フロー**: 2要素認証（ユーザー名 + セキュリティキー）

---

#### username指定なし

**前提条件**:
- Resident Key (rk=true) のCredential必須
- `allowCredentials=[]` でレスポンス

**フロー**: パスワードレス認証

---

### 2.3 登録時のExtensions

#### credProtect指定

**クライアント側設定**:
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

**認証器の最終決定**:
- 認証器が要求レベルをサポート → Level 2で登録
- 認証器が非サポート + `enforce=false` → Level 1にダウングレード
- 認証器が非サポート + `enforce=true` → 登録失敗

**サーバー側の処理**:
- 認証器が決定したcredProtectをAttestation Responseから抽出
- データベースに保存（認証時の検証用）

---

#### Extensions未指定

**動作**:
- 認証器のデフォルト動作
- 多くの場合、credProtect Level 1相当

---

## 3. 組み合わせパターンとユーザー体験

### 3.1 パスワードレスログイン（推奨パターン）

**目的**: ユーザー名入力不要 + 強力な本人確認

**設定例**:
```json
{
  "authenticatorSelection": {
    "residentKey": "required",
    "userVerification": "required",
    "authenticatorAttachment": "platform"
  }
}
```

**期待される挙動**:
1. ユーザーが "ログイン" ボタンクリック
2. サーバーが `allowCredentials=[]` でレスポンス
3. ブラウザが即座にTouchID/FaceIDプロンプト表示
4. 生体認証完了 → ログイン完了

**データフロー**:
```
認証器 (Platform)
  ↓ Discoverable Credential保存
  ↓ rk=true, transports=["internal"]
サーバー
  ↓ allowCredentials=[]
ブラウザ
  ↓ Platform認証器のみ探索
  ↓ アカウント選択UI（複数ある場合）
ユーザー
  ↓ 生体認証
完了
```

**適用例**: 金融アプリ、医療ポータル、エンタープライズSaaS

---

### 3.2 2要素認証（GitHub/Google方式）

**目的**: パスワード + セキュリティキーの2段階認証

**設定例**:
```json
{
  "authenticatorSelection": {
    "residentKey": "discouraged",
    "userVerification": "discouraged",
    "authenticatorAttachment": "cross-platform"
  }
}
```

**期待される挙動**:
1. ユーザーがユーザー名 + パスワード入力
2. サーバーがCredential ID一覧を取得し `allowCredentials` に列挙
3. ブラウザが "USBセキュリティキーを挿入" プロンプト表示
4. ユーザーがYubiKey挿入 → タップ
5. ログイン完了（PIN/生体認証不要）

**データフロー**:
```
ユーザー
  ↓ ユーザー名入力
サーバー
  ↓ SELECT id, transports FROM webauthn_credentials WHERE user_id = ?
  ↓ allowCredentials=[{id: "abc", transports: ["usb"]}]
ブラウザ
  ↓ USB認証器のみ探索
  ↓ Credential "abc" を持つYubiKey検出
ユーザー
  ↓ YubiKeyタップ（UV不要）
完了
```

**適用例**: GitHub、Google、AWS（セキュリティキー2FA）

---

### 3.3 FAPI準拠認証

**目的**: 金融グレードの認証（FAPI 1.0 Advanced準拠）

**設定例**:
```json
{
  "authenticatorSelection": {
    "residentKey": "required",
    "userVerification": "required"
  },
  "extensions": {
    "credProtect": 3
  }
}
```

**期待される挙動**:
1. ユーザーが "ログイン" ボタンクリック
2. ブラウザが認証器選択UI表示（Platform + Cross-platform）
3. ユーザーが認証器選択
4. **必ずPIN/生体認証が要求される**（credProtect=3により強制）
5. ログイン完了

**FAPI準拠ポイント**:
- `userVerification: "required"` - FAPI 1.0 Advanced Section 5.2.2.2 要件
- `credProtect: 3` - Credential再利用時も常にUV要求
- TLS 1.2以上
- MTLS（Mutual TLS）推奨

**適用例**: オープンバンキングAPI、決済サービス、証券取引アプリ

---

### 3.4 スマートフォン連携認証

**目的**: PC/タブレットでスマホ生体認証を利用

**設定例**:
```json
{
  "authenticatorSelection": {
    "residentKey": "required",
    "userVerification": "required"
  }
}
```

**期待される挙動（初回）**:
1. ユーザーが "ログイン" ボタンクリック
2. ブラウザがQRコード表示
3. ユーザーがスマートフォンでQRコード読み取り
4. スマホで生体認証（TouchID/FaceID）
5. PCでログイン完了

**期待される挙動（2回目以降）**:
1. ユーザーが "ログイン" ボタンクリック
2. Bluetooth経由でスマホ自動接続（QRコード不要）
3. スマホで生体認証通知
4. PCでログイン完了

**Transports**: `["hybrid"]` が登録時に自動設定される

**適用例**: Google Passkeys、Apple Passkeys、パスワードマネージャー

---

## 4. セキュリティ関連の挙動

### 4.1 Origin検証

**検証される値**:
- `clientDataJSON.origin`: ブラウザが自動設定（例: `https://example.com`）
- `clientDataJSON.type`: `webauthn.get` (認証) または `webauthn.create` (登録)
- `clientDataJSON.challenge`: サーバーが生成したチャレンジ

**サーバー側検証**:
- WebAuthn4jが `clientDataJSON.origin` と設定済み `expectedOrigin` を比較
- 不一致の場合、認証/登録を拒否

**検証失敗時の挙動**:
- 認証/登録リクエスト拒否
- エラーレスポンス: `{"error": "origin_mismatch"}`

**フィッシング対策の仕組み**:
- フィッシングサイト（`https://evil.com`）で認証を試みる
- ブラウザが `clientDataJSON.origin = "https://evil.com"` をセット
- サーバーが `expectedOrigin = "https://example.com"` と比較
- 不一致 → 認証失敗

**重要**: ブラウザがOriginを設定するため、JavaScriptで改ざん不可

---

### 4.2 Challenge検証

**チャレンジ生成方法**:
- 32バイト以上のランダム値を生成
- Base64URL形式でエンコード
- セッションに保存（1回のみ有効）

**再利用攻撃の防止**:
1. 認証/登録リクエスト受信時、セッションの保存済みチャレンジを取得
2. `clientDataJSON.challenge` と比較
3. 不一致の場合、認証/登録を拒否
4. 検証成功後、セッションからチャレンジを削除（再利用防止）

**有効期限の扱い**:
- デフォルト: 2分（120秒）
- FAPI準拠: 5分（300秒）推奨
- タイムアウト時: ブラウザが自動的にエラー表示

**攻撃シナリオと防止**:
```
攻撃: Replay Attack
  ↓ 攻撃者が過去のAssertion Responseを再送
  ↓ サーバーがChallengeを検証
  ↓ 不一致（チャレンジは毎回異なる）
  ↓ 認証失敗
```

---

### 4.3 signCount検証

**カウンタ増加の検証**:
1. データベースから保存済みsignCountを取得
2. 認証器から受信したsignCountと比較
3. `newCount > 0` かつ `newCount <= storedCount` の場合 → クローン検出
4. 検証成功後、データベースのsignCountを更新

**クローン検出時の挙動**:
1. エラーレスポンス返却
2. セキュリティイベント記録
3. 管理者への通知（推奨）
4. Credential無効化（ポリシー依存）

**signCount=0の扱い（Platform認証器）**:
- TouchID/FaceID/Windows Hello は常に `signCount=0` を返す
- 仕様: WebAuthn Level 2 Section 6.1.2
- 対策: `signCount=0` は検証スキップ（`newCount > 0` の条件で除外）

**クローン検出の仕組み**:
```
正常な認証器:
  認証1回目: signCount=1
  認証2回目: signCount=2
  認証3回目: signCount=3

クローンされた認証器:
  元の認証器で認証: signCount=4
  クローンで認証: signCount=4（増加しない）
  ↓ サーバー検出: newCount(4) <= storedCount(4)
```

---

### 4.4 ユーザー列挙攻撃対策

**攻撃シナリオ**:
```
攻撃者が "user@example.com" の存在確認を試みる
  ↓ 認証チャレンジリクエスト
  ↓ サーバーレスポンス観察:
    - allowCredentials=[] → ユーザー不在と推測
    - allowCredentials=[...] → ユーザー存在と推測
```

**ユーザー存在判定の防止方法**:
- **脆弱な実装**: ユーザー不在時に空配列を返却 → 存在判定可能
- **安全な実装**: ユーザー不在時もダミーCredentialを返却 → 存在判定不可
  - ランダムなCredential ID（32バイト）を生成
  - 実際には存在しないCredentialとして返却

**タイミング攻撃対策**:
- ユーザー存在確認のデータベース問い合わせ時間を一定にする
- ユーザー不在時もダミーのデータベース問い合わせを実行
- レスポンス時間を均一化（50ms程度）

**レスポンス時間の均一化**:
- ユーザー存在: 50ms
- ユーザー不在: 50ms（ダミー処理で調整）

---

## 5. トラブルシューティング

### 5.1 "認証器が見つかりません"

#### 原因1: allowCredentialsとCredential不一致

**症状**:
- ユーザーが認証器を持っているのに "認証器が見つかりません" エラー

**確認方法**:
```sql
-- ユーザーのCredential一覧確認
SELECT id, transports, rk
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123';

-- 結果例:
-- id: "abc123"
-- transports: ["usb"]
-- rk: false
```

```javascript
// ブラウザ開発者コンソールで確認
console.log(challengeResponse.allowCredentials);
// 出力: [{id: "xyz789", transports: ["internal"]}]

// 問題: サーバーが "xyz789" を返しているが、実際のCredential IDは "abc123"
```

**解決策**:
1. サーバー側のCredential取得ロジック確認
2. ユーザーIDマッピング確認（FIDO2 user.id vs アプリケーションuser_id）
3. Credential削除後の再登録

---

#### 原因2: authenticatorAttachment制約

**症状**:
- Platform認証器（TouchID）を持っているのに、セキュリティキー要求される

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

#### 原因3: Transportsミスマッチ

**症状**:
- Credentialは存在するが、ブラウザが認証器を探索しない

**確認方法**:
```sql
-- Transports確認
SELECT id, transports
FROM webauthn_credentials
WHERE id = 'credential-id';

-- 結果: transports = NULL（保存されていない）
```

**解決策**:
- Transports抽出・保存の実装確認
- 既存Credentialの場合: 再登録または手動UPDATE

---

### 5.2 "ユーザー検証に失敗しました"

#### 原因1: userVerification="required"だが認証器非対応

**症状**:
- 古いセキュリティキーで認証時にエラー

**確認方法**:
```json
// サーバー設定確認
{
  "authenticatorSelection": {
    "userVerification": "required"  // ← UV必須
  }
}
```

**エラーメッセージ例**:
```
UserVerificationException: User verification required but not performed
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

#### 原因2: credProtect制約

**症状**:
- パスワードレスログイン時のみUVエラー

**確認方法**:
```sql
-- credProtect確認
SELECT id, cred_protect
FROM webauthn_credentials
WHERE id = 'credential-id';

-- 結果: cred_protect = 3（常にUV必須）
```

**解決策**:
1. ユーザーに生体認証/PIN設定を依頼
2. 新規CredentialをcredProtect=2で登録
3. 既存Credential削除（最終手段）

---

### 5.3 パスワードレスログインできない

#### 原因: rk=falseでallowCredentials=[]

**症状**:
- "認証器が見つかりません" エラー（パスワードレス時のみ）

**確認方法**:
```sql
-- Resident Key確認
SELECT id, rk, transports
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123';

-- 結果: rk = false（Discoverable Credentialではない）
```

```json
// サーバーレスポンス確認
{
  "allowCredentials": []  // ← Discoverable Credential前提
}
```

**解決策**:
```json
// 登録時にResident Key必須化
{
  "authenticatorSelection": {
    "residentKey": "required",
    "requireResidentKey": true
  }
}
```

**代替案（既存Credential活用）**:
- ユーザー名入力を追加
- `allowCredentials` にCredential ID列挙

---

### 5.4 signCountエラー

#### 原因1: Credentialクローン検出

**症状**:
```
ClonedAuthenticatorException: Credential may be cloned (signCount did not increase)
```

**確認方法**:
```sql
-- signCount履歴確認
SELECT id, sign_count, authenticated_at
FROM webauthn_credentials
WHERE id = 'credential-id'
ORDER BY authenticated_at DESC;

-- 結果:
-- sign_count: 10 (2024-01-10 10:00:00)
-- sign_count: 10 (2024-01-10 10:05:00) ← クローン検出
```

**対処方法**:
1. ユーザーに通知（セキュリティアラート）
2. Credential無効化
3. 再登録を依頼

---

#### 原因2: Platform認証器のsignCount=0

**症状**:
- TouchID/FaceIDで常にsignCount=0

**確認方法**:
```sql
SELECT id, sign_count, transports
FROM webauthn_credentials
WHERE id = 'credential-id';

-- 結果:
-- sign_count: 0
-- transports: ["internal"]
```

**正常な挙動の説明**:
- Platform認証器（TouchID/FaceID/Windows Hello）は仕様上 `signCount=0` を返す
- WebAuthn Level 2 Section 6.1.2: "MAY return 0"
- クローン検出は不可能（仕様制約）

**対処**: 検証スキップ（実装済み）

---

### 5.5 Transportsが効いていない

#### 原因: データベース保存漏れ

**症状**:
- ブラウザが全認証器を探索（UX低下）

**確認方法**:
```sql
-- Transports確認
SELECT id, transports
FROM webauthn_credentials
WHERE id = 'credential-id';

-- 結果: transports = NULL（保存されていない）
```

**解決策**:
1. Transports抽出・保存の実装確認
2. 既存Credentialの場合: UPDATE文でtransports設定
   ```sql
   UPDATE webauthn_credentials
   SET transports = '["internal"]'::jsonb
   WHERE id = 'credential-id';
   ```

---

### 5.6 credProtectがnull

#### 原因: クライアント側Extension未設定

**症状**:
- credProtectが常にnull

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

## 6. データベース確認方法

### 6.1 Credential情報の確認

#### ユーザーのCredential一覧
```sql
-- ユーザーが持つ全Credential
SELECT
  id,
  username,
  user_display_name,
  rp_id,
  transports,
  rk,
  cred_protect,
  sign_count,
  created_at,
  authenticated_at
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123'
ORDER BY created_at DESC;
```

#### Transports確認
```sql
-- Transportsが正しく保存されているか
SELECT id, transports
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123';

-- 期待される結果例:
-- id: "abc123", transports: ["internal"]
-- id: "xyz789", transports: ["usb", "nfc"]
```

#### Resident Key確認
```sql
-- Discoverable Credentialか確認
SELECT id, rk, user_id, username
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123';

-- rk=true ならパスワードレスログイン可能
```

#### credProtect確認
```sql
-- Credential保護レベル確認
SELECT id, cred_protect
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123';

-- 値の意味:
-- 1 = userVerificationOptional
-- 2 = userVerificationOptionalWithCredentialIDList
-- 3 = userVerificationRequired
-- NULL = 未設定（Level 1相当）
```

---

### 6.2 認証履歴の確認

#### 最終認証日時
```sql
-- ユーザーの最終認証確認
SELECT
  id,
  username,
  authenticated_at,
  sign_count
FROM webauthn_credentials
WHERE tenant_id = ?::uuid AND user_id = 'user123'
ORDER BY authenticated_at DESC;
```

#### signCount履歴（クローン検出）
```sql
-- signCountの推移確認（監査ログから）
-- 注意: webauthn_credentialsテーブルは現在値のみ保存
SELECT
  id,
  sign_count,
  updated_at,
  authenticated_at
FROM webauthn_credentials
WHERE id = 'credential-id';

-- クローン検出: sign_countが減少または停滞している場合
```

**推奨**: 認証ごとにsignCountを監査ログに記録

```sql
-- 監査ログテーブル例（別途実装必要）
CREATE TABLE webauthn_audit_log (
  id UUID PRIMARY KEY,
  credential_id VARCHAR(255),
  sign_count BIGINT,
  authenticated_at TIMESTAMP,
  ip_address VARCHAR(45),
  user_agent TEXT
);
```

---

## 7. テナント設定による挙動制御

### 7.1 rpId設定

**設定値とドメイン検証**:
- `rpId`: ドメイン名（例: `example.com`）
- ブラウザが `rpIdHash = SHA-256(rpId)` を計算
- AuthenticatorDataの `rpIdHash` と一致確認

**サブドメイン対応**:
```json
// rpId設定例
{
  "rpId": "example.com"  // ← サブドメインも許可
}
```

**動作**:
- `https://example.com` → 有効
- `https://app.example.com` → 有効（サブドメイン）
- `https://other.com` → 無効

**重要**: `rpId` は親ドメインのみ（`app.example.com` を `rpId` にすると `example.com` は無効）

---

### 7.2 origin設定

**設定値とOrigin検証**:
- `origin`: 完全なURL（プロトコル + ドメイン）
- 例: `https://example.com`

**複数Origin対応**:
```json
{
  "allowedOrigins": [
    "https://example.com",
    "https://app.example.com",
    "https://mobile.example.com"
  ]
}
```

**動作**:
- `clientDataJSON.origin` が `allowedOrigins` のいずれかと完全一致
- 部分一致・ワイルドカードは不可（セキュリティ理由）

**注意**: 開発環境
```json
{
  "allowedOrigins": [
    "https://example.com",
    "http://localhost:3000"  // 開発環境用
  ]
}
```

---

### 7.3 timeout設定

**デフォルト値**:
- 登録: 120秒（2分）
- 認証: 120秒（2分）

**FAPI準拠時の推奨値**:
```json
{
  "timeout": 300000  // 5分（300秒）
}
```

**動作**:
- タイムアウト時、ブラウザが自動的にエラー表示
- ユーザーが認証器操作を中断可能
- サーバー側でのチャレンジ有効期限と一致させる

**設定例**:
```json
{
  "publicKey": {
    "timeout": 120000,  // ミリ秒単位
    "challenge": "...",
    "rpId": "example.com"
  }
}
```

---

### 7.4 認証ポリシー連携

**認証ポリシーでのFIDO2設定**:
```json
{
  "authentication_policy": {
    "id": "policy-001",
    "conditions": {
      "acr_values": ["fido2"]
    },
    "available_methods": [
      {
        "type": "fido2",
        "configuration": {
          "authenticatorSelection": {
            "residentKey": "required",
            "userVerification": "required",
            "authenticatorAttachment": "platform"
          }
        }
      }
    ],
    "success_conditions": {
      "required_methods": ["fido2"]
    }
  }
}
```

**多要素認証フロー**:
```json
{
  "authentication_policy": {
    "id": "policy-002",
    "available_methods": [
      {"type": "password"},
      {"type": "fido2"}
    ],
    "success_conditions": {
      "required_methods": ["password", "fido2"],
      "order": "sequential"
    }
  }
}
```

**動作**:
1. ユーザーがパスワード入力
2. パスワード検証成功
3. FIDO2認証プロンプト表示
4. FIDO2認証成功
5. 認証完了

**認証ポリシー条件分岐**:
```json
{
  "authentication_policy": {
    "conditions": {
      "scopes": ["openid", "payment"]  // payment scopeの場合
    },
    "available_methods": [
      {
        "type": "fido2",
        "configuration": {
          "authenticatorSelection": {
            "userVerification": "required"  // 高セキュリティ
          }
        }
      }
    ]
  }
}
```

---

## 参考資料

### 仕様書
- [W3C WebAuthn Level 2 Recommendation](https://www.w3.org/TR/webauthn-2/)
- [FIDO CTAP2.1 Specification](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html)
- [WebAuthn Level 2 - Section 5.8.6 (User Verification)](https://www.w3.org/TR/webauthn-2/#dom-authenticatorselectioncriteria-userverification)
- [WebAuthn Level 2 - Section 6.1.2 (signCount)](https://www.w3.org/TR/webauthn-2/#sctn-sign-counter)
- [FAPI 1.0 Advanced - Section 5.2.2 (Authorization Server)](https://openid.net/specs/openid-financial-api-part-2-1_0.html#authorization-server)
- [CTAP2.1 credProtect Extension](https://fidoalliance.org/specs/fido-v2.1-ps-20210615/fido-client-to-authenticator-protocol-v2.1-ps-errata-20220621.html#sctn-credProtect-extension)

### ライブラリ
- [WebAuthn4j GitHub](https://github.com/webauthn4j/webauthn4j)
- [WebAuthn4j Documentation](https://webauthn4j.github.io/webauthn4j/en/)

### 関連ドキュメント
- [FIDO2 / WebAuthn プロトコル概要](protocol-04-fido2-webauthn.md) - プロトコルの基本フロー
- [認証設定ガイド](../content_06_developer-guide/05-configuration/authn/webauthn.md) - テナント設定方法
- [AI開発者向けガイド - Authentication](../content_10_ai_developer/ai-14-authentication-federation.md) - 実装クラス詳細
