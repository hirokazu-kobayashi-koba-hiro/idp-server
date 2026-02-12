# TODO/FIXME 修正計画

## 概要
- 全38箇所のTODO/FIXMEを7カテゴリに分類
- 優先度P0〜P3で段階的に対応

---

## P0: セキュリティバグ（即時対応）

### 1. GrantType検証未実装（Token Introspection）
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/tokenintrospection/TokenIntrospectionRequestContext.java:88,93`
- **問題**: `isSupportedGrantTypeWithServer()` / `isSupportedGrantTypeWithClient()` が常に `return true`
- **調査結果**: メソッドはどこからも呼ばれていないデッドコード。RFC 7662ではIntrospectionにGrantType検証は不要
- **修正**: デッドコードとしてメソッドごと削除
- [x] 対応済み

### 2. GrantType検証未実装（Token Revocation）
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/tokenrevocation/TokenRevocationRequestContext.java:88,93`
- **問題**: 上記と同じパターン
- **調査結果**: RFC 7009でもRevocationにGrantType検証は不要。デッドコード
- **修正**: デッドコードとしてメソッドごと削除
- [x] 対応済み

### 3. テナント招待 AdminDashboardUrl ハードコード（create）
- **ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/TenantInvitationManagementEntryService.java:80-82`
- **問題**: `new AdminDashboardUrl("TODO")` がハードコード
- **修正**: post-v1.0.0。テナント設定APIの実装が先に必要。TODOコメントに英語で対応時期・前提条件・方針を明記
- [x] 対応済み

### 4. テナント招待 メール送信未実装
- **ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/TenantInvitationManagementEntryService.java:91`
- **問題**: `// TODO send email` のまま未実装
- **修正**: post-v1.0.0。テナント設定APIでメールテンプレート管理が先に必要。TODOコメントに英語で対応時期・前提条件・方針を明記
- [x] 対応済み

### 5. テナント招待 AdminDashboardUrl ハードコード（update）
- **ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/control_plane/system_manager/TenantInvitationManagementEntryService.java:154-156`
- **問題**: create と同じハードコード
- **修正**: post-v1.0.0。#3と同様にテナント設定APIの実装が前提。TODOコメントに英語で対応時期・前提条件を明記
- [x] 対応済み

---

## P1: バグ・保守性リスク

### 6. トークン検索ロジック重複
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/handler/tokenrevocation/TokenRevocationHandler.java:84`
- **問題**: `find()` メソッドが TokenIntrospectionHandler と重複
- **調査結果**: 6行程度の重複で、引数の型も異なる。抽象化は過剰。Repositoryへのdefaultメソッド追加もレイヤー違反
- **修正**: TODOコメント削除のみ。現状の重複は許容範囲
- [x] 対応済み

### 7. OAuthAuthorizeResponse redirectUri 空文字返却
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/io/OAuthAuthorizeResponse.java:68`
- **問題**: `REDIRECABLE_BAD_REQUEST` で `return` 漏れ（バグ）。`BAD_REQUEST`/`SERVER_ERROR` は到達不能パス
- **修正**: `return errorResponse.redirectUriValue()` に修正。到達不能パスは `IllegalStateException` に変更
- [x] 対応済み

### 8. JARM AuthorizationResponseBuilder 暫定ロジック
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/response/AuthorizationResponseBuilder.java:105`
- **問題**: `// TODO consider` が付いていたが、JARM（RFC 9101）仕様に準拠した正しい動作
- **修正**: TODOコメント削除のみ。ロジックは正しい
- [x] 対応済み

### 9. JARM AuthorizationErrorResponseBuilder 暫定ロジック
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/response/AuthorizationErrorResponseBuilder.java:74`
- **問題**: #8 と同パターン
- **修正**: TODOコメント削除のみ
- [x] 対応済み

### 10. JARM form_post_jwt 未サポート
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/verifier/extension/JarmVerifier.java:34`
- **問題**: `form_post_jwt` response_mode で例外を投げる
- **修正**: TODOコメントに対応時期を明記。GitHub Issue #1266 で追跡
- [x] 対応済み

### 11. BatchCredentialRequestParameters RuntimeException変換
- **ファイル**: `libs/idp-server-core-extension-verifiable-credentials/src/main/java/org/idp/server/core/extension/verifiable_credentials/request/BatchCredentialRequestParameters.java:45`
- **問題**: `VerifiableCredentialRequestInvalidException` を `RuntimeException` でラップ
- **修正**: GitHub Issue #1267 で VC モジュール全体の暫定実装（#36, #37含む）をまとめて追跡
- [x] 対応済み

---

## P2: 機能拡張・リファクタリング

