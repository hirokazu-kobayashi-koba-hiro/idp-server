# カスタムクレーム・スコープ

idp-serverの独自機能である、カスタムクレームスコープマッピングについて説明します。

> **基礎知識**: スコープとクレームの基本、標準的なユーザー属性については [ID管理](concept-02-id-management.md) を参照してください。

## カスタムクレームスコープマッピング

idp-serverでは、`claims:` プレフィックスを使用して**個別のクレームを直接指定**できます。これにより、必要なカスタムクレームだけを柔軟に取得できます。

### なぜ必要なのか

OpenID Connect標準では、スコープに対して複数のクレームがセットで紐付けられます。例えば`profile`スコープを要求すると、`name`, `given_name`, `family_name`, `birthdate`等、多数のクレームがまとめて返されます。

個別のクレームを取得したい場合、OIDC仕様では認可リクエストの`claims`パラメータで指定できます。しかし、以下のような課題があります：

- **`claims`パラメータは複雑**: 以下のような複雑なJSON構造を構築する必要がある
  ```json
  {
    "userinfo": {
      "name": {"essential": true},
      "roles": null
    }
  }
  ```
- **多くのRPが実装できない**: この複雑さから、一般的なRPでは`claims`パラメータを実装していないことが多い
- **スコープは全RPが対応**: 一方、スコープは単純な文字列リストなので、すべてのRPが容易に扱える

idp-serverの`claims:`プレフィックスは、この課題を解決します。個別クレームの要求を、複雑な`claims`パラメータではなく、シンプルなスコープ構文で実現できます。

### 使用例

```
scope=openid claims:name claims:roles claims:permissions
```

この場合、`sub`（openidで取得）、`name`、`roles`、`permissions`のみが取得されます。

### 有効化設定

テナント設定で以下を有効にする必要があります：

```
custom_claims_scope_mapping=true
```

## 身元確認済みクレームスコープマッピング

idp-serverでは、`verified_claims:` プレフィックスを使用して**個別の身元確認済みクレームを指定**できます。

### 使用例

```
scope=openid verified_claims:given_name verified_claims:family_name
```

検証された`given_name`と`family_name`のみが取得されます。

## 認証方式と組み合わせた動的スコープフィルタリング

認証ポリシーと組み合わせることで、認証方式に応じて取得できるクレームを動的に制御できます。

### ユースケース

**低セキュリティ認証（パスワードのみ）**:
- `openid profile`のみ許可

**高セキュリティ認証（FIDO2）**:
- `openid profile verified_claims:*`まで許可

これにより、認証強度に応じた情報開示を実現します。

## セキュリティ考慮事項

### スコープの最小化

必要最小限のスコープのみを要求することで、情報漏洩リスクを低減します。

### センシティブなクレームの保護

以下のクレームは特に慎重に扱う必要があります：

- `verified_claims`: 身元確認済み情報
- `credentials`: 認証資格情報
- `authentication_devices`: デバイス情報

### アクセス制御

`claims:`プレフィックスでアクセス可能なクレームは、スコープを許可されたクライアントであれば取得できます。センシティブなクレームへのアクセスを制限する場合は、クライアント設定でスコープを適切に制限してください。

## 関連ドキュメント

- [認証ポリシー](concept-05-authentication-policy.md) - 認証方式によるスコープ制御
- [身元確認済みID](concept-03-id-verified.md) - verified_claimsの詳細
- [ID管理](concept-02-id-management.md) - ユーザー属性の全体像

## 参考仕様

- [OpenID Connect Core 1.0 - Requesting Claims using Scope Values](https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims)
- [OpenID Connect for Identity Assurance 1.0](https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html)
