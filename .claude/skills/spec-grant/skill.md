---
name: spec-grant
description: Grant管理（Grant Management）機能の開発・修正を行う際に使用。AuthorizationGrant、ConsentClaims、同意管理、Grant管理API実装時に役立つ。
---

# Grant管理（Grant Management）開発ガイド

## ドキュメント

- `documentation/docs/content_03_concepts/03-authentication-authorization/concept-05-grant-management.md` - Grant管理概念
- `documentation/openapi/swagger-cp-grant-management-ja.yaml` - Grant管理API仕様（OpenAPI）

## 機能概要

Grant管理は、認可コンテキストを管理する層。
- **AuthorizationGrant**: ユーザー、認証、クライアント、スコープの複合構造
- **AuthorizationGranted**: 永続化されたGrant（created_at/updated_at含む）
- **ConsentClaims**: 同意情報の管理
- **Scopeベース同意**: クライアント別にscopeを記録
- **Grant管理API**: 管理者向けのGrant一覧取得・詳細取得・取り消しAPI

## モジュール構成

```
libs/
├── idp-server-core/                         # Grantコア
│   └── .../grant_management/
│       ├── AuthorizationGranted.java        # 永続化Grant（created_at/updated_at含む）
│       ├── AuthorizationGrantedIdentifier.java
│       ├── AuthorizationGrantedQueries.java # クエリパラメータ（limit上限1000）
│       ├── AuthorizationGrantedRepository.java
│       ├── AuthorizationGrantedQueryRepository.java
│       ├── grant/
│       │   ├── AuthorizationGrant.java      # 認可Grant
│       │   ├── GrantIdTokenClaims.java
│       │   └── GrantUserinfoClaims.java
│       └── consent/
│           └── ConsentClaims.java           # 同意情報
│
├── idp-server-core-adapter/                 # DB実装
│   └── .../grant_management/
│       ├── AuthorizationGrantedDataSource.java
│       ├── AuthorizationGrantedQueryDataSource.java
│       ├── ModelConverter.java              # DB→モデル変換
│       ├── PostgresqlExecutor.java
│       └── MysqlExecutor.java
│
├── idp-server-control-plane/                # Grant管理API定義
│   └── .../management/oidc/grant/
│       ├── OrgGrantManagementApi.java       # APIインターフェース
│       ├── GrantManagementContext.java
│       ├── GrantManagementContextBuilder.java
│       ├── handler/
│       │   ├── OrgGrantManagementHandler.java
│       │   ├── GrantManagementService.java
│       │   ├── GrantFindListService.java
│       │   ├── GrantFindService.java
│       │   └── GrantRevocationService.java  # Grant取り消し+トークン削除
│       └── io/
│           ├── GrantManagementRequest.java
│           ├── GrantManagementResponse.java
│           ├── GrantManagementResult.java
│           └── GrantManagementStatus.java
│
├── idp-server-use-cases/                    # EntryService
│   └── .../control_plane/organization_manager/
│       └── OrgGrantManagementEntryService.java
│
└── idp-server-springboot-adapter/           # REST API
    └── .../control_plane/restapi/organization/
        └── OrganizationGrantManagementV1Api.java
```

## Grant管理API（Organization Level）

### エンドポイント

| メソッド | パス | 説明 | 権限 |
|---------|------|------|------|
| GET | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/grants` | Grant一覧取得 | `GRANT_READ` |
| GET | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/grants/{grant-id}` | Grant詳細取得 | `GRANT_READ` |
| DELETE | `/v1/management/organizations/{org-id}/tenants/{tenant-id}/grants/{grant-id}` | Grant取り消し | `GRANT_DELETE` |

### クエリパラメータ（一覧取得）

| パラメータ | 説明 |
|-----------|------|
| `user_id` | ユーザーIDでフィルタ |
| `client_id` | クライアントIDでフィルタ |
| `from` | 作成日時の開始範囲（ISO 8601） |
| `to` | 作成日時の終了範囲（ISO 8601） |
| `limit` | 最大件数（デフォルト20、上限1000） |
| `offset` | スキップ件数 |

### dry_runモード

DELETE時に `?dry_run=true` を指定すると、実際の削除を行わずシミュレーション結果を返す。

### Grant取り消し時の動作

Grant削除時に、同じuser+clientの**全トークン**（アクセストークン、リフレッシュトークン）も削除される（Auth0スタイル）。

## AuthorizationGranted構造

```java
public class AuthorizationGranted {
    AuthorizationGrantedIdentifier identifier;
    AuthorizationGrant authorizationGrant;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public Map<String, Object> toMap() {
        // id, user, client, scopes, created_at, updated_at を返す
    }
}
```

## AuthorizationGrant構造

