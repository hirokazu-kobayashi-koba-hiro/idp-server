# Java言語の特徴

Javaはどんな言語で、なぜ選ばれるのかを学びます。

---

## Javaとは

Javaは1995年にSun Microsystems（現Oracle）が開発したプログラミング言語です。「Write Once, Run Anywhere（一度書けばどこでも動く）」をスローガンに、プラットフォーム非依存を実現しました。

現在も世界で最も広く使われている言語の一つで、特にエンタープライズシステム、Androidアプリ、大規模バックエンドで採用されています。

---

## Javaの特徴

### 1. プラットフォーム非依存

JavaコードはJVM（Java Virtual Machine）上で動作するため、Windows、macOS、Linuxなど異なるOSで同じプログラムが動きます。

```
Java ソースコード (.java)
        ↓ コンパイル
バイトコード (.class)
        ↓ JVMが実行
Windows / macOS / Linux
```

### 2. 静的型付け

変数の型がコンパイル時に決まります。型の間違いを実行前に検出できます。

```java
// Java（静的型付け）
String name = "Alice";
name = 123;  // コンパイルエラー！

// Python（動的型付け）
name = "Alice"
name = 123  # エラーにならない（実行時まで問題が分からない）
```

### 3. オブジェクト指向

すべてがクラスとオブジェクトで構成されます。カプセル化、継承、ポリモーフィズムをサポートします。

```java
public class User {
    private String name;  // カプセル化

    public String getName() {
        return name;
    }
}
```

### 4. ガベージコレクション

メモリ管理が自動化されています。C/C++のような手動のメモリ解放が不要です。

```java
// Java: 自動でメモリ解放
User user = new User();
user = null;  // GCが後で回収

// C++: 手動でメモリ解放が必要
User* user = new User();
delete user;  // 忘れるとメモリリーク
```

### 5. 豊富な標準ライブラリ

コレクション、I/O、ネットワーク、日付処理など、多くの機能が標準で提供されています。

```java
// 標準ライブラリだけで多くのことができる
List<String> list = new ArrayList<>();
Map<String, Integer> map = new HashMap<>();
LocalDateTime now = LocalDateTime.now();
HttpClient client = HttpClient.newHttpClient();
```

### 6. 後方互換性

古いJavaコードは新しいJVMでも動作します。20年以上前に書かれたコードが今でも動くことが多いです。

---

## 他言語との比較

| 特徴 | Java | Python | JavaScript | C++ |
|-----|------|--------|------------|-----|
| 型付け | 静的 | 動的 | 動的 | 静的 |
| メモリ管理 | GC | GC | GC | 手動 |
| 実行方式 | JVMバイトコード | インタプリタ | インタプリタ | ネイティブ |
| 実行速度 | 速い | 遅い | 中程度 | 最速 |
| 学習曲線 | 中程度 | 緩やか | 緩やか | 急 |
| 主な用途 | エンタープライズ、Android | データ分析、AI、Web | フロントエンド、Node.js | システム、ゲーム |

### Pythonとの違い

```python
# Python: 簡潔だが型がない
def greet(name):
    return f"Hello, {name}"

greet(123)  # 実行時まで問題が分からない
```

```java
// Java: 冗長だが型安全
public String greet(String name) {
    return "Hello, " + name;
}

greet(123);  // コンパイルエラー！
```

### JavaScriptとの違い

```javascript
// JavaScript: 柔軟だが予測しづらい
"5" + 3     // "53" (文字列連結)
"5" - 3     // 2 (数値演算)
[] + {}     // "[object Object]"
```

```java
// Java: 厳格で予測可能
"5" + 3     // "53" (明示的な変換)
// "5" - 3  // コンパイルエラー！
```

---

## Javaのバージョン

Javaは継続的に進化しています。現在は6ヶ月ごとに新バージョンがリリースされます。

### 主要バージョン

| バージョン | リリース | 主な機能 |
|-----------|---------|---------|
| Java 8 (LTS) | 2014 | ラムダ式、Stream API、Optional |
| Java 11 (LTS) | 2018 | HTTPクライアント、var（ローカル変数） |
| Java 17 (LTS) | 2021 | Sealed Classes、Pattern Matching（プレビュー） |
| Java 21 (LTS) | 2023 | Virtual Threads、Record Patterns、Pattern Matching完成 |

**LTS（Long-Term Support）**: 長期サポート版。本番環境ではLTSを使うことが推奨されます。

### Java 8 → Java 21 の進化

```java
// Java 8: ラムダ式とStream API
List<String> names = users.stream()
    .filter(u -> u.getAge() > 20)
    .map(User::getName)
    .collect(Collectors.toList());

// Java 10: var（型推論）
var names = users.stream()
    .filter(u -> u.getAge() > 20)
    .map(User::getName)
    .toList();

// Java 16: Record
record User(String name, int age) {}

// Java 17: Sealed Classes
sealed interface Shape permits Circle, Rectangle {}

// Java 21: Pattern Matching + Virtual Threads
String describe(Shape shape) {
    return switch (shape) {
        case Circle(var r) -> "Circle: " + r;
        case Rectangle(var w, var h) -> "Rectangle: " + w + "x" + h;
    };
}
```

---

## Javaが使われている領域

### エンタープライズシステム

銀行、保険、政府システムなど、信頼性が求められる大規模システムで広く採用されています。

- 型安全性による堅牢性
- 長期サポート
- 豊富なエンタープライズフレームワーク（Spring等）

### Androidアプリ

AndroidアプリはJava（またはKotlin）で開発されます。

### バックエンドAPI

Spring Boot などのフレームワークでREST APIを構築するのに広く使われています。

### ビッグデータ

Hadoop、Spark、Kafka などのビッグデータ基盤はJavaで書かれています。

---

## Javaの設計思想

### 「Simple, Object-Oriented, and Familiar」

Javaは C++ の複雑さを排除しつつ、オブジェクト指向を採用しました。

**排除されたもの:**
- ポインタ演算
- 手動メモリ管理
- 多重継承（インターフェースで代替）
- 演算子オーバーロード

### 「Robust and Secure」

- 強い型付けでバグを防ぐ
- 配列の境界チェック
- NullPointerExceptionでのクラッシュ（不正なメモリアクセスより安全）

### 「Architecture-Neutral and Portable」

JVMがプラットフォームの違いを吸収し、同じバイトコードがどこでも動きます。

---

## Javaの欠点

公平のため、Javaの欠点も挙げます。

### 1. 冗長な構文

```java
// Java: 冗長
public class HelloWorld {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}

// Python: 簡潔
print("Hello, World!")
```

### 2. 起動時間

JVMの起動にオーバーヘッドがあります（GraalVM Native Imageで改善可能）。

### 3. メモリ使用量

GCとJVMのオーバーヘッドにより、メモリ使用量が多くなりがちです。

### 4. 関数型プログラミングの後発対応

ラムダ式はJava 8（2014年）まで導入されませんでした。

---

## まとめ

| 特徴 | 説明 |
|-----|------|
| プラットフォーム非依存 | JVM上で動作、どのOSでも同じコード |
| 静的型付け | コンパイル時に型エラーを検出 |
| オブジェクト指向 | クラスベースの設計 |
| ガベージコレクション | 自動メモリ管理 |
| 豊富な標準ライブラリ | 多くの機能が標準で提供 |
| 後方互換性 | 古いコードが新しいJVMで動く |
| エンタープライズ向け | 大規模システムで広く採用 |

---

## 次のステップ

- [構文基礎](java-01-syntax.md) - Javaの基本構文を学ぶ
