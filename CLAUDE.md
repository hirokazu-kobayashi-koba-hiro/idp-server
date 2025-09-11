# Claude Code Context - idp-server

## プロジェクト概要
- **プロジェクト名**: idp-server
- **種類**: 包括的なアイデンティティプロバイダーフレームワーク
- **言語**: Java 21+ (Spring Boot)
- **ビルドシステム**: Gradle
- **現在のブランチ**: feat/issues-294

## アーキテクチャの特徴
- フレームワーク非依存のコア設計
- モジュラー構造による高い拡張性
- マルチテナント対応
- OAuth 2.0, OpenID Connect, CIBA, FAPI, Verifiable Credentials サポート

## プロジェクト構成

### メインアプリケーション
- `app/` - Spring Boot メインアプリケーション

### コアライブラリ (libs/)
- `idp-server-core` - コアエンジンロジック
- `idp-server-platform` - プラットフォーム基盤
- `idp-server-use-cases` - ユースケース実装
- `idp-server-control-plane` - コントロールプレーン

### 拡張機能
- `idp-server-core-extension-pkce` - PKCE (RFC 7636)
- `idp-server-core-extension-fapi` - Financial API
- `idp-server-core-extension-ciba` - Client Initiated Backchannel Authentication
- `idp-server-core-extension-verifiable-credentials` - Verifiable Credentials
- `idp-server-core-extension-ida` - Identity Assurance

### アダプター・統合
- `idp-server-springboot-adapter` - Spring Boot統合
- `idp-server-core-adapter` - コアアダプター
- `idp-server-webauthn4j-adapter` - WebAuthn/FIDO2統合
- `idp-server-email-aws-adapter` - AWS SESメール送信
- `idp-server-notification-fcm-adapter` - Firebase Cloud Messaging

### セキュリティ・認証
- `idp-server-authentication-interactors` - 認証インタラクター (Password, Email, WebAuthn等)
- `idp-server-security-event-framework` - セキュリティイベントフレームワーク
- `idp-server-security-event-hooks` - セキュリティイベントフック

### データ・連携
- `idp-server-database` - データベース層 (PostgreSQL/MySQL対応)
- `idp-server-federation-oidc` - OIDC連携

## 開発環境要件
- Java 21以上
- Docker & Docker Compose
- Node.js 18以上 (E2Eテスト用)

## テスト構成
- `e2e/` - E2Eテストスイート
  - `scenario/` - 現実的なユーザー・システム動作テスト
  - `spec/` - 仕様準拠テスト (OIDC, FAPI, JARM, VC等)
  - `monkey/` - 障害注入・エッジケーステスト
- `performance-test/` - K6によるパフォーマンステスト

## その他のディレクトリ
- `documentation/` - Docusaurus ドキュメント (英語・日本語対応)
- `config-sample/` - サンプル設定
- `secret/` - 機密情報・鍵管理
- `nginx/` - Nginxリバースプロキシ設定

## 設定・起動
1. `./init.sh` - API Key/Secret生成
2. 環境変数設定 (.env.local)
3. Docker イメージビルド
4. `docker compose up`
5. `./setup.sh` - 初期設定

## ビルド・テストコマンド
- **フォーマット修正**: `./gradlew spotlessApply` (ビルド前に必ず実行)
- ビルド: `./gradlew build`
- テスト: `./gradlew test`
- E2Eテスト: `cd e2e && npm test`
- 品質チェック: `./gradlew spotlessCheck`

## libs/idp-server-core 解析結果

### 概要
- **役割**: OpenID Connect/OAuth 2.0 のコアエンジン実装
- **依存関係**: idp-server-platform のみに依存
- **設計**: ドメイン駆動設計(DDD)による階層化アーキテクチャ

### 主要パッケージ構造

#### 1. 認証 (Authentication)
- `org.idp.server.core.openid.authentication/`
- ACR、LoA、MFA、認証ポリシー、インタラクション実行
- プラグイン可能な認証メカニズム

#### 2. OAuth/OIDC プロトコル
- `org.idp.server.core.openid.oauth/`
- **設定管理**: クライアント設定、サーバー設定
- **リクエスト処理**: 認可リクエスト、バリデーター
- **レスポンス生成**: 認可レスポンス、エラーハンドリング
- **クライアント認証**: client_credentials、mTLS、プラグイン

#### 3. プロトコル拡張
- **CIBA**: `type/ciba/` - Client Initiated Backchannel Authentication
- **PKCE**: `type/pkce/` - Proof Key for Code Exchange
- **RAR**: `type/rar/` - Rich Authorization Requests
- **VC**: `type/vc/`, `type/verifiablecredential/` - Verifiable Credentials
- **mTLS**: `type/mtls/` - Mutual TLS

#### 4. トークン管理
- `org.idp.server.core.openid.token/`
- **発行**: TokenRequestHandler、OAuthTokenCreationService
- **検証**: TokenIntrospectionHandler (内部/外部/拡張)
- **取り消し**: TokenRevocationHandler
- **プロトコル**: DefaultTokenProtocol

#### 5. ユーザーアイデンティティ
- `org.idp.server.core.openid.identity/`
- **コアエンティティ**: User - 包括的なユーザープロファイル
- **デバイス管理**: AuthenticationDevice、AuthenticationDevices
- **権限管理**: UserRole、権限、テナント/組織割り当て
- **ID Token**: プラグイン可能なID Token生成

#### 6. グラント管理
- `org.idp.server.core.openid.grant_management/`
- 認可グラント、同意管理

#### 7. ディスカバリー・メタデータ
- `org.idp.server.core.openid.discovery/`
- OpenID Connect Discovery実装

#### 8. フェデレーション
- `org.idp.server.core.openid.federation/`
- **SSO**: OIDC、SAML連携
- 外部IdP統合

#### 9. UserInfo エンドポイント
- `org.idp.server.core.openid.userinfo/`
- ユーザー情報提供、プラグイン拡張

### 主要ドメインモデル

#### ClientConfiguration
- OAuth/OIDCクライアント設定の包括的管理
- 拡張設定(ClientExtensionConfiguration)サポート
- FAPI、mTLS、Verifiable Credentials対応

#### User
- OpenID Connect標準クレーム完全対応  
- マルチテナント・組織サポート
- 認証デバイス管理(WebAuthn等)
- Verified Claims、カスタムプロパティ
- ユーザーライフサイクル管理

#### GrantType
- 標準OAuth グラント + CIBA拡張
- `authorization_code`, `client_credentials`, `refresh_token`, `ciba`

### アーキテクチャ特徴

1. **レイヤー分離**
   - Handler (プロトコル処理)
   - Service (ビジネスロジック) 
   - Repository (データアクセス抽象化)

2. **拡張性**
   - Plugin インターフェース
   - Extension 設定
   - プロトコル固有の type パッケージ

3. **マルチテナント対応**
   - TenantIdentifier、OrganizationIdentifier
   - テナント別設定管理

4. **セキュリティ**
   - 包括的なバリデーション
   - エラーハンドリング
   - 監査ログ対応

## ドキュメント解析結果

