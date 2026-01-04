# セッション管理

セッション管理は、ユーザーのログイン状態を維持し、シングルサインオン（SSO）やログアウト連携を実現するための仕組みです。

## セッションとは

**セッション（Session）** とは、ユーザーの認証状態を一定期間保持する仕組みです。

### セッションの役割

1. **認証状態の保持**: ログイン後、毎回パスワード入力せずにサービス利用可能
2. **シングルサインオン（SSO）**: 一度のログインで複数のアプリケーションにアクセス
3. **ログアウト連携**: 一箇所でログアウトすると関連するすべてのアプリからログアウト

### セッションとトークンの違い

| 項目 | セッション | トークン |
|:---|:---|:---|
| **用途** | ブラウザとIdP間の状態管理 | クライアントとリソースサーバー間の認可 |
| **保存場所** | Cookie（ブラウザ側）+ Redis/DB（サーバー側） | クライアントアプリケーション |
| **有効期限** | セッションタイムアウト（通常30分〜数時間） | トークン有効期限（アクセストークン: 分〜時間） |
| **識別対象** | ユーザーのブラウザ | クライアントアプリケーションのリクエスト |

## idp-serverのセッション管理

### なぜSpring Sessionを使わないか

idp-serverは、OIDC Session Managementの要件を満たすため、独自のセッション管理を実装しています。

| Spring Session | OIDC Session Management |
|---------------|-------------------------|
| 1ブラウザ = 1セッション | 1 OPセッション : N クライアントセッション |
| HttpSessionの分散化が目的 | sid, sub での複合検索が必要 |
| RP（クライアント）側での利用を想定 | OP（IdP）側でのセッション管理 |

この要件の違いから、Keycloakなどと同様に独自実装を採用しています。

### セッションの階層構造

idp-serverでは、2層のセッション構造を採用しています。

```
┌─────────────────────────────────────────────────────────────────┐
│                        Browser Session                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    OPSession                             │   │
│  │  - ブラウザとOP間のセッション（SSO用）                    │   │
│  │  - sub, authTime, acr, amr を保持                        │   │
│  │  - 複数のClientSessionを持つ                             │   │
│  └─────────────────────────────────────────────────────────┘   │
│           │                    │                    │           │
│           ▼                    ▼                    ▼           │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ ClientSession   │  │ ClientSession   │  │ ClientSession   │ │
│  │ アプリA         │  │ アプリB         │  │ アプリC         │ │
│  │ sid: xxx        │  │ sid: yyy        │  │ sid: zzz        │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### OPSession（OPセッション）

ブラウザとIdP（OP）間のセッションです。ユーザーがログインすると作成されます。

| 属性 | 説明 |
|:---|:---|
| **id** | セッションID（UUID） |
| **sub** | ユーザー識別子 |
| **authTime** | 認証時刻 |
| **acr** | 認証コンテキストクラス（認証強度） |
| **amr** | 認証方式（password, otp, fido等） |
| **expiresAt** | 有効期限 |

#### ClientSession（クライアントセッション）

OPSessionと特定のアプリケーション（RP）間のセッションです。認可が完了すると作成されます。

| 属性 | 説明 |
|:---|:---|
| **sid** | セッションID（ID Tokenのsidクレームに含まれる） |
| **opSessionId** | 親となるOPSessionのID |
| **clientId** | アプリケーションのClient ID |
| **scopes** | 認可されたスコープ |
| **nonce** | 認可リクエストのnonce |

## Cookieの役割

idp-serverは、セッション管理に複数のCookieを使用します。

### セッション識別Cookie

| Cookie名 | 内容 | HttpOnly | 目的 |
|----------|------|----------|------|
| `IDP_IDENTITY` | OPSessionのID | Yes | SSO識別用（サーバー側で使用） |
| `IDP_SESSION` | SHA256(opSessionId) | No | Session Management iframe用 |

- **IDP_IDENTITY**: サーバー側でセッションを識別するためのCookie（HttpOnlyでセキュア）
- **IDP_SESSION**: OIDC Session Managementのiframeでセッション状態を確認するためのCookie

### 認可フロー保護Cookie

| Cookie名 | 内容 | 目的 |
|----------|------|------|
| `AUTH_SESSION` | 認可セッションID | 認可フロー乗っ取り攻撃の防止 |

認可リクエストからトークン取得までの一連のフローを、同一ブラウザセッション内でのみ有効にします。

### テナント分離

Cookieのパスでテナントを分離できます。

```
Browser Cookie Storage:
├── /tenant-a/
│   ├── IDP_IDENTITY = "session-id-for-tenant-a"
│   └── IDP_SESSION = "hash-a..."
│
└── /tenant-b/
    ├── IDP_IDENTITY = "session-id-for-tenant-b"
    └── IDP_SESSION = "hash-b..."
```

これにより、同一ブラウザで複数テナントに独立してログインできます。

## シングルサインオン（SSO）

OPSessionにより、一度のログインで複数のアプリケーションにアクセスできます。

```
1. ユーザーがアプリAにアクセス
   └─ ログインしてOPSession作成 → ClientSession(A)作成

2. ユーザーがアプリBにアクセス
   └─ OPSessionが有効なので再認証不要 → ClientSession(B)作成

3. ユーザーがアプリCにアクセス
   └─ OPSessionが有効なので再認証不要 → ClientSession(C)作成
