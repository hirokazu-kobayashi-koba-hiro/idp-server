# JWTのベストプラクティス

## このドキュメントの目的

JWT設計・実装における**ベストプラクティス**を理解し、セキュアで効率的なトークン設計ができるようになることが目標です。

---

## 1. 有効期限の設定

### Access Token

**推奨**: **1時間以下**

**理由**:
- トークン漏洩時の被害を最小化
- 短すぎると頻繁なRefresh Token使用（パフォーマンス低下）
- 長すぎるとトークン失効が困難

**シーン別の推奨値**:
- 通常のWebアプリ: 1時間
- モバイルアプリ: 30分
- 高セキュリティ（金融機関）: 15分
- 内部API: 5分

### ID Token

**推奨**: **5分**

**理由**:
- ID Tokenは認証完了直後のみ使用
- Access Tokenより短くて良い
- クライアント側で長期保存する必要がない

### Refresh Token

**推奨**: **30日**

**理由**:
- 長期セッション維持用
- 短すぎるとユーザーが頻繁に再ログイン
- 長すぎるとセキュリティリスク

**シーン別の推奨値**:
- Webアプリ: 30日
- モバイルアプリ: 90日（ユーザーが毎日使う前提）
- 高セキュリティ: 7日

---

## 2. 必要最小限のクレーム

### ❌ 悪い例: 過剰なクレーム

```json
{
  "sub": "user-12345",
  "name": "John Doe",
  "email": "john@example.com",
  "phone": "+1-555-1234",
  "address": "123 Main St, City, Country",
  "birth_date": "1990-01-01",
  "credit_card": "4111-1111-1111-1111",  ← 危険
  "ssn": "123-45-6789",  ← 危険
  "password_hash": "...",  ← 危険
  ...
}
```

**問題**:
- トークンサイズ肥大化
- 機密情報が含まれる（JWTは暗号化されていない）
- ネットワーク盗聴で情報漏洩

### ✅ 良い例: 必要最小限

```json
{
  "sub": "user-12345",
  "aud": "client-abc",
  "exp": 1516242622,
  "iat": 1516239022,
  "scope": "openid profile email"
}
```

**原則**:
- 必要なクレームのみ
- 機密情報は含めない
- 詳細情報が必要ならUserInfo Endpointで取得

---

## 3. 機密情報をJWTに入れない

### ❌ 入れてはいけないもの

- パスワード、パスワードハッシュ
- クレジットカード番号
- 社会保障番号
- API Secret Key
- セッションID（Opaque Tokenを使用）

### ✅ 入れて良いもの

- ユーザーID（sub）
- 名前、メールアドレス（公開情報）
- 権限・ロール（非機密）
- 有効期限、発行者

**理由**: JWTは署名されているが、**暗号化されていない**（誰でもBase64URLデコードで読める）

**機密情報が必要な場合**: JWE（暗号化）を使用

---

## 4. audクレームの検証

### ❌ 悪い例: aud検証なし

```javascript
// 危険: audを検証しない
const payload = verifySignature(jwt, publicKey);
console.log(payload.sub);  // そのまま使用
```

**問題**:
- 他のクライアント向けのトークンを使用可能
- トークンの横流しリスク

### ✅ 良い例: aud検証

```javascript
// 安全: audを検証
const payload = verifySignature(jwt, publicKey);

if (payload.aud !== 'my-client-id') {
  throw new Error('Invalid audience');
}

console.log(payload.sub);
```

**効果**: 他のクライアント向けのトークンを拒否

---

## 5. expクレームの検証

### ❌ 悪い例: exp検証なし

```javascript
// 危険: 有効期限を検証しない
const payload = verifySignature(jwt, publicKey);
console.log(payload.sub);  // 期限切れでも使用
```

**問題**:
- 期限切れトークンを使用可能
- トークン失効が機能しない

### ✅ 良い例: exp検証

```javascript
// 安全: 有効期限を検証
const payload = verifySignature(jwt, publicKey);

if (payload.exp < Date.now() / 1000) {
  throw new Error('Token expired');
}

console.log(payload.sub);
```

---

## 6. algヘッダーの検証

### ❌ 悪い例: algを信用

