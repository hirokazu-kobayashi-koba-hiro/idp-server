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

idp-serverでは、ユーザーとパスキー（FIDO2クレデンシャル）は以下の関係で管理されます。**1デバイス = 1クレデンシャル**の設計を採用しています。

```
User (ユーザー)
  └── AuthenticationDevice (認証デバイス) [1:N]
        ├── credential_type: クレデンシャルタイプ (fido2, jwt_bearer等)
        ├── credential_id: クレデンシャルID
        ├── credential_payload: クレデンシャル固有データ
        │     └── FIDO2の場合:
        │           - credential_id: WebAuthnクレデンシャルID
        │           - rp_id: Relying Party ID
        │           - fido_server_id: FIDOサーバーID
        │           - public_key: 公開鍵
        └── credential_metadata: メタデータ（登録日時等）
```

### 関係性

| エンティティ | 説明 | 関係 |
|:---|:---|:---|
| **User** | ユーザーアカウント | 1ユーザーに複数の認証デバイスを登録可能 |
| **AuthenticationDevice** | 認証に使用するデバイス（iPhone、Mac等） | **1デバイス = 1クレデンシャル**（統合設計） |

### 1デバイス = 1クレデンシャル設計

この設計には以下のメリットがあります：

| メリット | 説明 |
|:---|:---|
| **シンプルなデータモデル** | デバイスとクレデンシャルの1:1対応で管理が容易 |
| **直感的なUI** | ユーザーはデバイス単位でパスキーを管理 |
| **効率的なクエリ** | JOINなしでデバイスとクレデンシャルを取得可能 |
| **明確なライフサイクル** | デバイス削除 = クレデンシャル削除 |

複数のパスキーを登録する場合は、それぞれ別のAuthenticationDeviceとして登録されます。

### 制約事項

| 制約 | 内容 |
|:---|:---|
| **rpIdの一致** | 登録時のrpIdと認証時のrpIdが一致する必要がある |
| **rpIdのスコープ** | rpIdは現在のドメインまたはその親ドメインのみ指定可能 |
| **クレデンシャルの一意性** | 同一rpId内でcredential_idは一意 |
| **1:1対応** | 1つのAuthenticationDeviceに1つのクレデンシャルのみ |

### FIDO2のusername

パスキー登録・認証時に使用されるusernameの仕様です。

> **WebAuthn仕様の詳細**: [FIDO2登録フローとインターフェース](../../content_11_learning/05-fido-webauthn/fido2-registration-flow-interface.md)を参照してください。

#### アーキテクチャ

idp-serverは、FIDOサーバーの実装を抽象化しています。

