# Native Image（GraalVM）

## はじめに

GraalVM Native Imageは、Javaアプリケーションを事前（AOT: Ahead-Of-Time）コンパイルしてネイティブ実行可能ファイルを生成する技術です。起動時間とメモリ使用量を大幅に削減でき、コンテナ環境やサーバーレスに適しています。

---

## JIT vs AOT

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JIT vs AOT コンパイル                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  JIT（Just-In-Time）- 従来のJVM                                     │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                                                                 │ │
│  │  .java → .class → JVM起動 → インタプリタ → JITコンパイル       │ │
│  │                      ↑                           ↓             │ │
│  │                   遅い起動              ピーク性能到達          │ │
│  │                                                                 │ │
│  │  特徴:                                                          │ │
│  │  ・起動時間: 秒〜十秒単位                                       │ │
│  │  ・ピーク性能: 高い（実行時最適化）                            │ │
│  │  ・メモリ: 多い（JVM + JIT + メタデータ）                      │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  AOT（Ahead-Of-Time）- Native Image                                 │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │                                                                 │ │
│  │  .java → .class → native-image → 実行可能ファイル → 即座に実行 │ │
│  │                        ↑                              ↓        │ │
│  │                   ビルド時間長い              起動が超高速     │ │
│  │                                                                 │ │
│  │  特徴:                                                          │ │
│  │  ・起動時間: ミリ秒単位                                        │ │
│  │  ・ピーク性能: JITより低いことがある                           │ │
│  │  ・メモリ: 少ない（必要最小限）                                │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 比較表

| 項目 | JIT (HotSpot) | AOT (Native Image) |
|-----|---------------|-------------------|
| 起動時間 | 1〜10秒 | 10〜100ミリ秒 |
| ピーク性能 | 高い | やや低い〜同等 |
| メモリ使用量 | 多い（100MB〜） | 少ない（10〜50MB） |
| ビルド時間 | 速い | 遅い（分単位） |
| 動的機能 | 完全サポート | 制限あり |
| デバッグ | 容易 | 困難 |

---

## Native Image のアーキテクチャ

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Native Image ビルドプロセス                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ソースコード                                                        │
│       │                                                              │
│       ↓                                                              │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  静的解析 (Points-to Analysis)                              │    │
│  │  ・到達可能なコードを特定                                   │    │
│  │  ・使用されるクラス・メソッドを列挙                         │    │
│  │  ・不要なコードを除外                                       │    │
│  └─────────────────────────────────────────────────────────────┘    │
│       │                                                              │
│       ↓                                                              │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  ヒープスナップショット                                     │    │
│  │  ・staticイニシャライザを実行                               │    │
│  │  ・初期ヒープ状態をイメージに埋め込み                       │    │
│  └─────────────────────────────────────────────────────────────┘    │
│       │                                                              │
│       ↓                                                              │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  AOTコンパイル                                               │    │
│  │  ・全てのコードをネイティブコードに変換                     │    │
│  │  ・最適化適用                                               │    │
│  └─────────────────────────────────────────────────────────────┘    │
│       │                                                              │
│       ↓                                                              │
│  ネイティブ実行可能ファイル                                         │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  ・Substrate VM（軽量ランタイム）                           │    │
│  │  ・コンパイル済みアプリケーションコード                     │    │
│  │  ・初期ヒープスナップショット                               │    │
│  │  ・GC（Serial GC / G1 GC）                                  │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## GraalVM のインストール

### SDKMAN での インストール

```bash
# GraalVM JDK 21 のインストール
sdk install java 21.0.2-graalce

# 使用
sdk use java 21.0.2-graalce

# 確認
java -version
native-image --version
```

### 手動インストール

```bash
# macOS (Homebrew)
brew install --cask graalvm-jdk

# 環境変数設定
export GRAALVM_HOME=/Library/Java/JavaVirtualMachines/graalvm-jdk-21/Contents/Home
export PATH=$GRAALVM_HOME/bin:$PATH
```

---

## 基本的な使い方

### シンプルなアプリケーション

```java
// HelloWorld.java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, Native Image!");
    }
}
```

```bash
# コンパイル
javac HelloWorld.java

# Native Image 生成
native-image HelloWorld

# 実行
./helloworld
```

### ビルドオプション

```bash
native-image \
  # 出力ファイル名
  -o myapp \

  # メインクラス指定（JARの場合）
  -jar myapp.jar \

  # ヒープサイズ
  -H:MaxHeapSize=256m \

  # GC選択
  --gc=G1 \

  # 静的リンク（musl libc使用時）
  --static --libc=musl \

  # ビルド時診断
  --verbose \

  # 最適化レベル
  -O3 \

  HelloWorld
```

