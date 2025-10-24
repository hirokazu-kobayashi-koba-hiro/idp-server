# スレッドセーフ性問題 - バグシナリオと影響分析

**作成日**: 2025-10-21
**目的**: 検出された各問題が実際に引き起こすバグシナリオと対策を詳細化

---

## 🚨 Issue #2: SystemDateTime.clock - タイムゾーン競合

### 現在の実装

**ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/date/SystemDateTime.java:25`

```java
public class SystemDateTime {
  private static Clock clock = Clock.systemUTC();  // ❌ non-final, non-volatile

  public static void configure(ZoneId zone) {
    clock = Clock.system(zone);  // ❌ 同期化なし
  }

  public static LocalDateTime now() {
    return LocalDateTime.now(clock);  // ❌ 古いclockを読む可能性
  }
}
```

---

### 🔥 発生しうるバグシナリオ

#### Scenario 1: タイムゾーン設定中の時刻取得

**状況**:
- Thread A: `SystemDateTime.configure(ZoneId.of("Asia/Tokyo"))` を実行中
- Thread B: `SystemDateTime.now()` で現在時刻を取得中

**バグ事象**:
```
Thread A: clock = Clock.system(zone);  // ← 書き込み途中
Thread B: LocalDateTime.now(clock);    // ← 不完全なclockオブジェクト参照
→ NullPointerException or 不正な時刻
```

**影響範囲**:
- トークン有効期限の誤計算（`ExpiresAt` 計算）
- 監査ログのタイムスタンプ不整合
- セッション有効期限の誤判定

**再現性**: 🟡 **Low-Medium** - 起動時に設定されるため通常は問題なし

---

#### Scenario 2: Visibility Problem（キャッシュの不整合）

**状況**:
- CPU-0 (Thread A): `configure()` でclockを更新
- CPU-1 (Thread B): `now()` で古いclockをキャッシュから読み取り

**バグ事象**:
```
CPU-0: clock = Clock.system(ZoneId.of("Asia/Tokyo"));  // ← メモリに書き込み
       // CPU-0のキャッシュには反映、メインメモリには未反映

CPU-1: LocalDateTime.now(clock);  // ← CPU-1のキャッシュから古いUTC clockを読む
       → 9時間のズレが発生
```

**影響例**:
```java
// Thread A: configure("Asia/Tokyo") 実行 → 2025-10-21 20:00 JST
// Thread B: now() 実行 → 2025-10-21 11:00 UTC (古いclock参照)
// → トークンの有効期限が9時間ずれる
```

**再現性**: 🔴 **Medium** - Java Memory Model違反、最適化により発生

---

#### Scenario 3: 本番環境での動的タイムゾーン変更

**状況**（仮想シナリオ）:
- 本番環境でタイムゾーン設定を動的に変更するAPI追加
- 複数リクエストが同時実行中

**バグ事象**:
```
Time: 0ms  - Thread A: configure(Tokyo)開始
Time: 1ms  - Thread B: now() → UTC clock読み取り
Time: 2ms  - Thread A: configure(Tokyo)完了
Time: 3ms  - Thread C: now() → Tokyo clock読み取り
Time: 4ms  - Thread B: トークン生成 → UTC時刻で有効期限設定
Time: 5ms  - Thread C: トークン生成 → Tokyo時刻で有効期限設定

→ 同時刻に生成されたトークンなのに有効期限が9時間ずれる
```

**影響**:
- データベースの時刻カラム不整合
- 監査ログの時系列が前後する
- トークンrevocation判定の誤り

**再現性**: 🟢 **Very Low** - 現在は起動時のみ設定（将来的リスク）

---

### 💡 修正案と効果

#### Option 1: volatile + synchronized（推奨）

```java
public class SystemDateTime {
  private static volatile Clock clock = Clock.systemUTC();  // ✅ volatile追加

  public static synchronized void configure(ZoneId zone) {  // ✅ synchronized追加
    if (zone == null) {
      throw new IllegalArgumentException("Zone cannot be null");
    }
    clock = Clock.system(zone);
  }

