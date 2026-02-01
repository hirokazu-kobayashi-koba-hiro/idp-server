# Java コレクション

Javaのコレクションフレームワークを学びます。

---

## なぜコレクションフレームワークか

配列は固定長で、要素の追加・削除が面倒です。

```java
// 配列: サイズ固定、操作が面倒
String[] array = new String[3];
array[0] = "A";
// 要素を追加するには新しい配列を作って全コピー...

// コレクション: 可変長、便利なメソッド
List<String> list = new ArrayList<>();
list.add("A");
list.add("B");
list.remove("A");  // 簡単に削除
```

コレクションフレームワークは、用途に応じたデータ構造（List, Set, Map, Queue）を提供します。

---

## コレクションの階層

```
                    Iterable
                       │
                   Collection
                  /    │     \
               List   Set    Queue
                │      │        │
           ArrayList  HashSet  PriorityQueue
           LinkedList TreeSet  ArrayDeque
                      LinkedHashSet

                     Map
                      │
                   HashMap
                   TreeMap
                   LinkedHashMap
```

---

## List

順序を保持し、重複を許可するコレクション。

### ArrayList

```java
// 作成
List<String> list = new ArrayList<>();
List<String> list2 = new ArrayList<>(100);  // 初期容量指定

// 不変リスト（Java 9以降）
List<String> immutable = List.of("A", "B", "C");

// 要素の追加
list.add("apple");
list.add("banana");
list.add(0, "cherry");  // インデックス指定

// 要素の取得
String first = list.get(0);
String last = list.get(list.size() - 1);

// 要素の更新
list.set(0, "apricot");

// 要素の削除
list.remove(0);           // インデックスで削除
list.remove("banana");    // 値で削除
list.removeIf(s -> s.startsWith("a"));  // 条件で削除

// サイズ
int size = list.size();
boolean empty = list.isEmpty();

// 検索
boolean contains = list.contains("apple");
int index = list.indexOf("apple");   // 見つからなければ-1

// イテレーション
for (String item : list) {
    System.out.println(item);
}

// forEachメソッド
list.forEach(System.out::println);
```

### LinkedList

```java
// 両端への追加・削除が高速（O(1)）
LinkedList<String> linkedList = new LinkedList<>();

linkedList.addFirst("first");
linkedList.addLast("last");
linkedList.removeFirst();
linkedList.removeLast();

// Deque（両端キュー）としても使える
linkedList.push("pushed");   // 先頭に追加
String popped = linkedList.pop();  // 先頭から削除
```

### ArrayList vs LinkedList

| 操作 | ArrayList | LinkedList |
|-----|-----------|------------|
| ランダムアクセス（get） | O(1) ★高速 | O(n) |
| 末尾への追加 | O(1) ★高速 | O(1) ★高速 |
| 先頭への追加 | O(n) | O(1) ★高速 |
| 中間への挿入 | O(n) | O(n) |
| メモリ効率 | ★高効率 | オーバーヘッドあり |

**結論**: ほとんどの場合は `ArrayList` を使う。

---

## Set

重複を許可しないコレクション。

### HashSet

```java
// 作成
Set<String> set = new HashSet<>();
Set<String> immutable = Set.of("A", "B", "C");

// 追加（重複は無視される）
set.add("apple");
set.add("banana");
set.add("apple");  // 追加されない、falseを返す

// サイズ
set.size();  // 2

// 存在確認
boolean contains = set.contains("apple");

// 削除
set.remove("apple");

// イテレーション（順序は保証されない）
for (String item : set) {
    System.out.println(item);
}
```

### LinkedHashSet

```java
// 挿入順序を保持
Set<String> linkedSet = new LinkedHashSet<>();
linkedSet.add("C");
linkedSet.add("A");
linkedSet.add("B");

// イテレーション → C, A, B の順序
```

### TreeSet

```java
// ソートされた状態を維持
Set<String> treeSet = new TreeSet<>();
treeSet.add("banana");
treeSet.add("apple");
treeSet.add("cherry");

// イテレーション → apple, banana, cherry の順序（自然順序）

// カスタムComparatorでソート
Set<String> reverseSet = new TreeSet<>(Comparator.reverseOrder());

// NavigableSetのメソッド
TreeSet<Integer> numbers = new TreeSet<>(List.of(1, 3, 5, 7, 9));
numbers.floor(4);    // 4以下で最大 → 3
numbers.ceiling(4);  // 4以上で最小 → 5
numbers.lower(5);    // 5未満で最大 → 3
numbers.higher(5);   // 5より大で最小 → 7
```

### Set の比較

| 実装 | 順序 | 性能 | 用途 |
|-----|------|------|------|
| HashSet | なし | O(1) | 一般的な用途 |
| LinkedHashSet | 挿入順 | O(1) | 順序が必要 |
| TreeSet | ソート順 | O(log n) | ソート済みが必要 |

---

## Map

キーと値のペアを格納するコレクション。

### HashMap

