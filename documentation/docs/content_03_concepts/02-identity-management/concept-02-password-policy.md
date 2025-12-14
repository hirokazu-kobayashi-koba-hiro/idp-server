# パスワードポリシー

**パスワードポリシー（Password Policy）** は、テナントごとにパスワードの要件を定義し、セキュリティ基準に準拠したパスワード管理を実現する仕組みです。

## パスワードポリシーの目的

パスワードポリシーを使うことで、以下のようなセキュリティ管理が可能になります：

- **組織のセキュリティ基準への準拠**
  OWASP、NIST SP 800-63Bなどの業界標準への対応

- **テナントごとの要件のカスタマイズ**
  金融機関は厳格、一般サービスは利便性重視など、用途に応じた設定

- **パスワード変更時の検証**
  新しいパスワードがポリシーに準拠しているかをリアルタイムで検証

- **セキュリティイベントの発行**
  パスワード変更の成功・失敗を記録し、監査証跡を確保

## パスワードポリシーの設定項目

### 基本設定

パスワードポリシーは、テナントの `identity_policy_config.password_policy` で設定します。

```json
{
  "identity_policy_config": {
    "password_policy": {
      "min_length": 8,
      "require_uppercase": false,
      "require_lowercase": false,
      "require_number": false,
      "require_special_char": false
    }
  }
}
```

### 設定可能な項目

| 項目 | 型 | デフォルト | 説明 |
|:---|:---|:---|:---|
| `min_length` | integer | 8 | 最小文字数 |
| `require_uppercase` | boolean | false | 大文字（A-Z）を含む必要があるか |
| `require_lowercase` | boolean | false | 小文字（a-z）を含む必要があるか |
| `require_number` | boolean | false | 数字（0-9）を含む必要があるか |
| `require_special_char` | boolean | false | 記号（`!@#$%^&*(),.?\":{}|&lt;&gt;`）を含む必要があるか |
| `custom_regex` | string | null | カスタム正規表現パターン（高度な検証） |
| `custom_regex_error_message` | string | null | カスタム正規表現エラー時のメッセージ |

## デフォルトポリシー

`idp-server` のデフォルトポリシーは **NIST SP 800-63B** の推奨に従い、**最小8文字のみ**を要求します。

### NIST SP 800-63B の推奨事項

NIST（アメリカ国立標準技術研究所）は、**複雑性要件よりも長さを重視**することを推奨しています：

> **推奨**: 最小8文字以上
> **非推奨**: 大文字・小文字・数字・記号の強制的な組み合わせ

**理由**:
- 複雑性要件は、ユーザーがパスワードを忘れやすくする
- 結果的に、パスワードをメモしたり、予測可能なパターン（`Password1!`）を使用してしまう
- 長いパスワード（パスフレーズ）の方が、複雑で短いパスワードより安全

### デフォルトポリシーの例

```json
{
  "identity_policy_config": {
    "password_policy": {
      "min_length": 8
    }
  }
}
```

**受け入れられるパスワード**:
- `mypassword` ✅ (8文字、複雑性不要)
- `12345678` ✅ (8文字、数字のみでもOK)
- `ALLCAPS` ❌ (7文字、短すぎる)

## ユースケース別の設定例

### 1. デフォルト（一般サービス向け）

**用途**: 一般的なWebサービス、SNS、eコマース

```json
{
  "password_policy": {
    "min_length": 8
  }
}
```

**特徴**:
- NIST推奨に準拠
- ユーザビリティ重視
- 長いパスワード（パスフレーズ）を推奨

**推奨するユーザー向けメッセージ**:
> 「8文字以上のパスワードを設定してください。
> 例: `my favorite coffee shop` のような覚えやすいフレーズがおすすめです」

### 2. 中程度のセキュリティ（企業内システム）

**用途**: 企業内ポータル、SaaS管理画面

```json
{
  "password_policy": {
    "min_length": 10,
    "require_uppercase": true,
    "require_lowercase": true,
    "require_number": true
  }
}
```

**特徴**:
- 10文字以上
- 大文字・小文字・数字を含む
- 記号は任意（強制するとユーザビリティが低下）

**受け入れられるパスワード**:
- `MyPassword123` ✅
- `CompanyPortal2024` ✅
- `mypassword123` ❌ (大文字なし)

### 3. 高セキュリティ（金融機関、医療機関）

**用途**: 銀行システム、医療記録システム、機密情報管理

