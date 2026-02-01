# Java 構文基礎

Javaの基本的な構文を学びます。

---

## この章で学ぶこと

Javaはオブジェクト指向の静的型付け言語です。この章では以下を学びます。

- **クラスとオブジェクト**: Javaの基本単位
- **変数と型**: プリミティブ型と参照型
- **演算子**: 算術、比較、論理演算
- **制御構文**: if, switch, for, while
- **文字列操作**: 文字列の基本操作
- **アクセス修飾子**: public, private, protected
- **static と final**: クラス変数と定数

他言語の経験があれば、差分を確認しながら読み進めてください。

---

## クラスとオブジェクト

### クラスの定義

```java
public class User {
    // フィールド（インスタンス変数）
    private String name;
    private int age;

    // コンストラクタ
    public User(String name, int age) {
        this.name = name;
        this.age = age;
    }

    // メソッド
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
```

### オブジェクトの生成

```java
User user = new User("Alice", 25);
String name = user.getName();  // "Alice"
```

---

## 変数と型

### プリミティブ型

```java
// 整数型
byte b = 127;           // 8ビット (-128 ~ 127)
short s = 32767;        // 16ビット
int i = 2147483647;     // 32ビット（最もよく使う）
long l = 9223372036854775807L;  // 64ビット（Lサフィックス）

// 浮動小数点型
float f = 3.14f;        // 32ビット（fサフィックス）
double d = 3.14159;     // 64ビット（デフォルト）

// 論理型
boolean flag = true;    // true または false

// 文字型
char c = 'A';           // 16ビットUnicode文字
```

### 参照型

```java
// 文字列
String text = "Hello, World!";

// 配列
int[] numbers = {1, 2, 3, 4, 5};
String[] names = new String[10];

// オブジェクト
User user = new User("Bob", 30);
```

### 型推論（var）

Java 10以降、ローカル変数で`var`が使える。

```java
var name = "Alice";           // String型と推論
var numbers = List.of(1, 2, 3);  // List<Integer>と推論
var user = new User("Bob", 30);  // User型と推論

// 注意：フィールドやメソッドの戻り値には使えない
// 注意：nullの代入では型が推論できない
// var x = null;  // コンパイルエラー
```

---

## 演算子

### 算術演算子

```java
int a = 10, b = 3;

int sum = a + b;      // 13
int diff = a - b;     // 7
int product = a * b;  // 30
int quotient = a / b; // 3（整数除算）
int remainder = a % b; // 1（剰余）

// 複合代入
a += 5;  // a = a + 5
a *= 2;  // a = a * 2

// インクリメント/デクリメント
int x = 5;
x++;  // 6（後置）
++x;  // 7（前置）
```

### 比較演算子

```java
int a = 10, b = 20;

a == b   // false（等しい）
a != b   // true（等しくない）
a < b    // true（より小さい）
a <= b   // true（以下）
a > b    // false（より大きい）
a >= b   // false（以上）

// オブジェクトの比較
String s1 = "hello";
String s2 = "hello";
s1 == s2      // 参照の比較（同じオブジェクトか）
s1.equals(s2) // 値の比較（内容が同じか）★重要
```

### 論理演算子

```java
boolean a = true, b = false;

a && b   // false（AND、短絡評価）
a || b   // true（OR、短絡評価）
!a       // false（NOT）

// 短絡評価
// &&: 左がfalseなら右は評価されない
// ||: 左がtrueなら右は評価されない
```

### 三項演算子

```java
int age = 20;
String status = age >= 18 ? "成人" : "未成年";
```

---

## 制御構文

### if-else

```java
int score = 85;

if (score >= 90) {
    System.out.println("優");
} else if (score >= 70) {
    System.out.println("良");
} else if (score >= 50) {
    System.out.println("可");
} else {
    System.out.println("不可");
}
```

### switch式（Java 14以降）

```java
// 従来のswitch文
switch (day) {
    case MONDAY:
    case TUESDAY:
        System.out.println("平日前半");
        break;
    case FRIDAY:
        System.out.println("華金");
        break;
    default:
        System.out.println("その他");
}

// switch式（推奨）
String result = switch (day) {
    case MONDAY, TUESDAY -> "平日前半";
    case FRIDAY -> "華金";
    default -> "その他";
};

// 複数行の処理
String message = switch (status) {
    case SUCCESS -> {
        log("成功");
        yield "処理が完了しました";
    }
    case ERROR -> {
        log("エラー");
        yield "エラーが発生しました";
    }
    default -> "不明な状態";
};
```