  public static LocalDateTime now() {
    return LocalDateTime.now(clock);  // ✅ volatileでvisibility保証
  }
}
```

**効果**:
- ✅ **Visibility保証**: volatileで全スレッドが最新値を読み取り
- ✅ **Atomicity保証**: synchronizedで複数スレッドの同時書き込み防止
- ✅ **シンプル**: 最小限の変更

**パフォーマンス影響**:
- `configure()`: 起動時1回のみ → 影響なし
- `now()`: volatileの読み取りオーバーヘッド → 無視できるレベル（数ns）

---

#### Option 2: AtomicReference（より安全）

```java
public class SystemDateTime {
  private static final AtomicReference<Clock> clock =
      new AtomicReference<>(Clock.systemUTC());  // ✅ final + AtomicReference

  public static void configure(ZoneId zone) {
    if (zone == null) {
      throw new IllegalArgumentException("Zone cannot be null");
    }
    clock.set(Clock.system(zone));  // ✅ Atomic操作
  }

  public static LocalDateTime now() {
    return LocalDateTime.now(clock.get());  // ✅ Atomic読み取り
  }
}
```

**効果**:
- ✅ **完全なAtomic性**: get/set が atomic操作
- ✅ **Immutable参照**: AtomicReference自体はfinal
- ✅ **Lock-free**: synchronizedより高性能

**パフォーマンス影響**:
- `configure()`: Atomic.set() → 影響なし
- `now()`: Atomic.get() → volatileと同等（数ns）

---

### 🎯 推奨修正

**Option 1 (volatile + synchronized)** を推奨

**理由**:
1. シンプルで理解しやすい
2. Java標準のスレッドセーフ性パターン
3. パフォーマンス影響は無視できる
4. 既存コードの変更が最小限

---

## 🚨 Issue #3: JsonConvertable.jsonConverter - 上書きリスク

### 現在の実装

**ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/json/JsonConvertable.java:20`

```java
public class JsonConvertable {
  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();  // ❌ non-final, package-private

  public static <TYPE> TYPE read(String value, Class<TYPE> typeClass) {
    return jsonConverter.read(value, typeClass);
  }
}
```

---

### 🔥 発生しうるバグシナリオ

#### Scenario 1: 誤ったJsonConverter上書き

**状況**:
- 同パッケージの新しいクラスで誤って上書き
- 複数スレッドが同時にJSON変換実行中

**バグ事象**:
```java
// 悪意なく、同パッケージで誤って上書き
package org.idp.server.platform.json;

public class SomeUtility {
  static {
    JsonConvertable.jsonConverter = JsonConverter.defaultInstance();  // ❌ 上書き可能
    // snakeCase → camelCase に変更されてしまう
  }
}
```

**影響**:
```
Before: {"user_id": "123", "client_id": "abc"}  // snake_case
After:  {"userId": "123", "clientId": "abc"}    // camelCase

→ データベース保存時にカラム名不一致
→ JSON Schema検証エラー
→ 外部API連携の失敗
```

**再現性**: 🟢 **Very Low** - 実際にはこのようなコードは書かれない

---

#### Scenario 2: Reflection による上書き

**状況**:
- テストコードやプラグインでReflectionを使用
- 誤ってJsonConverterを変更

**バグ事象**:
```java
// テストで誤ってグローバル状態を変更
Field field = JsonConvertable.class.getDeclaredField("jsonConverter");
field.setAccessible(true);
field.set(null, JsonConverter.defaultInstance());  // ❌ 全スレッドに影響

→ 他のテストケースに影響
→ テストの実行順序依存バグ
```

**影響**:
- テストの不安定性（flaky tests）
- CI/CDで断続的な失敗

**再現性**: 🟡 **Low** - テストコード不備で発生可能

---

### 💡 修正案と効果

```java
// ✅ 修正後
public class JsonConvertable {
  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  //       ^^^^^^ カプセル化    ^^^^^ Immutable化

  public static <TYPE> TYPE read(String value, Class<TYPE> typeClass) {
    return jsonConverter.read(value, typeClass);
  }
}
```

**効果**:
- ✅ **コンパイル時保証**: final で再代入不可
- ✅ **カプセル化**: private で外部アクセス不可
- ✅ **予防**: 将来のバグを事前防止

**パフォーマンス影響**: なし（既存と同じ）

---

## 🚨 Issue #4-13: ModelConverter.jsonConverter × 10箇所 - 同一パターン

### 現在の実装

**ファイル**: 10個のModelConverterクラス（idp-server-core-adapter）

```java
class ModelConverter {
  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();  // ❌ 10箇所で同じ問題

  static OAuthToken convert(Map<String, String> data) {
    return jsonConverter.read(data.get("json_column"), OAuthToken.class);
  }
}
```

