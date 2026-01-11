# JVM概要

## はじめに

Java Virtual Machine（JVM）は、Javaバイトコードを実行するための仮想マシンです。「Write Once, Run Anywhere」の理念を実現し、プラットフォームに依存しないJavaプログラムの実行を可能にしています。

---

## JVMアーキテクチャ

### 全体構成

```
┌─────────────────────────────────────────────────────────────┐
│                      Java Application                        │
├─────────────────────────────────────────────────────────────┤
│                      Java Class Files (.class)               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Class Loader Subsystem                   │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────────┐   │   │
│  │  │ Bootstrap  │→│ Extension  │→│  Application   │   │   │
│  │  │   Loader   │ │   Loader   │ │    Loader      │   │   │
│  │  └────────────┘ └────────────┘ └────────────────┘   │   │
│  └──────────────────────────────────────────────────────┘   │
│                           ↓                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                Runtime Data Areas                     │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌──────────┐   │   │
│  │  │  Heap   │ │ Method  │ │  Stack  │ │    PC    │   │   │
│  │  │         │ │  Area   │ │         │ │ Register │   │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └──────────┘   │   │
│  └──────────────────────────────────────────────────────┘   │
│                           ↓                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │               Execution Engine                        │   │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────────┐   │   │
│  │  │Interpreter │ │JIT Compiler│ │Garbage Collector│   │   │
│  │  └────────────┘ └────────────┘ └────────────────┘   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
├─────────────────────────────────────────────────────────────┤
│              Native Method Interface (JNI)                   │
├─────────────────────────────────────────────────────────────┤
│                  Operating System                            │
└─────────────────────────────────────────────────────────────┘
```

### 主要コンポーネント

| コンポーネント | 役割 |
|--------------|------|
| Class Loader Subsystem | クラスファイルのロード・リンク・初期化 |
| Runtime Data Areas | プログラム実行に必要なメモリ領域 |
| Execution Engine | バイトコードの解釈・実行・最適化 |
| Native Method Interface | ネイティブコード（C/C++）との連携 |

---

## JVMの実行モデル

### バイトコードとは

Javaソースコード（`.java`）はコンパイルされてバイトコード（`.class`）になります。

```java
// ソースコード: HelloWorld.java
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```

```bash
# コンパイル
javac HelloWorld.java

# バイトコード確認
javap -c HelloWorld.class
```

```
Compiled from "HelloWorld.java"
public class HelloWorld {
  public HelloWorld();
    Code:
       0: aload_0
       1: invokespecial #1    // Method java/lang/Object."<init>":()V
       4: return

  public static void main(java.lang.String[]);
    Code:
       0: getstatic     #7    // Field java/lang/System.out:Ljava/io/PrintStream;
       3: ldc           #13   // String Hello, World!
       5: invokevirtual #15   // Method java/io/PrintStream.println:(Ljava/lang/String;)V
       8: return
}
```

### インタプリタとJITコンパイラ

JVMは2つの実行モードを組み合わせて効率的にコードを実行します。

```
┌─────────────────────────────────────────────────────────┐
│                   実行の流れ                            │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  1. 初回実行：インタプリタ                               │
│     ┌─────────────┐      ┌─────────────┐                │
│     │ バイトコード │ ───→ │  即座に実行  │               │
│     └─────────────┘      └─────────────┘                │
│                              ↓                          │
│  2. 実行回数カウント                                     │
│     ┌─────────────────────────────────┐                 │
│     │ このメソッド、よく呼ばれるな...  │                │
│     │ (ホットスポット検出)             │                │
│     └─────────────────────────────────┘                 │
│                              ↓                          │
│  3. JITコンパイル                                        │
│     ┌─────────────┐      ┌─────────────┐                │
│     │ バイトコード │ ───→ │ネイティブコード│              │
│     └─────────────┘      └─────────────┘                │
│                              ↓                          │
│  4. 以降の実行                                          │
│     ┌─────────────────────────────────┐                 │
│     │ ネイティブコードで高速実行       │                │
│     └─────────────────────────────────┘                 │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

### JITコンパイラの階層

| 階層 | コンパイラ | 特徴 |
|-----|-----------|------|
| Level 0 | インタプリタ | 即座に実行、プロファイル情報収集 |
| Level 1-3 | C1 (Client Compiler) | 高速コンパイル、軽度の最適化 |
| Level 4 | C2 (Server Compiler) | 低速コンパイル、積極的な最適化 |

---

## JDKディストリビューション

### 主要なディストリビューション

| ディストリビューション | 提供元 | 特徴 | ライセンス |
|---------------------|-------|------|-----------|
| Oracle JDK | Oracle | 商用機能含む、有償サポート | Oracle NFTC |
| Eclipse Temurin | Eclipse Foundation | OpenJDK準拠、広く採用 | GPLv2+CE |
| Amazon Corretto | AWS | AWS最適化、長期サポート | GPLv2+CE |
| Azul Zulu | Azul Systems | 幅広いプラットフォーム | GPLv2+CE |
| Oracle GraalVM | Oracle | 高性能、多言語対応 | GFTC/CE |
| Microsoft Build of OpenJDK | Microsoft | Azure最適化 | GPLv2+CE |

### 選択の指針

```
┌─────────────────────────────────────────────────────────┐
│                  JDK選択フローチャート                   │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  AWS環境で運用？                                         │
│    ├─ Yes → Amazon Corretto                             │
│    └─ No                                                │
│         │                                               │
│         ├─ Azure環境？                                  │
│         │   └─ Yes → Microsoft Build of OpenJDK        │
│         │                                               │
│         ├─ Native Image必要？                           │
│         │   └─ Yes → GraalVM                           │
│         │                                               │
│         ├─ 商用サポート必要？                           │
│         │   └─ Yes → Oracle JDK or Azul Zulu           │
│         │                                               │
│         └─ 一般的な開発・運用                           │
│             └─ Eclipse Temurin（推奨）                  │
│                                                          │
└─────────────────────────────────────────────────────────┘
```

---

## JVMオプション

### カテゴリ

JVMオプションは3つのカテゴリに分類されます。

| プレフィックス | 説明 | 例 |
|--------------|------|-----|
| `-` | 標準オプション（全JVM共通） | `-version`, `-classpath` |
| `-X` | 非標準オプション（実装依存） | `-Xms`, `-Xmx` |
| `-XX:` | 開発者向け詳細オプション | `-XX:+UseG1GC` |

### よく使うオプション

```bash
# メモリ設定
java -Xms512m -Xmx2g -jar app.jar

