# Session

## このドキュメントの目的

**Spring Sessionを使ったセッション管理**の実装を理解することが目標です。

### 所要時間
⏱️ **約20分**

---

## Spring Sessionとは

**HTTPセッションをRedis等の外部ストアに保存する仕組み**

### なぜSpring Sessionが必要か

| 項目 | デフォルト（インメモリ） | Spring Session（Redis） |
|------|---------------------|----------------------|
| **永続化** | ❌ サーバー再起動で消失 | ✅ Redis保存で永続化 |
| **スケーラビリティ** | ❌ スティッキーセッション必須 | ✅ 複数サーバーで共有可能 |
| **HA構成** | ❌ サーバー障害で全セッション消失 | ✅ Redis冗長化で可用性向上 |

**idp-serverの要件**: マルチサーバー構成・高可用性 → Spring Session必須

---

## idp-serverでのセッション利用箇所

### 1. OAuth認可フロー（Authorization Code Flow）

```
1. [ユーザー] /oauth/authorize にアクセス
   ↓
2. [idp-server] AuthorizationRequest保存
   ↓ HttpSessionに保存
3. [ユーザー] ログイン画面で認証
   ↓
4. [idp-server] HttpSessionから AuthorizationRequest取得
   ↓ セッション継続
5. [idp-server] Authorization Code発行
```

**セッションがないと**: 認証完了後にAuthorization Requestの情報が取得できない

---

## 実装構造

### アーキテクチャ

```
Core層（ドメインロジック）
  ↓ インターフェース
OAuthSessionRepository（抽象化）
  ↓ 実装
OAuthSessionDataSource（Spring Session統合）
  ↓ 利用
HttpSession（Spring Session管理）
  ↓ 保存先
Redis（外部ストア）
```

---

## 実装解説

### OAuthSessionRepository（Core層インターフェース）

**定義場所**: `libs/idp-server-core/src/main/java/org/idp/server/core/openid/oauth/repository/`

```java
public interface OAuthSessionRepository {

  /**
   * セッション登録
   */
  void register(OAuthSession oAuthSession);

  /**
   * セッション検索
   */
  OAuthSession find(OAuthSessionKey oAuthSessionKey);

  /**
   * セッション更新
   */
  void update(OAuthSession oAuthSession);

  /**
   * セッション削除
   */
  void delete(OAuthSessionKey oAuthSessionKey);
}
```

**ポイント**: Core層はSpring依存なし（移植性）

---

### OAuthSessionDataSource（Spring Session統合）

**実装**: [OAuthSessionDataSource.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/session/datasource/OAuthSessionDataSource.java)

```java
@Repository
public class OAuthSessionDataSource implements OAuthSessionRepository {

  HttpSession httpSession;  // ✅ Spring管理のHttpSession
  LoggerWrapper log = LoggerWrapper.getLogger(OAuthSessionDataSource.class);

  public OAuthSessionDataSource(HttpSession httpSession) {
    this.httpSession = httpSession;  // ✅ Spring DIで注入
  }

  @Override
  public void register(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    log.debug("registerSession: {}", sessionKey);
    log.debug("register sessionId: {}", httpSession.getId());

    // ✅ HttpSession属性として保存
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public OAuthSession find(OAuthSessionKey oAuthSessionKey) {
    String sessionKey = oAuthSessionKey.key();

    // ✅ HttpSession属性から取得
    OAuthSession oAuthSession = (OAuthSession) httpSession.getAttribute(sessionKey);

    log.debug("find sessionId: {}", httpSession.getId());
    log.debug("findSession: {}", sessionKey);

    if (oAuthSession == null) {
      log.debug("session not found");
      return new OAuthSession();  // 空のセッション
    }

    return oAuthSession;
  }

  @Override
  public void update(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    log.debug("update sessionId: {}", httpSession.getId());
    log.debug("updateSession: {}", sessionKey);

    // ✅ HttpSession属性を上書き
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public void delete(OAuthSessionKey oAuthSessionKey) {
    log.debug("delete sessionId: {}", httpSession.getId());
    log.debug("deleteSession: {}", oAuthSessionKey.key());

    // ✅ セッション無効化
    httpSession.invalidate();
  }
}
```

