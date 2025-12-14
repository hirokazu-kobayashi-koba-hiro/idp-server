# Unit Testing - Detailed Class Lists & Verification Points

**Issue #415 対応**: 全モジュール・クラス単位の詳細テスト計画

---

## 📋 テスト対象クラス一覧 (実在クラスのみ)

### 🔴 **Critical Priority Modules** (Coverage Target: 75-85%)

#### **idp-server-core** (OAuth/OIDC コアエンジン)

##### **Token Management** (最優先 - 11 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `TokenRequestHandler` | トークンリクエスト処理 | • グラントタイプ別ルーティング<br />• エラーハンドリング<br />• プロトコル準拠 |
| `TokenRequestErrorHandler` | トークンエラーハンドリング | • エラー分類<br />• エラーレスポンス<br />• セキュリティ考慮 |
| `TokenRevocationHandler` | トークン取り消し | • 取り消し処理<br />• 関連トークン処理<br />• 監査ログ |
| `TokenIntrospectionHandler` | トークン検証 | • トークン有効性<br />• メタデータ取得<br />• 権限確認 |
| `TokenIntrospectionInternalHandler` | 内部トークン検証 | • 内部トークン処理<br />• システム間認証<br />• セキュリティ制約 |
| `TokenIntrospectionExtensionHandler` | 拡張トークン検証 | • 拡張機能対応<br />• カスタム検証<br />• プロトコル拡張 |
| `AuthorizationCodeGrantService` | 認可コード フロー | • コード検証・消費<br />• PKCE検証<br />• 状態管理 |
| `ClientCredentialsGrantService` | クライアント認証情報 フロー | • クライアント認証<br />• スコープ検証<br />• トークン発行 |
| `RefreshTokenGrantService` | リフレッシュトークン フロー | • トークン検証・更新<br />• スコープ制限<br />• ローテーション |
| `ResourceOwnerPasswordCredentialsGrantService` | パスワードグラント フロー | • ユーザー認証<br />• パスワード検証<br />• セキュリティ制約 |
| `OAuthTokenCreationService` | トークン生成サービス | • JWT署名・暗号化<br />• クレーム設定<br />• 有効期限計算 |

##### **Token Validators** (最優先 - 6 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `TokenRequestValidator` | トークンリクエスト検証 | • パラメータ検証<br />• 必須項目チェック<br />• フォーマット検証 |
| `TokenRequestCodeGrantValidator` | コードグラント検証 | • 認可コード有効性<br />• リダイレクトURI一致<br />• クライアント認証 |
| `ClientCredentialsGrantValidator` | クライアント認証情報検証 | • クライアント認証<br />• スコープ権限<br />• グラントタイプ許可 |
| `RefreshTokenGrantValidator` | リフレッシュトークン検証 | • トークン有効性<br />• バインディング確認<br />• 取り消し状態 |
| `CibaGrantValidator` | CIBA グラント検証 | • 認証リクエスト検証<br />• ユーザー同意確認<br />• デバイス認証 |
| `TokenIntrospectionValidator` | トークン検証バリデータ | • 検証リクエスト<br />• 権限確認<br />• セキュリティ制約 |

##### **OAuth Authorization** (高優先 - 5 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `OAuthHandler` | OAuth基本処理 | • 基本フロー制御<br />• 共通処理<br />• エラーハンドリング |
| `OAuthAuthorizeHandler` | 認可エンドポイント処理 | • パラメータ解析<br />• 認証状態確認<br />• リダイレクト処理 |
| `OAuthRequestHandler` | OAuthリクエスト処理 | • リクエスト解析<br />• セッション管理<br />• エラー応答 |
| `OAuthDenyHandler` | 拒否処理ハンドラ | • 拒否レスポンス<br />• エラー処理<br />• セキュリティ考慮 |
| `ClientAuthenticationHandler` | クライアント認証処理 | • 認証方式判定<br />• 認証実行<br />• mTLS対応 |

##### **OAuth Validators** (高優先 - 2 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `OAuthAuthorizeRequestValidator` | 認可リクエスト検証 | • 必須パラメータ<br />• クライアント検証<br />• スコープ妥当性 |
| `RequestObjectValidator` | リクエストオブジェクト検証 | • JWT検証<br />• クレーム検証<br />• セキュリティ要件 |

##### **Authentication & Identity** (高優先 - 5 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `AuthenticationInteractors` | 認証インタラクター管理 | • 認証方式選択<br />• インタラクター実行<br />• 結果統合 |
| `AuthenticationTransactionApi` | 認証トランザクションAPI | • 認証フロー管理<br />• 状態遷移<br />• セッション制御 |
| `IdPUserCreator` | IDPユーザー作成 | • ユーザー作成ロジック<br />• 初期設定<br />• データ整合性 |
| `UserUpdater` | ユーザー更新 | • ユーザー情報更新<br />• 変更検証<br />• 履歴管理 |
| `UserAuthenticationApi` | ユーザー認証API | • 認証実行<br />• 認証結果処理<br />• セキュリティ制約 |

#### **idp-server-platform** (プラットフォーム基盤)

