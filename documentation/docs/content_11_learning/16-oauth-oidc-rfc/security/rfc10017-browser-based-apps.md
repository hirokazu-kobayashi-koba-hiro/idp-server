# RFC 10017: OAuth 2.0 for Browser-Based Applications

RFC 10017（BCP 212、2026年8月）は、ブラウザで動くアプリケーションが OAuth 2.0 を使うときの脅威と対策をまとめた仕様です。SPA でのトークンの扱いについて、いままで議論が分かれていた点に決着をつけています。

---

## 第1部: 概要編

### RFC 10017 とは何か？

**ブラウザで動く OAuth クライアントは、悪意ある JavaScript が動いた時点で何を失うのか**を分析し、アーキテクチャの選択肢を security properties とともに並べた文書です。

[RFC 9700（OAuth Security BCP）](./rfc9700-security-bcp.md) が OAuth 全般のベストプラクティスであるのに対し、こちらは**ブラウザ環境に特化**しています。

### なぜ必要なのか？

「SPA ではトークンをどこに置くべきか」は長く議論されてきました。localStorage は XSS に弱い、メモリなら安全、といった話です。

RFC 10017 はこの議論の前提を問い直します。

> the *malicious JavaScript code has the same privileges as the legitimate application code*

悪意あるコードは、正規のコードと**同じ権限**を持ちます。正規のコードがトークンを読めるなら、攻撃者も読めます。正規のコードが API を叩けるなら、攻撃者も叩けます。

つまり**保存場所を変えても、攻撃者の到達範囲は本質的には変わらない**。ここが出発点です。

### 3つのアーキテクチャ

| パターン | ブラウザが持つもの | OAuth クライアントは誰か |
|---------|------------------|----------------------|
| **BFF**（Backend for Frontend） | Cookie のみ | バックエンド（confidential） |
| **Token-Mediating Backend** | アクセストークン | バックエンド（confidential） |
| **Browser-Based OAuth Client** | アクセストークン + リフレッシュトークン | ブラウザ（public） |

上から順に安全で、下から順に手軽です。

---

## 第2部: 詳細編

## 1. 悪意ある JavaScript の脅威（§5）

### まず侵入させない

RFC はこれを最優先に置いています。挙げられている対策:

- 信頼できないデータを扱う際の、文脈に応じた出力エンコードとサニタイズ
- 未検査のサードパーティリソースを読み込まない、または制限する
- Subresource Integrity で読み込めるスクリプトを制限する
- nonce ベースまたはハッシュベースの CSP で未認可のスクリプト実行を防ぐ
- Origin 分離と HTML5 sandbox で境界を作る

そのうえで、**それでも侵入されうる**という前提で以降を組み立てます。

### 4つの攻撃シナリオ（§5.1）

RFC は攻撃を「トークンを盗む」だけに限定しません。

| # | シナリオ | 内容 |
|---|---------|------|
| 5.1.1 | **Single-Execution Token Theft** | 実行時点のトークンを一度だけ盗む |
| 5.1.2 | **Persistent Token Theft** | ハンドラを仕込み、継続的に最新のトークンを盗み続ける |
| 5.1.3 | **Acquisition and Extraction of New Tokens** | 隠し iframe で**新しい認可フローを回し**、独立したトークンを発行させる |
| 5.1.4 | **Proxying Requests via the User's Browser** | トークンを盗まず、**ユーザーのブラウザから直接**リソースサーバーを叩く |

後半2つが重要です。

**5.1.3** は、既存のトークンを盗む必要すらありません。ユーザーの認可サーバーとのセッションはまだ生きているので、隠し iframe でサイレントに認可フローを回せば、アプリが持っているものとは**別の新しいトークン**が手に入ります。トークンをメモリに置いていようが、この経路は塞がりません。

**5.1.4** はトークンすら要りません。攻撃者は正規のアプリと同じコンテキストでコードを動かしているので、アプリと同じようにリクエストを送るだけです。リソースサーバーから見ると、**正規のリクエストと区別がつきません**。

:::warning 保存場所の議論では守れないもの
5.1.3 と 5.1.4 は、トークンの保存場所に一切依存しません。「メモリに置けば安全」という理解は、この2つを見落としています。
:::

### 3つの帰結（§5.2）