### プロジェクトのビジョン・価値提案
**「信頼できるIDを、すべてのサービスへ。」**

- **身元確認済みIDの発行・連携**が核心価値
- eKYC + Verifiable Credential による **本人確認済みID基盤**
- **マルチテナント対応**による企業・組織単位での運用

### 主要な特徴（ドキュメントより）

#### 1. 身元確認・ID検証
- **OIDC IDA (Identity Assurance)** 完全対応
- **verified_claims** によるOIDC標準準拠の身元情報連携  
- **eKYCサービス連携**: 外部身元確認サービスとのAPI統合
- **Verifiable Credential**: VC形式でのID発行・検証

#### 2. 包括的プロトコルサポート
- **OAuth 2.0 / OpenID Connect** 全フロー対応
- **CIBA** (Client Initiated Backchannel Authentication): Poll/Push/Ping
- **FAPI Baseline/Advanced**: 金融グレードセキュリティ
- **拡張仕様**: PKCE, JAR, JARM, RAR, PAR

#### 3. 高度なマルチテナント機能
- **テナント別分離**: データ・設定・UI完全分離
- **ブランディング**: テナント別テーマ・文言カスタマイズ
- **認証ポリシー**: テナント別MFA・セキュリティ設定

#### 4. 豊富な認証方式
- **パスワード**: 強度・リトライ制限設定
- **MFA**: SMS・Email・FIDO2・TOTP
- **WebAuthn/FIDO2**: Webauthn4j による完全実装
- **外部連携**: OIDC/SAML連携、レガシーシステム対応
- **カスタム認証**: プラグイン拡張可能

#### 5. エンタープライズ機能
- **セキュリティ監査**: 操作ログ・イベント追跡
- **外部通知**: Slack・Webhook・SSF(Shared Signals Framework)
- **分散セッション**: Redis によるスケーラブル構成
- **データベース**: PostgreSQL(推奨)・MySQL対応

### 想定利用ケース（ドキュメントより）

#### 🏦 金融機関
- オンライン口座開設での本人確認
- eKYC → verified_claims → 信頼性の高いID連携
- FAPI準拠による金融グレードセキュリティ

#### 🏢 企業グループ  
- グループ会社間での共通ID基盤
- テナント分離による子会社別運用
- ブランド別UI・認証ポリシー

#### 🧾 行政・公的機関
- デジタル住民サービスID基盤
- Verifiable Credential による証明書発行
- マイナンバー連携対応

#### 🛍️ SaaS/Webサービス
- 信頼性の高いユーザーID導入
- パスワードレス認証(WebAuthn/Passkey)
- 年齢確認・職業確認機能

### 技術アーキテクチャ洞察

#### 身元確認フロー
1. **申込みパターン**: idp-server経由でeKYC申込み → 審査結果反映
2. **直接登録パターン**: 外部eKYC結果を直接登録

#### verified_claims 取得方式
1. **OIDC4IDA標準**: claims パラメータ → IDトークン内埋め込み
2. **独自仕様**: `verified_claims:name` スコープ → アクセストークン内埋め込み

#### 設定の柔軟性
- **120以上の設定項目**による詳細制御
- テナント・クライアント単位での個別設定
- プラグインによる無制限拡張

### 結論
idp-serverは単なるIdPではなく、**身元確認済みIDを軸とした包括的なアイデンティティプラットフォーム**として設計されている。特に日本の金融・行政分野でのデジタルID基盤として、eKYC・OIDC IDA・VCを統合したエンタープライズグレードのソリューション。

## E2Eテスト解析結果

### テスト構成・アーキテクチャ

**技術スタック**
- **Jest** - テストフレームワーク
- **axios** - HTTP クライアント
- **jose** - JWT/JWS/JWE 処理
- **openid-client** - OpenID Connect クライアント
- **@faker-js/faker** - テストデータ生成（Monkeyテスト用）

### 3層テスト戦略

#### 📘 spec/ - 仕様準拠テスト（32ファイル）
**RFC・標準仕様の厳密な実装検証**

- **OAuth 2.0**: `rfc6749_4_1_code.test.js` (認可コード)、`rfc6749_4_4_client_credentials.test.js`
- **OpenID Connect**: `oidc_core_3_1_code.test.js`、`oidc_core_2_id_token.test.js`、`oidc_discovery.test.js`
- **FAPI**: `fapi_baseline.test.js`、`fapi_advance.test.js` - 金融グレード
- **CIBA**: `ciba_authentication_request.test.js`、`ciba_token_request.test.js` - バックチャネル認証
- **拡張仕様**: 
  - `rfc9396_rar.test.js` (Rich Authorization Requests)
  - `rfc9126_par.test.js` (Pushed Authorization Requests)
  - `rfc7662_token_introspection.test.js`
  - `jarm.test.js` - JWT形式レスポンス

#### 📕 scenario/ - 現実的シナリオテスト（17ファイル）
**実際のユースケースベース統合テスト**

**アプリケーション統合**:
- `scenario-03-mfa-registration.test.js` - MFA登録フロー
- `scenario-08-multi-app_fido-authn.test.js` - FIDO認証

**コントロールプレーン管理**:
- `client_management.test.js` - クライアント管理
- `user-management.test.js` - ユーザー管理
- `role-management.test.js` - ロール・権限管理
- `security-event-management.test.js` - セキュリティイベント
- `audit-log-management.test.js` - 監査ログ

**リソースサーバー**:
- `token_introspection_extensions.test.js` - 拡張トークン検証

#### 🐒 monkey/ - 故障注入テスト（1ファイル）
**異常系・エッジケース検証**

- `ciba-monkey.test.js` - CIBA異常パターン
- **Faker.js**による ランダムデータ生成
- **不正パラメータ**注入による堅牢性テスト

### テストインフラ・ライブラリ設計

