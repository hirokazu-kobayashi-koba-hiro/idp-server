# テナントベースログフィルタリング実装設計

## 概要

マルチテナント環境でのログ追跡性向上のため、テナント情報を自動的にログに含める機能を実装します。

## 現状の課題

- アプリケーションログにテナント情報が含まれていない
- ログが未整理で、エラー調査に必要な詳細情報が不足
- マルチテナント環境でのログフィルタリングが困難

## 既存インフラ分析

### TenantAwareEntryServiceProxy
既存の`TenantAwareEntryServiceProxy`が全EntryServiceでテナント解決を実行済み：

```java
// 既に実装済み: テナント自動解決
protected TenantIdentifier resolveTenantIdentifier(Object[] args) {
    for (Object arg : args) {
        if (arg instanceof TenantIdentifier tenantId) {
            return tenantId; // ← ここでテナント情報取得済み
        }
    }
}
```

**重要**: Filter層ではなく、EntryService層で既にテナント情報が利用可能！

## 設計方針（見直し版）

### アーキテクチャ

```
EntryService Method Call
         ↓
TenantAwareEntryServiceProxy.invoke()
         ↓
resolveTenantIdentifier() → TenantLoggingContext.setTenant()
         ↓
TransactionManager + LoggerWrapper → SLF4J MDC
         ↓
Logback (テナント情報付きログ)
         ↓
finally { TenantLoggingContext.clear() }
```

### 主要コンポーネント

1. **TenantLoggingContext**: SLF4J MDC管理
2. **TenantAwareEntryServiceProxy改修**: 既存Proxyにテナントコンテキスト設定追加
3. **LoggerWrapper拡張**: テナント情報自動注入
4. **Logback設定更新**: JSON出力にテナント情報追加

## 実装例

### 1. TenantLoggingContext

```java
public class TenantLoggingContext {
    private static final String TENANT_ID_KEY = "tenantId";

    public static void setTenant(TenantIdentifier tenantIdentifier) {
        if (Objects.nonNull(tenantIdentifier) && tenantIdentifier.exists()) {
            MDC.put(TENANT_ID_KEY, tenantIdentifier.value());
        }
    }

    public static String getCurrentTenant() {
        return MDC.get(TENANT_ID_KEY);
    }

    public static void clear() {
        MDC.remove(TENANT_ID_KEY);
    }
}
```

### 2. TenantAwareEntryServiceProxy改修

```java
public class TenantAwareEntryServiceProxy implements InvocationHandler {
    LoggerWrapper log = LoggerWrapper.getLogger(TenantAwareEntryServiceProxy.class);

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        boolean isTransactional = // 既存ロジック...

        if (isTransactional && operationType == OperationType.READ) {
            try {
                OperationContext.set(operationType);
                TenantIdentifier tenantIdentifier = resolveTenantIdentifier(args);

                // ★新規追加: テナントコンテキスト設定
                TenantLoggingContext.setTenant(tenantIdentifier);

                log.debug("READ start: [{}] {}: {} ...",
                    tenantIdentifier.value(),
                    target.getClass().getName(),
                    method.getName());

                // 既存処理...
                Object result = method.invoke(target, args);
                return result;
            } finally {
                // ★新規追加: テナントコンテキストクリア
                TenantLoggingContext.clear();
                TransactionManager.closeConnection();
            }
        }
        // WRITE操作も同様に改修...
    }
}

### 3. LoggerWrapper拡張

```java
public class LoggerWrapper {
    Logger logger;

    public void info(String message) {
        if (TenantLoggingContext.hasTenant()) {
            logger.info("[{}] {}", TenantLoggingContext.getCurrentTenant(), message);
        } else {
            logger.info(message);
        }
    }

