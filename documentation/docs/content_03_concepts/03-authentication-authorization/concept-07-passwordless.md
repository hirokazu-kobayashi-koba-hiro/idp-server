# パスワードレス認証

---

## 前提知識

このドキュメントを理解するには、以下の基礎知識が役立ちます：

- [認証ポリシー](concept-01-authentication-policy.md) - 認証方式の設定
- [多要素認証（MFA）](concept-02-mfa.md) - 認証要素の概念
- [FIDO2/WebAuthnの基礎](../basic/basic-16-fido2-webauthn-passwordless.md) - 技術的な背景

---

## 概要

idp-serverは、**パスワードレス認証**をサポートします。

**パスワードレス認証**とは、パスワードを使用せずにユーザーを認証する方式です。生体認証やセキュリティキーなど、より安全で使いやすい認証手段を提供します。

```
ユーザー ──[生体認証/セキュリティキー]──> 認証器 ──[署名]──> idp-server ──[検証]──> 認証成功
```

idp-serverでは以下のパスワードレス認証方式に対応しています：

* **FIDO2/WebAuthn**: 生体認証、セキュリティキー
* **Passkey**: デバイス間で同期可能な認証資格情報
* **FIDO UAF**: モバイルアプリ向け生体認証（CIBA連携）

---

## なぜパスワードレス認証が必要か

### パスワードの課題

パスワード認証には多くの課題があります：

| 課題 | 内容 | 影響 |
|:---|:---|:---|
| **フィッシング** | 偽サイトでパスワードを盗まれる | アカウント乗っ取り |
| **使い回し** | 同じパスワードを複数サイトで使用 | 漏洩時の被害拡大 |
| **覚えられない** | 複雑なパスワードは記憶困難 | ユーザー体験の低下 |
| **管理コスト** | リセット対応、ポリシー管理 | 運用負荷 |

### パスワードレス認証のメリット

| メリット | 説明 |
|:---|:---|
| **フィッシング耐性** | 認証器がオリジン（ドメイン）を検証するため、偽サイトでは認証不可 |
| **利便性** | 指紋や顔認証でワンタッチ認証 |
| **セキュリティ** | 秘密鍵は認証器から出ない |
| **運用コスト削減** | パスワードリセット対応が不要 |

---

## idp-serverにおけるパスワードレス認証

### 1. FIDO2/WebAuthn

**WebAuthn**（Web Authentication API）は、W3Cが標準化したパスワードレス認証の仕様です。

```
ユーザー          ブラウザ          idp-server        認証器
   |                 |                 |                |
   |--ログインボタン->|                 |                |
   |                 |--認証開始------->|                |
   |                 |<--challenge-----|                |
   |                 |--認証要求------------------------>|
   |<--生体認証-------------------------------------|
   |--指紋/顔---------------------------------------->|
   |                 |<--署名---------------------------|
   |                 |--署名検証------>|                |
   |                 |<--認証成功------|                |
```

**対応認証器**:
- **プラットフォーム認証器**: Touch ID, Face ID, Windows Hello
- **ローミング認証器**: YubiKey, セキュリティキー

**設定方法**: [FIDO2設定ガイド](../../content_06_developer-guide/05-configuration/authn/fido2.md)

### 2. Passkey

**Passkey**は、FIDO2の拡張で、デバイス間で認証資格情報を同期できる機能です。

```
┌─────────────────────────────┐  ┌─────────────────────────────┐
│          Apple              │  │          Google             │
│                             │  │                             │
│  iPhone ──┐                 │  │  Android ──┐                │
│           ├──> iCloud       │  │            ├──> Google      │
│  Mac ─────┘    Keychain     │  │  Chrome ───┘    Password    │
│                             │  │                 Manager     │
└─────────────────────────────┘  └─────────────────────────────┘
```

**メリット**:
- デバイス紛失時も他のデバイスで認証可能
- 新しいデバイスへの移行が容易
- ユーザー体験の向上

**詳細**: [Passkeyの基礎](../basic/basic-17-fido2-passkey-discoverable-credential.md)

---

## ユーザーとパスキーの関係

### データモデル

idp-serverでは、ユーザーとパスキー（FIDO2クレデンシャル）は以下の関係で管理されます。

```
User (ユーザー)
  └── AuthenticationDevice (認証デバイス) [1:N]
        └── DeviceCredential (デバイスクレデンシャル) [1:N]
              └── FidoCredentialData (FIDO2固有データ)
                    - credential_id: クレデンシャルID
                    - rp_id: Relying Party ID
                    - fido_server_id: FIDOサーバーID
```

### 関係性

| エンティティ | 説明 | 関係 |
|:---|:---|:---|
| **User** | ユーザーアカウント | 1ユーザーに複数の認証デバイスを登録可能 |
| **AuthenticationDevice** | 認証に使用するデバイス（iPhone、Mac等） | 1デバイスに複数のクレデンシャルを保持可能 |
| **DeviceCredential** | 認証資格情報（パスキー、JWT Bearer等） | FIDO2、FIDO UAF、JWT Bearerなど複数タイプに対応 |

