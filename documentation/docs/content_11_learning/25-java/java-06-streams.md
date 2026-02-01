# Java Stream API

コレクションを宣言的に処理するStream APIを学びます。

---

## Stream とは

Stream は要素の連続的な処理を表す。コレクションを直接変更せず、変換や集約を行う。

```java
List<String> names = List.of("Alice", "Bob", "Charlie", "David");

// 従来のループ
List<String> result = new ArrayList<>();
for (String name : names) {
    if (name.length() > 3) {
        result.add(name.toUpperCase());
    }
}

// Stream API
List<String> result = names.stream()
    .filter(name -> name.length() > 3)
    .map(String::toUpperCase)
    .toList();
// [ALICE, CHARLIE, DAVID]
```

---

## Stream の生成

### コレクションから

```java
List<String> list = List.of("A", "B", "C");
Stream<String> stream = list.stream();

Set<Integer> set = Set.of(1, 2, 3);
Stream<Integer> setStream = set.stream();

Map<String, Integer> map = Map.of("a", 1, "b", 2);
Stream<Map.Entry<String, Integer>> entryStream = map.entrySet().stream();
```

### 配列から

```java
String[] array = {"A", "B", "C"};
Stream<String> stream = Arrays.stream(array);
```

### Stream.of

```java
Stream<String> stream = Stream.of("A", "B", "C");
Stream<Integer> numbers = Stream.of(1, 2, 3, 4, 5);
```

### 無限ストリーム

```java
// iterate: 初期値と関数から生成
Stream<Integer> iterate = Stream.iterate(0, n -> n + 2);  // 0, 2, 4, 6, ...

// generate: Supplierから生成
Stream<Double> randoms = Stream.generate(Math::random);

// 制限をつける
List<Integer> first10 = Stream.iterate(0, n -> n + 2)
    .limit(10)
    .toList();
// [0, 2, 4, 6, 8, 10, 12, 14, 16, 18]
```

### プリミティブ型のStream

```java
IntStream intStream = IntStream.range(0, 10);      // 0-9
IntStream intStream2 = IntStream.rangeClosed(1, 10); // 1-10
LongStream longStream = LongStream.range(0, 100);
DoubleStream doubleStream = DoubleStream.of(1.0, 2.0, 3.0);

// ボクシング
Stream<Integer> boxed = intStream.boxed();
```

---

## 中間操作

中間操作は遅延評価され、終端操作が呼ばれるまで実行されない。

### filter（フィルタリング）

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

List<Integer> evens = numbers.stream()
    .filter(n -> n % 2 == 0)
    .toList();
// [2, 4, 6, 8, 10]
```

### map（変換）

```java
List<String> names = List.of("alice", "bob", "charlie");

List<String> upper = names.stream()
    .map(String::toUpperCase)
    .toList();
// [ALICE, BOB, CHARLIE]

List<Integer> lengths = names.stream()
    .map(String::length)
    .toList();
// [5, 3, 7]
```

### flatMap（フラット化）

```java
List<List<Integer>> nested = List.of(
    List.of(1, 2),
    List.of(3, 4),
    List.of(5, 6)
);

List<Integer> flat = nested.stream()
    .flatMap(List::stream)
    .toList();
// [1, 2, 3, 4, 5, 6]

// 文字列を文字に分解
List<String> words = List.of("Hello", "World");
List<String> chars = words.stream()
    .flatMap(word -> Arrays.stream(word.split("")))
    .toList();
// [H, e, l, l, o, W, o, r, l, d]
```

### distinct（重複除去）

```java
List<Integer> numbers = List.of(1, 2, 2, 3, 3, 3, 4);

List<Integer> unique = numbers.stream()
    .distinct()
    .toList();
// [1, 2, 3, 4]
```

### sorted（ソート）

```java
List<String> names = List.of("Charlie", "Alice", "Bob");

// 自然順序
List<String> sorted = names.stream()
    .sorted()
    .toList();
// [Alice, Bob, Charlie]

// 逆順
List<String> reversed = names.stream()
    .sorted(Comparator.reverseOrder())
    .toList();
// [Charlie, Bob, Alice]

// カスタム
List<String> byLength = names.stream()
    .sorted(Comparator.comparing(String::length))
    .toList();
