---
name: platform
description: プラットフォーム基盤（idp-server-platform）の開発・修正を行う際に使用。マルチテナント、JOSE、トランザクション管理、Plugin System、DI、HTTP クライアント実装時に役立つ。
---

# プラットフォーム基盤（Platform）開発ガイド

## ドキュメント

- `documentation/docs/content_06_developer-guide/01-getting-started/03-design-principles.md` - 設計原則

## 機能概要

`idp-server-platform` は全モジュールが依存する基盤レイヤー。マルチテナント、暗号化、トランザクション管理、Plugin等の横断的関心事を提供する。

```
idp-server-core → idp-server-platform
idp-server-use-cases → idp-server-platform
idp-server-*-adapter → idp-server-platform
```

---

## パッケージ構成

**探索起点**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/`

| カテゴリ | パッケージ | 責務 |
|---------|----------|------|
| マルチテナント | `multi_tenancy/tenant/` | テナント管理・識別・属性 |
| マルチテナント | `multi_tenancy/organization/` | 組織管理・メンバー管理 |
| セキュリティ | `crypto/`, `hash/`, `jose/`, `x509/`, `random/` | 暗号化・署名・証明書 |
| インフラ | `datasource/` | データソース・トランザクション |
| インフラ | `http/` | HTTP クライアント |
| インフラ | `proxy/` | Dynamic Proxy（トランザクション自動管理） |
| 基盤機能 | `plugin/` | ServiceLoader ベースのプラグイン |
| 基盤機能 | `dependency/` | 軽量DIコンテナ |
| ユーティリティ | `json/` | JsonConverter（Jackson ラッパー） |

---

## マルチテナント

### 主要クラス

- `TenantIdentifier` - テナント識別子（値オブジェクト）
- `TenantAttributes` - テナント固有設定
- `Tenant` - テナント集約ルート
- `OrganizationRepository` - 組織リポジトリ（**Tenant第一引数の例外**）

### TenantAttributes パターン

```java
// 設定取得はデフォルト値付きの opt メソッドを使用
attributes.optValueAsBoolean("oauth.pkce.enabled", false);
attributes.optValueAsString("token.custom_claim_key", "");
attributes.optValueAsStringList("oauth.allowed_scopes", List.of("openid", "profile"));
```

**注意**: `optValueAsInt()` は存在しない。整数値が必要な場合は `optValueAsString()` で取得して変換する。

---

## トランザクション管理（@Transaction + Dynamic Proxy）

### アーキテクチャ

```
EntryService (Interface)
    ↓
TenantAwareEntryServiceProxy (Dynamic Proxy)
    ↓ @Transaction アノテーション検出
    ↓ TransactionManager 自動呼び出し
    ↓ PostgreSQL RLS 自動設定
    ↓
EntryService (実装) → Repository → SqlExecutor
```

### 開発者がすべきこと

1. EntryService に `@Transaction` アノテーション付与
2. メソッド引数に `TenantIdentifier` を含める
3. EntryService を `TenantAwareEntryServiceProxy.createProxy()` でラップ
4. Repository で `SqlExecutor` を使用

### 開発者が意識不要なこと

- TransactionManager の直接呼び出し（Proxy が自動実行）
- Connection 管理（ThreadLocal で自動管理）
- commit/rollback（Proxy が自動実行）
- RLS 設定（`set_config('app.tenant_id', ?, true)` を Proxy が自動実行）

---

## JOSE (JWT/JWS/JWE/JWK)

**探索起点**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/jose/`

Nimbus JOSE + JWT のラッパー。主要クラス:

- `JoseHandler` - 統合ハンドラー（Plain JWT / JWS / JWE 自動判定）
- `JsonWebSignature` - JWS パース・クレーム取得
- `JsonWebSignatureVerifier` - 署名検証
- `JsonWebToken` - Plain JWT パース

---

## HTTP クライアント

**探索起点**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/http/`

- `HttpRequestExecutor` - `java.net.http.HttpClient` ベースの高機能実行エンジン
  - OAuth 2.0 自動認証
  - リトライ（エクスポネンシャルバックオフ）
  - Idempotency キー管理

---

## Plugin System

**探索起点**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/plugin/`

Java ServiceLoader ベース。**静的メソッド API**（インスタンス化不可）。

```java
// 内部モジュール（META-INF/services）
List<T> plugins = PluginLoader.loadFromInternalModule(T.class);

// 外部JAR（plugins/ ディレクトリ）
List<T> plugins = PluginLoader.loadFromExternalModule(T.class);
```

---

## DI コンテナ

**探索起点**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/dependency/`

- `ApplicationComponentContainer` - 軽量DIコンテナ（`register()` / `resolve()`）
- Spring Boot DI とは別レイヤー（Plugin, Interactor, Protocol の依存関係管理用）

---

## JsonConverter

```java
JsonConverter.defaultInstance();     // キャメルケース
JsonConverter.snakeCaseInstance();   // スネークケース（HTTP API 向け）
```

- `write(object)` - Java → JSON 文字列
- `read(json, Class)` - JSON → Java オブジェクト
- `read(map, Class)` - Map → Java オブジェクト