---

## Spring Boot との統合

### Spring Boot 3.x + GraalVM

```xml
<!-- pom.xml -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.graalvm.buildtools</groupId>
            <artifactId>native-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

```bash
# Native Image ビルド
./mvnw -Pnative native:compile

# 実行
./target/myapp
```

### Gradle の場合

```kotlin
// build.gradle.kts
plugins {
    id("org.graalvm.buildtools.native") version "0.10.0"
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("myapp")
            mainClass.set("com.example.Application")
            buildArgs.add("-O3")
            buildArgs.add("--gc=G1")
        }
    }
}
```

```bash
# Native Image ビルド
./gradlew nativeCompile

# 実行
./build/native/nativeCompile/myapp
```

---

## 動的機能の対応

### 制限される機能

Native Imageは静的解析に基づくため、以下の動的機能に制限があります。

```
┌─────────────────────────────────────────────────────────────────────┐
│                    動的機能の制限                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  リフレクション                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  Class.forName("com.example.MyClass")                          │ │
│  │  → ビルド時にクラス名が分からない                              │ │
│  │  → 設定ファイルで明示的に指定が必要                            │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  動的プロキシ                                                        │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  Proxy.newProxyInstance(...)                                   │ │
│  │  → プロキシ対象インターフェースの指定が必要                    │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  JNI                                                                 │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  ネイティブメソッドの呼び出し                                  │ │
│  │  → JNI設定ファイルで指定が必要                                 │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  リソースアクセス                                                    │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  getClass().getResourceAsStream("/config.json")                │ │
│  │  → リソース設定ファイルで指定が必要                            │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  シリアライゼーション                                                │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  ObjectInputStream / ObjectOutputStream                        │ │
│  │  → シリアライズ対象クラスの指定が必要                          │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 設定ファイルによる対応

```
src/main/resources/META-INF/native-image/
├── reflect-config.json      # リフレクション
├── proxy-config.json        # 動的プロキシ
├── resource-config.json     # リソース
├── jni-config.json          # JNI
└── serialization-config.json # シリアライゼーション
```

#### reflect-config.json

```json
[
  {
    "name": "com.example.User",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  },
  {
    "name": "com.example.Order",
    "methods": [
      { "name": "getId", "parameterTypes": [] },
      { "name": "setId", "parameterTypes": ["java.lang.Long"] }
    ]
  }
]
```

#### resource-config.json

```json
{
  "resources": {
    "includes": [
      { "pattern": "application\\.yml" },
      { "pattern": "templates/.*\\.html" },
      { "pattern": "static/.*" }
    ]
  }
}
```

### トレーシングエージェント

設定ファイルを自動生成するエージェント:

```bash
# エージェントを使用してアプリを実行
java -agentlib:native-image-agent=config-output-dir=src/main/resources/META-INF/native-image \
     -jar myapp.jar

# テストを実行して設定を収集
./mvnw test -DargLine="-agentlib:native-image-agent=config-merge-dir=src/main/resources/META-INF/native-image"
```

---

## パフォーマンス最適化

### Profile-Guided Optimization (PGO)

```bash
# 1. 計測用バイナリをビルド
native-image --pgo-instrument -o myapp-instrumented -jar myapp.jar

# 2. 実際のワークロードで実行（プロファイル収集）
./myapp-instrumented
# → default.iprof が生成される

# 3. プロファイルを使用して最適化ビルド
native-image --pgo=default.iprof -o myapp-optimized -jar myapp.jar
```

### G1 GC の使用

```bash
# G1 GC を使用（大きなヒープ向け）
native-image --gc=G1 -jar myapp.jar

# Serial GC（デフォルト、小さなヒープ向け）
native-image --gc=serial -jar myapp.jar

# Epsilon GC（GCなし、短命プロセス向け）
native-image --gc=epsilon -jar myapp.jar
```

### メモリ設定

```bash
native-image \
  # 最大ヒープサイズ
  -R:MaxHeapSize=512m \

  # 初期ヒープサイズ
  -R:MinHeapSize=64m \

  # スレッドスタックサイズ
  -R:StackSize=1m \

  -jar myapp.jar
```

---

## コンテナイメージの作成

### マルチステージビルド

```dockerfile
# ビルドステージ
FROM ghcr.io/graalvm/native-image-community:21 AS builder

WORKDIR /app
COPY . .

RUN ./gradlew nativeCompile --no-daemon

# 実行ステージ（最小イメージ）
FROM gcr.io/distroless/base-debian12

COPY --from=builder /app/build/native/nativeCompile/myapp /app/myapp

ENTRYPOINT ["/app/myapp"]
```