##### **Security & Event Configuration** (最優先 - 3 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `SecurityEventUserAttributeConfiguration` | セキュリティイベントユーザー属性設定 | • 属性設定管理<br />• テナント別設定<br />• セキュリティ制約 |
| `SecurityEventLogConfiguration` | セキュリティイベントログ設定 | • ログ設定管理<br />• フィルタリング<br />• 出力制御 |
| `SecurityEventHookConfiguration` | セキュリティイベントフック設定 | • フック設定管理<br />• 配信設定<br />• エラーハンドリング |

##### **Data & Transaction Management** (高優先 - 4 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `TransactionManager` | トランザクション管理 | • ACID保証<br />• 分散トランザクション<br />• ロールバック処理 |
| `ReaderTransactionManager` | 読み取り専用トランザクション管理 | • 読み取り分離<br />• パフォーマンス最適化<br />• 接続管理 |
| `SqlExecutor` | SQL実行 | • SQL実行処理<br />• パラメータ管理<br />• 例外処理 |
| `SqlErrorClassifier` | SQLエラー分類 | • エラー分類<br />• リトライ可否判定<br />• ログ出力 |

##### **Database & Cache Management** (中優先 - 3 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `ApplicationDatabaseTypeProvider` | アプリケーションDB種別プロバイダ | • DB種別判定<br />• 設定管理<br />• 接続戦略 |
| `DbConnectionProvider` | DB接続プロバイダ | • 接続プール<br />• 接続監視<br />• フェイルオーバー |
| `CacheStore` | キャッシュストア | • キャッシュ戦略<br />• TTL管理<br />• 無効化処理 |

#### **idp-server-use-cases** (ユースケース層)

##### **Management Services** (高優先 - 7 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `UserManagementEntryService` | ユーザー管理エントリサービス | • CRUD操作<br />• 権限確認<br />• データ整合性 |
| `ClientManagementEntryService` | クライアント管理エントリサービス | • クライアント設定<br />• 秘密情報管理<br />• 設定検証 |
| `AuthorizationServerManagementEntryService` | 認可サーバー管理エントリサービス | • 認可サーバー設定<br />• エンドポイント管理<br />• セキュリティ設定 |
| `TenantManagementEntryService` | テナント管理エントリサービス | • テナント分離<br />• 設定管理<br />• リソース制限 |
| `RoleManagementEntryService` | ロール管理エントリサービス | • ロール定義<br />• 権限割り当て<br />• 継承処理 |
| `SecurityEventManagementEntryService` | セキュリティイベント管理 | • イベント処理<br />• 配信管理<br />• フィルタリング |
| `AuditLogManagementEntryService` | 監査ログ管理 | • ログ収集<br />• 検索・フィルタ<br />• 保持期間 |

---

### 🟡 **High Priority Modules** (Coverage Target: 70-80%)

#### **idp-server-authentication-interactors** (認証インタラクター)

##### **Core Interactors** (高優先 - 7 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `PasswordAuthenticationInteractor` | パスワード認証インタラクター | • 認証ロジック<br />• パスワード検証<br />• ロック機能 |
| `SmsAuthenticationInteractor` | SMS認証インタラクター | • SMS送信<br />• コード検証<br />• 再送制御 |
| `EmailAuthenticationInteractor` | メール認証インタラクター | • メール送信<br />• リンク生成<br />• 有効期限管理 |
| `WebAuthnAuthenticationInteractor` | WebAuthn認証インタラクター | • WebAuthn処理<br />• Assertion検証<br />• ユーザープレゼンス |
| `WebAuthnRegistrationInteractor` | WebAuthn登録インタラクター | • WebAuthn登録<br />• 認証器管理<br />• 公開鍵管理 |
| `FidoUafAuthenticationInteractor` | FIDO認証インタラクター | • FIDO認証処理<br />• デバイス検証<br />• 署名確認 |
| `FidoUafRegistrationInteractor` | FIDO登録インタラクター | • デバイス登録<br />• 公開鍵管理<br />• 証明書検証 |

##### **Executors** (中優先 - 3 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `WebAuthnExecutor` | WebAuthn実行 | • WebAuthn処理実行<br />• エラーハンドリング<br />• セキュリティ検証 |
| `EmailAuthenticationExecutor` | メール認証実行 | • メール認証実行<br />• 送信処理<br />• 状態管理 |
| `SmsAuthenticationExecutor` | SMS認証実行 | • SMS認証実行<br />• 送信処理<br />• 状態管理 |

#### **idp-server-control-plane** (コントロールプレーン)

##### **Request Validators** (高優先 - 2 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `OrganizationInitializeRequestValidator` | 組織初期化リクエスト検証 | • 組織設定検証<br />• 初期化要件<br />• セキュリティ制約 |
| `IdpServerInitializeRequestValidator` | IDPサーバー初期化リクエスト検証 | • サーバー設定検証<br />• 初期化パラメータ<br />• 依存関係確認 |

##### **Management APIs** (中優先 - 2 classes)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `IdpServerOperationApi` | IDPサーバー運用API | • サーバー運用<br />• 操作実行<br />• 状態管理 |
| `IdpServerStarterApi` | IDPサーバースターターAPI | • サーバー起動処理<br />• 初期化実行<br />• エラーハンドリング |