```
┌─────────────────────────────────────────────────────────────┐
│                     idp-server                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │     Fido2RegistrationInteractor                       │  │
│  │     Fido2AuthenticationInteractor                     │  │
│  │       └── username解決（TenantIdentityPolicy）         │  │
│  └───────────────────────────────────────────────────────┘  │
│                           │                                 │
│              ┌────────────┴────────────┐                    │
│              ▼                         ▼                    │
│  ┌─────────────────────┐   ┌─────────────────────────────┐  │
│  │  WebAuthn4j Adapter │   │  External FIDO Server       │  │
│  │  (built-in)         │   │  (http_request経由)          │  │
│  └─────────────────────┘   └─────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

#### usernameの決定ルール

**`TenantIdentityPolicy`の`uniqueKeyType`設定**に基づいてusernameを決定します。

| uniqueKeyType | usernameの値 | 用途 |
|:---|:---|:---|
| `USERNAME` | `preferredUsername` | 社内システム（従業員ID） |
| `EMAIL` | `email` | 一般向けWebサービス（**デフォルト**） |
| `PHONE` | `phoneNumber` | モバイルアプリ（SMS認証） |
| `EXTERNAL_USER_ID` | `externalUserId` | 外部IdP連携 |

この解決は`Fido2RegistrationInteractor`で行われ、FIDOサーバーの実装（WebAuthn4j / 外部サーバー）に依存しません。

#### usernameの流れ

```
┌──────────────────────────────────────────────────────────────┐
│                    パスキー登録フロー                          │
├──────────────────────────────────────────────────────────────┤
│  1. フロントエンド → idp-server                               │
│     └── username を送信（TenantIdentityPolicyに基づく値）      │
│                                                              │
│  2. idp-server → FIDOサーバー                                 │
│     └── username を含むリクエストを転送                        │
│     └── WebAuthn4j または 外部FIDOサーバー                     │
│                                                              │
│  3. FIDOサーバー → 認証器                                     │
│     └── user.name として認証器UIに表示・保存                   │
│                                                              │
│  4. FIDOサーバー → idp-server                                 │
│     └── レスポンスにusernameを含める                          │
│     └── metadata.username_param で取得キーを指定              │
└──────────────────────────────────────────────────────────────┘
```

#### FIDO2設定

`metadata.username_param`で、FIDOサーバーのレスポンスからusernameを取得するパラメータ名を指定します。

**WebAuthn4j Adapter（built-in）の場合**:
```json
{
  "type": "fido2",
  "metadata": {
    "username_param": "username"
  },
  "interactions": {
    "fido2-registration": {
      "execution": {
        "function": "webauthn4j_registration"
      }
    }
  }
}
```

**外部FIDOサーバーの場合**:
```json
{
  "type": "fido2",
  "metadata": {
    "username_param": "user_id"
  },
  "interactions": {
    "fido2-registration": {
      "execution": {
        "function": "http_request",
        "http_request": {
          "url": "https://fido-server.example.com/registration"
        }
      }
    }
  }
}
```

外部FIDOサーバーを使用する場合、レスポンスのどのフィールドにusernameが含まれるかはサーバー実装に依存するため、`username_param`で適切に指定してください。

#### 注意事項

| 注意点 | 説明 |
|:---|:---|
| **一意性** | usernameはテナント内で一意である必要がある |
| **不変性** | 登録後のusername変更は新規パスキー登録が必要 |
| **64バイト制限** | WebAuthn仕様により、認証器で切り詰められる可能性あり |
| **FIDOサーバー依存** | 外部サーバー使用時は`username_param`の設定が重要 |

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

ユーザーは複数のパスキーを登録できます。各パスキーは個別のAuthenticationDeviceとして管理されます：

- **バックアップ用**: デバイス紛失時のリカバリー
- **複数デバイス**: iPhone、Mac、セキュリティキーなど
- **異なるrpId**: サブドメインごとに異なるパスキー（非推奨）

```
User: alice@example.com
  ├── AuthenticationDevice: "iPhone 15"
  │     ├── credential_type: "fido2"
  │     └── credential_payload: { rp_id: "example.com", ... }
  ├── AuthenticationDevice: "MacBook Pro"
  │     ├── credential_type: "fido2"
  │     └── credential_payload: { rp_id: "example.com", ... }
  └── AuthenticationDevice: "YubiKey 5"
        ├── credential_type: "fido2"
        └── credential_payload: { rp_id: "example.com", ... }
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

### パスキーの削除（登録解除）

ユーザーは登録済みのパスキーを削除できます。idp-serverは**ステップアップ認証**をサポートしており、パスキー削除時に追加の認証を要求できます。

#### 削除フロー

```
ユーザー          フロントエンド          idp-server
   |                    |                    |
   |--削除ボタン------->|                    |
   |                    |--DELETE /me/authentication-devices/{id}-->|
   |                    |                    |--ステップアップ認証が必要か判定
   |                    |<--401 step_up_authentication_required-----|
   |<--再認証要求-------|                    |
   |--パスキー認証----->|                    |
   |                    |--DELETE (認証済み)->|
   |                    |                    |--デバイス削除
   |                    |<--204 No Content---|
   |<--削除完了---------|                    |
```

#### ステップアップ認証

パスキー削除などのセキュリティ上重要な操作には、ステップアップ認証を要求できます。

**レスポンス例（認証が必要な場合）**:
```json
{
  "status": "step_up_authentication_required",
  "message": "Additional authentication is required for this operation"
}
```

**レスポンス例（成功）**:
```json
{
  "status": "OK"
}
```

#### 実装クラス

- `Fido2DeregistrationInteractor.java` - パスキー削除のインターアクター
- `WebAuthn4jDeregistrationExecutor.java` - WebAuthn4jでの削除実行
- `UserOperationEntryService.java` - ステップアップ認証判定

