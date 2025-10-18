# OAuth 2.0の「認可」の仕組み

---

## OAuth 2.0は「認可（Authorization）」のための標準仕様

OAuth 2.0は、ユーザーのパスワードや機密情報を直接共有せずに、  
第三者サービス（アプリ・Webサービス）へ「必要な権限だけ」を安全に渡すための仕組みです。

---

## 認可フローの基本

1. **ユーザー（Resource Owner）**  
   - 権限を持つ本人  
   例：Googleアカウントの持ち主

2. **クライアント（Client）**  
   - ユーザーから権限をもらうWebサービスやアプリ  
   例：カレンダー連携アプリ

3. **認可サーバー（Authorization Server）**  
   - 権限の管理・発行をするID基盤  
   例：GoogleのOAuth認可サーバー

4. **リソースサーバー（Resource Server）**  
   - 実際のデータや機能があるサーバー  
   例：GoogleカレンダーAPI

---

### 具体的な流れ

1. ユーザーが「外部サービスに自分のデータを使わせてもいい」と許可
2. クライアント（アプリ）は認可サーバーに「○○の権限が欲しい」とリクエスト
3. 認可サーバーがユーザーの同意を確認し、アクセストークンを発行
4. クライアントはアクセストークンを使ってリソースサーバー（API）にアクセス
5. リソースサーバーは、トークンの権限（スコープ）を確認し、許可された操作だけを実行

---

### 権限（スコープ）のイメージ

- 「カレンダーの閲覧だけ」
- 「プロフィールの参照だけ」
- 「メールの送信は不可」

アクセストークンには、「どの操作がOKか（scope）」という情報が含まれ、  
クライアントはその範囲内でしかユーザーのデータにアクセスできません。

---

## OAuth 2.0の「認可」のメリット

- **パスワードを預けずに権限を渡せる**  
  → なりすまし・漏洩リスクを大幅低減
- **必要最小限の権限（スコープ）だけ付与できる**  
  → ユーザーのプライバシー・安全性を担保
- **権限の取り消し・有効期限管理が標準でできる**  
  → 万が一の際も迅速に権限停止可能

---

## 仕様参照

### RFC文書
- **[RFC 6749: The OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749)** - OAuth 2.0の基本仕様
- **[RFC 6750: The OAuth 2.0 Authorization Framework: Bearer Token Usage](https://tools.ietf.org/html/rfc6750)** - Bearer Token使用方法
- **[RFC 7636: Proof Key for Code Exchange by OAuth Public Clients (PKCE)](https://tools.ietf.org/html/rfc7636)** - PKCEによる拡張仕様

### idp-server機能サポート状況

| 機能 | サポート状況 | 実装詳細 |
|------|-------------|----------|
| Authorization Code Grant | ✅ 完全対応 | [認可コードフロー](../../content_04_protocols/protocol-01-authorization-code-flow.md) |
| PKCE | ✅ 完全対応 | RFC 7636準拠 |
| Client Credentials Grant | ✅ 完全対応 | RFC 6749 Section 4.4 |
| Resource Owner Password Credentials | ❌ 非対応 | セキュリティ上の理由により非サポート |
| Implicit Grant | ⚠️ 非推奨 | 新規実装は推奨されません |
| Device Authorization Grant | 🔄 計画中 | [Issue #XXX](https://github.com/hirokazu-kobayashi-koba-hiro/idp-server/issues) |
| Bearer Token | ✅ 完全対応 | [トークンタイプ詳細](basic-10-oauth2-token-types.md) |
| Token Introspection (RFC 7662) | ✅ 完全対応 | [イントロスペクション](../../content_04_protocols/protocol-03-introspection.md) |
| Token Revocation (RFC 7009) | ✅ 完全対応 | RFC 7009準拠 |

### idp-server独自拡張

- **マルチテナント対応**: テナント単位での認可サーバー管理
- **プラガブルアーキテクチャ**: カスタム認可ロジックの実装可能
- **監査ログ**: 認可リクエストの詳細な監査証跡
- **セキュリティイベント**: リアルタイムセキュリティ監視

---

## まとめ

OAuth 2.0は「本人認証」の仕組みではなく、
**ユーザーが"どの権限をどこまで"外部サービスに渡すかを安全に管理する"認可"の仕組み**です。

---

> 次は、代表的な認可フロー（認可コードグラント、Implicit、CIBAなど）の図解と、ID連携の実装ポイントを解説します！