#### 共通ライブラリ (`src/lib/`)
- **jose/**: JWT署名・検証・暗号化
- **oauth/**: OAuth フロー支援
- **http/**: HTTP通信ラッパー  
- **pki/**: 公開鍵基盤操作
- **webauthn/**: WebAuthn/FIDO2 
- **vc/**: Verifiable Credential

#### 設定管理 (`testConfig.js`)
```javascript
serverConfig = {
  issuer: "${backendUrl}/67e7eae6-62b0-4500-9eff-87459f63fc66",
  authorizationEndpoint: "...",
  tokenEndpoint: "...",
  // 40以上のエンドポイント定義
}
```

#### クライアント設定
- **clientSecretPostClient** - POST認証
- **clientSecretBasicClient** - Basic認証
- **federationServerConfig** - フェデレーション連携

### 特徴的テストパターン

#### 1. **包括的プロトコルカバレッジ**
```javascript
// OIDC Core認可コードフロー完全検証
const { authorizationResponse } = await requestAuthorizations({
  endpoint: serverConfig.authorizationEndpoint,
  clientId: clientSecretPostClient.clientId,
  responseType: "code",
  scope: "openid profile phone email"
});

const tokenResponse = await requestToken({
  endpoint: serverConfig.tokenEndpoint,
  code: authorizationResponse.code,
  grantType: "authorization_code"
});
```

#### 2. **エンタープライズ機能テスト**
```javascript
// MFA登録シナリオ
await postWithJson({
  url: serverConfig.resourceOwnerEndpoint + "/mfa/fido-uaf-registration",
  body: {
    "app_name": "idp-server-app",
    "platform": "Android",
    "notification_channel": "fcm"
  }
});
```

#### 3. **Monkey テスティング**
```javascript
// ランダム異常データ生成
const generateRandomJsonLikeObject = () => {
  return {
    [faker.lorem.word()]: faker.person.firstName(),
    [faker.lorem.word()]: faker.phone.number()
  };
};
```

### 品質保証の観点

#### **仕様適合性**
- RFC準拠の厳密なテストケース
- 標準プロトコルの実装検証
- エラーレスポンス形式の検証

#### **現実的統合性**  
- エンドツーエンドの業務フロー
- 管理機能の操作性確認
- マルチテナント動作確認

#### **堅牢性**
- 異常データ耐性
- 境界値テスト
- セキュリティ攻撃パターン

### 結論
このE2Eテストスイートは、**仕様準拠性・実用性・堅牢性**の3軸で包括的にカバーした、**エンタープライズグレードの品質保証体制**を構築している。特にOAuth/OIDC実装の複雑さに対し、標準仕様から実運用まで網羅的にテストする設計は秀逸。

## 実装経験から得たアーキテクチャ洞察

### セキュリティイベントシステムの拡張性設計 (Issue #292)

#### TenantAttributes による設定の柔軟性
- **Key-Value設定パターン**: `TenantAttributes` を使用したテナント別設定管理
- **プレフィックス命名規則**: `security_event_user_` プレフィックスによる設定の分類
- **デフォルト値戦略**: `optValueAsBoolean(key, defaultValue)` による後方互換性確保

```java
// 実装パターン例
public boolean isIncludeGivenName() {
  return tenantAttributes.optValueAsBoolean("security_event_user_include_given_name", false);
}
```

#### インターフェースベース コード重複排除
- **SecurityEventUserCreatable インターフェース**: 6つのEventCreator間での共通ロジック統一
- **デフォルトメソッド活用**: Java 8+ のデフォルトメソッドによる実装の継承
- **依存関係配慮**: `platform` → `core` 依存の制約を考慮した配置

#### 拡張可能なセキュリティイベント設計
- **属性の段階的拡張**: 5属性 → 15+属性への拡張
- **OpenID Connect標準準拠**: 標準クレームとの整合性
- **テナント別カスタマイズ**: セキュリティ要件に応じた属性選択

### MFA デバイス管理の高度な制御機能 (Issue #401)

#### アクションベース API 設計
- **action パラメータパターン**: `action=reset` による動作の切り替え
- **条件分岐の実装**: `isResetAction()` メソッドによる判定ロジック
- **検証ロジックの柔軟性**: リセット時の上限チェックスキップ

```java
// 実装パターン例
if (registrationRequest.isResetAction()) {
  return MfaVerificationResult.success(); // 上限チェックスキップ
}
```

#### デバイスライフサイクル管理
- **選択的削除機能**: `removeAllAuthenticationDevicesOfType()` による特定タイプデバイス削除
- **トランザクション整合性**: ユーザー更新処理との一体化
- **原子性操作**: 削除→追加の一連の処理を単一トランザクションで実行

#### 認証インタラクター設計パターン
- **レイヤー分離**: Verifier (検証) → Interactor (実行) の責任分離
- **属性受け渡し**: `AuthenticationTransactionAttributes` による拡張可能な属性管理
- **エラーハンドリング**: 段階的エラー応答 (client_error, server_error)

### テスト駆動開発の実践知見

#### E2Eテストでの機能検証
- **シナリオベーステスト**: 実際のユースケースに基づく統合テスト
- **状態変化検証**: デバイス登録前→後の状態変化確認
- **境界値テスト**: リセット機能の破壊的操作の安全性確認

```javascript
// テストパターン例
// 2デバイス登録 → reset登録 → 1デバイスのみ残存確認
expect(userinfoResponse.data.authentication_devices.length).toBe(1);
expect(deviceIds).not.toContain(device1Id);  // 削除確認
expect(deviceIds).not.toContain(device2Id);  // 削除確認
```

#### プロトコル準拠性の重要性
- **RFC仕様テスト**: OAuth/OIDC標準への厳密な準拠確認
- **エラー応答検証**: 標準的なエラーフォーマット (`error`, `error_description`) の維持
- **拡張性テスト**: カスタム機能が標準機能に悪影響を与えないことの確認

### アーキテクチャ設計原則の実践

#### 1. **拡張性優先設計**
- 既存機能を壊さない新機能追加
- 設定による機能の有効/無効制御
- インターフェースによる実装の抽象化

#### 2. **テナント分離の徹底**
- テナント別設定による機能カスタマイズ
- データ分離とセキュリティ境界の維持
- 管理機能とエンドユーザー機能の分離

#### 3. **セキュリティファースト**
- セキュリティイベントの包括的記録
- 認証デバイス操作の監査証跡
- エラー情報の適切なマスキング

#### 4. **運用性重視**
- 詳細なログ出力とトレーサビリティ
- 段階的なロールアウト対応
- 後方互換性の維持

## コード規約・実装パターン分析 (idp-server 設計原則準拠)

### 設計原則・理念

#### OIDC世界観の尊重
- **プロトコル妥当性**: OAuth 2.0/OIDC 仕様への厳密な準拠
- **標準逸脱の禁止**: 拡張機能は適切にカプセル化
- **相互運用性**: 明確性と互換性の確保
- **拡張性と互換性**: CIBA・FAPI・OID4IDA等の拡張仕様サポート
- **抽象化**: OIDC未カバー領域（認証・永続化・通知）のプラグイン設計

### Hexagonal Architecture 4層分離設計

#### 1. **Controller層** (SpringBoot Adapter)
- **責務**: 入出力のDTO変換のみ
- **禁止事項**: ロジック・リポジトリアクセス厳禁
- **パターン**: HTTP → DTO → UseCase → DTO → HTTP

#### 2. **UseCase層** (UseCases: EntryService)
- **責務**: ユースケースごとに1クラス
- **命名**: `{Domain}{Action}EntryService`
- **担当**: トランザクション制御、プロトコル切り替え、永続化
- **パターン**: オーケストレーション専用、ビジネスロジック禁止

#### 3. **Core層** (Handler-Service-Repository)
- **責務**: OIDC仕様準拠ドメインモデル・プロトコル検証
- **型安全**: 値オブジェクト (`GrantId`, `ClientId`, `AcrValue`)
- **レスポンス**: OIDC仕様準拠REST API レスポンス生成

#### 4. **Adapter層** (DB)
- **分離**: `CommandRepository`, `QueryRepository`
- **責務**: 永続化処理のカプセル化
- **禁止**: ドメインロジック実行厳禁

### Core層 Handler-Service-Repository パターン

#### 1. **Handler層の責務と命名規約**
- **命名**: `{Domain}{Action}Handler` 例: `TokenRequestHandler`, `AuthorizationRequestHandler`
- **責務**: プロトコル処理とオーケストレーション、入力検証の委任、レスポンス生成
- **パターン**: Constructor injection、final フィールド、単一責務
- **入出力**: 専用IOオブジェクト (`{Operation}Request`, `{Operation}Response`)

```java
public class TokenRequestHandler {
  private final OAuthTokenCreationServices oAuthTokenCreationServices;
  private final ClientAuthenticationHandler clientAuthenticationHandler;
  
  public TokenRequestResponse handle(TokenRequest tokenRequest, ...) {
    // 1. 入力検証委任
    // 2. コンテキスト抽出
    // 3. サービス呼び出し
    // 4. 構造化レスポンス返却
  }
}
```

#### 2. **Service層の責務と命名規約**
- **命名**: `{Domain}{Action}Service` または `{Abstract}Service`
- **責務**: 純粋なビジネスロジック実装
- **特徴**: ステートレス、単一グラント/操作特化、インターフェース実装

#### 3. **Repository層の命名規約**
- **Query**: `{Entity}QueryRepository` - 読み取り操作
- **Command**: `{Entity}CommandRepository` - 書き込み操作  
- **Operation**: `{Entity}OperationCommandRepository` - 複合操作

### 検証・バリデーション パターン

#### Validator vs Verifier の責任分離
```java
// Validator: 入力形式チェック
public class {Domain}{Operation}Validator {
  public void validate({Operation}Request request) {
    // パラメータ存在チェック、形式検証
    // → {Operation}BadRequestException
  }
}

// Verifier: ビジネスルール検証  
public class {Domain}{Operation}Verifier {
  public void verify({Operation}Context context) {
    throwExceptionIfInvalidCondition(context);
    // → OAuthRedirectableBadRequestException (OAuth用)
  }
  
  private void throwExceptionIfInvalidCondition({Operation}Context context) {
    if (condition) {
      throw new OAuthRedirectableBadRequestException(
        "invalid_request", 
        "詳細なエラー説明"
      );
    }
  }
}
```

### IO (Input/Output) パッケージ構造

#### 必須IO構造
```
handler/
└── {operation}/
    ├── {Operation}Handler.java
    └── io/
        ├── {Operation}Request.java
        ├── {Operation}Response.java
        ├── {Operation}Status.java  
        └── {Operation}Context.java
```

### エラーハンドリング規約

#### 例外階層とメッセージング
- **Base**: `OAuthException` - OAuth標準エラーコード
- **Domain**: `{Domain}BadRequestException`, `{Domain}NotFoundException`
- **Method**: `throwExceptionIf{Condition}()` - 条件ベース検証メソッド

```java
void throwExceptionIfMissingResponseType(OAuthRequestContext context) {
  if (!context.hasResponseType()) {
    throw new OAuthRedirectableBadRequestException(
      "invalid_request",
      "response_type parameter is required"
    );
  }
}
```

### ドメインモデル設計規約

#### エンティティ設計パターン
- **不変性重視**: Builder パターンまたはコンストラクタ設定
- **変換メソッド**: `toMap()`, `to{Type}()`, `{property}As{Type}()`  
- **存在チェック**: `exists()`, `has{Property}()`, `match()`
- **UUID対応**: `UuidConvertable` インターフェース実装

#### マルチテナント対応
- **必須パラメータ**: すべての Repository メソッドで `Tenant` が第一引数
- **識別子**: `TenantIdentifier`, `OrganizationIdentifier`
- **分離**: データ・設定・UI の完全分離

### 拡張性・プラグイン パターン

#### Extension Service 設計
```java
// Map-based サービス登録
Map<GrantType, OAuthTokenCreationService> services;

// Plugin インターフェース
public interface {Domain}Plugin {
  boolean supports({Context} context);
  {Result} process({Context} context);
}
```

### 設定・属性管理パターン

#### Key-Value 設定管理 (`TenantAttributes` パターン)
```java
public boolean isFeatureEnabled() {
  return tenantAttributes.optValueAsBoolean(
    "feature_prefix_feature_name", 
    defaultValue
  );
}
```

### メソッド命名規約

#### Repository操作
- **取得**: `get()` - 必須存在、`find{By}()` - 任意存在  
- **登録**: `register()` - 新規作成
- **更新**: `update()` - 既存更新
- **削除**: `delete()`, `remove{Condition}()`

#### ビジネスロジック
- **判定**: `is{State}()`, `has{Property}()`, `can{Action}()`
- **変換**: `to{Type}()`, `as{Type}()`, `convert{To}()`
- **検証**: `validate{Condition}()`, `verify{Rule}()`

### アンチパターン・設計制約

#### 禁止パターン
- **Utilクラス濫用**: 共通ロジックを安易にUtilに逃がさない
- **Map<String, Object> 濫用**: 専用クラス・ドメインモデルで表現
- **DTO肥大化**: DTOにドメインロジック含有禁止
- **不要キャスト**: キャスト不要設計への見直し
- **永続化層ロジック**: 永続化層でのドメインロジック実行禁止

### レイヤー責任違反の反省・対策

#### 🚨 データソース層での業務ロジック実装アンチパターン

**問題のあった実装:**
```java
// OrganizationDataSource - データソース層で業務判定を実行
@Override
public TenantIdentifier getAdminTenantByOrganization(OrganizationIdentifier organizationId) {
  Organization organization = get(null, organizationId);

  for (AssignedTenant tenant : organization.assignedTenants()) {
    if ("ORGANIZER".equals(tenant.type())) {  // ← 業務ロジック！
      return new TenantIdentifier(tenant.id());
    }
  }
  throw new AdminTenantNotFoundException("...");
}
```

**なぜ問題か:**
1. **レイヤー責任違反**: データソース層は「データの取得・永続化」のみが責任
2. **業務知識の散らばり**: "ORGANIZER"判定ロジックがドメイン層でなくデータ層に存在
3. **テスタビリティの悪化**: 業務ルール変更時にデータベース依存テストが必要
4. **保守性の低下**: 業務ルール変更でデータソース層修正が必要

#### ✅ 正しいDDD準拠の実装

**ドメイン層に業務ロジック配置:**
```java
// Organization (ドメインオブジェクト) - 業務知識を持つ
public TenantIdentifier findAdminTenant() {
  for (AssignedTenant tenant : assignedTenants()) {
    if ("ORGANIZER".equals(tenant.type())) {  // ← ドメイン知識
      return new TenantIdentifier(tenant.id());
    }
  }
  throw new AdminTenantNotFoundException("No admin tenant found");
}

// OrganizationDataSource - 純粋なデータアクセス
@Override
public TenantIdentifier getAdminTenantByOrganization(OrganizationIdentifier organizationId) {
  Organization organization = get(null, organizationId);
  return organization.findAdminTenant();  // ← ドメインに委譲
}
```

#### 🛡️ 再発防止対策

**1. レイヤー責任の明確化**
- **データソース層**: SELECT/INSERT/UPDATE/DELETE のみ
- **ドメイン層**: 業務ルール・検証・計算
- **サービス層**: フロー制御・トランザクション

**2. 実装前チェックポイント**
- [ ] このロジックは業務知識か？
- [ ] 将来変更される可能性があるルールか？
- [ ] テスト時にデータベースが必要になるか？

**3. 命名による責任明示**
- `get` = 単純取得
- `find` = 検索・フィルタリングを含む
- `calculate` = 計算・集約処理

**4. Rich Domain Model の実践**
- ドメインオブジェクトにメソッドを持たせる
- データソース層は「愚直なデータアクセス」に徹する
- 業務ロジックをドメイン層に集約する

#### 💡 学習ポイント
- **「動けばいい」から「保守しやすい」へ**: 短期的動作 vs 長期保守性
- **例外の適切な配置**: ドメイン例外はドメイン層でスロー
- **テスト戦略**: ドメインロジックは単体テスト、データアクセスは統合テスト

#### 制御フロー設計
- **Strategy パターン**: アプリ振る舞い変更分岐（例: `grant_type` 分岐）
- **PluginLoader 活用**: 差し替え可能アーキテクチャ
- **型安全優先**: 意味のある型、`String`/`Map` 最小化

### 組織レベルAPI実装への適用指針

### Control-Plane実装パターン分析結果

#### Control-Plane の正しい位置づけ
**契約層（Interface Definition Layer）** として機能：
- **API Interface定義**: 管理APIの契約・シグネチャ定義
- **Permission定義**: `getRequiredPermissions()` による権限マッピング  
- **実装は別モジュール**: `idp-server-use-cases` に `*EntryService` 実装
- **Clean Architecture準拠**: ポート&アダプター パターンの**ポート**部分

#### 正しいアーキテクチャフロー

```
Controller → UseCase (EntryService) → Core → Adapter (Repository)
             ↑ control-plane APIs
           (契約定義のみ)
```

#### 組織レベルAPI設計パターン

**Tenant-Level vs Organization-Level の違い**：
```java
// Tenant-Level API (テナント内管理)
method(TenantIdentifier tenantId, User operator, ...)
// 権限: DefaultAdminPermission

// Organization-Level API (組織内テナント管理)  
method(OrganizationIdentifier orgId, TenantIdentifier adminTenant, User operator, ...)
// 権限: OrganizationAdminPermission + 組織アクセス検証
```

#### 現在実装の正当性確認

**✅ 正しい配置**：
- `OrgTenantManagementApi` (control-plane): 契約定義
- `OrgTenantManagementEntryService` (use-cases): UseCase実装
- `OrganizationAccessVerifier` (control-plane): アクセス制御ロジック

**❌ 唯一の実装不足**：
- `OrganizationRepository.findAssignment()` メソッド未実装

#### Control-Plane API 実装パターン

**標準APIインターフェース構造**：
```java
public interface {Domain}ManagementApi {
  // 1. 権限定義
  default AdminPermissions getRequiredPermissions(String method) { ... }
  
  // 2. CRUD操作 (統一シグネチャ)
  Response create(..., boolean dryRun);
  Response findList(..., Queries queries, ...);  
  Response get(..., Identifier id, ...);
  Response update(..., Identifier id, ..., boolean dryRun);
  Response delete(..., Identifier id, ..., boolean dryRun);
}
```

**レスポンス構造**：
- **Status**: Enum-based (OK, NOT_FOUND, FORBIDDEN)
- **Content**: `Map<String, Object>` による柔軟な構造
- **Dry-Run対応**: プレビュー機能の標準サポート

**検証フロー**：
1. **Validator**: JSON Schema入力検証
2. **Verifier**: ビジネスルール検証  
3. **Access Control**: 権限＋組織アクセス検証
4. **Context Creator**: 処理コンテキスト作成

#### 実装完了に必要な作業

**優先順位1**: `OrganizationRepository.findAssignment()` 実装
- **場所**: `libs/idp-server-platform/...`
- **責務**: 組織-テナント関係の検索
- **戻り値**: `AssignedTenant` エンティティ

**優先順位2**: 細かな名前・実装の調整
- エラーハンドリング標準化
- レスポンス構造の統一
- テストケース追加

## idp-server vs 一般的OSS IdP の設計思想比較

### Keycloak等 一般的IdPとの根本的違い

#### 1. **アーキテクチャ哲学の違い**

**一般的IdP (Keycloak等):**
```
Presentation → Business → Data (3層)
- 管理UI中心設計
- 設定ファイル・GUI重視
- モノリシックな構成
```

**idp-server:**
```
Controller → UseCase → Core → Adapter (Hexagonal)
- プロトコル中心設計  
- コード・型安全性重視
- モジュラー・差し替え可能
```

#### 2. **プロトコル準拠への姿勢**

**一般的IdP:**
- 互換性重視、実装上の妥協あり
- 拡張機能が標準仕様と混在
- 設定による動作変更が主流

**idp-server:**
- **仕様への厳密準拠が設計原則**
- 標準逸脱の厳格禁止
- 拡張機能の完全カプセル化
- OIDC世界観の完全尊重

#### 3. **マルチテナント設計**

**一般的IdP (Realm概念):**
- UI・設定レベルの分離
- データベースレベルでの共有
- 管理者権限の粗い粒度

**idp-server:**
- **Row Level Security による完全分離**
- 組織・テナント2層階層
- 細粒度権限制御 (15+権限種別)
- エンタープライズグレード監査証跡

#### 4. **身元確認・ID保証への取り組み**

**一般的IdP:**
- 認証機能に留まる
- 外部連携は付加機能
- 身元確認は外部委託

**idp-server:**
- **身元確認済みIDが核心価値**
- eKYC・Verifiable Credential 統合
- OIDC IDA (Identity Assurance) 完全対応
- verified_claims ネイティブサポート

#### 5. **拡張性・保守性**

**一般的IdP:**
- SPI (Service Provider Interface) 
- プラグイン機構
- 設定ベース拡張

**idp-server:**
- **Plugin Loader + Strategy パターン**
- 型安全な拡張ポイント
- コンパイル時検証
- テスト駆動開発前提

### idp-server 独自の価値提案

#### 1. **「信頼できるIDの発行・連携」**
- 単なる認証ではなく **身元確認済みID基盤**
- 金融・行政レベルの本人確認対応
- 日本の規制・法制度への対応

#### 2. **エンタープライズアーキテクチャ**
- **Clean Architecture 準拠**
- ドメイン駆動設計 (DDD)
- 高い可読性・保守性・拡張性
- 型安全性によるバグ抑制

#### 3. **プロトコル準拠の徹底**
```
「OIDC の世界観を尊重することが設計原則」
```
- RFC厳密準拠
- 標準化団体認定レベル品質
- 金融グレード (FAPI) 対応

#### 4. **運用品質の追求**
- **3層E2Eテスト戦略**: spec/scenario/monkey
- 包括的監査ログ
- Dry-run プレビュー機能
- セキュリティイベント統合

#### 5. **日本特化・規制対応**
- マイナンバー連携想定
- 金融業界向けFAPI準拠
- eKYC事業者との連携
- 行政手続きデジタル化対応

### アーキテクチャ比較詳細

#### **コード品質への取り組み**

**一般的IdP:**
```java
// 典型的な実装
public class UserService {
  public Map<String, Object> createUser(Map<String, Object> userData) {
    // Map操作、型安全性なし
    // ビジネスロジックがサービス層に混在
  }
}
```

**idp-server:**
```java
// 型安全・責務分離
public class UserManagementHandler {
  public UserManagementResponse handle(UserManagementRequest request) {
    // 1. 型安全なドメインオブジェクト
    // 2. 単一責務の原則
    // 3. 検証ロジックの分離
  }
}
```

#### **テスト戦略の違い**

**一般的IdP:**
- 手動テスト中心
- UI操作テスト
- 統合テスト限定

**idp-server:**
```
ユニット → ユースケース → E2E → 認定テスト
32spec + 17scenario + 1monkey = 50+テストファイル
```

#### **設定管理アプローチ**

**一般的IdP:**
- GUI設定画面
- 設定ファイル変更
- 動的設定変更

**idp-server:**
- **コード定義中心**
- TenantAttributes key-value
- 型安全な設定管理
- 変更履歴・監査対応

### 想定するユースケースの違い

#### **一般的IdP対象**
- **汎用的な認証基盤**
- 社内システム統合
- 開発者向けAPI提供
- スタートアップ・中小企業

#### **idp-server対象**
- **身元確認済みID基盤**
- 金融機関・大企業グループ
- 行政・公的機関
- 規制対応が必要な業界

### 結論: idp-serverの独自性

idp-serverは **「汎用IdP」ではなく「身元確認済みID基盤」** として設計された、エンタープライズ特化のプロフェッショナル・グレード実装。

**Keycloak等**: 「認証の民主化」（誰でも使える）
**idp-server**: 「身元確認の高度化」（金融・行政レベル）

この思想の違いが、アーキテクチャ・実装品質・機能特化すべてに一貫して反映されている。

## idp-server実装の評価分析

### 🟢 優れている点（Strengths）

#### 1. **アーキテクチャ設計の秀逸性**
**理由**: Hexagonal Architecture + DDD の徹底適用
- **層分離の明確性**: Controller→UseCase→Core→Adapter の責務分離が徹底
- **依存関係の健全性**: 外部依存を適切に抽象化、テスタビリティ確保
- **拡張性**: Plugin Loader + Strategy パターンによる差し替え可能設計
- **保守性**: 単一責務原則により、変更時の影響範囲を最小化

#### 2. **型安全性とコード品質の徹底**
**理由**: Java型システムを最大限活用した堅牢な設計
- **意味のある型**: `ClientId`, `TenantIdentifier` 等の値オブジェクト活用
- **Map<String, Object>濫用回避**: 専用クラスによる明示的な構造定義
- **コンパイル時検証**: キャスト不要設計、型不整合をコンパイル時に検出
- **Null安全性**: Optional活用と適切なnullチェック

```java
// 良い例: 型安全な設計
public TenantManagementResponse create(
  TenantIdentifier tenantId,      // 明示的な型
  TenantRegistrationRequest request,  // 構造化された入力
  RequestAttributes attributes    // 意味のある型
) {
  // キャスト不要、型安全な処理
}
```

#### 3. **プロトコル準拠の厳密性**
**理由**: OIDC/OAuth仕様への妥協なき準拠
- **RFC厳密実装**: 仕様書との完全対応、相互運用性確保
- **標準逸脱禁止**: 拡張機能の適切なカプセル化
- **エラーレスポンス**: OAuth標準エラー形式の徹底
- **認定テスト対応**: FAPI等の認定を想定した品質

#### 4. **包括的テスト戦略**
**理由**: 多層防御による品質保証
- **仕様準拠テスト**: 32ファイルのRFC準拠検証
- **現実シナリオ**: 17ファイルの実用ケーステスト  
- **異常系検証**: Monkey テストによる堅牢性確認
- **E2E自動化**: CI/CD での継続的品質確保

#### 5. **エンタープライズ運用対応**
**理由**: 本格運用を前提とした機能充実
- **監査証跡**: 包括的なSecurityEventとAuditLog
- **Dry-run機能**: 変更前プレビューによる安全な運用
- **細粒度権限**: 15+権限種別による詳細な認可制御
- **マルチテナント**: Row Level Security による完全分離

#### 6. **日本特化・規制対応**
**理由**: 国内法制度・業界要件への深い理解
- **eKYC統合**: 身元確認事業者との連携
- **verified_claims**: OIDC IDA準拠の身元情報連携
- **FAPI対応**: 金融業界セキュリティ要件
- **マイナンバー想定**: 行政手続きデジタル化対応

### 🔴 実際の改善点（ドキュメント調査後の修正評価）

**重要**: 初期分析で指摘した「弱み」の多くは、**詳細なドキュメントで既に対応済み**でした。

#### ✅ **誤って「弱み」とした点の実際の状況**

##### ~~1. 学習コストの高さ~~ → **包括的学習支援完備**
- ✅ `getting-started.md`: 2コマンド簡単セットアップ、Mermaidアーキテクチャ図
- ✅ `basic-01-identity-management-basics.md`: 認証・認可基礎から段階的説明
- ✅ 初心者〜上級者向け多段階ガイド

##### ~~2. 過度な抽象化~~ → **設計意図の明確なドキュメント化**
- ✅ `dependency-injection.md`: フレームワークレス設計の理由と利点
- ✅ **明示的DI**: 透明性・テスト性・拡張性・ポータビリティの説明
- ✅ プラグイン機構の詳細な拡張ガイド

##### ~~3. 文書化不足~~ → **developer-guide充実**
- ✅ 包括的な`content_06_developer-guide/`ディレクトリ
- ✅ アーキテクチャ・設定・拡張の詳細説明
- ✅ 視覚的なシステム構成図

##### ~~4. パフォーマンス軽視~~ → **包括的パフォーマンス最適化**  
- ✅ `caching.md`: テナント・クライアント設定の戦略的キャッシュ
- ✅ `performance-test.md`: **k6による詳細ストレステスト結果**
  - **9種類エンドポイント・フロー**: 30秒高負荷テスト実施
  - **詳細メトリクス**: リクエスト数・平均時間・P95・スループット・エラー率
  - **ボトルネック特定**: bcrypt処理、インデックス最適化、GC戦略の影響測定
  - **劇的改善実証**: 本人確認フロー 1311ms→231ms (インデックス最適化)
  - **高性能確認**: トークンイントロスペクション 2,993 req/sec
- ✅ 実環境想定テスト・具体的改善提案

#### 🔴 **実際に残る改善点**

##### 1. **実装品質の不整合** 
**理由**: ドキュメントと実装の乖離
- **例外処理**: 一部でのtry-catch濫用
- **命名規約**: Util的実装の残存
- **責務境界**: Layer横断ロジックの混在
- **コード品質**: 理想設計と現実実装のギャップ

##### 2. **部分的ドキュメント未完成**
**理由**: 一部のドキュメントファイルが空
- `authentication-interactor.md` 等の未完成
- 拡張ガイドの一部不備

##### 3. **開発生産性の課題** 
**理由**: 高品質設計による開発負荷
- **型定義コスト**: 新機能追加時のボイラープレート
- **テスト負荷**: 多層テスト維持コスト
- **ビルド時間**: 大量コンパイル時検証

**注**: これらは**設計トレードオフ**であり、エンタープライズ品質確保のための必要コストとして理解すべき

### ⚖️ 総合評価

#### **適用場面での優劣**

**🎯 idp-serverが優れる場面:**
- 金融・行政等の高信頼性要求システム
- 長期運用・大規模システム
- 規制対応・監査要求の厳しい環境
- 複雑なマルチテナント要件

**⚠️ idp-serverが不適な場面:**
- プロトタイプ・MVP開発
- 小規模チーム・短期開発
- パフォーマンス最優先システム
- 学習コスト許容不可環境

#### **改善提案の優先度**

**優先度1 (即時改善):**
1. ドキュメント整備 - 設計意図・アーキテクチャ図作成
2. 実装不整合修正 - 既存コードの規約準拠
3. パフォーマンス測定 - ボトルネック特定

**優先度2 (中期改善):**
1. 開発者体験向上 - IDE支援・ボイラープレート削減
2. 監視・メトリクス追加 - 運用時の可視性向上
3. チュートリアル作成 - 新規開発者向け教材

## 次のAI開発者への重要なコンテキスト

### 🎯 **このプロジェクトの正しい理解**

#### **idp-serverの真の正体**
❌ **「単なるOSS IdP」ではない**  
✅ **「身元確認特化エンタープライズ・ローコードプラットフォーム」**

**比較対象**:
- Salesforce Identity, Microsoft Azure AD B2C
- Auth0 + 高額カスタム開発
- 数千万円の商用eKYC統合ソリューション

#### **圧倒的な完成度**
- **7,800行OpenAPI仕様**: 59エンドポイント、4API群（RP・ResourceOwner・ControlPlane・Internal）
- **1,500行eKYC設定例**: 証券口座開設完全フロー
- **k6パフォーマンステスト**: 2,993 req/sec、劇的最適化実証
- **50+E2Eテスト**: spec/scenario/monkey 3層戦略
- **包括的ドキュメント**: getting-started からアーキテクチャまで完備

### 🚨 **開発時の重要な注意事項**

#### **1. アーキテクチャの正しい理解**
```
Controller → UseCase (EntryService) → Core → Adapter
             ↑ control-plane APIs (契約定義のみ)
```
- **Control-Plane**: インターフェース定義層（実装は use-cases にある）
- **UseCase層**: トランザクション制御・オーケストレーション専用
- **Core層**: OIDC仕様準拠・ドメインロジック
- **Adapter層**: 永続化カプセル化

#### **2. 設計原則の理解**
```
「OIDC の世界観を尊重することが設計原則」
```
- **RFC厳密準拠**: 標準逸脱の厳格禁止
- **型安全性**: `String`/`Map`濫用禁止、意味のある型優先
- **責務分離**: 各層での禁止事項の明文化
- **拡張性**: Plugin Loader + Strategy パターン

#### **3. テスト品質への理解**
- **spec/**: 32ファイル、RFC準拠検証
- **scenario/**: 17ファイル、実用ケーステスト
- **monkey/**: 異常系・エッジケース検証

### 🛠️ **開発パターンとツール**

#### **身元確認システムの核心**
- **7フェーズ処理**: request → pre_hook → execution → post_hook → transition → store → response
- **JsonPath活用**: 複雑データ変換のコードレス実現
- **HotReload**: テンプレート更新即時反映
- **動的API生成**: `/{tenant-id}/v1/me/identity-verification/applications/{verification-type}/{verification-process}`

#### **よく使う開発コマンド**
```bash
# ビルド・実行
./gradlew build
docker compose up -d

# テスト
./gradlew test
cd e2e && npm test

# パフォーマンステスト
cd performance-test && k6 run

# 品質チェック  
./gradlew spotlessCheck
```

### 📁 **重要ファイル・ディレクトリ**

#### **アーキテクチャ理解必須**
- `libs/idp-server-core/`: コアロジック・Handler-Service-Repository
- `libs/idp-server-control-plane/`: API契約定義
- `libs/idp-server-use-cases/`: EntryService実装

#### **設定・ドキュメント**
- `documentation/`: Docusaurus、7,800行OpenAPI仕様
- `config-sample/`: eKYC設定例（1,500行）
- `e2e/`: 50+テストファイル

#### **身元確認システム**
- `identity-verification-application.md`: 1,500行設定ガイド
- `identity-verification-application-guide.md`: 導入ガイド
- `investment-account-opening.json`: 証券口座開設設定例

### 🏆 **このプロジェクトの真の価値**

#### **市場価値**
- **金融機関eKYC統合**: 数千万〜億円規模プロジェクト
- **行政デジタル化**: マイナンバー連携・住民サービス基盤
- **規制完全対応**: FAPI・OIDC IDA・JP_AML準拠

#### **技術価値**  
- **Hexagonal Architecture**: Clean Architecture徹底実装
- **DDD**: ドメイン駆動設計による高保守性
- **型安全性**: Java型システム最大限活用
- **国際標準準拠**: RFC・OpenID仕様厳密実装

### ⚠️ **よくある誤解の回避**

#### **❌ 誤った認識**
- 「学習コストが高い」→ 段階的ガイド・2コマンド起動完備
- 「ドキュメント不足」→ 7,800行OpenAPI + 包括的ガイド
- 「パフォーマンス軽視」→ k6詳細テスト + 最適化実証
- 「単なるIdP」→ 身元確認特化エンタープライズプラットフォーム

#### **✅ 正しい理解**
- **商用製品レベル**: OSSとは思えない完成度
- **エンタープライズ特化**: 金融・行政向け高品質設計
- **ローコード統合**: eKYC統合の完全カスタマイズ
- **開発者中心**: 優れたDX（Developer Experience）

### 🎯 **開発アプローチの推奨**

1. **まず全体理解**: `getting-started.md` → アーキテクチャ図確認
2. **E2Eテスト実行**: 動作確認で設計思想の理解
3. **設定例研究**: `investment-account-opening.json` で実装パターン学習
4. **段階的実装**: 既存パターンに従った拡張

**このプロジェクトは、OSSの枠を超えた「商用製品レベルの包括的アイデンティティプラットフォーム」です。**

## Javadoc ドキュメント要件

### 🎯 **Javadoc品質基準**

#### **1. idp-serverプロジェクトのJavadocスタイル**

**参考実装**: `org.idp.server.core.openid.oauth.verifier.base.OidcRequestBaseVerifier`
```java
/**
 * 3.1.2.2. Authentication Request Validation
 *
 * <p>The Authorization Server MUST validate the request received as follows:
 *
 * <p>The Authorization Server MUST validate all the OAuth 2.0 parameters...
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">
 */