// [Bob, Alice, Charlie]
```

### limit / skip

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

// 最初の3つ
List<Integer> first3 = numbers.stream()
    .limit(3)
    .toList();
// [1, 2, 3]

// 最初の3つをスキップ
List<Integer> skip3 = numbers.stream()
    .skip(3)
    .toList();
// [4, 5, 6, 7, 8, 9, 10]

// ページング（4番目から3つ）
List<Integer> page = numbers.stream()
    .skip(3)
    .limit(3)
    .toList();
// [4, 5, 6]
```

### peek（デバッグ用）

```java
List<String> result = names.stream()
    .filter(name -> name.length() > 3)
    .peek(name -> System.out.println("Filtered: " + name))
    .map(String::toUpperCase)
    .peek(name -> System.out.println("Mapped: " + name))
    .toList();
```

### takeWhile / dropWhile（Java 9+）

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5, 1, 2);

// 条件を満たす間取得
List<Integer> taken = numbers.stream()
    .takeWhile(n -> n < 4)
    .toList();
// [1, 2, 3]

// 条件を満たす間スキップ
List<Integer> dropped = numbers.stream()
    .dropWhile(n -> n < 4)
    .toList();
// [4, 5, 1, 2]
```

---

## 終端操作

終端操作が呼ばれると、Streamが処理される。

### collect（収集）

```java
// toList（Java 16+）
List<String> list = stream.toList();

// Collectors.toList（可変リスト）
List<String> mutableList = stream.collect(Collectors.toList());

// toSet
Set<String> set = stream.collect(Collectors.toSet());

// toMap
Map<String, Integer> map = users.stream()
    .collect(Collectors.toMap(
        User::getName,      // キー
        User::getAge        // 値
    ));

// toMap（重複キーの処理）
Map<String, Integer> map = users.stream()
    .collect(Collectors.toMap(
        User::getName,
        User::getAge,
        (existing, replacement) -> existing  // 重複時は既存を保持
    ));

// joining
String joined = List.of("A", "B", "C").stream()
    .collect(Collectors.joining(", "));
// "A, B, C"

String joined2 = List.of("A", "B", "C").stream()
    .collect(Collectors.joining(", ", "[", "]"));
// "[A, B, C]"
```

### forEach

```java
names.stream()
    .forEach(System.out::println);

// 順序保証が必要な場合
names.parallelStream()
    .forEachOrdered(System.out::println);
```

### count

```java
long count = names.stream()
    .filter(name -> name.length() > 3)
    .count();
```

### reduce（集約）

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5);

// 合計
int sum = numbers.stream()
    .reduce(0, (a, b) -> a + b);
// 15

// または
int sum2 = numbers.stream()
    .reduce(0, Integer::sum);

// 初期値なし（Optional）
Optional<Integer> sum3 = numbers.stream()
    .reduce(Integer::sum);

// 最大値
Optional<Integer> max = numbers.stream()
    .reduce(Integer::max);
```

### min / max

```java
Optional<String> shortest = names.stream()
    .min(Comparator.comparing(String::length));

Optional<String> longest = names.stream()
    .max(Comparator.comparing(String::length));
```

### findFirst / findAny

```java
Optional<String> first = names.stream()
    .filter(name -> name.startsWith("A"))
    .findFirst();

// 並列処理では findAny が高速
Optional<String> any = names.parallelStream()
    .filter(name -> name.startsWith("A"))
    .findAny();
```

### anyMatch / allMatch / noneMatch

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5);

boolean hasEven = numbers.stream()
    .anyMatch(n -> n % 2 == 0);  // true

boolean allPositive = numbers.stream()
    .allMatch(n -> n > 0);  // true

boolean noNegative = numbers.stream()
    .noneMatch(n -> n < 0);  // true
```

---

## Collectors

### グルーピング

```java
List<User> users = getUsers();

// 年齢でグルーピング
Map<Integer, List<User>> byAge = users.stream()
    .collect(Collectors.groupingBy(User::getAge));

// グルーピング + カウント
Map<Integer, Long> countByAge = users.stream()
    .collect(Collectors.groupingBy(
        User::getAge,
        Collectors.counting()
    ));

// グルーピング + 集約
Map<String, Integer> sumByDept = users.stream()
    .collect(Collectors.groupingBy(
        User::getDepartment,
        Collectors.summingInt(User::getSalary)
    ));
```

### パーティショニング

```java
// 条件で2分割
Map<Boolean, List<User>> partitioned = users.stream()
    .collect(Collectors.partitioningBy(u -> u.getAge() >= 30));