### 制約事項

| 制約 | 内容 |
|:---|:---|
| **rpIdの一致** | 登録時のrpIdと認証時のrpIdが一致する必要がある |
| **rpIdのスコープ** | rpIdは現在のドメインまたはその親ドメインのみ指定可能 |
| **クレデンシャルの一意性** | 同一rpId内でcredential_idは一意 |
| **デバイス紐付け** | パスキーは特定のAuthenticationDeviceに紐づく |

### rpIdとサブドメインの関係

WebAuthn仕様では、rpIdの有効性は以下のルールで判定されます。

| ケース | 例 | 有効性 |
|:---|:---|:---|
| **完全一致** | ホスト: `auth.local.dev` / rpId: `auth.local.dev` | 有効 |
| **親ドメイン** | ホスト: `auth.local.dev` / rpId: `local.dev` | 有効 |
| **兄弟ドメイン** | ホスト: `auth.local.dev` / rpId: `api.local.dev` | 無効 |
| **無関係なドメイン** | ホスト: `auth.local.dev` / rpId: `example.com` | 無効 |

**推奨**: サブドメイン構成では、親ドメイン（例: `local.dev`）をrpIdとして設定することで、複数のサブドメイン間でパスキーを共有できます。

### 1ユーザー複数パスキー

ユーザーは複数のパスキーを登録できます：

- **バックアップ用**: デバイス紛失時のリカバリー
- **複数デバイス**: iPhone、Mac、セキュリティキーなど
- **異なるrpId**: サブドメインごとに異なるパスキー（非推奨）

```
User: alice@example.com
  ├── AuthenticationDevice: "iPhone 15"
  │     └── DeviceCredential: Passkey (rpId: example.com)
  ├── AuthenticationDevice: "MacBook Pro"
  │     └── DeviceCredential: Passkey (rpId: example.com)
  └── AuthenticationDevice: "YubiKey 5"
        └── DeviceCredential: Passkey (rpId: example.com)
```

### デバイス情報の自動抽出

パスキー登録時、idp-serverはHTTPリクエストの`User-Agent`ヘッダーを解析し、`AuthenticationDevice`のフィールドを自動設定します。これにより、ユーザーは登録済みパスキーを識別しやすくなります。

#### User-Agent解析の仕組み

```
User-Agent ヘッダー
         │
         ▼
    ┌─────────────────────────────────────┐
    │          DeviceInfo.parse()         │
    │  ┌──────────────────────────────┐   │
    │  │ 1. デバイス種別判定          │   │
    │  │ 2. OS判定 + バージョン抽出   │   │
    │  │ 3. ブラウザ判定 + バージョン │   │
    │  │ 4. モバイル判定              │   │
    │  └──────────────────────────────┘   │
    └─────────────────────────────────────┘
         │
         ▼
    AuthenticationDevice フィールド設定
```

#### フィールドマッピング

| AuthenticationDevice | DeviceInfo | 説明 | 例 |
|:---|:---|:---|:---|
| `app_name` | `toLabel()` | デバイスラベル | `"iPhone - Safari (iOS 17.2)"` |
| `platform` | `platform()` | プラットフォーム | `"Mobile"` / `"Desktop"` |
| `os` | `os()` | OS名 | `"iOS"`, `"macOS"`, `"Windows"` |
| `model` | `model()` | ブラウザ＋バージョン | `"Safari 17.2"`, `"Chrome 120"` |

#### User-Agent解析ルール

**デバイス種別判定**:

| User-Agent含有文字列 | デバイス |
|:---|:---|
| `iphone` | iPhone |
| `ipad` | iPad |
| `android` + `mobile` | Android Phone |
| `android` | Android Tablet |
| `macintosh`, `mac os` | Mac |
| `windows` | Windows PC |
| `linux` | Linux |

**OS判定**:

| User-Agent含有文字列 | OS |
|:---|:---|
| `iphone`, `ipad`, `ipod` | iOS |
| `android` | Android |
| `macintosh`, `mac os` | macOS |
| `windows` | Windows |
| `linux` | Linux |

**OSバージョン抽出（正規表現）**:

| OS | パターン | 例 | 抽出結果 |
|:---|:---|:---|:---|
| iOS | `(?:iPhone\|CPU) OS (\d+[_.]\d+(?:[_.]\d+)?)` | `iPhone OS 17_2_1` | `17.2.1` |
| macOS | `Mac OS X (\d+[_.]\d+(?:[_.]\d+)?)` | `Mac OS X 10_15_7` | `10.15.7` |
| Android | `Android (\d+(?:\.\d+)?)` | `Android 14` | `14` |
| Windows | `Windows NT (\d+\.\d+)` | `Windows NT 10.0` | `10/11` |

**ブラウザ判定（判定順序が重要）**:

| 判定条件 | ブラウザ |
|:---|:---|
| `edg/` または `edge/` | Edge |
| `firefox` | Firefox |
| `chrome` かつ `edg`を含まない | Chrome |
| `safari` かつ `chrome`, `chromium`を含まない | Safari |
| `opera` または `opr/` | Opera |