#### 注意事項

| 注意点 | 説明 |
|:---|:---|
| **最後のパスキー** | 最後のパスキーを削除すると、パスワードレス認証ができなくなる |
| **ステップアップ認証** | セキュリティ設定により、削除時に追加認証が必要な場合がある |
| **FIDOサーバー連携** | 外部FIDOサーバー使用時は、サーバー側でも削除が必要な場合がある |

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

### デバイス管理のセキュリティ

パスキーの登録・削除は、セキュリティ上重要な操作です。idp-serverは以下のセキュリティ機能を提供します：

| 機能 | 説明 |
|:---|:---|
| **ステップアップ認証** | デバイス削除時に追加認証を要求可能 |
| **セキュリティイベント** | デバイス登録・削除をイベントとして記録 |
| **監査ログ** | 操作履歴の追跡が可能 |

### 認証器の検証（Attestation）

**Attestation（アテステーション）**とは、パスキー登録時に認証器（Authenticator）の真正性を検証する仕組みです。認証器が生成した署名とともに、認証器自体の情報（メーカー、モデル、セキュリティ特性など）を証明するデータを提供します。

#### Attestationの構成要素

```
認証器 ──[公開鍵 + Attestation Statement]──> サーバー
                    │
                    ├── format: アテステーション形式
                    ├── attStmt: 署名データ
                    └── authData: 認証器データ（AAGUID含む）
```

| 要素 | 説明 |
|:---|:---|
| **AAGUID** | 認証器モデルを識別する128bit UUID |
| **Attestation Statement** | 認証器の署名（形式はformatで指定） |
| **Attestation Format** | 署名形式（none, packed, tpm, android-key等） |

#### Attestation形式（Format）

登録時のレスポンスに含まれる署名形式です。

| 形式 | 説明 | 対応認証器 |
|:---|:---|:---|
| **none** | アテステーションなし | プラットフォーム認証器（プライバシー保護） |
| **packed** | FIDO2標準形式 | セキュリティキー（YubiKey等） |
| **tpm** | TPMによる署名 | Windows Hello（TPM搭載PC） |
| **android-key** | Android Keystore | Android端末 |
| **android-safetynet** | SafetyNet API | 旧Android認証 |
| **apple** | Apple Anonymous Attestation | Apple端末（iOS/macOS） |
| **fido-u2f** | 旧U2F形式 | 旧型セキュリティキー |

#### Attestation Conveyance（クライアントへの要求）

サーバーからクライアントへの登録オプションで、アテステーションの要求レベルを指定します。

| 値 | 説明 | ユースケース |
|:---|:---|:---|
| **none** | アテステーション不要 | 一般向けサービス（プライバシー優先） |
| **indirect** | 匿名化されたアテステーション | 中間的なセキュリティ要件 |
| **direct** | 完全なアテステーション | エンタープライズ（認証器種別の制限） |
| **enterprise** | 企業向けアテステーション | 管理デバイスの識別 |

> **注意**: `direct`や`enterprise`を要求しても、プラットフォーム認証器（Touch ID、Face ID等）はプライバシー保護のため`none`形式で応答することがあります。これはWebAuthn仕様で許容されており、正常な動作です。

#### idp-serverでのAttestation検証パターン

idp-serverは3つのアテステーション検証パターンをサポートします。

| パターン | 説明 | 設定 |
|:---|:---|:---|
| **None（検証なし）** | アテステーションを検証しない | デフォルト、一般向けサービス |
| **TrustStore** | 事前登録された証明書で検証 | 特定認証器のみ許可 |
| **FIDO MDS** | FIDO Metadata Serviceで検証 | エンタープライズ、高セキュリティ |

**1. None（検証なし）**

アテステーションの検証を行わず、どの認証器でも登録を許可します。

```json
{
  "webauthn4j": {
    "attestation_preference": "none"
  }
}
```

- **メリット**: すべての認証器を許可、ユーザー体験を優先
- **デメリット**: 認証器の信頼性を検証できない
- **推奨**: 一般消費者向けサービス

**2. TrustStore（証明書ベース）**

事前に登録した証明書で認証器を検証します。

