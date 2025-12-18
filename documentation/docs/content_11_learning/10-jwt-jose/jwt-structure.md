# JWTの構造と検証方法

## このドキュメントの目的

**JWT（JSON Web Token）** の構造を理解し、検証方法を把握することが目標です。

---

## JWTとは

**JWT（JSON Web Token）**:
- JSON形式のデータをBase64URLエンコードしたトークン
- 署名付き（改ざん検知）
- OAuth 2.0/OIDCで広く使用（Access Token、ID Token、Refresh Token）

**RFC**: [RFC 7519 - JSON Web Token](https://datatracker.ietf.org/doc/html/rfc7519)

---

## JWTの3部構成

JWTは3つの部分をドット（.）で連結した形式：

```
Header.Payload.Signature
```

### 実際のJWT例

```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

↓ 3つの部分に分解

Part 1: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9
Part 2: eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ
Part 3: SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

---

## Part 1: Header（ヘッダー）

**役割**: 署名アルゴリズムとトークンタイプを指定

**Base64URLデコード前（JSON）**:
```json
{
  "alg": "RS256",
  "typ": "JWT"
}
```

**主要フィールド**:
- `alg`: 署名アルゴリズム（RS256、ES256、HS256等）
- `typ`: トークンタイプ（"JWT"固定）
- `kid`: 鍵ID（複数の署名鍵を使い分ける場合）

---

## Part 2: Payload（ペイロード）

**役割**: トークンの中身（クレーム）

**Base64URLデコード前（JSON）**:
```json
{
  "sub": "1234567890",
  "name": "John Doe",
  "iat": 1516239022,
  "exp": 1516242622
}
```

### 標準クレーム（Registered Claims）

| クレーム | 説明 | 例 |
|---------|------|-----|
| `iss` | 発行者（Issuer） | "https://idp.example.com" |
| `sub` | 主体（Subject、ユーザーID） | "user-12345" |
| `aud` | 対象者（Audience、このトークンを使うべきクライアント） | "client-abc" |
| `exp` | 有効期限（Expiration Time、UNIX時刻） | 1516242622 |
| `iat` | 発行時刻（Issued At、UNIX時刻） | 1516239022 |
| `nbf` | 有効開始時刻（Not Before） | 1516239022 |
| `jti` | JWT ID（一意な識別子） | "jwt-id-xyz" |

### カスタムクレーム

アプリケーション固有のクレームを追加可能：

```json
{
  "sub": "user-12345",
  "name": "John Doe",
  "email": "john@example.com",
  "roles": ["admin", "user"],
  "tenant_id": "tenant-abc"
}
```

**注意**: 機密情報をJWTに入れない（JWTは署名されているが暗号化されていない）

---

## Part 3: Signature（署名）

**役割**: Header + Payloadの改ざん検知

### 署名の生成方法

```
Signature = Sign(
  Base64URL(Header) + "." + Base64URL(Payload),
  SecretKey or PrivateKey
)
```

**例（RS256: RSA-SHA256の場合）**:
```
1. Header + Payloadを連結
   eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ

2. RSA秘密鍵で署名
   Signature = RSA-SHA256(data, privateKey)

3. Base64URLエンコード
   SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

---

## JWTの検証方法

### ステップ1: JWTを3部分に分割

```javascript
const parts = jwt.split('.');
const header = JSON.parse(base64UrlDecode(parts[0]));
const payload = JSON.parse(base64UrlDecode(parts[1]));
const signature = parts[2];
```

### ステップ2: 署名検証

```
1. Header + Payloadを連結
2. 公開鍵で署名を検証
3. 改ざんされていないか確認
```

**RS256（RSA）の場合**:
```javascript
const isValid = RSA_SHA256_Verify(
  header + '.' + payload,
  signature,
  publicKey
);
```

### ステップ3: クレーム検証

```javascript
// 有効期限チェック
if (payload.exp < Date.now() / 1000) {
  throw new Error('Token expired');
}

// 発行者チェック
if (payload.iss !== 'https://idp.example.com') {
  throw new Error('Invalid issuer');
}

// 対象者チェック
if (payload.aud !== 'my-client-id') {
  throw new Error('Invalid audience');
}
```

---

## JWT.ioでの確認

### 使い方

1. [JWT.io](https://jwt.io/)にアクセス
2. JWTを貼り付け
3. Header、Payloadが自動デコード
4. 公開鍵を入力して署名検証

### 確認できること

- ✅ Headerの内容（alg、typ、kid）
- ✅ Payloadの内容（全クレーム）
- ✅ 有効期限（expを人間が読める形式で表示）
- ✅ 署名の有効性（公開鍵で検証）

---

## JWTのセキュリティ

### 1. JWTは暗号化されていない

**重要**: JWTは署名されているが、**暗号化されていない**

```
JWTの中身は誰でも読める:
- Base64URLデコードすれば内容が見える
- 改ざんは検知できるが、秘匿性はない
```

**結論**: 機密情報をJWTに入れない

### 2. 署名検証は必須

```
悪意のある例:
1. 攻撃者がJWTのPayloadを改ざん
   {"sub": "user-12345", "role": "admin"} → {"sub": "user-12345", "role": "super-admin"}

2. 署名検証なしで使用
   → 攻撃者がsuper-admin権限を取得
```

**対策**: 必ず署名検証を実施

### 3. algヘッダーの検証

**攻撃**: `alg: none`攻撃

```
攻撃者がHeaderを改ざん:
{"alg": "RS256", "typ": "JWT"} → {"alg": "none", "typ": "JWT"}

署名検証をスキップ
```

**対策**: 許可されたalgのみ受け入れる（`alg: none`を拒否）

---

## まとめ

### 学んだこと

- ✅ JWTの3部構成（Header.Payload.Signature）
- ✅ Headerの役割（alg、typ、kid）
- ✅ Payloadの標準クレーム（iss、sub、aud、exp、iat）
- ✅ 署名の役割（改ざん検知）
- ✅ JWTの検証ステップ（署名検証 → クレーム検証）
- ✅ JWT.ioでの確認方法
- ✅ セキュリティ考慮事項（暗号化なし、署名検証必須、alg検証）

### 次に読むべきドキュメント

1. [署名アルゴリズムの選び方](./signature-algorithms.md) - RS256 vs ES256 vs HS256
2. [JWS/JWEの基礎](./jws-jwe-basics.md) - 署名と暗号化
3. [JWTのベストプラクティス](./jwt-best-practices.md) - 有効期限、クレーム設計

---

**最終更新**: 2025-12-18
**対象**: IDサービス開発初心者
