# OWASP Top 10 - ID/認証システムの観点から

## このドキュメントの目的

OWASP Top 10の脆弱性を**ID・認証システム**の観点から理解し、適切な対策方法を学びます。

---

## OWASPとは

```
OWASP = Open Worldwide Application Security Project

Webアプリケーションセキュリティの向上を目指す
非営利のオープンコミュニティ。

提供しているもの:
- OWASP Top 10: 最も重大なWebセキュリティリスクのランキング
- チートシート: 各分野のセキュリティ対策ガイド
- ツール: ZAP（脆弱性スキャナー）等

なぜ学ぶべきか:
- 業界標準のセキュリティ基準として広く認知
- 開発者が知るべき最低限のセキュリティ知識
- セキュリティ監査で必ず参照される
```

---

## OWASP Top 10（2021年版）概要

| # | 脆弱性カテゴリ | 何が起こるか | ID/認証との関連度 |
|---|--------------|-------------|-----------------|
| A01 | Broken Access Control | 他人のデータを閲覧・操作できる | ◎ 非常に高い |
| A02 | Cryptographic Failures | パスワードが解読・漏洩する | ◎ 非常に高い |
| A03 | Injection | 悪意あるコードが実行される | ○ 高い |
| A04 | Insecure Design | 根本的に守れない構造 | ◎ 非常に高い |
| A05 | Security Misconfiguration | 不要な機能が有効なまま | ○ 高い |
| A06 | Vulnerable Components | 既知の脆弱性を突かれる | △ 中程度 |
| A07 | Auth Failures | 総当たりで突破される | ◎ 直接該当 |
| A08 | Integrity Failures | 改ざんされたコードが動く | ○ 高い |
| A09 | Logging Failures | 侵入されても検知できない | ○ 高い |
| A10 | SSRF | 内部システムに不正アクセス | △ 中程度 |

---

## A01: Broken Access Control（アクセス制御の不備）

### 概要

認可されていないリソースへのアクセスを許してしまう脆弱性。

### ID/認証システムでの具体例

```
1. 他のユーザーのプロファイルを閲覧できる
   GET /users/12345 → 自分以外のユーザー情報が取得できる

2. 管理者APIに一般ユーザーがアクセスできる
   POST /admin/users → 権限チェックがない

3. テナント間のデータが見える（マルチテナント）
   Tenant AのユーザーがTenant Bのデータを取得
```

### 対策パターン

**1. デフォルト拒否（Deny by Default）**
```java
// 悪い例：許可リストで判定
if (user.hasRole("admin")) {
    // 許可
}
// else は何もしない → 暗黙的に許可される可能性

// 良い例：明示的に拒否
if (!user.hasRole("admin")) {
    throw new ForbiddenException("Admin role required");
}
```

**2. リソースオーナーシップ検証**
```java
// リソース取得時に所有者を確認
User user = userRepository.findById(userId);
if (!user.getTenantId().equals(currentTenant.getId())) {
    throw new ForbiddenException("Access denied");
}
```

**3. 主な対策**
- 全Repository操作でTenantを必須化
- スコープベース認可：Access Tokenのscopeでアクセス範囲を制限
- RBAC：ロールベースのアクセス制御

---

## A02: Cryptographic Failures（暗号化の失敗）

### 概要

機密データの暗号化不備、弱い暗号アルゴリズムの使用。

### ID/認証システムでの具体例

```
1. パスワードの平文保存
2. MD5/SHA1でのパスワードハッシュ（弱い）
3. 秘密鍵のハードコード
4. HTTPでの認証情報送信
5. JWTの署名なし（alg: none）
```

### 対策パターン

**1. パスワードハッシュ**
```
推奨アルゴリズム:
- bcrypt（コスト係数12以上）
- Argon2id（メモリハード）
- scrypt
```

**2. JWT署名アルゴリズム**
```
推奨:
- RS256（RSA + SHA-256）: 公開鍵検証が必要な場合
- ES256（ECDSA + SHA-256）: より短い鍵長で同等のセキュリティ
- PS256（RSA-PSS）: より安全なパディング

非推奨:
- HS256（共有秘密鍵）: 鍵管理が難しい
- none: 絶対に禁止
```

