# JWS/JWEの基礎

## このドキュメントの目的

**JWS（署名）** と **JWE（暗号化）** の違いを理解し、それぞれの用途を把握することが目標です。

---

## JOSE（JSON Object Signing and Encryption）

**JOSE**:
- JSON形式のデータを**署名**または**暗号化**するための仕様群
- JWT、JWS、JWE、JWK、JWAを含む

| 仕様 | 説明 | RFC |
|------|------|-----|
| **JWT** | JSON Web Token（署名付きトークン） | RFC 7519 |
| **JWS** | JSON Web Signature（署名） | RFC 7515 |
| **JWE** | JSON Web Encryption（暗号化） | RFC 7516 |
| **JWK** | JSON Web Key（鍵表現） | RFC 7517 |
| **JWA** | JSON Web Algorithms（アルゴリズム） | RFC 7518 |

---

## JWS（JSON Web Signature）- 署名

### 目的

**データの改ざん検知**:
- データが改ざんされていないことを保証
- 発行者の正当性を確認

**秘匿性**: なし（中身は誰でも読める）

### 構造

```
Header.Payload.Signature

eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.Signature
```

**Base64URLデコードすれば中身が見える**:
- Header: `{"alg":"RS256","typ":"JWT"}`
- Payload: `{"sub":"1234567890","name":"John Doe"}`

### 署名の役割

**検証できること**:
- ✅ データが改ざんされていないか
- ✅ 正しい秘密鍵で署名されたか（公開鍵で検証）

**検証できないこと**:
- ❌ データの秘匿性（誰でも読める）

### 使用例

**ID Token（OpenID Connect）**:
```json
{
  "alg": "RS256",
  "typ": "JWT"
}
.
{
  "iss": "https://idp.example.com",
  "sub": "user-12345",
  "aud": "client-abc",
  "exp": 1516242622,
  "iat": 1516239022,
  "email": "user@example.com"
}
.
Signature
```

**特徴**:
- emailなど、秘匿性不要の情報を含む
- クライアントが読めることが前提
- 改ざんされていないことのみ保証

---

## JWE（JSON Web Encryption）- 暗号化

### 目的

**データの秘匿性**:
- データを暗号化して、特定の受信者のみが復号可能
- 改ざん検知も含む（AEAD: Authenticated Encryption with Associated Data）

### 構造

```
Header.EncryptedKey.IV.Ciphertext.AuthTag

5部構成（JWSは3部構成）
```

**Base64URLデコードしても中身は見えない**:
- Ciphertextは暗号化されている
- 復号鍵がないと読めない

### 暗号化の役割

**保証できること**:
- ✅ データの秘匿性（受信者のみ復号可能）
- ✅ データの改ざん検知
- ✅ 発行者の正当性

### 使用例

**Request Object（PAR: Pushed Authorization Request）**:

機密情報を含むAuthorization Requestを暗号化：

```
Authorization Request:
- redirect_uri（機密）
- client_id
- scope
- claims（要求するユーザー属性）

↓ JWEで暗号化

暗号化されたRequest Object
↓ PARエンドポイントに送信

request_uri取得
↓ Authorization Endpointで使用
```

**特徴**:
- redirect_uri等の機密情報を暗号化
- Authorization Serverのみが復号可能

---

## JWS vs JWE の比較

| 項目 | JWS（署名） | JWE（暗号化） |
|------|-----------|--------------|
| **目的** | 改ざん検知 | 秘匿性 + 改ざん検知 |
| **中身** | 誰でも読める | 受信者のみ読める |
| **構造** | 3部構成 | 5部構成 |
| **サイズ** | 小 | 大 |
| **計算コスト** | 低 | 高 |
| **使用例** | ID Token、Access Token | Request Object、機密データ |

---

## いつJWEを使うべきか

### 使用すべきケース

1. **機密情報を含むトークン**
   - 例: 社会保障番号、クレジットカード情報
   - JWSだと誰でも読めてしまう

2. **Request Object（PAR）**
   - Authorization Requestのパラメータを暗号化
   - redirect_uri等の機密情報を保護

3. **バックチャネル通信**
   - サーバー間でトークンを送信
   - ネットワーク盗聴から保護

### 使用不要なケース

1. **ID Token**
   - クライアントが読む必要がある
   - 機密情報を含めない設計

2. **Access Token（Opaque）**
   - ランダム文字列（中身なし）
   - 暗号化不要

3. **公開情報のみ**
   - 署名（JWS）で十分

---

## JWEの構造詳細

### 5部構成

```
Part 1: Header
  - alg: 鍵暗号化アルゴリズム（RSA-OAEP、ECDH-ES等）
  - enc: コンテンツ暗号化アルゴリズム（A256GCM等）

Part 2: Encrypted Key
  - コンテンツ暗号化鍵（CEK）を受信者の公開鍵で暗号化

Part 3: Initialization Vector（IV）
  - 暗号化の初期化ベクトル

Part 4: Ciphertext
  - 暗号化されたPayload

Part 5: Authentication Tag
  - 改ざん検知用タグ（AEAD）
```

### 暗号化・復号の流れ

**暗号化**:
```
1. ランダムなCEK（Content Encryption Key）を生成
2. CEKでPayloadを暗号化 → Ciphertext
3. CEKを受信者の公開鍵で暗号化 → Encrypted Key
4. 5部構成のJWEを生成
```

**復号**:
```
1. Encrypted Keyを秘密鍵で復号 → CEKを取得
2. CEKでCiphertextを復号 → Payload取得
3. Authentication Tagで改ざん検知
```

---

## まとめ

### 学んだこと

- ✅ JOSEはJSON署名・暗号化の仕様群
- ✅ JWS（署名）は改ざん検知、秘匿性なし
- ✅ JWE（暗号化）は秘匿性 + 改ざん検知
- ✅ JWSは3部構成、JWEは5部構成
- ✅ ID Token/Access TokenはJWS、Request ObjectはJWE
- ✅ いつJWEを使うべきか（機密情報、PAR）

### 選択のガイドライン

**JWS（署名のみ）を使う**:
- 公開情報（ID Token、Access Token）
- 改ざん検知のみ必要

**JWE（暗号化）を使う**:
- 機密情報を含む
- ネットワーク盗聴から保護

### 次に読むべきドキュメント

1. [JWTのベストプラクティス](./jwt-best-practices.md) - 有効期限、クレーム設計
2. [JOSE Handler実装ガイド](../../content_06_developer-guide/04-implementation-guides/oauth-oidc/jose-handler.md) - 実装詳細

---

**最終更新**: 2025-12-18
**対象**: IDサービス開発初心者