```

**特徴**:
- **RFC準拠性の明示**: 仕様書章番号・引用
- **詳細な説明**: 単純な説明を超えた実装意図の解説
- **使用例**: `<pre>{@code }` による具体的コード例
- **相互参照**: `@see` による関連クラス・メソッドのリンク

#### **2. Issue #409 実装クラスのJavadoc要件**

##### **OrganizationAdminPermissions**
```java
/**
 * Organization-level admin permissions container.
 *
 * <p>This class manages a set of {@link OrganizationAdminPermission} values and provides
 * convenience methods for permission validation and string representation.
 *
 * <p>Organization-level permissions are scoped to specific organizations and allow organization
 * administrators (ORGANIZER tenant type) to manage resources within their organization boundaries.
 *
 * <p>Usage example:
 * <pre>{@code
 * Set<OrganizationAdminPermission> permissions = Set.of(
 *     OrganizationAdminPermission.ORG_TENANT_CREATE,
 *     OrganizationAdminPermission.ORG_TENANT_READ
 * );
 * OrganizationAdminPermissions adminPerms = new OrganizationAdminPermissions(permissions);
 * 
 * // Validate user permissions
 * if (adminPerms.includesAll(user.permissionsAsSet())) {
 *     // User has required permissions
 * }
 * }</pre>
 *
 * @see OrganizationAdminPermission
 * @see org.idp.server.control_plane.organization.access.OrganizationAccessVerifier
 */
