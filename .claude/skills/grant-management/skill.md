---
name: grant-management
description: Grant管理（Grant Management）機能の開発・修正を行う際に使用。AuthorizationGrant、ConsentClaims、同意管理実装時に役立つ。
---

# Grant管理（Grant Management）開発ガイド

## ドキュメント

- `documentation/docs/content_03_concepts/03-authentication-authorization/concept-05-grant-management.md` - Grant管理概念

## 機能概要

Grant管理は、認可コンテキストを管理する層。
- **AuthorizationGrant**: ユーザー、認証、クライアント、スコープの複合構造
- **ConsentClaims**: 同意情報の管理
- **Scopeベース同意**: クライアント別にscopeを記録

## モジュール構成

```
libs/
├── idp-server-core/                         # Grantコア
│   └── .../grant_management/
│       ├── grant/
│       │   ├── AuthorizationGrant.java     # 認可Grant
│       │   ├── GrantIdTokenClaims.java
│       │   └── GrantUserinfoClaims.java
│       └── consent/
│           └── ConsentClaims.java          # 同意情報
│
└── idp-server-core-adapter/                # DB実装
    └── .../grant_management/
        └── AuthorizationGrantRepositoryImpl.java
```

## AuthorizationGrant構造

`idp-server-core/grant_management/grant/AuthorizationGrant.java` 内の実際の構造:

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

    public String subjectValue() {
        return user.sub();
    }

    public boolean hasAuthentication() {
        return authentication != null && authentication.exists();
    }
}
```

**注意**: AuthorizationGrantは、単なる同意記録ではなく、認可リクエスト全体のコンテキストを表現する複合構造です。

## ConsentClaims

`idp-server-core/grant_management/consent/` 内:

```java
public class ConsentClaims {
    // 同意情報を管理
    // User, Client, Scopeの組み合わせで同意を記録
}
```

## E2Eテスト

```
e2e/src/tests/
└── scenario/application/
    └── (Grant関連テストは各認可フローテスト内で検証)
```

## コマンド

```bash
# ビルド
./gradlew :libs:idp-server-core:compileJava

# テスト
cd e2e && npm test -- scenario/application/
```

## トラブルシューティング

### Grantが見つからない
- AuthorizationGrantが正しく生成されているか確認
- ユーザー、認証、クライアント情報が揃っているか確認