| # | 帰結 | 内容 |
|---|------|------|
| 5.2.1 | 盗まれたリフレッシュトークンの悪用 | 長期のアクセスを与える。最も影響が大きい |
| 5.2.2 | 盗まれたアクセストークンの悪用 | 有効期限まで、スコープの範囲で悪用可能 |
| 5.2.3 | **Client Hijacking** | トークンを盗まず、クライアントそのものを乗っ取る |

Client Hijacking は、トークン窃取より**弱い**と RFC は位置づけています。攻撃者はトークンを直接制御できず、クライアントに課されたセキュリティポリシー（CORS 等）の制約を受けるためです。

---

## 2. 3つのアーキテクチャパターン（§6）

### 2.1 BFF（Backend for Frontend）

バックエンドが OAuth の責務を全部引き受け、ブラウザにはトークンを一切渡しません。

BFF の3つの責務:

1. **confidential クライアント**として認可サーバーとやり取りする
2. アクセストークンとリフレッシュトークンを**Cookie ベースのセッションの文脈で管理**し、ブラウザに露出させない
3. リソースサーバーへの**すべてのリクエストを中継**し、適切なアクセストークンを付与する

:::note BFF は API Gateway ではありません
RFC は「BFF がフロントエンドアプリケーションの OAuth クライアントになる」と明記しています。リバースプロキシや API Gateway と混同しないよう注意が必要です。BFF はサーバーサイドで動きますが、**フロントエンドアプリケーションの構成要素**です。
:::

攻撃シナリオへの耐性:

| シナリオ | 結果 |
|---------|------|
| 5.1.1 / 5.1.2 トークン窃取 | **防げる**（ブラウザにトークンが無い） |
| 5.1.3 新しいトークンの取得 | **防げる**（BFF が confidential クライアントなので、ブラウザから新しいフローを回せない） |
| 5.1.4 ブラウザ経由のプロキシ | **防げない**（攻撃者は BFF にリクエストを送れる） |

HttpOnly Cookie を使うことで、攻撃者がセッション状態に直接アクセスすることを防ぎ、**client hijacking から session hijacking への昇格**を止められます。

### 2.2 Token-Mediating Backend

バックエンドが confidential クライアントとしてトークンを取得し、**アクセストークンだけをブラウザに渡す**方式です。ブラウザはリソースサーバーを直接叩きます。

BFF より軽量（全リクエストの中継が不要）ですが、RFC は「BFF より安全性は低い」と位置づけています。

| シナリオ | 結果 |
|---------|------|
| リフレッシュトークンの悪用 | **防げる**（ブラウザに渡らない） |
| 5.1.3 新しいトークンの取得 | **防げる**（confidential クライアント） |
| アクセストークンの窃取 | **防げない**（ブラウザに露出している） |
| 5.1.4 ブラウザ経由のプロキシ | **防げない** |

### 2.3 Browser-Based OAuth 2.0 Client

ブラウザ自身が OAuth クライアントとして、すべての責務を負う方式です。バックエンドは関与しません。

RFC の評価は明快です。

> this application architecture is vulnerable to all attack scenarios discussed earlier

**§5.1 のすべての攻撃シナリオに対して脆弱**です。攻撃者はアクセストークンとリフレッシュトークンの両方を認可サーバーから取得でき、ユーザーに代わって保護されたリソースへ長期間アクセスできる可能性があります。

---

## 3. 非推奨・廃止されたパターン（§7）

| § | 何が非推奨か | 理由 |
|---|---------|------|
| 7.1 | 単一ドメインアプリで **OAuth を使うこと** | セッション管理を OAuth で置き換える必要はない |
| 7.2 | **Implicit Grant** | アクセストークンがフラグメントで返るため、傍受の機会が多い |
| 7.3 | **Resource Owner Password Credentials Grant** | 資格情報をクライアントに渡す構造そのものが問題 |
| 7.4 | **Service Worker で OAuth フローを処理** | 攻撃者の到達範囲を狭められない |

:::note 7.1 だけ向きが逆です
7.2〜7.4 は「その方式を使うな」ですが、**7.1 は「OAuth を使うな」**です。RFC の見出しは "Single-Domain Browser-Based Applications (Not Using OAuth)" で、OAuth を使わない構成のほうが推奨されています。

> Too often, simple applications are made needlessly complex by using OAuth to replace the concept of session management.