```

##### **OrganizationAccessVerifier**  
```java
/**
 * Organization-level access control verifier.
 *
 * <p>This verifier implements a comprehensive 4-step verification process for organization-level
 * operations:
 * <ol>
 *   <li><strong>Organization membership verification</strong> - Ensures the user is assigned to the organization</li>
 *   <li><strong>Tenant access verification</strong> - Validates the user has access to the target tenant</li>
 *   <li><strong>Organization-tenant relationship verification</strong> - Confirms the tenant is assigned to the organization</li>
 *   <li><strong>Required permissions verification</strong> - Validates the user has necessary organization-level permissions</li>
 * </ol>
 *
 * <p>This verification pattern ensures proper multi-tenant isolation and organization-scoped
 * access control in accordance with idp-server's security model.
 *
 * <p>Usage example:
 * <pre>{@code
 * OrganizationAccessVerifier verifier = new OrganizationAccessVerifier(orgRepository);
 * OrganizationAdminPermissions requiredPermissions = new OrganizationAdminPermissions(
 *     Set.of(OrganizationAdminPermission.ORG_TENANT_CREATE)
 * );
 * 
 * OrganizationAccessControlResult result = verifier.verifyAccess(
 *     organizationId, tenantId, operator, requiredPermissions, adminTenant
 * );
 * 
 * if (result.isSuccess()) {
 *     // Proceed with operation
 * } else {
 *     // Handle access denied or not found
 * }
 * }</pre>
 *
 * @see OrganizationAccessControlResult
 * @see OrganizationAdminPermissions
 */