### for

```java
// 基本的なfor
for (int i = 0; i < 10; i++) {
    System.out.println(i);
}

// 拡張for（for-each）
String[] names = {"Alice", "Bob", "Charlie"};
for (String name : names) {
    System.out.println(name);
}

// List のイテレーション
List<String> list = List.of("A", "B", "C");
for (String item : list) {
    System.out.println(item);
}
```

### while / do-while

```java
// while
int count = 0;
while (count < 5) {
    System.out.println(count);
    count++;
}

// do-while（最低1回は実行）
int n = 0;
do {
    System.out.println(n);
    n++;
} while (n < 5);
```

### break / continue

```java
// break: ループを抜ける
for (int i = 0; i < 10; i++) {
    if (i == 5) break;
    System.out.println(i);  // 0, 1, 2, 3, 4
}

// continue: 次のイテレーションへ
for (int i = 0; i < 10; i++) {
    if (i % 2 == 0) continue;
    System.out.println(i);  // 1, 3, 5, 7, 9
}

// ラベル付きbreak（ネストしたループを抜ける）
outer:
for (int i = 0; i < 3; i++) {
    for (int j = 0; j < 3; j++) {
        if (i == 1 && j == 1) break outer;
        System.out.println(i + ", " + j);
    }
}
```

---

## 文字列操作

### 文字列の基本

```java
String s = "Hello, World!";

// 長さ
int len = s.length();  // 13

// 文字の取得
char c = s.charAt(0);  // 'H'

// 部分文字列
String sub = s.substring(0, 5);  // "Hello"

// 検索
int index = s.indexOf("World");  // 7
boolean contains = s.contains("World");  // true

// 置換
String replaced = s.replace("World", "Java");  // "Hello, Java!"

// 大文字・小文字
String upper = s.toUpperCase();  // "HELLO, WORLD!"
String lower = s.toLowerCase();  // "hello, world!"

// 空白除去
String trimmed = "  hello  ".trim();  // "hello"
String stripped = "  hello  ".strip();  // "hello"（Java 11以降、Unicode対応）

// 分割
String[] parts = "a,b,c".split(",");  // ["a", "b", "c"]

// 結合
String joined = String.join("-", "a", "b", "c");  // "a-b-c"
```

### 文字列の連結

```java
// + 演算子（少量なら問題なし）
String s1 = "Hello" + " " + "World";

// StringBuilder（ループ内での連結に推奨）
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 100; i++) {
    sb.append(i).append(", ");
}
String result = sb.toString();

// String.format
String formatted = String.format("Name: %s, Age: %d", "Alice", 25);

// テキストブロック（Java 15以降）
String json = """
    {
        "name": "Alice",
        "age": 25
    }
    """;
```

### 文字列の比較

```java
String s1 = "hello";
String s2 = "hello";
String s3 = new String("hello");

// == は参照の比較（同じオブジェクトか）
s1 == s2   // true（文字列リテラルはプールされる）
s1 == s3   // false（新しいオブジェクト）

// equals は値の比較（★常にこちらを使う）
s1.equals(s2)  // true
s1.equals(s3)  // true

// 大文字小文字を無視
"Hello".equalsIgnoreCase("hello")  // true

// null安全な比較
Objects.equals(s1, s2)  // s1がnullでもNullPointerExceptionにならない
```

---

## 配列

### 配列の宣言と初期化

```java
// 宣言と初期化
int[] numbers = new int[5];  // 要素数5、デフォルト値0
int[] primes = {2, 3, 5, 7, 11};  // リテラル初期化

// 多次元配列
int[][] matrix = new int[3][3];
int[][] grid = {
    {1, 2, 3},
    {4, 5, 6},
    {7, 8, 9}
};
```

### 配列の操作

```java
int[] arr = {5, 2, 8, 1, 9};

// 長さ
int len = arr.length;  // 5

// アクセス
int first = arr[0];    // 5
arr[0] = 10;           // 値の変更

// ループ
for (int i = 0; i < arr.length; i++) {
    System.out.println(arr[i]);
}

// for-each
for (int num : arr) {
    System.out.println(num);
}

// ソート
Arrays.sort(arr);  // [1, 2, 5, 8, 9]

// コピー
int[] copy = Arrays.copyOf(arr, arr.length);

// 検索（ソート済み配列）
int index = Arrays.binarySearch(arr, 5);

// 配列をListに変換
List<Integer> list = Arrays.asList(1, 2, 3);  // 固定サイズ
List<Integer> mutableList = new ArrayList<>(Arrays.asList(1, 2, 3));  // 可変
```