```javascript
// 危険: Headerのalgをそのまま使用
const header = JSON.parse(base64UrlDecode(parts[0]));
const alg = header.alg;  // 攻撃者が"none"に改ざん可能

if (alg === 'none') {
  // 署名検証スキップ ← 危険
}
```

**攻撃**: `alg: none`攻撃

### ✅ 良い例: 許可されたalgのみ

```javascript
// 安全: 許可されたalgのみ受け入れ
const allowedAlgs = ['RS256', 'ES256'];

if (!allowedAlgs.includes(header.alg)) {
  throw new Error('Unsupported algorithm');
}

// alg: noneは拒否される
```

---

## 7. トークンの保存場所

### ❌ 悪い例: localStorage

```javascript
// 危険: localStorageに保存
localStorage.setItem('access_token', accessToken);
```

**問題**:
- XSS攻撃でJavaScriptからアクセス可能
- 同じドメインの全ページからアクセス可能

### ✅ 良い例: HttpOnly Cookie

```
Set-Cookie: access_token=xxx; HttpOnly; Secure; SameSite=Strict
```

**メリット**:
- JavaScriptからアクセス不可（XSS対策）
- HTTPSのみで送信（Secure）
- CSRF対策（SameSite）

**または**: メモリ内変数（SPAの場合）
```javascript
let accessToken = null;  // ページリロードで消える
```

---

## 8. Refresh Token Rotation

### ❌ 悪い例: Refresh Token再利用

```
同じRefresh Tokenを何度も使用可能
↓
Refresh Token盗聴 → 攻撃者が永続的にアクセス
```

### ✅ 良い例: Refresh Token Rotation

```
1. Refresh Token使用時に新しいRefresh Tokenを発行
2. 古いRefresh Tokenを無効化
3. 同じRefresh Tokenの再利用を検知 → 全トークン失効
```

**効果**: Refresh Token盗聴を検知・対応

---

## 9. jtiクレームでトークン追跡

### 用途

**jti（JWT ID）**: トークンの一意な識別子

```json
{
  "sub": "user-12345",
  "jti": "jwt-abc-xyz-123",
  "exp": 1516242622
}
```

**活用例**:
- トークン失効リスト（Revocation）
- トークン再利用検知
- 監査ログでのトークン追跡

---

## 10. クレームの命名規則

### Public Claim Names

**登録済みクレーム名を使う**: iss、sub、aud、exp、iat等

### Private Claim Names

**カスタムクレームには名前空間を使う**:

❌ 悪い例:
```json
{
  "sub": "user-12345",
  "role": "admin",  ← 他のシステムとクレーム名が衝突
  "dept": "sales"
}
```

✅ 良い例:
```json
{
  "sub": "user-12345",
  "https://example.com/claims/role": "admin",
  "https://example.com/claims/dept": "sales"
}
```

**効果**: クレーム名の衝突を防止

---

## セキュリティチェックリスト

JWT実装時に確認すべき項目：

### 発行側（Authorization Server）
- [ ] 有効期限を適切に設定（Access Token: 1時間以下）
- [ ] 機密情報をPayloadに含めない
- [ ] 推奨アルゴリズムを使用（RS256、ES256）
- [ ] kidヘッダーで鍵を識別
- [ ] jtiクレームでトークン追跡

### 検証側（Resource Server、Client）
- [ ] 署名を検証
- [ ] expクレームを検証
- [ ] issクレームを検証
- [ ] audクレームを検証
- [ ] algヘッダーを検証（許可されたalgのみ）
- [ ] トークンをlocalStorageに保存しない

---

## まとめ

### 学んだこと

- ✅ 有効期限の設定（Access Token: 1時間、ID Token: 5分、Refresh Token: 30日）
- ✅ 必要最小限のクレーム
- ✅ 機密情報をJWTに入れない
- ✅ aud/exp検証の重要性
- ✅ alg: none攻撃対策
- ✅ トークン保存場所（HttpOnly Cookie推奨）
- ✅ Refresh Token Rotation
- ✅ jtiクレームでトークン追跡

### JWT設計の原則

1. **最小限**: 必要なクレームのみ
2. **短命**: 短い有効期限
3. **検証**: 全クレームを検証
4. **秘匿性なし**: 機密情報は入れない
5. **追跡可能**: jtiで追跡

---

**最終更新**: 2025-12-18
**対象**: IDサービス開発初心者