```java
public class AuthorizationGrant {
    TenantIdentifier tenantIdentifier;
    User user;
    Authentication authentication;
    RequestedClientId requestedClientId;
    ClientAttributes clientAttributes;
    GrantType grantType;
    Scopes scopes;
    GrantIdTokenClaims idTokenClaims;
    GrantUserinfoClaims userinfoClaims;
    CustomProperties customProperties;
    AuthorizationDetails authorizationDetails;
    ConsentClaims consentClaims;
}
```

## ConsentClaims（規約・ポリシーの同意追跡）

### 概要

`ConsentClaims` は、クライアントの `tos_uri`（利用規約）と `policy_uri`（プライバシーポリシー）への同意を追跡する。
規約が変更された場合、既存の同意は無効化され、ユーザーに再同意を要求する。

### データ構造

```java
// ConsentClaims: カテゴリ別の同意リスト
Map<String, List<ConsentClaim>> claims;
// カテゴリ: "terms"（tos_uri）, "privacy"（policy_uri）

// ConsentClaim: 個別の同意レコード
public class ConsentClaim {
    String name;              // "tos_uri" or "policy_uri"
    String value;             // URI値
    LocalDateTime consentedAt; // 同意日時
}
```

### DB上の保存形式（authorization_granted.consent_claims）

```json
{
  "terms": [
    {"name": "tos_uri", "value": "https://example.com/terms/v1", "consented_at": "..."},
    {"name": "tos_uri", "value": "https://example.com/terms/v2", "consented_at": "..."}
  ],
  "privacy": [
    {"name": "policy_uri", "value": "https://example.com/privacy/v1", "consented_at": "..."}
  ]
}
```

規約が更新されるたびに新エントリが追加される（上書きではなく履歴追加）。

### 同意比較フロー

```
認可リクエスト時:
1. OAuthRequestContext.createConsentClaims()
   → クライアントの現在の tos_uri / policy_uri から ConsentClaims を生成

2. authorizationGranted.isConsentedClaims(consentClaims)
   → AuthorizationGrant.isConsentedClaims()
   → ConsentClaims.isAllConsented()
   → 各 ConsentClaim の name + value が一致するかチェック

3. 不一致の場合:
   → OAuthRedirectableBadRequestException("interaction_required",
      "authorization request contains unauthorized consent")
```

### 比較ロジックの詳細

- `ConsentClaim.equals()`: `name` + `value` で比較（`consentedAt` は無視）
- `ConsentClaims.isAllConsented()`: リクエスト側の全 ConsentClaim が保存済みリストに `contains` されるかチェック
- `ConsentClaims.merge()`: 同じ claim は最古の `consentedAt` を保持、新しい claim は履歴に追加

### 関連ファイル

| ファイル | 役割 |
|---------|------|
| `ConsentClaim.java` | 個別の同意レコード（name, value, consentedAt） |
| `ConsentClaims.java` | 同意コレクション（merge, isAllConsented） |
| `OAuthRequestContext.java` | ConsentClaims生成 + 同意比較（canAutomaticallyAuthorize） |
| `OAuthAuthorizeContext.java` | AuthorizationGrant生成時にConsentClaimsを含める |
| `ClientConfiguration.java` | クライアントの tosUri / policyUri を保持 |

## Grantが作成されるタイミング

- Authorization Code Flowでの同意時
- Password Grantでのトークン発行時（スコープはマージされる）
- その他のGrant Typeでのトークン発行時

## E2Eテスト

```
e2e/src/tests/
├── usecase/standard/
│   └── standard-05-grant-revocation.test.js   # Grant取り消しE2E
└── scenario/control_plane/organization/
    └── organization_grant_management.test.js  # Grant管理APIテスト
```

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-core:compileJava
./gradlew :libs:idp-server-control-plane:compileJava

# E2Eテスト
cd e2e && npm test -- src/tests/usecase/standard/standard-05-grant-revocation.test.js
cd e2e && npm test -- src/tests/scenario/control_plane/organization/organization_grant_management.test.js
```

## トラブルシューティング

### Grantが見つからない
- AuthorizationGrantedが正しく生成されているか確認
- ユーザー、認証、クライアント情報が揃っているか確認
- Password Grantの場合、`ResourceOwnerPasswordCredentialsGrantService`でGrant作成ロジックを確認

### Grant取り消し後もトークンが有効
- `GrantRevocationService`で`deleteByUserAndClient`が呼ばれているか確認
- トランザクションが正しくコミットされているか確認

### created_at/updated_atがレスポンスに含まれない
- `ModelConverter.parseLocalDateTime()`が正しくパースしているか確認
- DBからのtimestamp形式を確認（`LocalDateTimeParser`で対応）
