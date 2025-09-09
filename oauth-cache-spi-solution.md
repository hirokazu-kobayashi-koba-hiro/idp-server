# OAuth キャッシュのSPI問題解決案

## 🔍 現在の問題分析

### 根本課題: SPIパターンとDIパターンの競合

```java
// 現状: SPIクラスで直接インスタンス化（キャッシュなし）
public HttpRequestEmailSender() {
    this.httpRequestExecutor = new HttpRequestExecutor(
        HttpClientFactory.defaultClient(), 
        new OAuthAuthorizationResolvers()  // ← DIできない
    );
}
```

**問題点:**
- SPIで動的ロードされるクラスは `new` でインスタンス化される
- DIコンテナから `OAuthAuthorizationResolvers` を注入できない
- 結果としてOAuthトークンキャッシュ機能が使えない

## 📋 対象ファイルの分類

### SPIベース（6ファイル）
```
1. HttpRequestEmailSender - EmailSender SPI
2. UserinfoHttpRequestsExecutor - UserinfoExecutor 
3. UserinfoHttpRequestExecutor - UserinfoExecutor
4. HttpRequestParameterResolver - AdditionalRequestParameterResolver
5. IdentityVerificationApplicationHttpRequestExecutor - IdentityVerificationApplicationExecutor
```

### ファクトリベース（1ファイル）
```
6. HttpRequestsAuthenticationExecutor - AuthenticationExecutorFactory経由
```

## 🏗️ 解決策: Factory + DI統合パターン

### アプローチ1: Factory インターフェース拡張

#### 1. 既存Factoryパターン利用

```java
// 既存: AuthenticationExecutorFactory
public interface AuthenticationExecutorFactory {
    AuthenticationExecutor create(AuthenticationDependencyContainer container);
}

// 新規: EmailSenderFactory
public interface EmailSenderFactory {
    EmailSender create(ApplicationComponentDependencyContainer container);
}
```

#### 2. プラグインローダー修正

```java
public class EmailSenderPluginLoader {
    public static EmailSenders load(ApplicationComponentDependencyContainer container) {
        // Factory経由でインスタンス作成
        List<EmailSenderFactory> factories = loadFromInternalModule(EmailSenderFactory.class);
        for (EmailSenderFactory factory : factories) {
            EmailSender sender = factory.create(container);
            senders.put(sender.function(), sender);
        }
    }
}
```

### アプローチ2: プラグイン初期化フック

#### 1. 初期化インターフェース追加

```java
public interface DependencyInjectable {
    void initialize(ApplicationComponentDependencyContainer container);
}

// SPIクラスで実装
public class HttpRequestEmailSender implements EmailSender, DependencyInjectable {
    private HttpRequestExecutor httpRequestExecutor;
    
    public HttpRequestEmailSender() {
        // デフォルトコンストラクタは空
    }
    
    @Override
    public void initialize(ApplicationComponentDependencyContainer container) {
        OAuthAuthorizationResolvers resolvers = container.resolve(OAuthAuthorizationResolvers.class);
        this.httpRequestExecutor = new HttpRequestExecutor(
            HttpClientFactory.defaultClient(), resolvers);
    }
}
```

## 🎯 推奨アプローチ: Factory統一パターン

### 設計方針
1. **統一性**: 全てのSPIクラスをFactory経由に移行
2. **後方互換性**: 既存のSPI検索も維持
3. **DI統合**: コンテナからの依存注入を保証

### 実装ステップ

#### Step 1: Factory インターフェース作成

```java
// 汎用Factory基底
public interface ComponentFactory<T> {
    T create(ApplicationComponentDependencyContainer container);
    String type(); // SPI識別用
}

// 各種Factory
public interface EmailSenderFactory extends ComponentFactory<EmailSender> {}
public interface UserinfoExecutorFactory extends ComponentFactory<UserinfoExecutor> {}
public interface AdditionalRequestParameterResolverFactory extends ComponentFactory<AdditionalRequestParameterResolver> {}
public interface IdentityVerificationApplicationExecutorFactory extends ComponentFactory<IdentityVerificationApplicationExecutor> {}
```

#### Step 2: プラグインローダー統一

```java
public abstract class DependencyAwarePluginLoader<T, F extends ComponentFactory<T>> {
    
    protected Map<String, T> loadWithDependencies(
            Class<F> factoryClass, 
            ApplicationComponentDependencyContainer container) {
        
        Map<String, T> components = new HashMap<>();
        
        // Factory経由の新方式
        List<F> factories = loadFromInternalModule(factoryClass);
        for (F factory : factories) {
            T component = factory.create(container);
            components.put(factory.type(), component);
        }
        
        return components;
    }
}
```