---

### 🔥 発生しうるバグシナリオ

#### Scenario 1: データベースデータの破損

**状況**:
- Thread A: OAuth Tokenをデータベースから読み取り中
- Thread B: 別のテーブルからユーザー情報を読み取り中
- Thread C: 誤ってModelConverter.jsonConverterを上書き

**バグ事象**:
```java
// Thread A: OAuthToken読み取り
Time: 0ms  - Thread A: ModelConverter.jsonConverter 使用準備
Time: 1ms  - Thread C: ModelConverter.jsonConverter = null;  // ❌ 誤って上書き
Time: 2ms  - Thread A: jsonConverter.read(...) 実行
                       → NullPointerException

// または

Time: 0ms  - Thread A: jsonConverter.read(snakeCase JSON)
Time: 1ms  - Thread C: jsonConverter = JsonConverter.defaultInstance();
Time: 2ms  - Thread B: jsonConverter.read(snakeCase JSON)
                       → camelCase期待で読み取り失敗
                       → JsonParseException
```

**影響**:
```
Exception in thread "http-nio-8080-exec-12" java.lang.NullPointerException
  at ModelConverter.convert(ModelConverter.java:49)
  at OAuthTokenQueryRepository.find(...)
  at TokenIntrospectionHandler.handle(...)

→ トークン検証API全体が停止
→ 全ユーザーのログインができなくなる
```

**再現性**: 🟢 **Very Low** - 実際には誤った上書きは発生しない

---

#### Scenario 2: 複数Repository同時アクセス時の不整合

**状況**:
- 10個のModelConverterクラスが別々のスレッドで同時使用
- 各クラスで独立したJsonConverter期待

**バグ事象**:
```java
// token/query/ModelConverter.java
static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

// identity/ModelConverter.java
static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

// これらは別々のインスタンス → 問題なし
// しかし、誤って同じフィールド名で衝突リスク
```

**実質的リスク**: 🟢 **Very Low** - 各クラスで独立した変数

---

#### Scenario 3: テスト環境での状態汚染

**状況**:
- 統合テストで複数のテストケースが並列実行
- テストAがModelConverter.jsonConverterを変更
- テストBが影響を受ける

**バグ事象**:
```java
// Test A
@Test
void testCustomJsonConverter() {
  ModelConverter.jsonConverter = customConverter;  // ❌ グローバル状態変更
  // ... テストロジック
}

// Test B (並列実行)
@Test
void testTokenConversion() {
  OAuthToken token = ModelConverter.convert(data);  // ❌ customConverterが使われる
  // → 期待と異なる変換結果
  // → assertion failure
}
```

**影響**:
- Flaky tests（実行順序依存）
- CI/CDで断続的な失敗
- デバッグ困難

**再現性**: 🟡 **Low-Medium** - テスト並列実行で発生可能

---

### 💡 修正案と効果

```java
// ✅ 修正後（10箇所すべて）
class ModelConverter {
  private static final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  //       ^^^^^^         ^^^^^
  //       カプセル化      Immutable化

  static OAuthToken convert(Map<String, String> data) {
    return jsonConverter.read(data.get("json_column"), OAuthToken.class);
  }
}
```

**効果**:
- ✅ **予防**: 将来的な誤用を防止
- ✅ **テスト安定性**: グローバル状態変更不可
- ✅ **一貫性**: 10箇所すべてで同じパターンに統一

**修正工数**: 10ファイル × 2文字追加 = 1分以内

---

## 🚨 FunctionRegistry.map - 初期化後の変更リスク

### 現在の実装

**ファイル**: `libs/idp-server-platform/src/main/java/org/idp/server/platform/mapper/functions/FunctionRegistry.java:23`

```java
public class FunctionRegistry {
  private final Map<String, ValueFunction> map = new HashMap<>();  // ❌ HashMap

  public FunctionRegistry() {
    register(new FormatFunction());
    register(new RandomStringFunction());
    // ... 15+ functions
  }

  public void register(ValueFunction fn) {  // ⚠️ public method
    map.put(fn.name(), fn);
  }

  public ValueFunction get(String name) {
    return map.get(name);
  }
}

// MappingRuleObjectMapper.java
private static final FunctionRegistry functionRegistry = new FunctionRegistry();
```

---

### 🔥 発生しうるバグシナリオ