```java
// 作成
Map<String, Integer> map = new HashMap<>();
Map<String, Integer> immutable = Map.of("a", 1, "b", 2);

// 追加
map.put("apple", 100);
map.put("banana", 200);

// 取得
Integer price = map.get("apple");      // 100
Integer unknown = map.get("unknown");  // null

// デフォルト値付き取得
Integer value = map.getOrDefault("unknown", 0);  // 0

// 更新（存在しない場合のみ追加）
map.putIfAbsent("apple", 150);  // 変更されない（既に存在）

// 計算して更新
map.compute("apple", (k, v) -> v == null ? 1 : v + 1);
map.computeIfAbsent("cherry", k -> 300);
map.computeIfPresent("apple", (k, v) -> v * 2);

// マージ
map.merge("apple", 50, Integer::sum);  // 既存値 + 50

// 削除
map.remove("apple");
map.remove("banana", 200);  // キーと値の両方が一致する場合のみ

// サイズ・存在確認
int size = map.size();
boolean hasKey = map.containsKey("apple");
boolean hasValue = map.containsValue(100);

// イテレーション
for (Map.Entry<String, Integer> entry : map.entrySet()) {
    System.out.println(entry.getKey() + " = " + entry.getValue());
}

// forEachメソッド
map.forEach((k, v) -> System.out.println(k + " = " + v));

// キー・値の取得
Set<String> keys = map.keySet();
Collection<Integer> values = map.values();
```

### LinkedHashMap

```java
// 挿入順序を保持
Map<String, Integer> linkedMap = new LinkedHashMap<>();

// アクセス順序を保持（LRUキャッシュに使える）
Map<String, Integer> lruCache = new LinkedHashMap<>(16, 0.75f, true);
```

### TreeMap

```java
// キーでソートされた状態を維持
Map<String, Integer> treeMap = new TreeMap<>();

// NavigableMapのメソッド
TreeMap<Integer, String> numbers = new TreeMap<>();
numbers.put(1, "one");
numbers.put(3, "three");
numbers.put(5, "five");

numbers.floorKey(4);     // 4以下で最大のキー → 3
numbers.ceilingKey(4);   // 4以上で最小のキー → 5
numbers.subMap(1, 4);    // 1以上4未満の部分Map
```

### Map の比較

| 実装 | 順序 | 性能 | 用途 |
|-----|------|------|------|
| HashMap | なし | O(1) | 一般的な用途 |
| LinkedHashMap | 挿入/アクセス順 | O(1) | 順序が必要、LRUキャッシュ |
| TreeMap | キーのソート順 | O(log n) | ソート済みが必要 |

---

## Queue と Deque

### Queue（FIFO）

```java
Queue<String> queue = new LinkedList<>();

// 追加
queue.offer("first");  // 追加（失敗時false）
queue.add("second");   // 追加（失敗時例外）

// 取得・削除
String head = queue.poll();  // 先頭を取得して削除（空ならnull）
String head2 = queue.remove();  // 先頭を取得して削除（空なら例外）

// 参照のみ
String peek = queue.peek();  // 先頭を参照（空ならnull）
String element = queue.element();  // 先頭を参照（空なら例外）
```

### Deque（両端キュー）

```java
Deque<String> deque = new ArrayDeque<>();

// 先頭に追加
deque.addFirst("first");
deque.offerFirst("zero");

// 末尾に追加
deque.addLast("last");
deque.offerLast("final");

// 先頭から取得・削除
String first = deque.pollFirst();
String first2 = deque.removeFirst();

// 末尾から取得・削除
String last = deque.pollLast();
String last2 = deque.removeLast();

// スタックとして使用
deque.push("pushed");  // addFirstと同じ
String popped = deque.pop();  // removeFirstと同じ
```

### PriorityQueue

```java
// 優先度付きキュー（最小値が先頭）
PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.offer(3);
pq.offer(1);
pq.offer(2);

pq.poll();  // 1
pq.poll();  // 2
pq.poll();  // 3

// カスタムComparator
PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Comparator.reverseOrder());
```

---

## 不変コレクション

### Java 9+ のファクトリメソッド

```java
// 不変List
List<String> list = List.of("A", "B", "C");
// list.add("D");  // UnsupportedOperationException

// 不変Set
Set<String> set = Set.of("A", "B", "C");

// 不変Map
Map<String, Integer> map = Map.of("a", 1, "b", 2);
Map<String, Integer> map2 = Map.ofEntries(
    Map.entry("a", 1),
    Map.entry("b", 2),
    Map.entry("c", 3)
);
```

### コピーして不変化

```java
List<String> original = new ArrayList<>(List.of("A", "B", "C"));

// 不変コピー（Java 10+）
List<String> copy = List.copyOf(original);

// Collections.unmodifiableXxx（ビュー、元が変わると影響を受ける）
List<String> view = Collections.unmodifiableList(original);
original.add("D");  // viewにも反映される！
```

---

## コレクションの変換

### List ↔ Set

```java
List<String> list = List.of("A", "B", "A", "C");

// List → Set（重複除去）
Set<String> set = new HashSet<>(list);

// Set → List
List<String> list2 = new ArrayList<>(set);
```