### 静的リンク（Alpine Linux 対応）

```dockerfile
# ビルドステージ
FROM ghcr.io/graalvm/native-image-community:21-muslib AS builder

WORKDIR /app
COPY . .

RUN ./gradlew nativeCompile \
    -Pnative.buildArgs="--static --libc=musl" \
    --no-daemon

# 実行ステージ（scratch も可能）
FROM alpine:3.19

COPY --from=builder /app/build/native/nativeCompile/myapp /app/myapp

ENTRYPOINT ["/app/myapp"]
```

### イメージサイズ比較

| イメージ | サイズ |
|---------|-------|
| JVM (Eclipse Temurin) | 約 400MB |
| Native Image (distroless) | 約 80MB |
| Native Image (static + Alpine) | 約 30MB |
| Native Image (static + scratch) | 約 25MB |

---

## ユースケース別の選択指針

```
┌─────────────────────────────────────────────────────────────────────┐
│                    JVM vs Native Image 選択                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Native Image が適しているケース                                     │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  ・サーバーレス / FaaS（AWS Lambda等）                         │ │
│  │    → コールドスタート時間が重要                                │ │
│  │                                                                 │ │
│  │  ・CLI ツール                                                   │ │
│  │    → 即座に起動して終了するアプリ                              │ │
│  │                                                                 │ │
│  │  ・マイクロサービス（多数のインスタンス）                       │ │
│  │    → メモリ効率が重要                                          │ │
│  │                                                                 │ │
│  │  ・Kubernetes でのスケールアウト                                │ │
│  │    → 高速なスケールが必要                                      │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
│  JVM が適しているケース                                              │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │  ・長時間稼働するサービス                                       │ │
│  │    → JITによるピーク性能が活きる                               │ │
│  │                                                                 │ │
│  │  ・動的機能を多用するアプリ                                     │ │
│  │    → リフレクション、動的プロキシ等                            │ │
│  │                                                                 │ │
│  │  ・開発中のアプリ                                               │ │
│  │    → 高速なビルド・デバッグサイクル                            │ │
│  │                                                                 │ │
│  │  ・複雑な依存関係                                               │ │
│  │    → Native Image 対応が不十分なライブラリ                     │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## トラブルシューティング

### よくあるエラー

| エラー | 原因 | 対処 |
|-------|------|------|
| `ClassNotFoundException` at runtime | リフレクション設定不足 | reflect-config.json に追加 |
| `NoSuchMethodException` | メソッドが静的解析で検出されない | reflect-config.json に追加 |
| `MissingResourceException` | リソースがイメージに含まれない | resource-config.json に追加 |
| `UnsupportedFeatureException` | サポートされない機能の使用 | 代替実装を検討 |

### デバッグ

```bash
# 詳細なビルドログ
native-image --verbose -jar myapp.jar

# 到達可能性レポート
native-image -H:+PrintAnalysisCallTree -jar myapp.jar

# ビルド時間の内訳
native-image -H:+PrintClassInitialization -jar myapp.jar
```

### ビルド時間の短縮

```bash
# 並列ビルド
native-image -J-Xmx8g --parallelism=8 -jar myapp.jar

# Quick Build モード（最適化を減らす）
native-image -Ob -jar myapp.jar
```

---

## idp-server での検討

| 観点 | 評価 |
|-----|------|
| 起動時間 | Native Imageで大幅改善可能 |
| メモリ効率 | Kubernetes環境で効果的 |
| 動的機能 | Spring Security、JPA等が多用 → 設定が複雑 |
| ビルド時間 | CI/CDパイプラインへの影響 |
| 運用 | デバッグ・トラブルシューティングの難易度 |

### 段階的なアプローチ

```
1. 開発・テスト環境: JVM（高速なイテレーション）
2. ステージング: Native Image でテスト
3. 本番:
   - 長時間稼働サービス → JVM
   - スケールアウト重視 → Native Image
```

---

## まとめ

| 項目 | ポイント |
|-----|---------|
| Native Image | Javaを事前コンパイルしてネイティブ実行可能ファイル化 |
| メリット | 高速起動（ミリ秒）、低メモリ消費 |
| デメリット | 長いビルド時間、動的機能の制限 |
| 設定ファイル | リフレクション、リソース等を明示的に指定 |
| トレーシングエージェント | 設定ファイルの自動生成に活用 |
| ユースケース | サーバーレス、CLI、マイクロサービス向け |

---

## 参考リソース

- [GraalVM Native Image Documentation](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Spring Boot Native Image Support](https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html)
- [GraalVM Reachability Metadata](https://github.com/oracle/graalvm-reachability-metadata)
