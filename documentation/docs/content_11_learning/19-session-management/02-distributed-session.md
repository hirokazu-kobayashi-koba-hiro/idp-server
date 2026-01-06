# 分散セッション管理

## このドキュメントの目的

**分散環境**でのセッション管理を理解し、HttpSession経由と直接アクセスの違いを学ぶことが目標です。

### 所要時間
約30分

### 学べること
- なぜ分散セッションが必要か
- Spring Session（HttpSession経由Redis）の仕組み
- 直接Redisアクセスとの違い
- それぞれの使い分け

---

## なぜ分散セッションが必要か

### 単一サーバーの限界

```
【単一サーバー構成】

ブラウザ ────→ Webサーバー
               │
               ▼
          ┌─────────┐
          │ Session │
          │ Storage │
          │ (メモリ) │
          └─────────┘

問題なし！セッションはサーバーメモリに保存
```

### 複数サーバーでの問題

```
【複数サーバー構成（ロードバランサー）】

                    ┌─────────────────┐
                    │ Load Balancer   │
                    └────────┬────────┘
                    ┌────────┼────────┐
                    ▼        ▼        ▼
              ┌────────┐ ┌────────┐ ┌────────┐
              │Server A│ │Server B│ │Server C│
              │        │ │        │ │        │
              │Session:│ │Session:│ │Session:│
              │user123 │ │ (なし) │ │ (なし) │
              └────────┘ └────────┘ └────────┘

リクエスト1: Server A → ログイン成功、セッション作成
リクエスト2: Server B → セッションがない！ログアウト状態？
```

**問題**:
- 各サーバーは自分のメモリにセッションを保持
- ロードバランサーが異なるサーバーに振り分けるとセッションが見つからない

### 解決策: セッション共有

```
【分散セッション構成】

                    ┌─────────────────┐
                    │ Load Balancer   │
                    └────────┬────────┘
                    ┌────────┼────────┐
                    ▼        ▼        ▼
              ┌────────┐ ┌────────┐ ┌────────┐
              │Server A│ │Server B│ │Server C│
              └────┬───┘ └────┬───┘ └────┬───┘
                   │          │          │
                   └──────────┼──────────┘
                              ▼
                    ┌─────────────────┐
                    │      Redis      │
                    │                 │
                    │ session:abc123  │
                    │   user: alice   │
                    │   cart: [...]   │
                    └─────────────────┘

どのサーバーでも同じセッションにアクセス可能
```

**Redisを使う理由**:
- インメモリで高速
- TTL（有効期限）をネイティブサポート
- クラスタ構成で高可用性

---

## Redisへのアクセス方法: 2つのパターン

Redisにセッションを保存する方法は2つあります。

```
【2つのアクセスパターン】

┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  パターン1: HttpSession経由（Spring Session）                    │
│                                                                 │
│  アプリケーション → HttpSession API → Spring Session → Redis   │
│                                                                 │
│  ・標準APIを使用（コード変更不要）                               │
│  ・Servletコンテナのセッション管理を「差し替え」                 │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  パターン2: 直接アクセス（独自実装）                             │
│                                                                 │
│  アプリケーション → 独自Repository → Redis Client → Redis       │
│                                                                 │
│  ・完全に自由なデータ構造                                        │
│  ・任意のキーで検索可能                                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## パターン1: HttpSession経由（Spring Session）

### Spring Sessionとは

**Spring Session**: HttpSession APIの実装を差し替えるフレームワーク

```
【通常のHttpSession】

アプリケーション
    │
    ▼
HttpSession API
    │
    ▼
Servlet Container（Tomcat等）
    │
    ▼
サーバーメモリ（JVM）


【Spring Session】

アプリケーション
    │
    ▼
HttpSession API  ← 同じAPIを使用（コード変更不要）
    │
    ▼
Spring Session（実装を差し替え）
    │
    ▼
Redis
```

**ポイント**:
- アプリケーションコードは変更不要
- 設定のみでRedisにセッションを保存
- 既存のServlet標準に従う

### 動作の流れ

```
【リクエスト処理】