```

### max_ageパラメータ

アプリケーションは認可リクエストで`max_age`パラメータを指定することで、最後の認証からの経過時間を制限できます。

```
max_age=300  → 最後の認証から5分以上経過していたら再認証を要求
```

### prompt=loginパラメータ

`prompt=login`を指定すると、既存のセッションを無視して再認証を要求できます。

## ログアウト

idp-serverは、OIDC仕様に準拠した複数のログアウト方式をサポートしています。

### RP-Initiated Logout

アプリケーション（RP）からログアウトを開始する方式です。

```
1. ユーザーがアプリでログアウトボタンをクリック
2. アプリがIdPの/logoutエンドポイントにリダイレクト
3. IdPがセッションを終了
4. IdPが指定されたURLにリダイレクト
```

### Back-Channel Logout

IdPからアプリケーションにサーバー間通信でログアウトを通知する方式です。

```
1. ユーザーがIdPまたは他のアプリでログアウト
2. IdPが各アプリのbackchannel_logout_uriにLogout Tokenを送信
3. 各アプリがローカルセッションを終了
```

**特徴**:
- ユーザーのブラウザを経由しない（サーバー間通信）
- 信頼性が高い
- アプリがオフラインでも後で処理可能

### Front-Channel Logout

IdPからアプリケーションにブラウザ経由でログアウトを通知する方式です。

```
1. ユーザーがIdPでログアウト
2. IdPがログアウト確認ページを表示
3. ページ内のiframeで各アプリのfrontchannel_logout_uriを読み込み
4. 各アプリがローカルセッションを終了
```

**特徴**:
- ユーザーのブラウザを経由
- Cookie削除などブラウザ側の処理が可能
- ブラウザの制限（3rdパーティCookieブロック等）の影響を受ける

## セッションストレージ

idp-serverは、Redisをセッションストレージとして使用します。

### なぜRedisか

| 要件 | Redisの利点 |
|:---|:---|
| **高速アクセス** | インメモリDB |
| **TTL（有効期限）** | ネイティブサポート |
| **複合検索** | セカンダリインデックス |
| **分散環境** | クラスタ構成対応 |

### インデックス構造

効率的な検索のため、以下のインデックスを作成します。

```
# OPSession
op_session:{tenantId}:{opSessionId}           # メインデータ
idx:tenant:{tenantId}:sub:{sub}               # ユーザー別検索

# ClientSession
client_session:{tenantId}:{sid}               # メインデータ
idx:tenant:{tenantId}:op_session:{opSessionId} # OPSession別検索
idx:tenant:{tenantId}:sub:{sub}               # ユーザー別検索
idx:tenant:{tenantId}:client:{clientId}:sub:{sub} # クライアント×ユーザー検索
```

## セキュリティ機能

### セッション固定攻撃対策

認証成功時にセッションIDを再生成します。

### CSRF対策

- **SameSite Cookie属性**: `Lax`または`Strict`でクロスサイトリクエストを制限
- **AUTH_SESSION Cookie**: 認可フローの乗っ取りを防止

### Cookie属性

| 属性 | 設定 | 目的 |
|:---|:---|:---|
| **HttpOnly** | IDP_IDENTITY: Yes | XSSによるCookie盗難防止 |
| **Secure** | Yes | HTTPS通信のみ |
| **SameSite** | Lax | CSRF攻撃軽減 |

### 認証トランザクションとのセッションバインディング

認可フロー全体を通じて、同一ブラウザセッションであることを保証する仕組みです。

```
認可リクエスト開始
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ AuthenticationTransaction                            │
│   authSessionId: "xyz789"  ←── 認可リクエスト時に生成 │
└─────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────┐
│ AUTH_SESSION Cookie: "xyz789"                        │
│   ブラウザに設定（HttpOnly, Secure, SameSite=Lax）   │
└─────────────────────────────────────────────────────┘
    │
    ▼
認証・認可の各エンドポイントで検証
    POST /password-authentication → validateAuthSession()
    POST /authorize              → validateAuthSession()
    POST /authorize-with-session → validateAuthSession()
```

- **目的**: 認可フローハイジャック攻撃の防止
- **制御**: 認証ポリシーの`auth_session_binding_required`で有効/無効を設定（デフォルト: 有効）
- **例外**: CIBA等のnon-browserフローでは自動的にスキップ

### 認証ポリシー整合性の検証

SSOセッション再利用時に、認証ポリシーの要件を満たしているかを検証する仕組みです。

```
【問題となるシナリオ】

1. パスワードのみのクライアントでログイン
   └─ OPSession作成（パスワード認証のみ）

2. MFA必須のクライアントにアクセス
   └─ OPSessionが有効なのでSSOが可能に見える

3. authorize-with-session を実行
   └─ [対策なし] MFAをバイパスして認可される ❌
   └─ [対策あり] 認証ポリシーの条件を満たさず拒否 ✓
```

この対策により、異なる認証強度を要求するクライアント間でのセッション再利用を適切に制御します。

- **仕組み**: OPSessionに認証結果（interactionResults）を保存し、authorize-with-session時に認証ポリシーのsuccessConditionsを再評価
- **結果**: 認証ポリシーの条件を満たさない場合、セキュリティイベントを発行し認可を拒否

## 関連ドキュメント

- [セッション管理 実装ガイド](../../content_06_developer-guide/04-implementation-guides/oauth-oidc/session-management.md) - 実装詳細
- [トークン管理](../04-tokens-claims/concept-02-token-management.md) - トークンとセッションの使い分け
- [マルチテナント](../01-foundation/concept-01-multi-tenant.md) - テナント分離の仕組み

## 関連仕様

- [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html)
- [OpenID Connect RP-Initiated Logout 1.0](https://openid.net/specs/openid-connect-rpinitiated-1_0.html)
- [OpenID Connect Back-Channel Logout 1.0](https://openid.net/specs/openid-connect-backchannel-1_0.html)
- [OpenID Connect Front-Channel Logout 1.0](https://openid.net/specs/openid-connect-frontchannel-1_0.html)
