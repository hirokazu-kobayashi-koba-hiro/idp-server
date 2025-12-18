# JWT入門 - OAuth/OIDCにおけるJWTの役割

## このドキュメントの目的

**JWT（JSON Web Token）** がOAuth 2.0/OpenID Connectでどのように使われるかを理解し、なぜJWTが必要かを把握することが目標です。

---


## JWTとは

**JWT（JSON Web Token）**:
- **JWSの一種**（署名付きトークン）
- JSON形式のデータを署名付きで送受信
- 改ざん検知が可能
- 自己完結型（トークン自体に情報を含む）

**重要**: JWTは署名されているが、**暗号化されていない**（秘匿性なし）

---

## OAuth/OIDCにおけるJWTの使われ方

### 1. ID Token（OpenID Connect）

**役割**: 認証の証明

```
ユーザーがログイン完了
  ↓
認可サーバーがID Token発行
  ↓
クライアントがID Tokenを受け取る
  ↓
ID Tokenの中身を見る:
  - sub: ユーザーID
  - email: メールアドレス
  - name: 名前
```

**なぜJWTか**:
- ✅ クライアントがトークンの中身を読める
- ✅ 署名により改ざん検知
- ✅ 認可サーバーに問い合わせ不要（自己完結）

---

### 2. Access Token

**役割**: API アクセスの認可

```
クライアント → Resource Server（API）
              （Access Token付き）
  ↓
Resource ServerがAccess Tokenを検証
  ↓
Access Tokenの中身を見る:
  - sub: ユーザーID
  - scope: 権限（read, write等）
  - exp: 有効期限
  ↓
権限があればAPI実行
```

**2つの形式**:

#### JWTフォーマット（Structured Token）

```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyIsInNjb3BlIjoicmVhZCJ9.Signature

メリット:
- Resource Serverが認可サーバーに問い合わせ不要
- トークンの中身をその場で検証
- 高速
```

#### Opaqueフォーマット（Reference Token）

```
random-string-abc-xyz-123

メリット:
- トークンの中身が見えない
- 失効が即座に反映
- サイズが小さい

デメリット:
- Resource Serverが認可サーバーに問い合わせ必要（Token Introspection）
```

**どちらを使うか**: 実装による

---

### 3. Refresh Token

**役割**: Access Token再取得

```
Access Token有効期限切れ
  ↓
クライアント → 認可サーバー
              （Refresh Token付き）
  ↓
認可サーバーがRefresh Token検証
  ↓
新しいAccess Token発行
```

**形式**: JWT または Opaque

**推奨**: Opaque（失効管理が容易）

---

## なぜJWTが必要か

### 従来の方式: セッションID

```
[クライアント]
  ↓ 1. ログイン
[サーバー]
  ↓ 2. セッションID発行（ランダム文字列）
  ↓ 3. サーバー側でセッション情報を保存
[クライアント]
  ↓ 4. セッションIDをCookieに保存
  ↓ 5. API呼び出し（セッションID付き）
[サーバー]
  ↓ 6. セッションIDでサーバー側のセッション情報を検索
  ↓ 7. ユーザー情報取得
  ↓ 8. API実行
```

**問題点**:
- ❌ サーバー側でセッション保存が必要（メモリ、Redis等）
- ❌ スケールアウト時にセッション共有が必要
- ❌ マイクロサービスでは各サービスがセッション情報にアクセス必要

---

### JWTの方式: 自己完結型

```
[クライアント]
  ↓ 1. ログイン
[認可サーバー]
  ↓ 2. JWT発行（ユーザー情報を含む）
[クライアント]
  ↓ 3. JWTを保存
  ↓ 4. API呼び出し（JWT付き）
[Resource Server]
  ↓ 5. JWTの署名検証（公開鍵で）
  ↓ 6. JWTの中身を読む（ユーザーID、権限等）
  ↓ 7. API実行（サーバー側のセッション不要）
```

**メリット**:
- ✅ サーバー側でセッション保存不要（ステートレス）
- ✅ スケールアウトが容易（サーバー追加のみ）
- ✅ マイクロサービス向き（各サービスが独立してJWT検証）

---

## JWTの構造（概要）

### 3部構成

```
Header.Payload.Signature

例:
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9
.
eyJzdWIiOiJ1c2VyLTEyMyIsImV4cCI6MTUxNjI0MjYyMn0
.
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

**Part 1 (Header)**: 署名アルゴリズム（RS256、ES256等）
**Part 2 (Payload)**: ユーザー情報、有効期限等
**Part 3 (Signature)**: 改ざん検知用の署名

詳細: [JWTの構造と検証方法](./jwt-structure.md)

---

## OAuth/OIDCフローでのJWT

### Authorization Code Flow（OIDC）

```
1. ユーザーがログイン
   ↓
2. 認可サーバーがAuthorization Code発行
   ↓
3. クライアントがToken Request
   ↓
4. 認可サーバーがトークン発行:
   - Access Token（JWT）
   - ID Token（JWT）← 必ずJWT
   - Refresh Token（Opaque）
   ↓
5. クライアントがID Tokenを検証:
   - 署名検証（公開鍵で）
   - ユーザー情報取得（sub、email、name等）
```

**ID TokenはJWT必須**: OpenID Connect Core 1.0で規定

---

## JWTのメリット・デメリット

### メリット

1. **自己完結型**
   - トークンの中身に情報を含む
   - 認可サーバーへの問い合わせ不要

2. **ステートレス**
   - サーバー側でセッション保存不要
   - スケールアウトが容易

3. **標準化**
   - RFC準拠
   - 多くのライブラリがサポート

### デメリット

1. **失効が困難**
   - トークンが自己完結型のため、即座の失効が難しい
   - 有効期限まで有効（Token Revocation List必要）

2. **サイズが大きい**
   - Base64エンコード + 署名でサイズ増加
   - Opaqueトークンより大きい

3. **秘匿性なし**
   - 署名されているが暗号化されていない
   - 誰でもBase64デコードで中身が見える

---

## JWTを使うべきケース

### 使うべき

- ✅ ID Token（OIDC必須）
- ✅ マイクロサービス間認証（自己完結型が有利）
- ✅ ステートレスAPI（サーバー側セッション不要）

### 使わなくても良い

- ⚠️ Refresh Token（Opaqueで十分）
- ⚠️ 頻繁に失効する必要があるトークン
- ⚠️ 機密情報を含むトークン（JWEを検討）

---

## まとめ

### 学んだこと

- ✅ JWTはOAuth 2.0/OIDCで広く使われる
- ✅ ID TokenはJWT必須
- ✅ Access TokenはJWTまたはOpaque
- ✅ JWTは自己完結型（サーバー側セッション不要）
- ✅ JWTのメリット（ステートレス、スケーラビリティ）
- ✅ JWTのデメリット（失効困難、サイズ、秘匿性なし）

### OAuth/OIDCでのJWTの位置づけ

```
OAuth 2.0/OpenID Connect
  ├─ ID Token → JWT必須
  ├─ Access Token → JWT or Opaque
  └─ Refresh Token → Opaque推奨
```

### 次に読むべきドキュメント

1. [JWTの構造と検証方法](./jwt-structure.md) - 3部構成の詳細
2. [署名アルゴリズムの選び方](./signature-algorithms.md) - RS256 vs ES256 vs HS256
3. [JWS/JWEの基礎](./jws-jwe-basics.md) - 署名と暗号化

---

**最終更新**: 2025-12-18
**対象**: IDサービス開発初心者