---

### 🟢 **Medium Priority Modules** (Coverage Target: 60-70%)

#### **Extension Modules**

##### **idp-server-core-extension-ciba** (CIBA拡張)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `CibaRequestHandler` | CIBAリクエスト処理 | • バックチャネル認証<br />• ユーザー識別<br />• 通知処理 |
| `CibaAuthorizeHandler` | CIBA認可ハンドラ | • CIBA認可処理<br />• ユーザー同意<br />• 認証完了 |
| `CibaGrantService` | CIBAグラントサービス | • グラント管理<br />• 認証完了処理<br />• トークン発行 |
| `CibaRequestValidator` | CIBAリクエスト検証 | • リクエスト検証<br />• ユーザー同意<br />• セキュリティ制約 |

##### **idp-server-core-extension-fapi** (FAPI拡張)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `FapiAdvanceVerifier` | FAPI Advanced検証 | • 高度セキュリティ<br />• mTLS要件<br />• JARM対応 |
| `TlsClientAuthAuthenticator` | TLSクライアント認証 | • mTLS認証<br />• 証明書検証<br />• セキュリティ確保 |

##### **idp-server-core-extension-pkce** (PKCE拡張)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `PkceVerifier` | PKCE検証 | • Code Challenge<br />• Code Verifier<br />• 方式検証 |
| `CodeChallengeCalculator` | コードチャレンジ計算 | • チャレンジ生成<br />• 暗号化処理<br />• エントロピー |

#### **Adapter Modules**

##### **idp-server-webauthn4j-adapter** (WebAuthn統合)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `WebAuthn4jCredentialDataSource` | WebAuthnクレデンシャルデータソース | • クレデンシャル管理<br />• データ永続化<br />• 検索・取得 |
| `WebAuthn4jCredentialSqlExecutor` | WebAuthnクレデンシャルSQL実行 | • SQL実行<br />• データ操作<br />• 例外処理 |

---

### 🔵 **Low Priority Modules** (Coverage Target: 40-60%)

#### **idp-server-core-adapter** (コアアダプター)

| クラス名 | 責任範囲 | 主要検証ポイント |
|---------|----------|------------------|
| `OAuthTokenCommandDataSource` | OAuthトークンコマンドデータソース | • トークンCRUD操作<br />• データ永続化<br />• 例外処理 |
| `OAuthTokenOperationCommandDataSource` | OAuthトークン操作コマンドデータソース | • トークン操作<br />• バッチ処理<br />• 整合性保証 |

---

## 🎯 検証ポイント詳細

### **Critical検証ポイント** (全モジュール共通)

#### **セキュリティ検証**
- **入力値検証**: SQLインジェクション、XSS、コマンドインジェクション対策
- **認証・認可**: 権限確認、セッション管理、アクセス制御
- **暗号化**: 強度確保、鍵管理、プロトコル準拠
- **監査ログ**: セキュリティイベント記録、改ざん防止

#### **プロトコル準拠性**
- **OAuth 2.0**: RFC 6749準拠、グラント型対応
- **OpenID Connect**: Core 1.0準拠、クレーム処理
- **FAPI**: Baseline/Advanced準拠、セキュリティ要件
- **CIBA**: RFC準拠、バックチャネル認証

#### **データ整合性**
- **ACID特性**: トランザクション整合性
- **制約チェック**: 外部キー、一意性制約
- **状態管理**: オブジェクト状態遷移
- **並行制御**: 競合状態、デッドロック対策

#### **エラーハンドリング**
- **例外処理**: 適切な例外タイプ、メッセージ
- **リトライ機能**: 一時的障害対応
- **ログ出力**: 詳細なエラー情報、追跡可能性
- **ユーザビリティ**: 分かりやすいエラーメッセージ

#### **パフォーマンス**
- **レスポンス時間**: SLA準拠、ベンチマーク
- **スループット**: 同時アクセス対応
- **リソース使用量**: メモリ、CPU効率
- **スケーラビリティ**: 負荷増加対応

### **テスト実装優先順序 (実在クラスベース)**

#### **Phase 1 (Week 1-2)**: Foundation Testing
1. **idp-server-platform** - 基盤クラス (10 classes)
2. **idp-server-core** - トークン管理・検証 (17 classes)

#### **Phase 2 (Week 3-4)**: Core Business Logic
1. **idp-server-core** - OAuth認可・認証 (12 classes)
2. **idp-server-use-cases** - 管理サービス (7 classes)

#### **Phase 3 (Week 5-6)**: Authentication & Control
1. **idp-server-authentication-interactors** (10 classes)
2. **idp-server-control-plane** (5 classes)

#### **Phase 4 (Week 7-8)**: Extensions & Adapters
1. **Extension modules** - CIBA, FAPI, PKCE (8 classes)
2. **Adapter modules** - WebAuthn, Core Adapter (4 classes)

**合計実在クラス**: 73 test classes、実現可能な65-75% overall coverage

この詳細計画により、各モジュールの責任範囲と検証ポイントが明確化され、系統的な単体テスト実装が可能になります。