# GC選択
java -XX:+UseG1GC -jar app.jar          # G1GC（デフォルト）
java -XX:+UseZGC -jar app.jar           # ZGC（低レイテンシ）
java -XX:+UseShenandoahGC -jar app.jar  # Shenandoah

# デバッグ・診断
java -XX:+HeapDumpOnOutOfMemoryError -jar app.jar
java -Xlog:gc*:file=gc.log -jar app.jar

# パフォーマンス
java -XX:+TieredCompilation -jar app.jar
```

### 本番環境向け推奨設定

```bash
java \
  # メモリ設定
  -Xms4g -Xmx4g \
  -XX:MaxMetaspaceSize=256m \

  # GC設定
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \

  # OOM時のダンプ
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/app/heapdump.hprof \

  # GCログ
  -Xlog:gc*:file=/var/log/app/gc.log:time,uptime,level,tags:filecount=10,filesize=100m \

  # アプリケーション
  -jar app.jar
```

---

## JDK/JRE/JVMの関係

```
┌─────────────────────────────────────────────────────────────┐
│                        JDK                                   │
│  (Java Development Kit)                                      │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │                      JRE                                │ │
│  │  (Java Runtime Environment)                             │ │
│  │                                                         │ │
│  │  ┌─────────────────────────────────────────────────┐   │ │
│  │  │                    JVM                           │   │ │
│  │  │  (Java Virtual Machine)                          │   │ │
│  │  │                                                  │   │ │
│  │  │  - Class Loader                                  │   │ │
│  │  │  - Runtime Data Areas                            │   │ │
│  │  │  - Execution Engine                              │   │ │
│  │  │  - Native Interface                              │   │ │
│  │  └─────────────────────────────────────────────────┘   │ │
│  │                                                         │ │
│  │  + クラスライブラリ (java.lang, java.util, etc.)      │ │
│  │  + デプロイメントツール                                │ │
│  └────────────────────────────────────────────────────────┘ │
│                                                              │
│  + 開発ツール (javac, javadoc, jdb, etc.)                  │
│  + 診断ツール (jconsole, jvisualvm, jmap, etc.)            │
└─────────────────────────────────────────────────────────────┘
```

### Java 11以降の変化

Java 11以降、JREは単独配布されなくなりました。

```bash
# 必要なモジュールだけを含むカスタムランタイムを作成
jlink --module-path $JAVA_HOME/jmods \
      --add-modules java.base,java.logging \
      --output custom-runtime

# サイズ比較
du -sh $JAVA_HOME        # 約300MB（フルJDK）
du -sh custom-runtime    # 約40MB（カスタム）
```

---

## 診断ツール

### JDK付属ツール

| ツール | 用途 | 使用例 |
|-------|------|-------|
| `jps` | Javaプロセス一覧 | `jps -lv` |
| `jinfo` | JVMオプション確認・変更 | `jinfo <pid>` |
| `jstat` | GC・クラス統計 | `jstat -gc <pid> 1000` |
| `jstack` | スレッドダンプ取得 | `jstack <pid>` |
| `jmap` | ヒープダンプ取得 | `jmap -dump:format=b,file=heap.hprof <pid>` |
| `jcmd` | 診断コマンド実行 | `jcmd <pid> VM.flags` |

### 実践例

```bash
# 実行中のJavaプロセス確認
jps -lv

# GC状況をリアルタイム監視（1秒間隔）
jstat -gc <pid> 1000

# ヒープ使用状況
jmap -heap <pid>

# スレッドダンプ
jstack <pid> > threaddump.txt

# ヒープダンプ
jcmd <pid> GC.heap_dump /tmp/heap.hprof
```

---

## まとめ

| 項目 | ポイント |
|-----|---------|
| JVMとは | Javaバイトコードを実行する仮想マシン |
| 実行モデル | インタプリタ + JITコンパイラのハイブリッド |
| JDK選択 | 一般的にはEclipse Temurin、AWS環境ではCorretto |
| オプション | `-X`（非標準）、`-XX:`（詳細）で細かく制御可能 |
| 診断ツール | jps, jstat, jstack, jmap, jcmd で問題調査 |

---

## 次のステップ

- [クラスローディング](jvm-02-classloading.md) - クラスがどのようにロードされるか
- [メモリ管理](jvm-03-memory.md) - JVMのメモリ構造を深く理解