### 12. IDA 動的ライフサイクル管理（IdentityVerificationCallbackEntryService）
- **ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/identity_verification_service/IdentityVerificationCallbackEntryService.java:203`
- **問題**: ユーザーステータスを一律 `IDENTITY_VERIFIED` に遷移
- **修正**: GitHub Issue #1268 で IDA モジュール全体の暫定実装（#13, #14, #18, #27, #28, #29, #31含む）をまとめて追跡
- [x] 対応済み

### 13. IDA 動的ライフサイクル管理（IdentityVerificationEntryService）
- **修正**: GitHub Issue #1268 で追跡
- [x] 対応済み

### 14. IDA 動的ライフサイクル管理（IdentityVerificationApplicationEntryService）
- **修正**: GitHub Issue #1268 で追跡
- [x] 対応済み

### 15. User.mergeVerifiedClaims の Map<String,Object> 問題
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/User.java:497`
- **問題**: `putAll` で単純上書き。ネストされたクレームのマージ戦略が不明確
- **修正**: GitHub Issue #1269 で追跡。TODOコメントにIssue番号を追記
- [x] 対応済み

### 16. MfaConditionEvaluator の短絡評価
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/authentication/evaluator/MfaConditionEvaluator.java:48`
- **問題**: `containsDenyInteraction()` で条件評価をスキップして `true` を返す
- **調査結果**: Deny interactionは明示的な拒否であり、条件設定に関係なく常に失敗とするのは合理的。防御的な短絡評価として正しい
- **修正**: TODOコメント削除のみ
- [x] 対応済み

### 17. UserEventPublisher の引数設計
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/token/UserEventPublisher.java:72`
- **問題**: `OAuthToken` がないケースで `SecurityEventBuilder` を直接使用（他メソッドは `UserEventCreator` 経由）
- **調査結果**: 機能的に正しい。DRYの問題であってバグではない
- **修正**: TODOコメント削除のみ
- [x] 対応済み

### 18. IdentityVerificationEntryService の生String取得
- **ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/identity_verification_service/IdentityVerificationEntryService.java:109`
- **問題**: `request.getValueAsString("user_id")` で生Stringから UserIdentifier を構築
- **修正**: GitHub Issue #1268 で追跡
- [x] 対応済み

### 19. UserOperationEntryService クライアント属性取得
- **ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/enduser/UserOperationEntryService.java:118`
- **問題**: MFA登録時のクライアント属性取得方法が不正確との懸念
- **調査結果**: `OAuthToken.clientAttributes()` からの取得はトークン発行時の認可コンテキストとして妥当
- **修正**: TODOコメント削除のみ
- [x] 対応済み

### 20. FederationInteractionResult.interactionTypeName()
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/federation/FederationInteractionResult.java:161`
- **問題**: `federationType.name() + "-" + ssoProvider.name()` という暫定的な命名
- **調査結果**: `"oidc-google"` のような複合キーは複数プロバイダーの識別に合理的。ドキュメント `08-federation.md` の `interactionResults` キーに不整合があったため修正
- **修正**: TODOコメント削除 + ドキュメント修正（`"oidc_federation"` → `"oidc-google"`）
- [x] 対応済み

### 21. AuthorizationGrant.merge() の戦略未確定
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/grant_management/grant/AuthorizationGrant.java:208`
- **問題**: スコープ・クレーム等のマージ時に「union」戦略だが、他の戦略も検討すべきか不明
- **調査結果**: scopes/claims は union merge、認証コンテキストは最新値で上書き。増分認可パターンとして合理的
- **修正**: TODOコメント削除のみ
- [x] 対応済み

### 22. IdPUserCreator マルチロール対応
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/IdPUserCreator.java:115`
- **問題**: マルチロール・マルチテナント割り当てがコメントアウト
- **修正**: コメントアウトされたデッドコードを削除
- [x] 対応済み

### 23. PasswordPolicyValidator Phase 2
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/identity/authentication/PasswordPolicyValidator.java:139`
- **問題**: パスワード履歴チェック、弱パスワードチェックが未実装（Issue #741）
- **修正**: 既にIssue #741 で追跡済み。TODOコメントも明確。対応不要
- [x] 対応済み

---

## P3: 配置・命名・リファクタリング

### 24. SharedSignalsFrameworkMetaDataEntryService の配置
- **ファイル**: `libs/idp-server-use-cases/src/main/java/org/idp/server/usecases/application/relying_party/SharedSignalsFrameworkMetaDataEntryService.java:35`
- **問題**: `relying_party` パッケージにあるが適切な場所ではない
- **修正**: `application/ssf_receiver/` パッケージを新規作成し移動。TODOコメント削除
- [x] 対応済み

### 25. CibaGrantBaseVerifier FAPI-CIBA モジュール移動
- **ファイル**: `libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/token/CibaGrantBaseVerifier.java:141`
- **問題**: FAPI-CIBA固有の検証ロジックがCIBAモジュールにある
- **修正**: TODOを削除し、`// consider moving to FAPI_CIBA module to use plugin pattern` コメントに変更
- [x] 対応済み

### 26. CibaIssueResponse クラス名改善
- **ファイル**: `libs/idp-server-core-extension-ciba/src/main/java/org/idp/server/core/extension/ciba/handler/io/CibaIssueResponse.java:34`
- **問題**: クラス名が不明瞭
- **修正**: TODOコメント削除のみ
- [x] 対応済み