---

## アクセス修飾子

```java
public class Example {
    public String publicField;      // どこからでもアクセス可能
    protected String protectedField; // 同パッケージ + サブクラス
    String packageField;            // 同パッケージのみ（デフォルト）
    private String privateField;    // このクラス内のみ
}
```

| 修飾子 | 同クラス | 同パッケージ | サブクラス | 他パッケージ |
|-------|---------|------------|----------|------------|
| public | ○ | ○ | ○ | ○ |
| protected | ○ | ○ | ○ | × |
| (default) | ○ | ○ | × | × |
| private | ○ | × | × | × |

---

## static

### staticフィールド

```java
public class Counter {
    // staticフィールド（クラス変数）
    private static int count = 0;

    // インスタンスフィールド
    private String name;

    public Counter(String name) {
        this.name = name;
        count++;  // 全インスタンスで共有
    }

    public static int getCount() {
        return count;
    }
}

// 使用
Counter c1 = new Counter("A");
Counter c2 = new Counter("B");
Counter.getCount();  // 2
```

### staticメソッド

```java
public class MathUtils {
    // staticメソッド（インスタンス不要で呼び出せる）
    public static int add(int a, int b) {
        return a + b;
    }

    public static int max(int a, int b) {
        return a > b ? a : b;
    }
}

// 使用
int sum = MathUtils.add(1, 2);  // 3
int max = MathUtils.max(5, 3);  // 5
```

### staticインポート

```java
import static java.lang.Math.*;
import static java.util.Objects.requireNonNull;

// Math.をつけずに呼び出せる
double result = sqrt(pow(3, 2) + pow(4, 2));  // 5.0
```

---

## final

```java
// final変数（定数）
final int MAX_SIZE = 100;
// MAX_SIZE = 200;  // コンパイルエラー

// final参照（参照先は変更不可、中身は変更可能）
final List<String> list = new ArrayList<>();
list.add("A");  // OK
// list = new ArrayList<>();  // コンパイルエラー

// finalメソッド（オーバーライド不可）
public final void doSomething() { }

// finalクラス（継承不可）
public final class ImmutableClass { }
```

---

## 定数の定義

```java
public class Constants {
    // 定数は static final で定義
    public static final int MAX_RETRY = 3;
    public static final String DEFAULT_CHARSET = "UTF-8";

    // privateコンストラクタでインスタンス化を防ぐ
    private Constants() {
        throw new AssertionError("Cannot instantiate");
    }
}

// 使用
int max = Constants.MAX_RETRY;
```

---

## null と Optional

### nullの問題

```java
String name = null;
int length = name.length();  // NullPointerException!

// nullチェック（従来の方法）
if (name != null) {
    int length = name.length();
}
```

### Optional（推奨）

```java
// Optionalの生成
Optional<String> opt1 = Optional.of("hello");      // 非null
Optional<String> opt2 = Optional.ofNullable(null); // nullの可能性あり
Optional<String> opt3 = Optional.empty();          // 空

// 値の取得
opt1.get();           // "hello"（空の場合は例外）
opt1.orElse("default");  // 値があればその値、なければデフォルト
opt1.orElseThrow();   // 値があればその値、なければ例外

// 値の存在チェック
if (opt1.isPresent()) {
    System.out.println(opt1.get());
}

// より関数型のスタイル（推奨）
opt1.ifPresent(value -> System.out.println(value));

// 変換
Optional<Integer> length = opt1.map(String::length);

// フィルタ
Optional<String> filtered = opt1.filter(s -> s.length() > 3);
```

---

## まとめ

| 概念 | ポイント |
|-----|---------|
| 型 | プリミティブ型と参照型、varによる型推論 |
| 文字列比較 | `==`ではなく`equals()`を使う |
| switch | Java 14以降のswitch式が便利 |
| 配列 | 固定長、コレクションの方が柔軟 |
| アクセス修飾子 | できるだけprivateに |
| static | インスタンス不要で使える |
| final | 変更不可を明示 |
| null | Optionalで安全に扱う |

---

## 次のステップ

- [オブジェクト指向](java-02-oop.md) - 継承、インターフェース
