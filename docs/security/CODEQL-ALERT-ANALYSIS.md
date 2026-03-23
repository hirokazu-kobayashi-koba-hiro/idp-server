# CodeQL アラート分析レポート

実施日: 2026-03-23
対象: idp-server Java コード
ツール: GitHub CodeQL (security-extended クエリ)

---

## サマリー

| ルール | 件数 | 重要度 | 判定 |
|--------|------|--------|------|
| CSRF unprotected request type | 55 | High | 誤検知 (False Positive) |
| Spring CSRF disabled | 1 | High | 意図的 (Won't Fix) |
| Log Injection | 9 | High | 誤検知（構造的） |
| Sensitive log | 8 | High | 誤検知（構造的） |
| Polynomial ReDoS | 4 | High | **要修正** |
| Weak cryptographic algorithm | 2 | High | 仕様準拠 / 要検討 |
| User-controlled bypass | 4 | High | 誤検知 |
| Tainted arithmetic | 1 | High | 誤検知 |
| Insecure SMTP SSL | 1 | Medium | **要修正** |
| SSRF | 1 | Critical | Dismiss済み（SSRF保護実装済み） |

---

## 詳細分析

### 1. CSRF unprotected request type (55件) — 誤検知

**アラート番号**: #184〜#238

**事象**: Management API の PUT/DELETE/PATCH エンドポイントに CSRF 保護がないと検出。

**対象ファイル**:
- `control_plane/restapi/management/*.java` (システムレベルAPI)
- `control_plane/restapi/organization/*.java` (組織レベルAPI)
- `application/restapi/oauth/OAuthV1Api.java`
- `application/view/OAuthController.java`
- `application/restapi/metadata/FidoUafDiscoveryV1Api.java`

**原因**: CodeQL は Spring Security の CSRF 設定が無効化されている状態で、state-changing な HTTP メソッド（POST/PUT/DELETE/PATCH）のエンドポイントを検出する。

**判定: 誤検知 (False Positive)**

理由:
- idp-server は **REST API** であり、ブラウザの Cookie ベース認証ではない
- すべての Management API は **Bearer トークン認証**（`Authorization` ヘッダー）で保護
- CSRF は **ブラウザが自動送信する Cookie** を悪用する攻撃。Bearer トークンはブラウザが自動送信しないため CSRF の対象外
- OWASP CSRF Prevention Cheat Sheet: "If your application uses Bearer tokens for authentication, CSRF protection is not needed"

**対応**: False Positive として Dismiss

---

### 2. Spring CSRF disabled (1件) — 意図的

**アラート番号**: #158

**事象**: `SecurityConfig.java:58` で `http.csrf(AbstractHttpConfigurer::disable)` が検出。

**対象コード**:
```java
http.csrf(AbstractHttpConfigurer::disable);
```

**判定: 意図的な設計 (Won't Fix)**

理由:
- 上記 CSRF unprotected の分析と同じ理由
- `SessionCreationPolicy.STATELESS` と組み合わせて使用しており、サーバーサイドセッション不使用
- OAuth 2.0 / OIDC の仕様に準拠した REST API 設計

**対応**: Won't Fix として Dismiss

---

### 3. Log Injection (9件) + Sensitive log (8件) — 誤検知（構造的）

**アラート番号**: #175〜#183 (Log Injection), #164〜#171 (Sensitive log)

**事象**: `LoggerWrapper.java` の全メソッド（trace/debug/info/warn/error）が検出。

**対象コード**:
```java
public void info(String message, Object... args) {
    logger.info(message, args);
}
```

**判定: 誤検知（構造的）**

理由:
- `LoggerWrapper` は SLF4J Logger の薄いラッパー
- CodeQL は `LoggerWrapper` の引数にユーザー入力が到達する可能性を検出しているが、**SLF4J のパラメータ化ログ（`{}`プレースホルダー）** を使用しており、文字列連結ではない
- SLF4J はパラメータを `toString()` で変換してから出力するため、改行コードインジェクション等のログ改ざんは SLF4J レベルで防御される
- ただし、**呼び出し側**でユーザー入力を直接ログに渡している箇所がないか個別に確認は推奨

**対応**: False Positive として Dismiss。呼び出し側のログ出力内容は別途レビュー。

---

### 4. Polynomial ReDoS (4件) — 要修正

**アラート番号**: #153〜#156

**事象**: `PasswordPolicyValidator.java` のパスワードバリデーション正規表現が多項式的。

**対象コード**:
```java
// 90行目: password.matches(".*[A-Z].*")
// 97行目: password.matches(".*[a-z].*")
// 104行目: password.matches(".*[0-9].*")
// 110行目: password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")
```

**原因**: `.*X.*` パターンは、マッチしない入力に対してバックトラッキングが多項式的に増加する。ユーザーが入力するパスワードが対象のため、攻撃可能。

**リスク評価**:
- `maxLength` チェック（80行目）で事前に長さ制限されている（デフォルト64文字）
- 64文字程度では実害のあるDoSにはなりにくい
- **ただし修正は容易**であり、対応すべき

**対応方法**:
```java
// Before (ReDoS可能性あり)
password.matches(".*[A-Z].*")
password.matches(".*[a-z].*")
password.matches(".*[0-9].*")
password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")

// After (ReDoS不可)
password.chars().anyMatch(Character::isUpperCase)
password.chars().anyMatch(Character::isLowerCase)
password.chars().anyMatch(Character::isDigit)
password.chars().anyMatch(c -> "!@#$%^&*(),.?\":{}|<>".indexOf(c) >= 0)
```

---

### 5. Weak cryptographic algorithm (2件) — 仕様準拠 / 要検討

**アラート番号**: #173, #174

#### #173: HashAlgorithm.java:42

**事象**: `MessageDigest.getInstance(value)` で MD5 / SHA-1 が使用可能。

**対象コード**:
```java
enum HashAlgorithm {
  MD5("MD5"),      // ← 弱い
  SHA_1("SHA-1"),  // ← 弱い
  SHA_256("SHA-256"),
  SHA_384("SHA-384"),
  SHA_512("SHA-512");
}
```

**使用箇所**: `MessageDigestable.java` で MD5 と SHA-1 を使用。

**判定**: 要検討
- MD5/SHA-1 がパスワードハッシュやセキュリティ用途で使われていればリスクあり
- JWK Thumbprint (RFC 7638) は SHA-256 必須なので、そちらでは問題ない
- **MD5 の使用箇所を特定し、セキュリティ用途でなければ許容**

#### #174: Uuid5Function.java:140

**事象**: UUID v5 生成で SHA-1 を使用。

**対象コード**:
```java
MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
```

**判定: 仕様準拠 (Won't Fix)**
- UUID v5 は RFC 4122 で SHA-1 を使用すると規定されている
- UUID v5 のSHA-1使用は暗号強度のためではなく、名前空間ベースの決定的ID生成のため
- 仕様変更は不可能

**対応**: #174 は Won't Fix として Dismiss。#173 は MD5 使用箇所の確認後に判断。

---

### 6. User-controlled bypass (4件) — 誤検知

**アラート番号**: #160〜#163

**事象**: ユーザー制御のデータで条件分岐をバイパスする可能性を検出。

**対象コード**:
- `User.java:911` — `patchUser.hasAuthenticationDevices()` で認証デバイス更新の分岐
- `User.java:992` — 同上の別パッチメソッド
- `SecurityEventUserCreatable.java:160` — セキュリティイベント生成時のユーザー情報取得
- `AuthenticationDeviceLogEntryService.java:89` — 認証デバイスログ記録

**判定: 誤検知**

理由:
- CodeQL は `has*()` メソッドの結果でセンシティブな操作をスキップしていると検出
- 実際には `patchUser` は **Management API 経由で管理者が操作** するオブジェクトであり、エンドユーザーが直接制御するものではない
- パッチ操作は Bearer トークン + RBAC で保護されている

**対応**: False Positive として Dismiss

---

### 7. Tainted arithmetic (1件) — 誤検知

**アラート番号**: #159

**事象**: `TenantStatisticsDataQueryDataSource.java:65` でユーザー制御の値が算術演算に使用。

**対象コード**:
```java
int offset = queries.offset();
int limit = queries.limit();
int endIndex = Math.min(offset + limit, allStats.size());
```

**判定: 誤検知**

理由:
- `offset + limit` の結果は `Math.min(..., allStats.size())` で上限が制限されている
- `offset >= allStats.size()` の事前チェック（67行目）でオーバーフロー時も空リストを返す
- 整数オーバーフローが発生しても `Math.min` で安全側に倒れる

**対応**: False Positive として Dismiss

---

### 8. Insecure SMTP SSL (1件) — 要修正

**アラート番号**: #172

**事象**: `SmtpEmailSender.java:44` で JavaMail の SSL 設定が安全でない。

**対象コード**:
```java
Session session = Session.getInstance(props, new Authenticator() { ... });
```

**原因**: SMTP接続で `mail.smtp.ssl.checkserveridentity` が未設定、または `mail.smtp.starttls.enable` の設定が不十分な可能性。

**リスク評価**:
- SMTP接続で中間者攻撃（MITM）のリスク
- メール内容にOTPコードが含まれるため、漏洩リスクあり

**対応方法**:
```java
props.put("mail.smtp.ssl.checkserveridentity", "true");
props.put("mail.smtp.starttls.required", "true");  // starttls.enable ではなく required
```

---

## 対応計画

### 即時対応（Dismiss）

| アラート | 件数 | 理由 | Dismiss種別 |
|---------|------|------|------------|
| CSRF unprotected | 55 | Bearer トークン認証、CSRF不要 | False Positive |
| Spring CSRF disabled | 1 | 意図的な設計 | Won't Fix |
| Log Injection | 9 | SLF4J パラメータ化ログ | False Positive |
| Sensitive log | 8 | 同上 | False Positive |
| User-controlled bypass | 4 | Management API、RBAC保護 | False Positive |
| Tainted arithmetic | 1 | Math.min で上限制限済み | False Positive |
| Weak crypto (UUID v5) | 1 | RFC 4122 仕様準拠 | Won't Fix |

### 修正対応（PR作成）

| アラート | 件数 | 対応内容 | 優先度 |
|---------|------|---------|--------|
| ReDoS | 4 | 正規表現を chars().anyMatch() に変更 | High |
| Insecure SMTP SSL | 1 | SSL設定を強化 | Medium |

### 追加調査

| アラート | 件数 | 調査内容 |
|---------|------|---------|
| Weak crypto (MD5) | 1 | MD5 の使用箇所がセキュリティ用途かどうか確認 |
