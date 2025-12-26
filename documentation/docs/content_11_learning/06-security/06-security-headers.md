# セキュリティヘッダー完全ガイド

## このドキュメントの目的

Webアプリケーションを保護するための**HTTPセキュリティヘッダー**を理解し、ID/認証システムでの適切な設定方法を学びます。

---

## セキュリティヘッダー一覧

| ヘッダー | 目的 | 重要度 |
|---------|------|--------|
| Strict-Transport-Security | HTTPS強制 | ◎ 必須 |
| Content-Security-Policy | XSS対策 | ◎ 必須 |
| X-Content-Type-Options | MIMEスニッフィング防止 | ◎ 必須 |
| X-Frame-Options | クリックジャッキング防止 | ○ 推奨 |
| X-XSS-Protection | ブラウザXSSフィルタ | △ レガシー |
| Referrer-Policy | リファラー情報制御 | ○ 推奨 |
| Permissions-Policy | ブラウザ機能制限 | ○ 推奨 |
| Cache-Control | キャッシュ制御 | ◎ 必須 |

---

## HSTS (HTTP Strict Transport Security)

### 目的

ブラウザにHTTPS接続を強制し、中間者攻撃を防止。

### 設定例

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
```

### パラメータ解説

| パラメータ | 説明 | 推奨値 |
|-----------|------|--------|
| max-age | HTTPS強制期間（秒） | 31536000（1年） |
| includeSubDomains | サブドメインにも適用 | 有効化推奨 |
| preload | ブラウザプリロードリストに登録 | 慎重に検討 |

### 段階的導入

```http
# Step 1: 短い期間でテスト（5分）
Strict-Transport-Security: max-age=300

# Step 2: 期間を延長（1週間）
Strict-Transport-Security: max-age=604800

# Step 3: 本番設定（1年）
Strict-Transport-Security: max-age=31536000; includeSubDomains
```

### 注意点

```
⚠️ HSTSの注意点:
- 一度設定すると、max-age期間中はHTTP接続不可
- 証明書の問題があるとサイトにアクセスできなくなる
- preloadは取り消しが困難（数ヶ月かかる）
```

---

## CSP (Content-Security-Policy)

### 目的

XSS（クロスサイトスクリプティング）攻撃を防止。許可されたリソースのみ読み込み可能に。

### 基本構文

```http
Content-Security-Policy: directive-name 'value' source-list;
```

### 主要ディレクティブ

| ディレクティブ | 制御対象 |
|---------------|---------|
| default-src | デフォルトのリソース |
| script-src | JavaScript |
| style-src | CSS |
| img-src | 画像 |
| font-src | フォント |
| connect-src | XHR/Fetch/WebSocket |
| frame-src | iframe |
| frame-ancestors | 自サイトをiframeで埋め込めるサイト |
| form-action | フォーム送信先 |
| base-uri | base要素のURL |

### ソース値

| 値 | 意味 |
|----|------|
| 'none' | 何も許可しない |
| 'self' | 同一オリジンのみ |
| 'unsafe-inline' | インラインスクリプト/スタイル（非推奨） |
| 'unsafe-eval' | eval()等（非推奨） |
| 'nonce-xxx' | 特定のnonceを持つ要素のみ |
| 'strict-dynamic' | nonceで許可されたスクリプトからの読み込みを許可 |
| https: | HTTPSのみ |
| data: | data: URI |

### ID/認証システム向け推奨設定

```http
Content-Security-Policy:
  default-src 'self';
  script-src 'self' 'nonce-{random}';
  style-src 'self' 'nonce-{random}';
  img-src 'self' data:;
  font-src 'self';
  connect-src 'self';
  frame-ancestors 'none';
  form-action 'self';
  base-uri 'self';
  upgrade-insecure-requests;
```

### レポートモード

本番適用前にテスト:

```http
Content-Security-Policy-Report-Only:
  default-src 'self';
  report-uri /csp-violation-report;
```

---

## X-Content-Type-Options

### 目的

ブラウザのMIMEタイプスニッフィングを防止。

### 攻撃シナリオ

```
1. 攻撃者がJavaScriptを含むファイルをアップロード（拡張子は.txt）
2. サーバーがContent-Type: text/plainで配信
3. ブラウザがコンテンツを解析し、JavaScriptとして実行
4. XSS攻撃成功
```

### 設定

```http
X-Content-Type-Options: nosniff
```

### 効果

- ブラウザはContent-Typeヘッダーを厳密に解釈
- text/plainのファイルをJavaScriptとして実行しない

---

## X-Frame-Options

### 目的

クリックジャッキング攻撃を防止。

### 攻撃シナリオ

```
1. 攻撃者が悪意のあるサイトを作成
2. 透明なiframeで銀行サイトを埋め込む
3. ユーザーは見えているボタンをクリック
4. 実際には銀行サイトの「送金」ボタンをクリック
```

### 設定オプション

```http
# 全てのiframe埋め込みを禁止
X-Frame-Options: DENY

# 同一オリジンからの埋め込みのみ許可
X-Frame-Options: SAMEORIGIN
```

### CSPとの関係

```http
# CSPのframe-ancestorsがより新しい方式
Content-Security-Policy: frame-ancestors 'none'