#### Scenario 1: 初期化後の register() 呼び出し

**状況**:
- static final FunctionRegistryは全スレッドで共有
- 誰かが `MappingRuleObjectMapper.functionRegistry.register(...)` を呼び出し

**バグ事象**:
```java
// Thread A: Mapping実行中
Time: 0ms  - Thread A: functionRegistry.get("format") → 取得成功
Time: 1ms  - Thread B: functionRegistry.register(new CustomFunction())
                       → HashMap内部構造変更
Time: 2ms  - Thread A: functionRegistry.get("uuid") 実行
                       → ConcurrentModificationException

// または

Time: 0ms  - Thread A: functionRegistry.get("format") でiteration開始
Time: 1ms  - Thread B: functionRegistry.register(...) でHashMap変更
Time: 2ms  - Thread A: next() 呼び出し
                       → ConcurrentModificationException
```

**スタックトレース例**:
```
java.util.ConcurrentModificationException
  at java.util.HashMap$HashIterator.nextNode(HashMap.java:1493)
  at java.util.HashMap$KeyIterator.next(HashMap.java:1516)
  at FunctionRegistry.get(FunctionRegistry.java:57)
  at MappingRuleObjectMapper.applyFunctions(...)
  at IdentityVerificationService.executeMapping(...)

→ Identity Verification API全体が500エラー
→ 本人確認フローが完全停止
```

**影響範囲**:
- Identity Verification API
- HTTP Request Executor (動的マッピング)
- すべてのカスタムマッピング処理

**再現性**: 🟡 **Low** - `register()` がpublicなので呼び出し可能

---

#### Scenario 2: HashMap内部のリハッシュ競合

**状況**:
- HashMapの容量が閾値を超えてリハッシュ実行
- 複数スレッドが同時に `get()` を実行中

**バグ事象**:
```java
Time: 0ms  - Thread A: map.put() でHashMap容量75%到達
Time: 1ms  - Thread A: リハッシュ開始（内部配列再構築）
Time: 2ms  - Thread B: map.get("format") 実行
                       → リハッシュ中の不完全な配列を参照
                       → null返却 or 無限ループ

→ NullPointerException
→ または Thread がハング
```

**影響**:
- API応答停止
- スレッドプールの枯渇
- サービス全体のダウン

**再現性**: 🟢 **Very Low** - 現在はコンストラクタでのみ初期化、容量超過なし

---

### 💡 修正案と効果

#### Option 1: Collections.unmodifiableMap（推奨）

```java
public class FunctionRegistry {
  private final Map<String, ValueFunction> map;

  public FunctionRegistry() {
    Map<String, ValueFunction> temp = new HashMap<>();
    temp.put("format", new FormatFunction());
    temp.put("randomString", new RandomStringFunction());
    temp.put("now", new NowFunction());
    temp.put("exists", new ExistsFunction());
    temp.put("convertType", new ConvertTypeFunction());
    temp.put("uuid4", new Uuid4Function());
    temp.put("uuid5", new Uuid5Function());
    temp.put("uuidShort", new UuidShortFunction());
    temp.put("substring", new SubstringFunction());
    temp.put("replace", new ReplaceFunction());
    temp.put("regexReplace", new RegexReplaceFunction());
    temp.put("case", new CaseFunction());
    temp.put("trim", new TrimFunction());
    temp.put("if", new IfFunction());
    temp.put("switch", new SwitchFunction());

    MapFunction mapFunction = new MapFunction();
    temp.put("map", mapFunction);
    mapFunction.setFunctionRegistry(this);

    temp.put("filter", new FilterFunction());
    temp.put("join", new JoinFunction());
    temp.put("split", new SplitFunction());

    this.map = Collections.unmodifiableMap(temp);  // ✅ Immutable化
  }

  // ❌ register()メソッドは削除（初期化専用）

  public ValueFunction get(String name) {
    return map.get(name);
  }

  public boolean exists(String name) {
    return map.containsKey(name);
  }
}
```

**効果**:
- ✅ **Runtime保証**: `UnsupportedOperationException` で変更試行を防止
- ✅ **Thread-safe**: Immutable mapは完全にスレッドセーフ
- ✅ **明示的**: 読み取り専用が明確

**破壊的変更**:
- `register()` メソッドが使えなくなる
- → 現在外部から呼ばれていないため影響なし

---

#### Option 2: ConcurrentHashMap（変更を許可する場合）

