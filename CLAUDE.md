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

## 現在の状況
- 作業ディレクトリ: clean (コミット可能な変更なし)
- 最新コミット: Add FIDO-UAF reset functionality for device replacement (Issue #401)
- 完了したイシュー: #292 (SecurityEventUser拡張), #401 (FIDO-UAFリセット機能)
- 最新コミット: Merge pull request #384 (MFA登録時のエラーハンドリング改善)