1. リクエスト受信
   ┌────────────────────────────────────────────────────────────┐
   │ Cookie: SESSION=abc123                                     │
   │                                                            │
   │ Spring Session Filter:                                     │
   │   ① Cookieからセッションキーを取得                         │
   │   ② Redisからセッションデータを取得                        │
   │   ③ HttpSessionオブジェクトとしてラップ                    │
   └────────────────────────────────────────────────────────────┘
                            │
                            ▼
2. アプリケーション処理
   ┌────────────────────────────────────────────────────────────┐
   │ 通常通りHttpSession APIを使用                              │
   │                                                            │
   │ session.getAttribute("user") → ユーザー情報取得           │
   │ session.setAttribute("cart", items) → カート保存          │
   └────────────────────────────────────────────────────────────┘
                            │
                            ▼
3. レスポンス送信
   ┌────────────────────────────────────────────────────────────┐
   │ Spring Session Filter:                                     │
   │   ① セッションの変更をRedisに保存                          │
   │   ② Set-Cookie送信（必要に応じて）                         │
   └────────────────────────────────────────────────────────────┘
```

### Redisのデータ構造

```
【Spring SessionのRedisキー】

spring:session:sessions:abc123
├── creationTime: 1704067200000
├── lastAccessedTime: 1704070800000
├── maxInactiveInterval: 1800
├── sessionAttr:user → { id: "001", name: "alice" }
├── sessionAttr:cart → ["item1", "item2"]
└── sessionAttr:loginTime → "2024-01-01T10:00:00"

spring:session:sessions:expires:abc123
└── (TTLで自動削除用)
```

**特徴**:
- セッションIDがキーの一部
- 属性は `sessionAttr:` プレフィックス付き
- TTLで自動的に期限切れ

---

## パターン2: 直接Redisアクセス

### 直接アクセスとは

**HttpSession APIを使わず、アプリケーションが直接Redisにアクセス**

```
【直接Redisアクセス】

アプリケーション
    │
    ▼
独自のSession Repository
    │
    ▼
Redis Client（Lettuce, Jedis等）
    │
    ▼
Redis
```

**ポイント**:
- データ構造を自由に設計
- 任意のキーでインデックス作成可能
- 検索条件を柔軟に設定

### Redisのデータ構造

```
【直接アクセスのRedisキー】

# メインデータ
op_session:tenant-001:session-abc
├── id: "session-abc"
├── sub: "user-123"
├── authTime: "2024-01-01T10:00:00Z"
├── acr: "urn:example:acr:mfa"
└── expiresAt: "2024-01-01T20:00:00Z"

client_session:tenant-001:sid-xyz
├── sid: "sid-xyz"
├── opSessionId: "session-abc"
├── clientId: "web-app"
└── scopes: ["openid", "profile"]

# 検索用インデックス（Set型）
idx:tenant:tenant-001:sub:user-123
└── ["session-abc", "session-def"]

idx:tenant:tenant-001:op_session:session-abc
└── ["sid-xyz", "sid-uvw"]
```

**特徴**:
- 自由なキー設計
- インデックスで複合検索可能
- 階層的な関連付け

---

## 比較: HttpSession経由 vs 直接アクセス

### 機能比較

| 機能 | HttpSession経由 | 直接アクセス |
|:----|:---------------|:------------|
| **API** | HttpSession（標準） | 独自Repository |
| **コード変更** | 不要（設定のみ） | Repository実装が必要 |
| **データ構造** | key-value属性 | 自由（Hash, Set等） |
| **TTL管理** | Spring Session管理 | アプリで制御 |
| **検索** | セッションIDのみ | 任意のインデックス |

### アクセスパターンの違い

```
【HttpSession経由】

検索: セッションID → セッションデータ
      ↓
      OK

検索: ユーザーID → そのユーザーの全セッション
      ↓
      ❌ できない！セッションIDを知らないとアクセス不可


【直接アクセス】