List<User> over30 = partitioned.get(true);
List<User> under30 = partitioned.get(false);
```

### 統計

```java
IntSummaryStatistics stats = users.stream()
    .collect(Collectors.summarizingInt(User::getAge));

stats.getCount();    // 件数
stats.getSum();      // 合計
stats.getAverage();  // 平均
stats.getMin();      // 最小
stats.getMax();      // 最大
```

### カスタムCollector

```java
// Collectors.toCollection で特定のコレクション型を指定
LinkedList<String> linkedList = names.stream()
    .collect(Collectors.toCollection(LinkedList::new));

TreeSet<String> treeSet = names.stream()
    .collect(Collectors.toCollection(TreeSet::new));
```

---

## プリミティブStream

ボクシング/アンボクシングのオーバーヘッドを避ける。

```java
// IntStream
int sum = IntStream.range(1, 101).sum();  // 1から100の合計

double avg = IntStream.of(1, 2, 3, 4, 5)
    .average()
    .orElse(0.0);

// mapToInt
int totalAge = users.stream()
    .mapToInt(User::getAge)
    .sum();

// 配列に変換
int[] array = IntStream.range(0, 10).toArray();

// ボクシング
List<Integer> boxed = IntStream.range(0, 10)
    .boxed()
    .toList();
```

---

## 並列Stream

### 基本

```java
// parallelStream で並列処理
List<String> result = names.parallelStream()
    .filter(name -> name.length() > 3)
    .map(String::toUpperCase)
    .toList();

// stream を parallel に変換
List<String> result2 = names.stream()
    .parallel()
    .filter(name -> name.length() > 3)
    .toList();
```

### 注意点

```java
// NG: 状態を共有する副作用のある操作
List<String> result = new ArrayList<>();
names.parallelStream()
    .forEach(result::add);  // スレッドセーフでない！

// OK: collect を使う
List<String> result = names.parallelStream()
    .collect(Collectors.toList());

// OK: toList を使う
List<String> result = names.parallelStream()
    .toList();
```

### いつ使うか

- 要素数が多い（数千以上）
- 各要素の処理が重い
- 順序が重要でない
- スレッドセーフな操作のみ

---

## 実践的なパターン

### Optional との組み合わせ

```java
// nullを含む可能性のあるリスト
List<String> items = Arrays.asList("A", null, "B", null, "C");

List<String> nonNull = items.stream()
    .filter(Objects::nonNull)
    .toList();

// Optional のリストから値を取り出す
List<Optional<String>> optionals = getOptionals();
List<String> values = optionals.stream()
    .filter(Optional::isPresent)
    .map(Optional::get)
    .toList();

// Java 9+ の flatMap(Optional::stream)
List<String> values2 = optionals.stream()
    .flatMap(Optional::stream)
    .toList();
```

### ネストしたオブジェクトの処理

```java
// 全ユーザーの全メールアドレスを取得
List<String> allEmails = users.stream()
    .flatMap(user -> user.getEmails().stream())
    .distinct()
    .toList();

// 部署ごとに最年長のユーザーを取得
Map<String, Optional<User>> oldestByDept = users.stream()
    .collect(Collectors.groupingBy(
        User::getDepartment,
        Collectors.maxBy(Comparator.comparing(User::getAge))
    ));
```

### 条件付き処理

```java
public List<User> findUsers(String name, Integer minAge, Boolean active) {
    Stream<User> stream = users.stream();

    if (name != null) {
        stream = stream.filter(u -> u.getName().contains(name));
    }
    if (minAge != null) {
        stream = stream.filter(u -> u.getAge() >= minAge);
    }
    if (active != null) {
        stream = stream.filter(u -> u.isActive() == active);
    }

    return stream.toList();
}
```

---

## まとめ

| 操作タイプ | メソッド | 説明 |
|----------|---------|------|
| 中間操作 | filter | 条件でフィルタリング |
| 中間操作 | map | 要素を変換 |
| 中間操作 | flatMap | ネストをフラット化 |
| 中間操作 | sorted | ソート |
| 中間操作 | distinct | 重複除去 |
| 終端操作 | collect | コレクションに収集 |
| 終端操作 | forEach | 各要素に処理 |
| 終端操作 | reduce | 集約 |
| 終端操作 | count | カウント |
| 終端操作 | findFirst | 最初の要素 |
| 終端操作 | anyMatch | 条件に一致する要素があるか |

---

## 次のステップ

- [ラムダ式](java-07-lambda.md) - 関数型インターフェース