#### Step 3: 段階的移行戦略

```java
public class EmailSenderPluginLoader extends DependencyAwarePluginLoader<EmailSender, EmailSenderFactory> {
    
    public static EmailSenders load(ApplicationComponentDependencyContainer container) {
        Map<String, EmailSender> senders = new HashMap<>();
        
        // 新方式: Factory経由（推奨）
        senders.putAll(loadWithDependencies(EmailSenderFactory.class, container));
        
        // 旧方式: 直接SPI（非推奨、後方互換性のみ）
        List<EmailSender> legacySenders = loadFromInternalModule(EmailSender.class);
        for (EmailSender sender : legacySenders) {
            if (!senders.containsKey(sender.function())) {
                log.warn("Using legacy EmailSender without DI: " + sender.function());
                senders.put(sender.function(), sender);
            }
        }
        
        return new EmailSenders(senders);
    }
}
```

## 🔧 実装優先度

### Phase 1: フレームワーク基盤
1. `DependencyAwarePluginLoader` 基底クラス作成
2. `ComponentFactory` インターフェース群定義
3. プラグインローダー修正

### Phase 2: EmailSender移行（実証）
1. `HttpRequestEmailSenderFactory` 実装
2. `EmailSenderPluginLoader` 修正
3. SPI登録ファイル更新

### Phase 3: 全面展開
1. 残り5クラスのFactory実装
2. 各プラグインローダー修正
3. 旧方式の段階的廃止

## 📐 アーキテクチャ設計指針

### DI制限の明確化

```java
/**
 * SPI Plugin Development Guidelines:
 * 
 * ❌ 禁止: 直接インスタンス化
 * new OAuthAuthorizationResolvers()
 * 
 * ✅ 推奨: Factory + DI
 * ComponentFactory.create(container)
 * 
 * 🔄 互換: 初期化フック
 * DependencyInjectable.initialize(container)
 */
```

### フレームワークルール
1. **新規プラグイン**: Factory必須
2. **既存プラグイン**: 段階移行（警告→エラー）
3. **依存注入**: コンテナ経由のみ許可

## 🎯 具体的な実装例

### HttpRequestEmailSender のFactory実装

#### 1. Factory クラス作成

```java
public class HttpRequestEmailSenderFactory implements EmailSenderFactory {
    
    @Override
    public EmailSender create(ApplicationComponentDependencyContainer container) {
        OAuthAuthorizationResolvers resolvers = container.resolve(OAuthAuthorizationResolvers.class);
        HttpRequestExecutor executor = new HttpRequestExecutor(
            HttpClientFactory.defaultClient(), resolvers);
        return new HttpRequestEmailSender(executor);
    }
    
    @Override
    public String type() {
        return "http_request";
    }
}
```

#### 2. EmailSender コンストラクタ修正

```java
public class HttpRequestEmailSender implements EmailSender {
    
    private final HttpRequestExecutor httpRequestExecutor;
    private final JsonConverter jsonConverter;
    
    // Factory用コンストラクタ
    public HttpRequestEmailSender(HttpRequestExecutor httpRequestExecutor) {
        this.httpRequestExecutor = httpRequestExecutor;
        this.jsonConverter = JsonConverter.snakeCaseInstance();
    }
    
    // 後方互換用デフォルトコンストラクタ（非推奨）
    @Deprecated
    public HttpRequestEmailSender() {
        this(new HttpRequestExecutor(
            HttpClientFactory.defaultClient(), 
            new OAuthAuthorizationResolvers())); // キャッシュなし
    }
}
```

#### 3. SPI登録

```
# META-INF/services/org.idp.server.platform.notification.email.EmailSenderFactory
org.idp.server.platform.notification.email.HttpRequestEmailSenderFactory
```

## 💡 期待効果

### 即座の効果
- OAuth トークンキャッシュがSPIクラスでも利用可能
- 設定ベースのキャッシュ制御（テナント/サービス別）
- パフォーマンス向上（冗長なトークンリクエスト削減）

### 長期的効果
- フレームワークとしてのDI統制強化
- プラグイン開発ガイドラインの明確化
- 後方互換性を保ちながらの段階的移行

## 🚀 まとめ

このアプローチにより、OAuth キャッシュ機能が全SPIクラスで利用可能になり、フレームワークとしてのDI統制も強化されます。段階的な移行戦略により、既存のプラグインに影響を与えることなく、新しい仕組みを導入できます。