**ブラウザバージョン抽出**:

| ブラウザ | パターン | 例 | 抽出結果 |
|:---|:---|:---|:---|
| Edge | `Edg/(\d+)` | `Edg/120.0.0.0` | `120` |
| Firefox | `Firefox/(\d+(?:\.\d+)?)` | `Firefox/121.0` | `121` |
| Chrome | `Chrome/(\d+)` | `Chrome/120.0.0.0` | `120` |
| Safari | `Version/(\d+(?:\.\d+)?)` | `Version/17.2` | `17.2` |
| Opera | `OPR/(\d+)` | `OPR/106.0` | `106` |

#### 解析結果例

**Safari on iPhone**:
```
User-Agent: Mozilla/5.0 (iPhone; CPU iPhone OS 17_2_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1

→ app_name: "iPhone - Safari (iOS 17.2.1)"
→ platform: "Mobile"
→ os: "iOS"
→ model: "Safari 17.2"
```

**Chrome on Mac**:
```
User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36

→ app_name: "Mac - Chrome (macOS 10.15.7)"
→ platform: "Desktop"
→ os: "macOS"
→ model: "Chrome 120"
```

**Edge on Windows**:
```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0

→ app_name: "Windows PC - Edge (Windows 10/11)"
→ platform: "Desktop"
→ os: "Windows"
→ model: "Edge 120"
```

#### 実装クラス

- `DeviceInfo.java` - User-Agent解析とフィールド抽出
- `UserAgent.java` - User-Agentラッパー、`toDeviceInfo()`メソッド
- `Fido2RegistrationInteractor.java` - 登録時のデバイス情報設定

---

### 3. FIDO UAF（CIBA連携）

**FIDO UAF**（Universal Authentication Framework）は、モバイルアプリ向けの生体認証仕様です。idp-serverでは、**CIBA**（Client Initiated Backchannel Authentication）と組み合わせて使用できます。

```
クライアント      idp-server      モバイルアプリ      ユーザー
     |                |                 |                |
     |--CIBA認証----->|                 |                |
     |  リクエスト    |--プッシュ通知-->|                |
     |                |                 |--生体認証要求->|
     |                |                 |<--指紋/顔------|
     |                |<--認証完了------|                |
     |<--トークン発行-|                 |                |
```

**ユースケース**:
- コールセンターでの本人確認
- 決済承認
- 高セキュリティ操作の承認

**詳細**: [CIBAフロー](../../content_04_protocols/protocol-02-ciba-flow.md)

---

## 認証ポリシーとの連携

パスワードレス認証は、認証ポリシーと組み合わせて使用します。

### 段階的な導入

**推奨される導入ステップ**:
1. **Phase 1**: パスワード + FIDO2（オプション）
2. **Phase 2**: FIDO2推奨、パスワードはフォールバック
3. **Phase 3**: FIDO2のみ（パスワードレス完全移行）

### MFAとの組み合わせ

FIDO2は単独でMFAの要件を満たすことができます：

| 認証要素 | FIDO2での実現 |
|:---|:---|
| **所持** | 認証器（スマートフォン、セキュリティキー） |
| **生体** | 指紋、顔認証 |
| **知識** | PIN（オプション） |

---

## セキュリティ考慮事項

### フィッシング耐性

FIDO2/WebAuthnは設計上フィッシング耐性があります：

- **オリジン検証**: 認証器が登録時のドメインと照合
- **署名バインディング**: challengeとオリジンを含めて署名

### 認証器の紛失対応

- **複数認証器の登録**: バックアップ用の認証器を推奨
- **リカバリーフロー**: 管理者による認証器リセット機能

### 認証器の検証（Attestation）

**Attestationレベル**:
- **none**: 認証器の種類を検証しない（推奨）
- **indirect**: 匿名化された検証
- **direct**: 認証器の種類を厳密に検証

---

## 関連ドキュメント

### 基礎知識
- [FIDO2/WebAuthnの基礎](../basic/basic-16-fido2-webauthn-passwordless.md)
- [Passkeyの基礎](../basic/basic-17-fido2-passkey-discoverable-credential.md)
- [FIDO2アーキテクチャ](../basic/basic-18-fido2-architecture-rp-browser-authenticator.md)

### 設定
- [FIDO2設定](../../content_06_developer-guide/05-configuration/authn/fido2.md)
- [認証ポリシー設定](../../content_06_developer-guide/05-configuration/authentication-policy.md)

### プロトコル
- [CIBAフロー](../../content_04_protocols/protocol-02-ciba-flow.md) - FIDO UAF連携

---

## 参考仕様

- [Web Authentication (WebAuthn)](https://www.w3.org/TR/webauthn-2/) - W3C標準仕様
- [FIDO2 Specifications](https://fidoalliance.org/specifications/) - FIDO Alliance
- [Passkeys](https://passkeys.dev/) - Passkey開発者向けリソース
