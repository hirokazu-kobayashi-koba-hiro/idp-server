# ユースケース一覧

`idp-server` における主要なユースケースを網羅的に整理したものです。

アクター・機能分類ごとにユースケースを分けることで、機能全体の把握、仕様設計、テスト設計、および外部連携設計のベースとして活用できます。

## アクター定義

各ユースケースに登場するアクターの役割と責務を定義します。

| アクター | 役割 | システム境界 | 説明 |
|---------|------|------------|------|
| **ユーザー** | エンドユーザー | 外部 | サービスを利用する人（ブラウザ/モバイルアプリ経由でアクセス） |
| **RP (Relying Party)** | クライアントアプリケーション | 外部 | IdP を信頼してユーザー認証を委任するアプリケーション（OAuth 2.0 Client） |
| **リソースサーバー** | API サーバー | 外部 | アクセストークンで保護されたリソース（API）を提供するサーバー |
| **システム** | 外部サービス | 外部 | 身元確認サービス等の外部連携システム（コールバック経由で連携） |
| **非同期ワーカー** | バックグラウンド処理 | 内部 | イベント駆動で非同期処理を実行（通知送信・監査ログ記録等） |
| **サーバー管理者** | 運用者 | 内部 | IdP サーバー全体の初期設定・メンテナンスを実施 |
| **テナントオーナー** | テナント所有者 | 外部 | テナント作成者（初期オンボーディングを実施） |
| **テナント管理者** | テナント運用者 | 外部 | テナント内の設定・ユーザー・クライアントを管理 |

## 用語集

主要な専門用語の説明です。