```json
{
  "webauthn4j": {
    "attestation_preference": "direct",
    "trust_store": {
      "certificates": [
        "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----"
      ]
    }
  }
}
```

- **メリット**: 特定の認証器のみ許可可能
- **デメリット**: 証明書の管理が必要
- **推奨**: 社内システム、特定セキュリティキーの強制

**3. FIDO Metadata Service（MDS）**

FIDO Allianceが提供するメタデータサービスを使用して認証器を検証します。

```json
{
  "webauthn4j": {
    "attestation_preference": "direct",
    "mds": {
      "enabled": true,
      "cache_ttl_seconds": 86400
    }
  }
}
```

- **メリット**: 最新の認証器情報、脆弱性情報を自動取得
- **デメリット**: ネットワーク接続が必要、MDSに登録されていない認証器は検証不可
- **推奨**: エンタープライズ、金融サービス

#### FIDO Metadata Service（MDS）

FIDO MDSは、FIDO Allianceが運営する認証器のメタデータ配信サービスです。

**MDSで取得できる情報**:

| 情報 | 説明 |
|:---|:---|
| **認証器名** | YubiKey 5 NFC、iPhone等 |
| **アイコン** | 認証器のアイコン画像（Base64） |
| **認証レベル** | FIDO認定レベル（L1, L2, L3） |
| **ステータス** | 正常、脆弱性あり、失効等 |
| **対応アルゴリズム** | ES256、RS256等 |

**認証器ステータス**:

| ステータス | 説明 | 対応 |
|:---|:---|:---|
| **FIDO_CERTIFIED** | FIDO認定済み | 許可 |
| **FIDO_CERTIFIED_L1/L2/L3** | 認定レベル別 | 許可 |
| **UPDATE_AVAILABLE** | ファームウェア更新あり | 警告 |
| **ATTESTATION_KEY_COMPROMISE** | 鍵が漏洩 | **拒否** |
| **USER_VERIFICATION_BYPASS** | UVバイパス脆弱性 | **拒否** |
| **REVOKED** | 失効 | **拒否** |

idp-serverは登録時にMDSをチェックし、危殆化した認証器を検出した場合は警告をログに出力します。

**MDSキャッシュ**:

MDSデータは`CacheStore`にキャッシュされ、不要なネットワーク通信を防ぎます。

| キャッシュキー | 内容 | TTL |
|:---|:---|:---|
| `mds:entries` | 全認証器メタデータ | 24時間（設定可能） |
| `mds:status:{aaguid}` | 認証器ステータス | 24時間（設定可能） |
| `mds:last_fetch` | 最終取得時刻 | 24時間（設定可能） |

#### プラットフォーム認証器の制限

Apple（Touch ID、Face ID）やAndroid等のプラットフォーム認証器は、プライバシー保護のためアテステーションを提供しないことがあります。

| プラットフォーム | アテステーション | 備考 |
|:---|:---|:---|
| **Apple（iOS/macOS）** | `none`または`apple`（匿名） | MDSに未登録 |
| **Android** | `android-key` | 一部端末のみ |
| **Windows Hello** | `tpm`（TPM搭載時） | TPM非搭載PCは`none` |

> **重要**: `attestation: "direct"`を要求しても、プラットフォーム認証器が`none`で応答することは仕様上正常です。idp-serverはこのケースを適切に処理します。

#### 推奨設定

| ユースケース | 推奨設定 |
|:---|:---|
| **一般消費者向けサービス** | `attestation_preference: "none"`、MDS無効 |
| **企業内システム** | `attestation_preference: "direct"`、TrustStoreで許可認証器を限定 |
| **金融・医療など高セキュリティ** | `attestation_preference: "direct"`、MDS有効、危殆化チェック |
| **政府・防衛** | `attestation_preference: "enterprise"`、MDS有効、FIDO認定必須 |

#### 関連ドキュメント

- [FIDO2 AAGUID - 認証器の識別](../../content_11_learning/05-fido-webauthn/fido2-aaguid-authenticator-identification.md)
- [FIDO2 アテステーションタイプと検証](../../content_11_learning/05-fido-webauthn/fido2-attestation-types-and-verification.md)
- [FIDO2 Metadata Service](../../content_11_learning/05-fido-webauthn/fido2-metadata-service.md)

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