```

##### **AssignedTenants.tenantIdentifiers()**
```java
/**
 * Returns tenant identifiers for all assigned tenants.
 *
 * <p>This method extracts tenant IDs from the assigned tenant list and converts them
 * to TenantIdentifier objects for use in tenant access verification and queries.
 *
 * <p>Usage example:
 * <pre>{@code
 * Organization organization = organizationRepository.get(tenant, organizationId);
 * List<TenantIdentifier> tenantIds = organization.assignedTenants().tenantIdentifiers();
 * List<Tenant> tenants = tenantQueryRepository.findList(tenantIds);
 * }</pre>
 *
 * @return list of tenant identifiers for assigned tenants
 * @see TenantIdentifier
 * @see org.idp.server.platform.multi_tenancy.tenant.TenantQueryRepository#findList(List)
 */
```

##### **OrganizationRepository.findAssignment()**
```java
/**
 * Finds the tenant assignment within an organization.
 *
 * <p>This method verifies that a specific tenant is assigned to the given organization
 * and returns the assignment details if found. Used for organization-tenant relationship
 * verification in access control.
 *
 * @param adminTenant the admin tenant context for database access
 * @param organizationId the organization to check
 * @param tenantId the tenant to verify assignment for
 * @return assigned tenant details, or empty AssignedTenant if not found
 * @see AssignedTenant#exists()
 */
