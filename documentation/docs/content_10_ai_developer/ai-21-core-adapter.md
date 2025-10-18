# idp-server-core-adapter

## モジュール概要

**情報源**: `libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/`
**確認日**: 2025-10-12

### 責務

Core層のRepositoryインターフェースの実装。データソースへのアクセスをカプセル化。

- **PostgreSQL/MySQL対応**: Dialect切り替え
- **Redis Cache**: セッション・一時データ
- **暗号化**: 機密データの暗号化・復号化
- **ハッシュ化**: トークンのHMAC-SHA256ハッシュ

## パッケージ構成

```
libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/
└── datasource/
    ├── token/              # トークンRepository実装
    ├── identity/           # ユーザーRepository実装
    ├── oidc/               # クライアント設定Repository実装
    ├── grant_management/   # グラント管理Repository実装
    ├── authentication/     # 認証Repository実装
    ├── federation/         # フェデレーションRepository実装
    ├── config/             # 設定Repository実装
    ├── ciba/               # CIBARepository実装
    ├── security/           # セキュリティイベントRepository実装
    ├── verifiable_credentials/ # VC Repository実装
    ├── multi_tenancy/      # テナント・組織Repository実装
    ├── audit/              # 監査ログRepository実装
    └── cache/              # Redisキャッシュ実装
```

**情報源**: `find libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource -type d -maxdepth 2`

## DataSource - SqlExecutor パターン

### DataSource - Repository実装

**情報源**: [OAuthTokenCommandDataSource.java:25](../../../libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/token/command/OAuthTokenCommandDataSource.java#L25)

```java
/**
 * Repository実装 - DataSourceパターン
 * 確認方法: 実ファイルの25-47行目
 */
public class OAuthTokenCommandDataSource implements OAuthTokenCommandRepository {

  OAuthTokenSqlExecutor executor;  // ✅ SQLExecutorに委譲
  AesCipher aesCipher;             // ✅ 暗号化
  HmacHasher hmacHasher;           // ✅ ハッシュ化

  public OAuthTokenCommandDataSource(
      OAuthTokenSqlExecutor executor, AesCipher aesCipher, HmacHasher hmacHasher) {
    this.executor = executor;
    this.aesCipher = aesCipher;
    this.hmacHasher = hmacHasher;
  }

  @Override
  public void register(Tenant tenant, OAuthToken oAuthToken) {
    // ✅ SQLExecutorに委譲（暗号化・ハッシュ化を渡す）
    executor.insert(oAuthToken, aesCipher, hmacHasher);
  }

  @Override
  public void delete(Tenant tenant, OAuthToken oAuthToken) {
    executor.delete(oAuthToken, aesCipher, hmacHasher);
  }
}
```

### SqlExecutor - SQL実行

```java
/**
 * SQL実行 - Dialect別に実装
 */
public interface OAuthTokenSqlExecutor {
  void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher);
  void delete(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher);
}

// PostgreSQL実装
public class PostgresqlExecutor implements OAuthTokenSqlExecutor {
  @Override
  public void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    // PostgreSQL固有のSQL実行
  }
}

// MySQL実装
public class MysqlExecutor implements OAuthTokenSqlExecutor {
  @Override
  public void insert(OAuthToken oAuthToken, AesCipher aesCipher, HmacHasher hmacHasher) {
    // MySQL固有のSQL実行
  }
}
```

## 重要原則

### データソース層でのビジネスロジック禁止

```java
// ❌ 悪い例: Repository実装でビジネス判定
public class ClientConfigurationDataSource implements ClientConfigurationQueryRepository {
  @Override
  public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    ClientConfiguration config = executor.selectById(clientId);

    // ❌ ビジネスロジックがデータソース層に漏れている
    if ("ORGANIZER".equals(tenant.type())) {
      config.setSpecialPermissions(true);
    }

    return config;
  }
}

// ✅ 良い例: データアクセスのみ
public class ClientConfigurationDataSource implements ClientConfigurationQueryRepository {
  @Override
  public ClientConfiguration get(Tenant tenant, RequestedClientId clientId) {
    // ✅ データ取得のみ
    return executor.selectById(clientId);
  }
}
```

**原則**: データソース層 = SELECT/INSERT/UPDATE/DELETE、ドメイン層 = 業務ルール

**情報源**: CLAUDE.md「⚠️ レイヤー責任違反の重要教訓」

## 暗号化・ハッシュ化

### AesCipher - 暗号化

```java
// 暗号化
String encrypted = aesCipher.encrypt(plaintext);

// 復号化
String plaintext = aesCipher.decrypt(encrypted);
```

### HmacHasher - ハッシュ化

```java
// ハッシュ生成
String hash = hmacHasher.hash(data);

// ハッシュ検証
boolean valid = hmacHasher.verify(data, expectedHash);
```

**用途**:
- **AesCipher**: 機密データ（クライアントシークレット、リフレッシュトークン等）
- **HmacHasher**: トークンの検索キー（一方向ハッシュ）

## Redis Cache実装

**情報源**: [JedisCacheStore.java:28](../../../libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/cache/JedisCacheStore.java#L28)

### JedisCacheStore - Redis キャッシュ

```java
/**
 * Redis Cache実装（Jedis使用）
 * 確認方法: 実ファイルの28-80行目
 */
public class JedisCacheStore implements CacheStore {
  JedisPool jedisPool;
  JsonConverter jsonConverter;
  int timeToLiveSecond;

  // ✅ キャッシュ保存
  @Override
  public <T> void put(String key, T value) {
    try (Jedis resource = jedisPool.getResource()) {
      String json = jsonConverter.write(value);
      resource.setex(key, timeToLiveSecond, json);  // TTL付き保存
    }
  }

  // ✅ キャッシュ取得
  @Override
  public <T> Optional<T> find(String key, Class<T> type) {
    try (Jedis resource = jedisPool.getResource()) {
      String json = resource.get(key);
      if (json == null) {
        return Optional.empty();
      }
      return Optional.of(jsonConverter.read(json, type));
    }
  }

  // ✅ キャッシュ削除
  @Override
  public void delete(String key) {
    try (Jedis resource = jedisPool.getResource()) {
      resource.del(key);
    }
  }
}
```

**キャッシュ対象**:
- **OAuthSession**: SSOセッション
- **AuthorizationRequest**: 認可リクエスト（一時保存）
- **AuthenticationTransaction**: 認証トランザクション
- **JWKS**: 公開鍵セット（パフォーマンス向上）

**設定例**:
```properties
cache.host=localhost
cache.port=6379
cache.max.total=100
cache.max.idle=50
cache.min.idle=10
cache.ttl.seconds=3600
```

## 関連ドキュメント

- [Adapter層統合ドキュメント](./ai-20-adapters.md) - core-adapterを含む全アダプターモジュール
- [idp-server-database](./ai-22-database.md) - データベーススキーマ・マイグレーション
- [idp-server-springboot-adapter](./ai-23-springboot-adapter.md) - Spring Boot統合

---

**情報源**:
- `libs/idp-server-core-adapter/src/main/java/`配下の実装コード
- CLAUDE.md「⚠️ レイヤー責任違反の重要教訓」「4層アーキテクチャ」
- [OAuthTokenCommandDataSource.java](../../../libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource/token/command/OAuthTokenCommandDataSource.java)

**最終更新**: 2025-10-12
**確認方法**: `find libs/idp-server-core-adapter/src/main/java/org/idp/server/core/adapters/datasource -type d -maxdepth 2`