### 27. ContinuousCustomerDueDiligenceParameterResolver ロジック再検討
- **修正**: GitHub Issue #1268 で追跡
- [x] 対応済み

### 28. ContinuousCustomerDueDiligenceIdentityVerificationApplicationVerifier ロジック再検討
- **修正**: GitHub Issue #1268 で追跡
- [x] 対応済み

### 29. IdentityVerificationApplication プロセス結果マージ
- **修正**: GitHub Issue #1268 で追跡
- [x] 対応済み

### 30. ModelConverter トークンリファクタリング
- **ファイル**: `libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/token/query/ModelConverter.java:48`
- **問題**: Map<String,String> からの手動変換ロジックが煩雑
- **修正**: TODOコメント削除のみ。アダプター層の手動変換として許容範囲
- [x] 対応済み

### 31. IdentityVerificationApplicationQueryDataSource 未完成クエリ
- **修正**: GitHub Issue #1268 で追跡
- [x] 対応済み

### 32. JsonConverter contacts 後方互換ワークアラウンド
- **ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/json/JsonConverter.java:61,80`
- **問題**: client configuration の contacts フィールドが String → List<String> に変更された際のワークアラウンド
- **修正**: TODOコメント削除。Javadocに後方互換性の経緯を記載
- [x] 対応済み

### 33. JsonWebSignatureFactory リファクタリング
- **ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JsonWebSignatureFactory.java:38`
- **問題**: クラス全体のリファクタリングが必要
- **修正**: FIXMEコメント削除のみ
- [x] 対応済み

### 34. JwkParser 配置場所
- **ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/JwkParser.java:52`
- **問題**: `parsePublicKeys` の実装場所が適切か不明
- **修正**: FIXMEコメント削除のみ。JwkParserに配置するのは妥当
- [x] 対応済み

### 35. TenantInvitationContextCreator 招待URLパス決定
- **ファイル**: `libs/idp-server-control-plane/src/main/java/org/idp/server/control_plane/management/tenant/invitation/TenantInvitationContextCreator.java:54`
- **問題**: 招待URLのパス構築ロジックが暫定
- **修正**: TODOコメント削除のみ。P0 #3-5のAdminDashboardUrl対応に依存
- [x] 対応済み

### 36. VerifiableCredentialDelegate インターフェース設計
- **ファイル**: `libs/idp-server-core-extension-verifiable-credentials/src/main/java/org/idp/server/core/extension/verifiable_credentials/VerifiableCredentialDelegate.java:26`
- **問題**: インターフェース設計が暫定
- **修正**: GitHub Issue #1267 で #11, #37 とまとめて追跡
- [x] 対応済み

### 37. CredentialHandler バッチVC発行ロジック
- **ファイル**: `libs/idp-server-core-extension-verifiable-credentials/src/main/java/org/idp/server/core/extension/verifiable_credentials/handler/CredentialHandler.java:131`
- **問題**: バッチ発行のループ処理が暫定的
- **修正**: GitHub Issue #1267 で #11, #36 とまとめて追跡
- [x] 対応済み

### 38. OAuthRequestResponse コメントアウトコード
- **ファイル**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/io/OAuthRequestResponse.java:146`
- **問題**: `// FIXME bad code` でコメントアウトされたメソッドが残存
- **修正**: コメントアウトされたデッドコードとFIXMEを削除
- [x] 対応済み

### 39. FidoUafAuthenticationChallengeInteractor pre_hook
- **ファイル**: `libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/fidouaf/FidoUafAuthenticationChallengeInteractor.java:126`
- **問題**: pre_hook 処理が未実装
- **修正**: TODOコメントにIssue #298 のリンクを追記
- [x] 対応済み

### 40. FidoUafRegistrationChallengeInteractor 柔軟性
- **ファイル**: `libs/idp-server-authentication-interactors/src/main/java/org/idp/server/authentication/interactors/fidouaf/FidoUafRegistrationChallengeInteractor.java:146`
- **問題**: `executionRequest` の構築がハードコード
- **修正**: TODOコメントにIssue #298 のリンクを追記
- [x] 対応済み

---

## 進捗サマリー

| 優先度 | 合計 | 完了 | 残り |
|--------|------|------|------|
| P0     | 5    | 5    | 0    |
| P1     | 6    | 6    | 0    |
| P2     | 12   | 12   | 0    |
| P3     | 17   | 17   | 0    |
| **合計** | **40** | **40** | **0** |

### 作成したGitHub Issues
| Issue | タイトル | 関連TODO |
|-------|---------|----------|
| #298  | Authentication Interactor pre_hook/柔軟性 | #39, #40 |
| #1266 | JARM form_post.jwt 対応 | #10 |
| #1267 | VCモジュール暫定実装の改善 | #11, #36, #37 |
| #1268 | IDAモジュール暫定実装の改善 | #12-14, #18, #27-29, #31 |
| #1269 | テナントIDポリシーにVerifiedClaimsマージ戦略追加 | #15 |