```json
{
  "password_policy": {
    "min_length": 12,
    "require_uppercase": true,
    "require_lowercase": true,
    "require_number": true,
    "require_special_char": true
  }
}
```

**特徴**:
- 12文字以上
- 大文字・小文字・数字・記号すべて必須
- コンプライアンス要件（PCI DSS、HIPAA）に対応

**受け入れられるパスワード**:
- `MyP@ssw0rd2024` ✅
- `Secure!Bank#123` ✅
- `MyPassword123` ❌ (記号なし)

### 4. パスフレーズ推奨（セキュリティ専門家向け）

**用途**: DevOps、セキュリティチーム、管理者アカウント

```json
{
  "password_policy": {
    "min_length": 20
  }
}
```

**特徴**:
- 複雑性要件なし
- 長いパスフレーズ（20文字以上）のみ要求
- 覚えやすく、ブルートフォース攻撃に強い

**推奨するパスワード**:
- `i love drinking coffee in the morning` ✅ (40文字)
- `correct horse battery staple` ✅ (28文字、[XKCD](https://xkcd.com/936/)参照)

### 5. カスタム正規表現（高度なカスタマイズ）

**用途**: 特殊な要件がある組織（会社名を含める、連続する数字を禁止など）

標準の要件では対応できない場合、**カスタム正規表現**を使って独自のパスワードルールを定義できます。

#### 例1: 会社名を含むパスワード

```json
{
  "password_policy": {
    "min_length": 10,
    "custom_regex": ".*(?i)(idp|server).*",
    "custom_regex_error_message": "Password must contain 'idp' or 'server' (case-insensitive)"
  }
}
```

**受け入れられるパスワード**:
- `myIDPpassword` ✅
- `SecureServer123` ✅
- `password123` ❌ (会社名なし)

#### 例2: 連続する数字を禁止

```json
{
  "password_policy": {
    "min_length": 8,
    "custom_regex": "^(?!.*(012|123|234|345|456|567|678|789)).*$",
    "custom_regex_error_message": "Password must not contain sequential numbers (e.g., 123, 456)"
  }
}
```

**受け入れられるパスワード**:
- `Pass1024word` ✅
- `Secure8019Pass` ✅
- `Pass123word` ❌ (123を含む)
- `Test456Pass` ❌ (456を含む)

#### 例3: 日本企業のメールアドレス形式

```json
{
  "password_policy": {
    "min_length": 8,
    "custom_regex": ".*@[a-zA-Z0-9.-]+\\.co\\.jp$",
    "custom_regex_error_message": "Password must end with a valid Japanese company email (@xxx.co.jp)"
  }
}
```

**受け入れられるパスワード**:
- `user@example.co.jp` ✅
- `admin@my-company.co.jp` ✅
- `user@example.com` ❌ (.co.jpではない)

#### 例4: 他の要件との組み合わせ

カスタム正規表現は、他の要件（大文字・小文字・数字など）と組み合わせることができます。

```json
{
  "password_policy": {
    "min_length": 12,
    "require_uppercase": true,
    "require_number": true,
    "custom_regex": ".*(?i)secure.*",
    "custom_regex_error_message": "Password must contain the word 'secure'"
  }
}
```

**検証順序**:
1. 最小文字数（12文字）
2. 大文字を含む
3. 小文字を含む（デフォルトでは不要だがカスタム正規表現でチェック可能）
4. 数字を含む
5. カスタム正規表現（"secure"を含む）

**受け入れられるパスワード**:
- `MySecure123Pass` ✅
- `MyPassword123` ❌ ("secure"なし)
- `mysecure123pass` ❌ (大文字なし)

#### 注意事項

**正規表現の構文**:
- Java正規表現の構文に従う
- 不正な正規表現は設定エラーとして扱われる

**パフォーマンス**:
- 複雑すぎる正規表現（ReDoS攻撃のリスク）は避ける
- 可能な限りシンプルなパターンを使用

**ユーザビリティ**:
- エラーメッセージは明確に（`custom_regex_error_message`を必ず設定）
- ユーザーが理解できる要件にする

## パスワード変更API

ユーザーは、リソースオーナーAPIを使って自分のパスワードを変更できます。

### エンドポイント

```
POST /{tenant-id}/v1/me/password/change
Authorization: Bearer <access_token>
Content-Type: application/json

{
  "current_password": "CurrentPassword123",
  "new_password": "NewSecurePassword456"
}
```

### 検証フロー

1. **現在のパスワード検証**
   - 現在のパスワードが正しいか確認
   - 不正な場合は `invalid_current_password` エラー

2. **新しいパスワードのポリシー検証**
   - テナントの `password_policy` に基づいて検証
   - ポリシー違反の場合は `invalid_new_password` エラー

3. **パスワード更新**
   - 新しいパスワードをハッシュ化して保存
   - 古いパスワードは無効化

4. **セキュリティイベント発行**
   - `password_change_success`: 成功時
   - `password_change_failure`: 失敗時

### レスポンス例

**成功時（200 OK）**:
```json
{
  "message": "Password changed successfully."
}
```

**ポリシー違反（400 Bad Request）**:
```json
{
  "error": "invalid_new_password",
  "error_description": "Password must be at least 8 characters long."
}
```

```json
{
  "error": "invalid_new_password",
  "error_description": "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character."
}
```

## セキュリティイベント

パスワード変更時には、以下のセキュリティイベントが発行されます。

### password_change_success

**発行タイミング**: パスワード変更が成功した時

**用途**:
- ユーザーへの通知メール送信
- 監査ログへの記録
- 不正なパスワード変更の検出（本人が変更していない場合）

**イベント例**:
```json
{
  "event_type": "password_change_success",
  "timestamp": "2024-11-15T12:00:00Z",
  "user_id": "123e4567-e89b-12d3-a456-426614174000",
  "tenant_id": "67e7eae6-62b0-4500-9eff-87459f63fc66"
}
```

### password_change_failure

**発行タイミング**: パスワード変更が失敗した時

**用途**:
- 不正なアクセス試行の検出
- アカウントロックの判断材料
- セキュリティアラート

**イベント例**:
```json
{
  "event_type": "password_change_failure",
  "timestamp": "2024-11-15T12:00:00Z",
  "user_id": "123e4567-e89b-12d3-a456-426614174000",
  "tenant_id": "67e7eae6-62b0-4500-9eff-87459f63fc66",
  "error": "invalid_current_password"
}
```

## ベストプラクティス

### 1. NIST推奨に従う

- ✅ **推奨**: 最小8文字以上
- ✅ **推奨**: パスフレーズを推奨
- ❌ **非推奨**: 厳格な複雑性要件（大文字・小文字・数字・記号すべて必須）

### 2. ユーザビリティとセキュリティのバランス

**一般サービス**:
```json
{ "min_length": 8 }
```

**企業システム**:
```json
{
  "min_length": 10,
  "require_uppercase": true,
  "require_lowercase": true,
  "require_number": true
}
```

### 3. パスワード変更時の通知

パスワード変更成功時は、必ずユーザーにメール通知を送信してください。

```javascript
// Security Event Hook の例
if (event.event_type === "password_change_success") {
  sendEmail({
    to: user.email,
    subject: "パスワードが変更されました",
    body: "あなたのアカウントのパスワードが変更されました。身に覚えがない場合は、すぐにご連絡ください。"
  });
}
```

### 4. 定期的なパスワード変更は不要

NIST SP 800-63Bは、**定期的なパスワード変更を非推奨**としています。

**理由**:
- ユーザーが予測可能なパターン（`Password1` → `Password2`）を使用してしまう
- パスワードを忘れやすくなり、サポートコストが増加
- 本質的なセキュリティ向上にはつながらない

**推奨**:
- 漏洩が疑われる場合のみ変更を要求
- 多要素認証（MFA）の有効化を推奨

## 関連ドキュメント

- [認証ポリシー](../03-authentication-authorization/concept-01-authentication-policy.md) - 認証方式の制御
- [多要素認証（MFA）](../03-authentication-authorization/concept-02-mfa.md) - パスワード以外の認証要素
- [セキュリティイベント](../06-security-extensions/concept-01-security-events.md) - イベント管理
- [マルチテナント](../01-foundation/concept-01-multi-tenant.md) - テナントごとの設定

## 参考文献

- [NIST SP 800-63B: Digital Identity Guidelines](https://pages.nist.gov/800-63-3/sp800-63b.html)
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [Microsoft Graph API: changePassword](https://learn.microsoft.com/en-us/graph/api/user-changepassword)
- [Okta Users API: Change Password](https://developer.okta.com/docs/reference/api/users/#change-password)
- [XKCD: Password Strength](https://xkcd.com/936/)