**3. 鍵管理**
```yaml
# 悪い例：設定ファイルに直接記載
jwt:
  secret: "my-super-secret-key"

# 良い例：環境変数または外部シークレット管理
jwt:
  secret: ${JWT_SECRET}  # 環境変数から取得
```

---

## A03: Injection（インジェクション）

### 概要

ユーザー入力がコード/クエリとして実行される脆弱性。

### ID/認証システムでの具体例

**SQLインジェクション**
```sql
-- 脆弱なコード
SELECT * FROM users WHERE username = '${username}'

-- 攻撃例
username = "admin' OR '1'='1"
→ SELECT * FROM users WHERE username = 'admin' OR '1'='1'
→ 全ユーザーが返される
```

**LDAPインジェクション**
```
-- 脆弱なコード
(&(uid=${username})(userPassword=${password}))

-- 攻撃例
username = "*)(uid=*))(|(uid=*"
→ 認証バイパス
```

### 対策パターン

**1. パラメータ化クエリ**
```java
// 悪い例
String query = "SELECT * FROM users WHERE username = '" + username + "'";

// 良い例
PreparedStatement stmt = conn.prepareStatement(
    "SELECT * FROM users WHERE username = ?"
);
stmt.setString(1, username);
```

**2. 主な対策**
- ORMを使用（パラメータバインディング自動適用）
- 入力バリデーション（許可文字のホワイトリスト）

---

## A04: Insecure Design（安全でない設計）

### 概要

設計段階でのセキュリティ考慮不足。

### ID/認証システムでの具体例

```
1. パスワードリセットで秘密の質問を使用（推測可能）
2. 認証試行回数に制限がない（ブルートフォース可能）
3. セッションタイムアウトがない（盗まれたセッションが永続化）
4. 多要素認証のバイパス経路がある
```

### 対策パターン

**1. 脅威モデリング**
```
STRIDE分析:
- Spoofing（なりすまし）→ 強力な認証
- Tampering（改ざん）→ 署名・MAC
- Repudiation（否認）→ 監査ログ
- Information Disclosure（情報漏洩）→ 暗号化
- Denial of Service（サービス拒否）→ レート制限
- Elevation of Privilege（権限昇格）→ 最小権限
```

**2. セキュアなデフォルト**
```yaml
# セキュアデフォルト例
authorization_server:
  access_token_duration: 3600      # 1時間（短め）
  refresh_token_duration: 2592000  # 30日
  authorization_code_duration: 600 # 10分
  require_pkce: true               # PKCE必須
```

---

## A05: Security Misconfiguration（セキュリティ設定ミス）

### 概要

デフォルト設定のまま運用、不要な機能の有効化、不適切なエラーメッセージなど、設定に起因する脆弱性。

### ID/認証システムでの具体例

```
1. デフォルト認証情報
   - 管理者アカウント: admin/admin のまま
   - APIキー: 公開サンプルのまま使用

2. 不要な機能の有効化
   - デバッグエンドポイントが本番で有効
   - 未使用のOAuth grant_typeが有効
   - トークンイントロスペクションが認証なしで公開

3. 過剰な情報露出
   - エラーメッセージにスタックトレース
   - /.well-known/openid-configuration に内部情報
   - HTTPヘッダーにサーバーバージョン

4. セキュリティヘッダー未設定
   - HSTS未設定でHTTPダウングレード可能
   - CSP未設定でXSSリスク増大
```

### 対策パターン

**1. 本番環境チェックリスト**
```
設定確認:
- [ ] デフォルト認証情報を変更済み
- [ ] デバッグモード無効化
- [ ] 不要なエンドポイント無効化
- [ ] エラーメッセージは最小限
- [ ] セキュリティヘッダー設定済み
```

**2. 最小機能の原則**
```yaml
# 必要なgrant_typeのみ有効化
oauth:
  grant_types:
    - authorization_code  # 必要
    - refresh_token       # 必要
    # - implicit          # 不要なら無効化
    # - password          # 不要なら無効化
```