| 用語 | 正式名称 | 説明 | 関連仕様 |
|------|---------|------|---------|
| **OIDC** | OpenID Connect | OAuth 2.0 ベースの認証プロトコル。ID トークンによるユーザー認証を提供 | [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html) |
| **CIBA** | Client Initiated Backchannel Authentication | デバイス外（バックチャンネル）で認証を完了するフロー。IoT・ATM 等で利用 | [OpenID Connect CIBA](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html) |
| **FIDO UAF** | Fast Identity Online - Universal Authentication Framework | パスワードレス認証の標準規格。生体認証等を使用 | [FIDO UAF Spec](https://fidoalliance.org/specs/fido-uaf-v1.2-ps-20201020/) |
| **RP** | Relying Party | OpenID Provider を信頼するクライアントアプリケーション（OAuth 2.0 Client と同義） | [OIDC Terminology](https://openid.net/specs/openid-connect-core-1_0.html#Terminology) |
| **認可コード** | Authorization Code | トークンと交換するための一時的なコード（OAuth 2.0 Authorization Code Flow） | [RFC 6749 Section 1.3.1](https://www.rfc-editor.org/rfc/rfc6749#section-1.3.1) |
| **アクセストークン** | Access Token | リソースサーバーへのアクセス権限を表すトークン | [RFC 6749 Section 1.4](https://www.rfc-editor.org/rfc/rfc6749#section-1.4) |
| **リフレッシュトークン** | Refresh Token | 新しいアクセストークンを取得するためのトークン | [RFC 6749 Section 1.5](https://www.rfc-editor.org/rfc/rfc6749#section-1.5) |
| **ID トークン** | ID Token | ユーザー認証情報を含む JWT（OIDC） | [OIDC Core Section 2](https://openid.net/specs/openid-connect-core-1_0.html#IDToken) |
| **eKYC** | electronic Know Your Customer | オンラインでの本人確認プロセス | - |
| **MFA** | Multi-Factor Authentication | 多要素認証（パスワード + 生体認証等） | - |
| **SSF** | Shared Signals Framework | セキュリティイベントを共有するためのフレームワーク | [OpenID SSF](https://openid.net/specs/openid-sse-framework-1_0.html) |

## アプリケーション

エンドユーザーやクライアントアプリケーションが利用する機能です。

### 認証・認可（ユーザー）

| # | ユーザーストーリー | システム機能 | 実装詳細 |
|---|---------------|------------|---------|
| 1 | **アプリケーションにログインしたい** | OAuth 2.0 認可コードフローによる認証・認可 | <details><summary>フロー概要</summary>認可リクエスト → 認証画面（ログイン） → 同意画面 → 認可コード発行 → RP へリダイレクト<br/><br/>**関連仕様**: [RFC 6749 Section 4.1](https://www.rfc-editor.org/rfc/rfc6749#section-4.1)</details> |
| 2 | **Google/GitHub アカウントでログインしたい** | フェデレーション認証（外部 IdP 連携） | <details><summary>フロー概要</summary>外部 IdP へリダイレクト → 外部 IdP で認証 → コールバック処理 → IdP セッション確立</details> |
| 3 | **ATM・IoT デバイスで認証したい** | CIBA（バックチャンネル認証） | <details><summary>フロー概要</summary>デバイスが認証リクエスト送信 → ユーザーがモバイルアプリで承認/拒否 → デバイスがトークン取得<br/><br/>**関連仕様**: [OpenID Connect CIBA](https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html)</details> |
| 4 | **一度ログインしたら他のアプリでも自動ログインしたい** | SSO（シングルサインオン）セッション管理 | <details><summary>フロー概要</summary>既存セッション確認 → 自動認可（同意画面スキップ） → 認可コード発行</details> |
| 5 | **すべてのアプリからログアウトしたい** | RP-Initiated Logout（全セッション終了） | <details><summary>フロー概要</summary>IdP セッション削除 → 全 RP へログアウト通知（Front-Channel/Back-Channel）</details> |

### トークン・リソースアクセス

| # | ユーザーストーリー | システム機能 | 実装詳細 |
|---|---------------|------------|---------|
| 1 | **API を呼び出したい** | アクセストークン発行・リフレッシュ | <details><summary>フロー概要</summary>認可コードをトークンに交換（Token Exchange）<br/>トークン期限切れ時はリフレッシュトークンで更新<br/><br/>**関連仕様**: [RFC 6749 Section 6](https://www.rfc-editor.org/rfc/rfc6749#section-6)</details> |
| 2 | **ログアウト時にトークンを無効化したい** | トークン失効（Revocation） | <details><summary>概要</summary>アクセストークン・リフレッシュトークンを無効化し、以降の利用を防止<br/><br/>**関連仕様**: [RFC 7009](https://www.rfc-editor.org/rfc/rfc7009)</details> |
| 3 | **ユーザーのプロフィール情報を取得したい** | UserInfo エンドポイント | <details><summary>概要</summary>アクセストークンを使って email・name・profile 等のユーザー属性を取得</details> |

### 本人確認（eKYC）

| # | ユーザーストーリー | システム機能 | 実装詳細 |
|---|---------------|------------|---------|
| 1 | **オンラインで本人確認を完了したい** | eKYC 申請・審査結果取得 | <details><summary>フロー概要</summary>身元確認申請送信 → 外部 eKYC サービスで審査 → 結果コールバック受信 → ユーザーが結果取得<br/><br/>**操作**: 申請・履歴照会・結果取得・キャンセル<br/>**外部連携**: eKYC サービスとの HTTP 連携（動的テンプレート対応）</details> |

### 多要素認証・デバイス管理

| # | ユーザーストーリー | システム機能 | 実装詳細 |
|---|---------------|------------|---------|
| 1 | **SMS・Email で追加認証したい** | MFA（多要素認証）フロー | <details><summary>フロー概要</summary>MFA トランザクション開始 → コード送信（SMS/Email） → ユーザーがコード入力 → 検証完了</details> |
| 2 | **生体認証・FIDO で認証したい** | FIDO UAF 統合 | <details><summary>概要</summary>FIDO UAF プロトコルによる生体認証<br/>デバイス登録履歴・最終利用日時の管理</details> |

### システム統合（RP・リソースサーバー向け）

| # | 利用者 | ユーザーストーリー | システム機能 | 実装詳細 |
|---|-------|---------------|------------|---------|
| 1 | RP | **IdP の設定を自動取得したい** | OIDC Discovery（メタデータ配信） | <details><summary>概要</summary>IdP エンドポイント・サポート機能・公開鍵を JSON で配信<br/>RP は動的に設定を取得可能</details> |
| 2 | リソースサーバー | **アクセストークンの有効性を確認したい** | Token Introspection | <details><summary>概要</summary>トークンの有効性・スコープ・有効期限を検証<br/><br/>**関連仕様**: [RFC 7662](https://www.rfc-editor.org/rfc/rfc7662)</details> |
| 3 | 外部システム | **テナント設定を動的に取得したい** | テナントメタデータ配信 | <details><summary>概要</summary>テナント固有の設定（外部 API URL・カスタム属性等）を JSON で配信</details> |

### バックグラウンド処理（非同期ワーカー）

| # | ユーザーストーリー | システム機能 | 実装詳細 |
|---|---------------|------------|---------|
| 1 | **ユーザー作成時に Welcome メールを送りたい** | ユーザーライフサイクルイベント処理 | <details><summary>トリガー</summary>イベント: ユーザー作成/更新/削除<br/>処理: 通知送信・外部システム連携・監査ログ記録</details> |
| 2 | **不正ログイン時にアカウントをロックしたい** | セキュリティイベント処理 | <details><summary>トリガー</summary>イベント: 連続ログイン失敗・異常アクセス検知<br/>処理: アカウントロック・管理者通知・SSF イベント送信</details> |

## コントロールプレーン

テナント管理者が IdP の設定・リソースを管理するための機能です。

### 初期セットアップ

| # | 管理者 | ユーザーストーリー | システム機能 | 実装詳細 |
|---|-------|---------------|------------|---------|
| 1 | サーバー管理者 | **IdP サーバーを起動したい** | サーバー初期設定 | <details><summary>概要</summary>初期管理者作成・システムテナント作成・基本設定の初期化</details> |
| 2 | サーバー管理者 | **最初のテナントを作成したい** | テナント初期登録 | <details><summary>概要</summary>初回のみ特別権限でテナント作成可能</details> |
| 3 | テナントオーナー | **新規テナントを利用開始したい** | テナントオンボーディング | <details><summary>概要</summary>テナント作成 + 初期管理者登録 + 基本設定を一括実行</details> |

### テナント・リソース管理

| # | ユーザーストーリー | システム機能 | 実装詳細 |
|---|---------------|------------|---------|
| 1 | **複数のテナントを管理したい** | テナント CRUD | <details><summary>操作</summary>テナント作成・一覧取得・詳細取得・設定更新・削除<br/><br/>**dry_run 対応**: 変更前プレビュー可能</details> |
| 2 | **OAuth クライアントを登録・管理したい** | Client CRUD | <details><summary>操作</summary>クライアント登録・一覧取得・詳細取得・設定更新・削除<br/><br/>**設定項目**: redirect_uri・grant_types・scopes・token 有効期限等</details> |
| 3 | **ユーザーアカウントを管理したい** | User CRUD + 権限管理 | <details><summary>操作</summary>基本 CRUD + パスワード更新・ロール割当・テナント割当・組織割当<br/><br/>**マルチテナント対応**: ユーザーは複数テナント・組織に所属可能</details> |
| 4 | **IdP の動作設定を変更したい** | 認可サーバー設定 | <details><summary>設定項目</summary>トークン有効期限・サポートする grant_type・PKCE 必須化・セッション設定等</details> |

### 認証・認可設定

| # | ユーザーストーリー | システム機能 | 実装詳細 |
|---|---------------|------------|---------|
| 1 | **認証フローをカスタマイズしたい** | 認証設定管理 | <details><summary>設定例</summary>MFA 必須化・パスワードポリシー・セッションタイムアウト・認証手段の優先順位等<br/><br/>**動的テンプレート対応**: JSON で柔軟に設定可能</details> |
| 2 | **Google・GitHub ログインを有効化したい** | フェデレーション設定 | <details><summary>設定例</summary>外部 IdP 種別（Google・GitHub・SAML 等）・OAuth 2.0 Client ID/Secret・スコープ・属性マッピング</details> |
| 3 | **本人確認プロバイダーを連携したい** | 身元確認設定 | <details><summary>設定例</summary>eKYC サービス API 連携・審査フロー定義・コールバック URL・HTTP リクエストテンプレート</details> |

### セキュリティ・監査

| # | ユーザーストーリー | システム機能 | 実装詳細 |
|---|---------------|------------|---------|
| 1 | **セキュリティイベントを外部に通知したい** | Security Event Hook 設定 | <details><summary>設定例</summary>Webhook URL・通知イベント種別（ログイン失敗・アカウントロック等）・認証方式（HMAC・OAuth 2.0）</details> |
| 2 | **ユーザー操作履歴を監査したい** | 監査ログ閲覧 | <details><summary>概要</summary>全ての管理操作（CRUD）の履歴を記録・照会可能<br/>フィルタリング: 日時・操作種別・ユーザー・リソース種別</details> |

### チーム管理

| # | ユーザーストーリー | システム機能 | 実装詳細 |
|---|---------------|------------|---------|
| 1 | **他のメンバーをテナントに招待したい** | メンバー招待管理 | <details><summary>フロー概要</summary>招待メール送信 → 受信者が招待リンククリック → アカウント作成 → テナントメンバーとして登録<br/><br/>**期限管理**: 招待の有効期限・使用済み招待の無効化</details> |