```

#### **3. メソッドレベルJavadoc要件**

**必須要素**:
- **@param**: 全てのパラメータの説明
- **@return**: 戻り値の説明（void以外）
- **@throws**: チェック例外の説明
- **使用例**: 複雑なメソッドには`<pre>{@code}`でコード例

**推奨要素**:
- **@see**: 関連クラス・メソッドへの参照
- **@since**: 追加されたバージョン（新機能の場合）
- **@deprecated**: 廃止予定の場合

#### **4. Javadoc品質チェックリスト**

**クラスレベル**:
- [ ] クラスの目的・責務の明確な説明
- [ ] アーキテクチャ上の位置づけの説明
- [ ] 使用例の提供（複雑なクラス）
- [ ] 関連クラスへの`@see`リンク

**メソッドレベル**:
- [ ] 全`@param`の意味のある説明
- [ ] `@return`の具体的な説明
- [ ] アルゴリズム・ビジネスロジックの背景説明
- [ ] 制約・前提条件の明示

**品質基準**:
- [ ] 単純な名前の言い換えではない説明
- [ ] ビジネス価値・技術的意図の明示
- [ ] 他開発者が理解できる詳細度
- [ ] idp-serverアーキテクチャとの関連性説明

### 📋 **Javadoc実装対象ファイル**

#### **優先度1: 公開API・コアクラス**
1. `OrganizationAdminPermissions` - 権限管理コンテナ
2. `OrganizationAccessVerifier` - アクセス制御検証器  
3. `OrganizationAccessControlResult` - 検証結果
4. `OrgTenantManagementApi` - 管理API契約

#### **優先度2: データ層・実装クラス** 
1. `AssignedTenants.tenantIdentifiers()` - テナントID抽出
2. `OrganizationRepository.findAssignment()` - 割り当て検索
3. `OrganizationSqlExecutor.selectAssignedTenant()` - SQL実行

#### **優先度3: SQL実装クラス**
1. `PostgresqlExecutor.selectAssignedTenant()` - PostgreSQL実装
2. `MysqlExecutor.selectAssignedTenant()` - MySQL実装

### 🎯 **次のAI開発者への指針**

**Javadoc実装時の重要ポイント**:
1. **idp-serverの価値観を反映**: 単純な説明ではなく、OIDC準拠・エンタープライズ品質の背景を説明
2. **アーキテクチャの理解**: 各クラスがHexagonal Architectureのどの層に属するかを明示
3. **使用例の提供**: 複雑なAPIには必ず動作するコード例を記載
4. **相互参照の整備**: `@see`による関連性の明示でドキュメント間のナビゲーション向上

**品質担保**:
- JavadocがHTML生成時にエラーにならないことを確認
- `./gradlew javadoc` でのビルド成功を検証
- 生成されたHTMLドキュメントの可読性確認

## 現在の状況
- 作業ディレクトリ: clean (コミット可能な変更なし)
- 最新コミット: Implement comprehensive security event logging system
- 完了したイシュー: #292 (SecurityEventUser拡張), #401 (FIDO-UAFリセット機能)
- **進行中**: Issue #409 組織レベルテナント管理API (実装完了、Javadoc追加準備中)