**3. 環境別設定の分離**
```
本番環境:
- debug: false
- detailed_errors: false
- admin_endpoints: 内部ネットワークのみ

開発環境:
- debug: true
- detailed_errors: true
```

---

## A06: Vulnerable and Outdated Components（脆弱なコンポーネント）

### 概要

既知の脆弱性を持つライブラリやフレームワークを使用することで、攻撃を受けるリスク。

### ID/認証システムでの具体例

```
1. JWT/JOSEライブラリの脆弱性
   - CVE-2022-21449: Java 15-18のECDSA署名検証バイパス
   - alg: none攻撃に脆弱な古いライブラリ

2. 暗号ライブラリの脆弱性
   - OpenSSL Heartbleed (CVE-2014-0160)
   - 古いbcryptライブラリのタイミング攻撃

3. フレームワークの脆弱性
   - Spring4Shell (CVE-2022-22965)
   - Log4Shell (CVE-2021-44228)

4. 認証ライブラリの脆弱性
   - passport.jsの認証バイパス
   - OAuthライブラリのオープンリダイレクト
```

### 対策パターン

**1. 依存関係の監視**
```bash
# 脆弱性スキャン
npm audit                    # Node.js
./gradlew dependencyCheckAnalyze  # Java (OWASP Dependency Check)
trivy fs .                   # コンテナ/ファイルシステム
```

**2. 定期的な更新**
```
推奨プラクティス:
- セキュリティパッチは即座に適用
- メジャーバージョンは四半期ごとに評価
- EOL（サポート終了）ライブラリの排除計画
```

**3. 脆弱性情報の収集**
```
情報源:
- CVE/NVD（National Vulnerability Database）
- GitHub Security Advisories
- 利用ライブラリのセキュリティML/RSS
- JPCERT/CC（日本）
```

**4. SBOM（ソフトウェア部品表）の管理**
```
目的:
- 使用コンポーネントの一覧化
- 脆弱性発見時の影響範囲特定
- ライセンスコンプライアンス
```

---

## A07: Identification and Authentication Failures

### 概要

認証・識別の実装不備。OWASP Top 10で**最もID/認証に直接関連**するカテゴリ。

### 具体的な脆弱性

| 脆弱性 | 影響 | 対策 |
|--------|------|------|
| 弱いパスワード許可 | アカウント乗っ取り | パスワードポリシー強制 |
| ブルートフォース可能 | アカウント乗っ取り | レート制限、アカウントロック |
| クレデンシャルスタッフィング | 大量アカウント侵害 | 漏洩パスワードチェック |
| セッション固定攻撃 | セッションハイジャック | 認証後セッションID再生成 |
| 平文パスワード送信 | 盗聴 | HTTPS必須 |
| 多要素認証なし | 単一障害点 | MFA推奨/必須 |

### 対策

**1. パスワードポリシー**
```yaml
password_policy:
  min_length: 12
  require_uppercase: true
  require_lowercase: true
  require_number: true
  require_special: true
  max_consecutive_chars: 3
```

**2. 認証試行制限**
```yaml
authentication_policy:
  max_attempts: 5
  lockout_duration: 900  # 15分
  progressive_delay: true
```

**3. 多要素認証**
```yaml
authentication_policy:
  mfa:
    required: true
    methods:
      - webauthn
      - totp
```

---

## A08: Software and Data Integrity Failures

### 概要

ソフトウェアやデータの整合性検証不備。

### ID/認証システムでの具体例

```
1. JWTの署名検証をスキップ
2. 外部IdPからのSAMLレスポンスを未検証で信頼
3. ソフトウェア更新時の署名検証なし
```

### 対策パターン

**1. JWT検証の徹底**
```
必須の検証項目:
- 署名検証（alg: noneを拒否）
- 発行者（iss）検証
- 有効期限（exp）検証
- 対象者（aud）検証
```