```java
public class FunctionRegistry {
  private final Map<String, ValueFunction> map = new ConcurrentHashMap<>();  // ✅

  public void register(ValueFunction fn) {
    map.put(fn.name(), fn);  // ✅ Thread-safe
  }

  public ValueFunction get(String name) {
    return map.get(name);  // ✅ Thread-safe
  }
}
```

**効果**:
- ✅ **Thread-safe**: ConcurrentHashMapで同期化
- ✅ **拡張可能**: 動的に関数を追加可能

**デメリット**:
- 🔴 **設計意図不明**: 初期化後に追加する想定がない
- 🔴 **複雑性**: 不要なthread-safety機構

---

### 🎯 推奨修正

**Option 1 (Collections.unmodifiableMap)** を推奨

**理由**:
1. 設計意図が明確（初期化後は変更不可）
2. 不要な `register()` メソッドを削除
3. 完全なImmutability保証
4. パフォーマンス最適化（同期化不要）

---

## 📊 全問題の優先度マトリクス

| Issue | 実質的リスク | 理論的リスク | 再現性 | 影響範囲 | 優先度 | 推奨対応 |
|-------|------------|------------|-------|---------|--------|---------|
| #1 VerifiableCredentialContext | - | - | - | - | - | ✅ 修正済 (PR #777) |
| #2 SystemDateTime.clock | 🟢 Low | 🔴 Medium | 🟡 Low-Medium | 🔴 Critical | 🟡 **Medium** | volatile + synchronized |
| #3 JsonConvertable.jsonConverter | 🟢 Very Low | 🟡 Low | 🟢 Very Low | 🟡 Medium | 🟢 **Low** | final + private |
| #4-13 ModelConverter × 10 | 🟢 Very Low | 🟡 Low | 🟡 Low-Medium | 🟡 Medium | 🟡 **Medium** | final + private (一括) |
| FunctionRegistry.map | 🟢 Very Low | 🟡 Low | 🟢 Very Low | 🔴 High | 🟡 **Medium** | unmodifiableMap |

---

## 🎯 推奨修正順序

### Phase 1: 一括修正（工数: 5分）
**Issue #4-13: ModelConverter × 10箇所**
- 同じパターンの繰り返し
- 検索置換で一括修正可能
- リスクは低いが数が多い

### Phase 2: 設計改善（工数: 10分）
**FunctionRegistry.map**
- Collections.unmodifiableMap()
- register()メソッド削除
- 明示的なImmutability

### Phase 3: クリティカル修正（工数: 5分）
**Issue #2: SystemDateTime.clock**
- volatile + synchronized追加
- 理論的リスクが最も高い
- 影響範囲が広い

### Phase 4: 予防的修正（工数: 2分）
**Issue #3: JsonConvertable.jsonConverter**
- final + private追加
- 最もリスクが低い

---

## 🔍 実際のバグ発生条件

### 発生しやすさランキング

1. 🔴 **高**: なし（すべてLow以下）
2. 🟡 **中**: **Issue #2 (SystemDateTime)** - 将来的にconfigure()が追加呼び出しされる可能性
3. 🟢 **低**: **Issue #4-13, #3** - 実際には発生しない

### 現実的なリスク評価

#### 本番環境
- **現状**: ✅ 問題発生の可能性は極めて低い
- **理由**:
  - SystemDateTime.configure()は起動時1回のみ
  - JsonConverterの上書きは実装されていない
  - ModelConverterはRead-Only使用

#### 将来的リスク
- **動的設定変更機能追加時**: Issue #2 が顕在化
- **プラグインシステム拡張時**: Issue #4-13 が顕在化
- **テスト並列化時**: 全Issue が顕在化可能性

---

## 💡 総合推奨

### 即座修正（High Priority）
**なし** - すべてMedium/Low

### 推奨修正（Medium Priority）
1. ✅ **Issue #4-13**: ModelConverter × 10箇所 - 一括PR（5分で完了）
2. ✅ **Issue #2**: SystemDateTime.clock - volatile + synchronized
3. ✅ **FunctionRegistry.map** - Collections.unmodifiableMap()

### 任意修正（Low Priority）
4. ⭕ **Issue #3**: JsonConvertable.jsonConverter - final + private

---

**結論**: 現状の実装は「実質的に安全」だが、**将来的な保守性とコード品質向上のため、Medium Priority問題の修正を推奨**