# 互換性のため両方設定することを推奨
X-Frame-Options: DENY
Content-Security-Policy: frame-ancestors 'none'
```

---

## Referrer-Policy

### 目的

リファラー情報の送信を制御し、URLに含まれる機密情報の漏洩を防止。

### 設定オプション

| 値 | 動作 |
|----|------|
| no-referrer | リファラーを送信しない |
| no-referrer-when-downgrade | HTTPSからHTTPへの遷移時は送信しない |
| same-origin | 同一オリジンにのみ送信 |
| origin | オリジンのみ送信（パス情報なし） |
| strict-origin | HTTPSの場合のみオリジンを送信 |
| origin-when-cross-origin | クロスオリジンはオリジンのみ、同一オリジンは完全URL |
| strict-origin-when-cross-origin | 推奨設定 |

### ID/認証システムでの推奨

```http
Referrer-Policy: strict-origin-when-cross-origin
```

### なぜ重要か

```
問題のあるURL例:
https://idp.example.com/reset-password?token=abc123

このURLがリファラーとして外部サイトに送信されると、
パスワードリセットトークンが漏洩する可能性がある
```

---

## Cache-Control

### 目的

認証情報やセンシティブなレスポンスがキャッシュされることを防止。

### 認証関連レスポンスの設定

```http
Cache-Control: no-store, no-cache, must-revalidate, private
Pragma: no-cache
Expires: 0
```

### 各ディレクティブの意味

| ディレクティブ | 意味 |
|---------------|------|
| no-store | レスポンスを保存しない |
| no-cache | 再検証なしでキャッシュを使用しない |
| must-revalidate | 期限切れ後は必ず再検証 |
| private | 共有キャッシュ（CDN等）に保存しない |

### エンドポイント別の設定例

```java
// トークンエンドポイント
@PostMapping("/token")
public ResponseEntity<?> token() {
    HttpHeaders headers = new HttpHeaders();
    headers.setCacheControl("no-store");
    headers.setPragma("no-cache");
    return new ResponseEntity<>(tokenResponse, headers, HttpStatus.OK);
}

// ユーザー情報エンドポイント
@GetMapping("/userinfo")
public ResponseEntity<?> userinfo() {
    HttpHeaders headers = new HttpHeaders();
    headers.setCacheControl("no-store, private");
    return new ResponseEntity<>(userInfo, headers, HttpStatus.OK);
}
```

---

## Permissions-Policy（旧Feature-Policy）

### 目的

ブラウザ機能（カメラ、マイク、位置情報等）の使用を制限。

### 設定例

```http
Permissions-Policy:
  camera=(),
  microphone=(),
  geolocation=(),
  payment=(),
  usb=()
```

### ID/認証システムでの活用

```http
# WebAuthn使用時はカメラ/マイクを許可しない設定
Permissions-Policy:
  camera=(),
  microphone=(),
  geolocation=(),
  publickey-credentials-get=(self),
  publickey-credentials-create=(self)
```

---

## 実装例：Spring Security

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> headers
                // HSTS
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true)
                )
                // X-Content-Type-Options
                .contentTypeOptions(Customizer.withDefaults())
                // X-Frame-Options
                .frameOptions(frame -> frame.deny())
                // CSP
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self'; " +
                        "frame-ancestors 'none'"
                    )
                )
                // Referrer-Policy
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
                // Permissions-Policy
                .permissionsPolicy(permissions -> permissions
                    .policy("camera=(), microphone=(), geolocation=()")
                )
            );
        return http.build();
    }
}
```

---

## セキュリティヘッダーチェックツール

### オンラインツール

- [Security Headers](https://securityheaders.com/) - ヘッダー評価
- [Mozilla Observatory](https://observatory.mozilla.org/) - 総合セキュリティ評価
- [CSP Evaluator](https://csp-evaluator.withgoogle.com/) - CSP設定評価

### curlでの確認

```bash
curl -I https://your-idp.example.com | grep -E "^(Strict-Transport|Content-Security|X-Content-Type|X-Frame|Referrer|Cache-Control)"
```

---

## まとめ：最小推奨設定

```http
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: strict-origin-when-cross-origin
Cache-Control: no-store
```

---

## 発展: CSP Nonce方式

> この内容は発展的なトピックです。CSPの基礎を理解してから学習することを推奨します。

インラインスクリプトを安全に使用するための高度な手法です。

### 仕組み

```html
<!-- サーバーがリクエストごとにランダムなnonceを生成 -->
<script nonce="abc123xyz">
  // このスクリプトのみ実行可能
</script>
```

```http
<!-- CSPヘッダー -->
Content-Security-Policy: script-src 'nonce-abc123xyz'
```

### 動作

1. サーバーがリクエストごとに一意のnonce値を生成
2. HTMLのscriptタグにnonce属性を付与
3. CSPヘッダーで同じnonce値を指定
4. nonce値が一致するスクリプトのみ実行を許可

### 注意点

```
- nonceは暗号学的に安全なランダム値（128ビット以上）
- リクエストごとに新しいnonceを生成（使い回し禁止）
- strict-dynamicと組み合わせることで柔軟性向上
```

---

## 参考資料

- [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers/)
- [MDN HTTP Headers](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers)
- [Content Security Policy Reference](https://content-security-policy.com/)

---

**最終更新**: 2025-12-25
**対象**: Webセキュリティ初心者〜中級者