**ポイント**:
- ✅ HttpSessionをDI注入（Springが管理）
- ✅ `setAttribute()`/`getAttribute()`でシンプルに保存・取得
- ✅ ログ出力で追跡可能

---

### OAuthSessionDelegate（Core層へのコールバック）

**実装**: [OAuthSessionService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/session/OAuthSessionService.java)

```java
@Service
public class OAuthSessionService implements OAuthSessionDelegate {

  OAuthSessionRepository httpSessionRepository;

  public OAuthSessionService(OAuthSessionRepository httpSessionRepository) {
    this.httpSessionRepository = httpSessionRepository;
  }

  @Override
  public void registerSession(OAuthSession oAuthSession) {
    httpSessionRepository.register(oAuthSession);
  }

  @Override
  public OAuthSession findOrInitialize(OAuthSessionKey oAuthSessionKey) {
    OAuthSession oAuthSession = httpSessionRepository.find(oAuthSessionKey);

    if (oAuthSession.exists()) {
      return oAuthSession;
    }

    // セッションが存在しない場合は初期化
    return OAuthSession.init(oAuthSessionKey);
  }

  // update(), delete() メソッドも同様...
}
```

**Delegateパターンの理由**: Core層はRepositoryインターフェースに依存、具体実装（Spring Session）は知らない

---

## Spring Session設定

### Gradle依存関係

**ファイル**: `libs/idp-server-springboot-adapter/build.gradle`

```groovy
dependencies {
    // Spring Session Core
    implementation 'org.springframework.session:spring-session-core'

    // Redisを使う場合（推奨）
    // implementation 'org.springframework.session:spring-session-data-redis'
}
```

---

### セッションストア選択

#### Option 1: インメモリ（開発環境）

**設定不要** - デフォルトでインメモリ

**メリット**: 設定不要、簡単
**デメリット**: サーバー再起動で消失、スケールしない

---

#### Option 2: Redis（本番環境推奨）

**application.properties**:
```properties
# Spring Session Redis設定
spring.session.store-type=redis
spring.session.redis.namespace=spring:session
spring.session.timeout=1800  # 30分

# Redis接続
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.password=
```

**Gradle依存関係追加**:
```groovy
implementation 'org.springframework.session:spring-session-data-redis'
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

**Java Config**:
```java
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {

  @Bean
  public SafeRedisSessionRepository sessionRepository(
      RedisOperations<String, Object> sessionRedisOperations) {
    // ✅ デフォルトではなくSafeRedisSessionRepository使用
    return new SafeRedisSessionRepository(sessionRedisOperations);
  }
}
```

**メリット**: スケーラブル、永続化、HA対応
**デメリット**: Redisインフラ必要

---

## SafeRedisSessionRepository（耐障害性強化）

### Redis障害時のGraceful Degradation

**実装**: [SafeRedisSessionRepository.java](../../../../app/src/main/java/org/idp/server/SafeRedisSessionRepository.java)

idp-serverでは、**Redis障害時でもサービスを継続**するための独自実装を提供：

```java
/**
 * Redis障害時でも例外をスローせず、ログ出力してサービス継続
 *
 * 設計思想:
 * - 可用性優先: Redis障害でもIdPサービスは継続
 * - Graceful Degradation: セッション失われても認証フロー継続可能
 * - 監視可能: エラーログで障害検知
 */
public class SafeRedisSessionRepository extends RedisIndexedSessionRepository {

  @Override
  public void save(RedisSession session) {
    try {
      super.save(session);
    } catch (Exception e) {
      // ✅ 例外をスローせず、エラーログのみ
      logger.error("Failed to save session (Redis disconnected): {}", e.getMessage());
      // サービスは継続（セッションは失われるが、認証フローは継続可能）
    }
  }

  @Override
  public RedisSession findById(String id) {
    try {
      return super.findById(id);
    } catch (Exception e) {
      logger.error("Failed to load session: {}", e.getMessage());
      // ✅ nullを返す（セッションなしとして扱う）
      return null;
    }
  }

