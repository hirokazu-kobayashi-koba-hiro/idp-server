# Spring Boot 4.0 移行ガイド

## 概要

Spring Boot 3.5.6 → 4.0.4 + Jackson 2 → 3 への移行で発生した問題と対応をまとめる。

### バージョン変更

| コンポーネント | Before | After |
|-------------|--------|-------|
| Spring Boot | 3.5.6 | 4.0.4 |
| Spring Framework | 6.x | 7.x |
| Spring Security | 6.x | 7.x |
| Jackson | 2.14.2 | 3.1.0 |
| Servlet API | 6.0 (Tomcat 10.x) | 6.1 (Tomcat 11) |
| dependency-management plugin | 1.1.4 | 1.1.7 |

---

## 1. Jackson 2 → 3 移行

> **公式ガイド**: [Migrating to Jackson 3](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md) | [Jackson 3.0.0 Release Notes](https://cowtowncoder.medium.com/jackson-3-0-0-ga-released-1f669cda529a) | [Jackson 3 in Spring Boot 4](https://www.danvega.dev/blog/2025/11/10/jackson-3-spring-boot-4) | [Spring の Jackson 3 サポート](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/)

### 1.1 パッケージ変更

| Before | After | 備考 |
|--------|-------|------|
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.json.JsonMapper` | immutable builder pattern |
| `com.fasterxml.jackson.core.JsonProcessingException` | `tools.jackson.core.JacksonException` | unchecked exception |
| `com.fasterxml.jackson.databind.JsonNode` | `tools.jackson.databind.JsonNode` | |
| `com.fasterxml.jackson.databind.node.JsonNodeFactory` | `tools.jackson.databind.node.JsonNodeFactory` | |
| `com.fasterxml.jackson.databind.PropertyNamingStrategies` | `tools.jackson.databind.PropertyNamingStrategies` | |
| `com.fasterxml.jackson.annotation.*` | 変更なし | annotations は共有 |

### 1.2 API 変更

#### ObjectMapper → JsonMapper (Builder Pattern)

```java
// Before (Jackson 2)
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
objectMapper.registerModule(new JavaTimeModule());

// After (Jackson 3)
JsonMapper jsonMapper = JsonMapper.builder()
    .changeDefaultVisibility(vc ->
        vc.withVisibility(PropertyAccessor.ALL, Visibility.NONE)
          .withVisibility(PropertyAccessor.FIELD, Visibility.ANY))
    .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    // JavaTimeModule は不要（Jackson 3 に内蔵）
    .build();
```

#### Coercion 設定

```java
// Before (Jackson 2)
objectMapper
    .coercionConfigFor(LogicalType.Collection)
    .setCoercion(CoercionInputShape.String, CoercionAction.AsNull);

// After (Jackson 3)
JsonMapper.builder()
    .withCoercionConfig(LogicalType.Collection, config -> {
        config.setCoercion(CoercionInputShape.String, CoercionAction.AsNull);
    })
    .build();
```

#### JsonNode API 変更

| Before (Jackson 2) | After (Jackson 3) |
|---------------------|-------------------|
| `jsonNode.fieldNames()` | `jsonNode.properties()` → `Set<Map.Entry<String, JsonNode>>` |
| `jsonNode.elements()` | `jsonNode.iterator()` |

#### jackson-datatype-jsr310

Jackson 3 では `java.time` サポートが内蔵されたため、`jackson-datatype-jsr310` 依存は不要。

### 1.3 private final フィールド問題（重要）

**Jackson 3 では `private final` フィールドへのリフレクション書き込みができなくなった。**

Jackson でデシリアライズされるクラス（Redis キャッシュ経由、JSON → Object 変換等）の `private final` フィールドを `private` に変更する必要がある。

```java
// Before - Jackson 3 でデシリアライズ失敗
public class SecurityEventLogConfiguration {
    private final boolean persistenceEnabled;  // ← final があるとデフォルト値のまま
}

// After - Jackson 3 でデシリアライズ成功
public class SecurityEventLogConfiguration {
    private boolean persistenceEnabled;  // ← final を外す
}
```

**影響範囲**: フィールドの型として使われるネストされたオブジェクトも含めて、デシリアライズ対象のオブジェクトグラフ全体で `private final` を確認する必要がある。

**対象クラスの例**:
- テナント設定クラス（`SecurityEventLogConfiguration`, `SessionConfiguration`, `CorsConfiguration`, `UIConfiguration` 等）
- セッション関連値オブジェクト（`OPSessionIdentifier`, `BrowserState` 等）
- Redis キャッシュ経由でシリアライズ/デシリアライズされる全オブジェクト

---

## 2. Spring Boot 4.0 固有の変更

> **公式ガイド**: [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) | [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes) | [Spring Security 7 リファレンス](https://docs.spring.io/spring-security/reference/)

### 2.1 SessionAutoConfiguration 削除

Spring Boot 4.0 では `SessionAutoConfiguration` が削除された。

```java
// Before
@SpringBootApplication(exclude = SessionAutoConfiguration.class)
public class IdPApplication { }

// After
@SpringBootApplication
public class IdPApplication { }
```

### 2.2 ContentCachingRequestWrapper

`ContentCachingRequestWrapper` のコンストラクタに `maxContentLength` 引数が必須になった。

```java
// Before
new ContentCachingRequestWrapper(request);

// After
new ContentCachingRequestWrapper(request, 50000);
```

### 2.3 httpBasic の無効化

Spring Security 7 では `httpBasic` を明示的に無効化する必要がある場合がある。

```java
http.httpBasic(AbstractHttpConfigurer::disable);
```

---

## 3. Tomcat 11 (Servlet 6.1) の変更

> **公式ガイド**: [Tomcat 11 Migration Guide](https://tomcat.apache.org/migration-11.html) | [Servlet 6.1 Specification (Jakarta EE 11)](https://jakarta.ee/specifications/servlet/6.1/) | [RFC 7230 Section 3.2 - Header Fields](https://www.rfc-editor.org/rfc/rfc7230#section-3.2)

### 3.1 ヘッダー名のケース保持

**Tomcat 10.x** では `HttpServletRequest.getHeaderNames()` がヘッダー名を小文字で返していたが、**Tomcat 11** では原形のケースを保持するようになった。

```
Tomcat 10.x: "authorization"
Tomcat 11:   "Authorization"
```

HTTP ヘッダー名は RFC 7230 で case-insensitive と定義されているため、ヘッダー名でMap検索する場合は case-insensitive で比較する必要がある。

```java
// Before - Tomcat 10.x では動いたが Tomcat 11 で失敗
String authorization = headersMap.get("authorization");

// After - case-insensitive 検索
private String getHeaderValueCaseInsensitive(Map<String, String> headers, String name) {
    for (Map.Entry<String, String> entry : headers.entrySet()) {
        if (entry.getKey().equalsIgnoreCase(name)) {
            return entry.getValue();
        }
    }
    return "";
}
```

**注意**: Spring MVC の `@RequestHeader` アノテーションは内部で case-insensitive 処理を行うため、この問題は発生しない。問題は `HttpServletRequest.getHeaderNames()` で取得したヘッダー名を自前で Map に格納し、後から文字列キーで検索するパターンで発生する。

---

## 4. 移行チェックリスト

### build.gradle

- [ ] Spring Boot バージョンを 4.0.x に更新
- [ ] dependency-management プラグインを 1.1.7 に更新
- [ ] Jackson 依存を `tools.jackson.core:jackson-databind` に変更
- [ ] `jackson-datatype-jsr310` 依存を削除

### Java コード

- [ ] Jackson import 文を `tools.jackson.*` に変更（annotations は除く）
- [ ] `ObjectMapper` → `JsonMapper` (Builder Pattern) に変更
- [ ] `JsonProcessingException` → `JacksonException` に変更
- [ ] `fieldNames()` → `properties()` に変更
- [ ] `elements()` → `iterator()` に変更
- [ ] Coercion 設定を Builder API に変更
- [ ] `SessionAutoConfiguration` の exclude を削除
- [ ] `ContentCachingRequestWrapper` に `maxContentLength` 引数を追加
- [ ] Jackson でデシリアライズされるクラスの `private final` → `private` に変更
- [ ] ヘッダー名の case-insensitive 検索を確認

### テスト

- [ ] ユニットテスト全通し
- [ ] E2E テスト全通し
- [ ] Redis キャッシュをフラッシュしてからテスト（古い形式のキャッシュデータが残ると問題）

---

## 5. 影響を受けないもの

- `@RequestHeader` アノテーション経由のヘッダー取得（Spring MVC が case-insensitive 処理）
- `com.fasterxml.jackson.annotation.*`（Jackson 2/3 共有）
- MongoDB, Undertow, Hazelcast（未使用）
- `@MockBean` / `@SpyBean`（未使用）

---

## 参考資料

### Spring Boot / Spring Framework
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring Boot 4.0 Migration Guide for Production Teams](https://dev.to/aytronn/spring-boot-40-migration-guide-for-production-teams-what-actually-breaks-and-how-to-upgrade-safely-22me)
- [Spring Boot 4 Migration Guide: Faster, Safer, at Scale (Moderne)](https://www.moderne.ai/blog/spring-boot-4x-migration-guide)
- [Spring Security 7 リファレンス](https://docs.spring.io/spring-security/reference/)
- [Spring Boot Dependency Versions](https://docs.spring.io/spring-boot/appendix/dependency-versions/index.html)

### Jackson
- [Jackson 3 Migration Guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)
- [Jackson 3.0.0 (GA) Released](https://cowtowncoder.medium.com/jackson-3-0-0-ga-released-1f669cda529a)
- [Jackson 3 in Spring Boot 4: JsonMapper, JSON Views, and What's Changed](https://www.danvega.dev/blog/2025/11/10/jackson-3-spring-boot-4)
- [Introducing Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring/)
- [Upgrading to Jackson 3 with Spring Boot 4](https://dimitri.codes/jsonmapper/)
- [OpenRewrite: Migrate Jackson 2.x to 3.x](https://docs.openrewrite.org/recipes/java/jackson/upgradejackson_2_3)

### Tomcat / Servlet
- [Tomcat 11 Migration Guide](https://tomcat.apache.org/migration-11.html)
- [Jakarta EE 11 - Servlet 6.1 Specification](https://jakarta.ee/specifications/servlet/6.1/)
- [RFC 7230 Section 3.2 - Header Fields (case-insensitive)](https://www.rfc-editor.org/rfc/rfc7230#section-3.2)