フロントエンドとバックエンドが同一ドメインなら、両者の間のアクセス制御に OAuth は要りません。サーバーサイドの Cookie ベースのセッションで足ります。ユーザー認証を外部プロバイダに委ねる目的で OpenID Connect を使うのは、これとは別の話です。
:::

Implicit Grant について RFC は、認可サーバー側での対処を規範として述べています。

> the authorization server MUST NOT issue access tokens in the authorization response

7.4 は、かつて「Service Worker がトークンを隠せば安全では」と提案されたパターンです。Service Worker がフローを実行し、クライアントコードから認可サーバーへの直接呼び出しをブロックする、という発想でしたが、RFC は採用していません。

---

## 4. ブラウザでのトークン保存（§8）

RFC は保存場所ごとの性質を整理していますが、**§5 の分析を踏まえると保存場所の選択は決定打にならない**という位置づけです。

| § | 保存場所 |
|---|---------|
| 8.1 | Cookie |
| 8.2 | Service Worker |
| 8.3 | Web Worker |
| 8.4 | メモリ内 |
| 8.5 | 永続ストレージ |
| 8.6 | ブラウザストレージ API のファイルシステム上の考慮 |

保存場所ごとの具体的な比較は [トークン保存のセキュリティ](../../06-security/05-token-storage-security.md) を参照してください。

---

## 5. セキュリティ考慮事項（§9）

### 5.1 トークンの権限を減らす（§9.1）

どのアーキテクチャにも適用できる一般原則として、RFC は次を挙げています。

- アクセストークンの**有効期間を短く**し、更新はリフレッシュトークンに任せる
- アクセストークンに紐づく**スコープや権限を減らす**
- [RFC 8707](../extensions/rfc8707-resource-indicators.md) の拡張で、アクセストークンを**単一のリソースに限定**する

OpenID Connect を使う場合は、ID Token のクレームによる情報漏洩にも注意が必要です。

> The authorization server SHOULD NOT include any ID Token claims that aren't used by the client.

### 5.2 Sender-Constrained Token（§9.2）

RFC は最初に釘を刺します。

> the use of sender-constrained tokens does not solve the security limitations of browser-only OAuth clients

**ブラウザのみのクライアントが抱える制約は、sender-constrained token では解決しません。** そのうえで、token-mediating backend やブラウザのみのクライアントで十分なユースケースであれば、[DPoP（RFC 9449）](../extensions/rfc9449-dpop.md) 等によってアクセストークンとリフレッシュトークンの安全性を高められる、としています。

盗まれても、鍵の所持を証明できなければトークン単体では使えないためです。

### 5.3 Origin による分離（§9.4）

異なる信頼レベルのアプリケーションを別々の Origin に置くことで、境界を作れます。§5.2.3 で触れられているように、リソースサーバー側の CORS ポリシーで `web.example.org` からのリクエストを拒否する、といった防御が成立するのはこの分離があるためです。

---

## まとめ

| 問い | RFC 10017 の答え |
|------|----------------|
| トークンをどこに置くべきか？ | **保存場所を変えても攻撃者の到達範囲は本質的に変わらない** |
| ではどうすべきか？ | **ブラウザにトークンを渡さない**（BFF） |
| BFF が難しい場合は？ | Token-Mediating Backend でリフレッシュトークンだけでも守る |
| ブラウザのみのクライアントは？ | §5.1 のすべての攻撃に脆弱であることを理解したうえで選ぶ |
| DPoP を使えば安全？ | ブラウザのみのクライアントの制約は解決しない |

議論の焦点が「**保存場所**」から「**そもそもブラウザにトークンを渡すか**」へ移った、というのがこの RFC の要点です。

---

## 参考リンク

- [RFC 10017: OAuth 2.0 for Browser-Based Applications](https://www.rfc-editor.org/rfc/rfc10017.html)
- [RFC 9700: OAuth 2.0 Security Best Current Practice](./rfc9700-security-bcp.md) - OAuth 全般のベストプラクティス
- [トークン保存のセキュリティ](../../06-security/05-token-storage-security.md) - 保存場所ごとの比較
- [OAuth セキュリティ脅威](../../06-security/04-oauth-security-threats.md) - 攻撃手法の全体像
- [RFC 8707: Resource Indicators](../extensions/rfc8707-resource-indicators.md) - アクセストークンのリソース限定
- [RFC 9449: DPoP](../extensions/rfc9449-dpop.md) - Sender-Constrained Token