### 配列 ↔ List

```java
// 配列 → List
String[] array = {"A", "B", "C"};
List<String> list = Arrays.asList(array);  // 固定サイズ
List<String> mutableList = new ArrayList<>(Arrays.asList(array));

// List → 配列
String[] array2 = list.toArray(new String[0]);
String[] array3 = list.toArray(String[]::new);  // Java 11+
```

### Map のキー・値をList/Setに

```java
Map<String, Integer> map = Map.of("a", 1, "b", 2, "c", 3);

Set<String> keys = map.keySet();
Collection<Integer> values = map.values();
List<Integer> valueList = new ArrayList<>(map.values());
```

---

## ソート

### List のソート

```java
List<String> list = new ArrayList<>(List.of("banana", "apple", "cherry"));

// 自然順序でソート
Collections.sort(list);
list.sort(Comparator.naturalOrder());

// 逆順
list.sort(Comparator.reverseOrder());

// カスタムComparator
list.sort(Comparator.comparing(String::length));

// 複数条件
List<User> users = new ArrayList<>();
users.sort(Comparator
    .comparing(User::getAge)
    .thenComparing(User::getName));

// nullを末尾に
users.sort(Comparator.comparing(User::getName, Comparator.nullsLast(String::compareTo)));
```

### Comparableの実装

```java
public class User implements Comparable<User> {
    private String name;
    private int age;

    @Override
    public int compareTo(User other) {
        // 年齢で比較、同じなら名前で比較
        int result = Integer.compare(this.age, other.age);
        if (result == 0) {
            result = this.name.compareTo(other.name);
        }
        return result;
    }
}
```

---

## 検索

### List での検索

```java
List<String> list = List.of("apple", "banana", "cherry");

// 線形検索
boolean contains = list.contains("banana");  // O(n)
int index = list.indexOf("banana");  // O(n)

// 二分探索（ソート済みリストのみ）
List<Integer> sorted = List.of(1, 3, 5, 7, 9);
int idx = Collections.binarySearch(sorted, 5);  // O(log n)
```

### Stream での検索

```java
List<User> users = getUsers();

// 条件に一致する最初の要素
Optional<User> found = users.stream()
    .filter(u -> u.getName().equals("Alice"))
    .findFirst();

// 条件に一致する要素が存在するか
boolean exists = users.stream()
    .anyMatch(u -> u.getAge() > 30);
```

---

## スレッドセーフなコレクション

### 同期化ラッパー

```java
// Collections.synchronizedXxx
List<String> syncList = Collections.synchronizedList(new ArrayList<>());
Set<String> syncSet = Collections.synchronizedSet(new HashSet<>());
Map<String, Integer> syncMap = Collections.synchronizedMap(new HashMap<>());

// イテレーション時は手動で同期
synchronized (syncList) {
    for (String item : syncList) {
        // ...
    }
}
```

### Concurrent コレクション（推奨）

```java
// ConcurrentHashMap
ConcurrentMap<String, Integer> map = new ConcurrentHashMap<>();
map.put("key", 1);
map.computeIfAbsent("key", k -> expensiveComputation());

// CopyOnWriteArrayList（読み取りが多い場合）
List<String> list = new CopyOnWriteArrayList<>();

// ConcurrentLinkedQueue
Queue<String> queue = new ConcurrentLinkedQueue<>();

// BlockingQueue（生産者-消費者パターン）
BlockingQueue<String> blockingQueue = new LinkedBlockingQueue<>();
blockingQueue.put("item");     // 空きができるまでブロック
String item = blockingQueue.take();  // 要素が来るまでブロック
```

---

## パフォーマンスのヒント

### 初期容量の指定

```java
// 要素数が分かっている場合は初期容量を指定
List<String> list = new ArrayList<>(1000);
Set<String> set = new HashSet<>(1000);
Map<String, Integer> map = new HashMap<>(1000);

// リサイズを避けてパフォーマンス向上
```

### 適切なコレクションの選択

| 用途 | 推奨コレクション |
|-----|----------------|
| 順序付きリスト | ArrayList |
| 重複除去 | HashSet |
| キーバリュー | HashMap |
| ソート済み必要 | TreeSet / TreeMap |
| 順序保持 + 重複除去 | LinkedHashSet |
| FIFO | ArrayDeque |
| 優先度付き | PriorityQueue |
| スレッドセーフMap | ConcurrentHashMap |

---

## まとめ

| インターフェース | 主な実装 | 特徴 |
|---------------|---------|------|
| List | ArrayList | 順序あり、重複OK、ランダムアクセスO(1) |
| Set | HashSet | 重複なし、順序なし、検索O(1) |
| Map | HashMap | キーバリュー、キーは重複なし |
| Queue | ArrayDeque | FIFO |
| Deque | ArrayDeque | 両端操作、スタックとしても |

---

## 次のステップ

- [例外処理](java-05-exceptions.md) - try-catch、カスタム例外