  @Override
  public void deleteById(String id) {
    try {
      super.deleteById(id);
    } catch (Exception e) {
      logger.error("Failed to delete session: {}", e.getMessage());
      // ✅ 削除失敗を無視（セッションは自動期限切れで削除される）
    }
  }
}
```

**動作**:
- ✅ **Redis正常時**: 通常通りセッション保存・取得
- ✅ **Redis障害時**: エラーログ出力 + サービス継続（セッションなしで動作）

**用途**:
- ✅ HA構成のIdP（Redis障害時もダウンしない）
- ✅ ハイブリッドセッション管理（Redis + Stateless Fallback）

**Fallback動作例**:
```
1. ユーザーがログイン実行
   ↓
2. Redis障害でセッション保存失敗
   ↓ SafeRedisSessionRepository がエラーログのみ
3. 認証は成功（セッションなしでも動作）
   ↓
4. Authorization Code発行
   ↓ セッション取得失敗 → Stateless処理にFallback
5. トークン発行成功
```

**重要**: Redis障害時もIdPサービスは継続（セッション機能のみ劣化）

---

## セッション管理のベストプラクティス

### 1. セッションタイムアウト設定

```properties
# 30分で自動削除（デフォルト）
spring.session.timeout=1800

# Authorization Request有効期限と合わせる
```

### 2. セッションID生成

Spring Sessionが自動生成（UUID）

```
JSESSIONID=550e8400-e29b-41d4-a716-446655440000
```

### 3. セッションCookie設定

```properties
# Cookie設定
server.servlet.session.cookie.http-only=true  # XSS対策
server.servlet.session.cookie.secure=true     # HTTPS必須
server.servlet.session.cookie.same-site=lax   # CSRF対策
```

---

## トラブルシューティング

### エラー1: `Session not found`

**原因**: セッションタイムアウト or サーバー再起動（インメモリ使用時）

**解決策**:
- Redisを使用（永続化）
- タイムアウト延長

### エラー2: `Could not get JDBC Connection`（Redis使用時）

**原因**: Redis接続失敗

**解決策**:
```bash
# Redis起動確認
redis-cli ping
# PONG が返ればOK
```

### エラー3: `Session serialization error`

**原因**: セッションに保存するオブジェクトがSerializable未実装

**解決策**: `OAuthSession`クラスに`Serializable`実装
```java
public class OAuthSession implements Serializable {
    private static final long serialVersionUID = 1L;
    // ...
}
```

---

## セッション vs トークン

### いつセッションを使うか

| 用途 | セッション | トークン |
|------|----------|---------|
| **Authorization Flow中** | ✅ 使用（認証前） | ❌ まだトークンなし |
| **API認証** | ❌ 使用しない | ✅ Access Token |
| **ステートフル** | ✅ サーバー側で状態管理 | ❌ ステートレス |

**idp-serverの使い分け**:
- ✅ セッション: Authorization Request → 認証 → Authorization Code発行まで
- ✅ トークン: Authorization Code発行後のAPI呼び出し

---

## チェックリスト

Spring Session統合時の確認項目：

### 実装
- [ ] OAuthSessionRepository実装
- [ ] HttpSession DI注入
- [ ] `setAttribute()`/`getAttribute()`使用
- [ ] OAuthSessionDelegate実装

### 設定（本番環境）
- [ ] spring.session.store-type=redis
- [ ] Redis接続設定
- [ ] セッションタイムアウト設定
- [ ] Cookie設定（http-only, secure, same-site）

### テスト
- [ ] セッション保存・取得テスト
- [ ] サーバー再起動後の永続化確認（Redis）
- [ ] マルチサーバーでのセッション共有確認

---

## 次のステップ

✅ Spring Session統合を理解した！

### 🔗 詳細情報

- [AI開発者向け: Platform - Session](../content_10_ai_developer/ai-12-platform.md#session---セッション管理)
- [Concepts: Session Management](../../content_03_concepts/03-authentication-authorization/concept-03-session-management.md)
- [Spring Session Documentation](https://spring.io/projects/spring-session)

---

**情報源**:
- [OAuthSessionDataSource.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/session/datasource/OAuthSessionDataSource.java)
- [OAuthSessionService.java](../../../../libs/idp-server-springboot-adapter/src/main/java/org/idp/server/adapters/springboot/application/session/OAuthSessionService.java)
**最終更新**: 2025-10-12