**2. Federation時の検証**
```
OIDC Federationでの検証項目:
1. ID Tokenの署名検証（外部IdPの公開鍵で）
2. issクレームが期待するIdPか
3. audクレームが自分のclient_idか
4. nonceが送信したものと一致するか
5. expが有効期限内か
```

---

## A09: Security Logging and Monitoring Failures

### 概要

セキュリティイベントのログ記録・監視不備。

### ID/認証システムで記録すべきイベント

```
必須ログ:
- 認証成功/失敗（ユーザーID、IP、タイムスタンプ）
- 権限変更（ロール付与/剥奪）
- パスワード変更
- MFA設定変更
- セッション作成/破棄
- 管理操作（ユーザー作成/削除）

推奨ログ:
- 認可判定（許可/拒否）
- トークン発行/失効
- 設定変更
```

---

## A10: Server-Side Request Forgery (SSRF)

### 概要

サーバーに外部URLを取得させる機能を悪用し、内部システムへ不正アクセスする攻撃。

### ID/認証システムでの具体例

```
1. redirect_uriの検証不備
   攻撃者: redirect_uri=http://169.254.169.254/latest/meta-data/
   → サーバーがクラウドメタデータにアクセスし、認証情報が漏洩

2. ユーザー情報取得時の外部URL指定
   攻撃者: picture=http://internal-service/admin
   → サーバーが内部サービスにアクセス

3. WebFinger/OpenID Connect Discovery
   攻撃者: issuer=http://internal-server/.well-known/openid-configuration
   → 内部サーバーへのアクセスを誘発
```

### 攻撃の流れ

```
┌──────────┐    ┌──────────────┐    ┌──────────────┐
│  攻撃者   │    │   IdPサーバー │    │  内部システム  │
└────┬─────┘    └──────┬───────┘    └──────┬───────┘
     │                 │                   │
     │ redirect_uri=   │                   │
     │ http://169.254. │                   │
     │ 169.254/...     │                   │
     │────────────────>│                   │
     │                 │                   │
     │                 │ GET /latest/      │
     │                 │ meta-data/        │
     │                 │──────────────────>│
     │                 │                   │
     │                 │ IAMクレデンシャル  │
     │                 │<──────────────────│
     │                 │                   │
     │ 認証情報が漏洩   │                   │
     │<────────────────│                   │
```

### 対策パターン

**1. redirect_uriの厳格な検証**
```
対策:
- 事前登録されたURIとの完全一致
- ワイルドカード禁止
- localhostやプライベートIPの禁止
- URLスキームはhttpsのみ許可
```

**2. 外部リソース取得の制限**
```
対策:
- 許可リスト方式（取得可能なドメインを限定）
- プライベートIPレンジへのアクセス禁止
  - 10.0.0.0/8
  - 172.16.0.0/12
  - 192.168.0.0/16
  - 169.254.0.0/16（クラウドメタデータ）
- DNSリバインディング対策（解決後のIPも検証）
```

**3. ネットワークレベルの防御**
```
対策:
- IdPサーバーから内部ネットワークへのアクセスを制限
- メタデータサービスへのアクセスをIMDSv2必須に
- アウトバウンド通信のファイアウォール設定
```

---

## まとめ：ID/認証システムのセキュリティチェックリスト

### 設計フェーズ
- [ ] 脅威モデリングを実施した
- [ ] 認証フローを文書化した
- [ ] データフロー図でセキュリティ境界を明確化した

### 実装フェーズ
- [ ] パスワードはbcrypt/Argon2idでハッシュ化
- [ ] SQLはパラメータ化クエリを使用
- [ ] JWTは署名検証を必ず実施
- [ ] HTTPS必須化
- [ ] セッションIDは認証後に再生成

### 運用フェーズ
- [ ] 認証イベントをログ記録
- [ ] 異常検知アラートを設定
- [ ] 定期的なセキュリティレビュー
- [ ] 依存ライブラリの脆弱性スキャン

---

## 参考資料

- [OWASP Top 10 2021](https://owasp.org/Top10/)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
- [OWASP Session Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)

---

**最終更新**: 2025-12-25
**対象**: IDサービス開発者