検索: セッションID → セッションデータ
      ↓
      OK

検索: ユーザーID → そのユーザーの全セッション
      ↓
      ✅ OK！インデックスで検索可能
      idx:tenant:xxx:sub:user-123 → ["session-a", "session-b"]
```

### データモデルの違い

```
【HttpSession（1ブラウザ = 1セッション）】

Browser ──── SESSION=abc123 ────→ セッションデータ
                                  ├── user
                                  ├── cart
                                  └── preferences

→ フラットな構造
→ クライアント（RP）の概念なし


【直接アクセス（1ユーザー = N クライアント）】

Browser ──── IDP_IDENTITY=op-001 ────→ OPSession
                                       ├── sub: user-123
                                       ├── authTime
                                       └── clientSessions:
                                           ├── ClientSession(App A)
                                           │   ├── sid: sid-001
                                           │   └── clientId: app-a
                                           │
                                           └── ClientSession(App B)
                                               ├── sid: sid-002
                                               └── clientId: app-b

→ 階層的な構造
→ 複数クライアントのセッションを管理
```

---

## 使い分け

### HttpSession経由が適切な場合

**一般的なWebアプリケーション（RP側）**

- ログイン状態の管理
- ショッピングカート
- フォームの一時保存
- 既存コードの移行（コード変更不要）

**特徴**:
- セッションIDでのみアクセス
- Spring Securityとの統合が容易
- シンプルなセッション管理

### 直接アクセスが適切な場合

**IdP（Identity Provider）**

- ユーザーIDで全セッション検索（Back-Channel Logout）
- クライアントIDでセッション検索
- sidクレームでセッション特定
- 階層的なセッション構造

**特徴**:
- 任意のキーでアクセス
- インデックスによる複合検索
- データ構造の自由度が高い

---

## なぜIdPには直接アクセスが必要か

### OIDC Session Managementの要件

```
【Back-Channel Logout の流れ】

1. ユーザーがログアウト
   POST /logout (Cookie: SESSION=abc123)

2. IdPはこのユーザーの全クライアントセッションを検索
   「ユーザー user-123 の全セッションを取得」

   HttpSession経由: ❌ 不可能
   直接アクセス:    ✅ インデックスで検索可能

3. 各クライアントにLogout Tokenを送信
   POST https://client-a.com/backchannel-logout
   POST https://client-b.com/backchannel-logout
   POST https://client-c.com/backchannel-logout
```

### 必要な検索パターン

| 検索条件 | HttpSession経由 | 直接アクセス |
|:--------|:---------------|:------------|
| セッションID → データ | ✅ | ✅ |
| ユーザーID → 全セッション | ❌ | ✅ |
| クライアントID → セッション | ❌ | ✅ |
| sid → 特定のClientSession | ❌ | ✅ |

---

## まとめ

### 重要ポイント

| 項目 | HttpSession経由 | 直接アクセス |
|:----|:---------------|:------------|
| **用途** | 一般的なWebアプリ | IdP、複雑なセッション管理 |
| **検索** | セッションIDのみ | 任意のキー |
| **データ構造** | フラット | 階層的・自由 |
| **コード変更** | 不要 | Repository実装必要 |
| **Spring連携** | 自動 | 手動 |

### 選択基準

```
セッションIDでのみアクセス？
├── Yes → HttpSession経由（Spring Session）
│         シンプル、既存コード互換
│
└── No → ユーザーIDやクライアントIDで検索必要？
         ├── Yes → 直接アクセス
         │         IdP、複合検索が必要な場合
         │
         └── No → 要件を再確認
```

### 次のステップ

次に読むべきドキュメント:
- [03. OIDC Session Management](./03-oidc-session-management.md) - IdPのセッション管理仕様
- [04. IdPセッション設計パターン](../../../../secret/04-idp-session-patterns.md) - Keycloak等の設計

---

## 関連リンク

- [Spring Session Documentation](https://docs.spring.io/spring-session/reference/)
- [Redis Data Types](https://redis.io/docs/data-types/)