    // または MDC を活用してLogback設定で自動追加
    public void info(String message) {
        logger.info(message); // MDCから自動的にテナント情報を取得
    }
}
```

### 4. Logback設定更新

```xml
<configuration>
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeContext>true</includeContext>
            <includeMdc>true</includeMdc>
            <customFields>{"service":"idp-server"}</customFields>
        </encoder>
    </appender>

    <!-- テナント別ログファイル出力 (オプション) -->
    <appender name="TENANT_FILE" class="ch.qos.logback.classic.sift.SiftingAppender">
        <discriminator>
            <key>tenantId</key>
            <defaultValue>unknown</defaultValue>
        </discriminator>
        <sift>
            <appender name="FILE-${tenantId}" class="ch.qos.logback.core.FileAppender">
                <file>logs/tenant-${tenantId}.log</file>
                <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
            </appender>
        </sift>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_CONSOLE"/>
    </root>
</configuration>
```

## ログ出力例

### Before (現状)
```json
{
  "timestamp": "2025-01-20T10:30:45.123Z",
  "level": "INFO",
  "message": "User authentication successful",
  "logger": "org.idp.server.core.auth.AuthenticationHandler"
}
```

### After (実装後)
```json
{
  "timestamp": "2025-01-20T10:30:45.123Z",
  "level": "INFO",
  "message": "User authentication successful",
  "logger": "org.idp.server.core.auth.AuthenticationHandler",
  "mdc": {
    "tenantId": "tenant-123-abc",
    "requestId": "req-456-def"
  },
  "service": "idp-server"
}
```

## 既存テナント抽出の活用

既存の`TenantAwareEntryServiceProxy`は以下の方法でテナント情報を取得：

### メソッド引数からの自動抽出
```java
protected TenantIdentifier resolveTenantIdentifier(Object[] args) {
    for (Object arg : args) {
        if (arg instanceof TenantIdentifier tenantId) {
            return tenantId; // メソッド引数から自動検出
        }
    }
    throw new MissingRequiredTenantIdentifierException(
        "Missing required TenantIdentifier");
}
```

### EntryServiceメソッドのパターン
```java
// 全EntryServiceメソッドは必須でTenantIdentifierを持つ
public UserResponse getUser(TenantIdentifier tenantIdentifier, UserId userId);
public void createUser(TenantIdentifier tenantIdentifier, CreateUserRequest request);
```

**利点**:
- HTTP層の複雑な抽出ロジック不要
- 既存アーキテクチャパターンの活用
- 確実なテナント情報取得（必須引数のため）

## 実装順序（見直し版）

1. **TenantLoggingContext** - MDC管理基盤
2. **TenantAwareEntryServiceProxy改修** - 既存Proxyにテナントコンテキスト設定追加
3. **LoggerWrapper拡張** - MDC活用ログ出力
4. **Logback設定更新** - JSON出力拡張
5. **テスト実装** - 統合テスト

### 実装範囲
- ✅ **対象**: EntryService層のすべてのトランザクション操作
- ✅ **自動化**: プロキシによる透明なテナント情報設定
- ⚠️ **制限**: Controller層やFilter層ではテナント情報なし（設計通り）

## 注意事項

### スレッドリーク対策
- **必須**: `finally`ブロックでMDC.clear()
- **Proxy**: トランザクション完了時の自動クリーンアップ
- **非同期処理**: 明示的なコンテキスト伝播（必要に応じて）

### パフォーマンス考慮
- MDC操作のオーバーヘッドは最小限
- 既存のテナント解決ロジックを活用（追加コストなし）
- ログレベル設定での制御

### セキュリティ
- テナントIDの漏洩防止
- ログ出力での機密情報除去
- アクセス制御との連携

## 設定オプション

```yaml
idp:
  logging:
    tenant:
      enabled: true
      extraction-strategy: header # header, subdomain, jwt, path
      header-name: X-Tenant-ID
      include-in-message: false # メッセージ本文に含めるか
      separate-files: false # テナント別ファイル出力
```

## モニタリング・運用

### ログクエリ例
```bash
# 特定テナントのログ抽出
jq '.mdc.tenantId == "tenant-123"' < application.log

# テナント別エラー集計
jq -r 'select(.level == "ERROR") | .mdc.tenantId' < application.log | sort | uniq -c
```

### アラート設定
- テナント別エラー率監視
- ログ量異常検知
- テナント情報欠落検出