# Java 学習ガイド

Java言語の基礎から実践的なパターンまでを学ぶドキュメント集です。

---

## 学習の目的

- Java言語の基本構文とオブジェクト指向を理解する
- ジェネリクス、コレクション、Stream APIを使いこなす
- Java 21の新機能（Records、Sealed Classes、Pattern Matching）を活用する
- idp-serverのコードを読み書きできるようになる

---

## 学習ロードマップ

```
┌─────────────────────────────────────────────────────────────┐
│                    学習の流れ                               │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  基礎 (Java入門)                                            │
│  ├── 構文基礎                                               │
│  │   └── クラス、メソッド、変数、演算子                    │
│  └── オブジェクト指向                                       │
│      └── 継承、インターフェース、抽象クラス、ポリモーフィズム │
│                                                              │
│  中級 (実践的なJava)                                        │
│  ├── ジェネリクス                                           │
│  │   └── 型パラメータ、境界、ワイルドカード                │
│  ├── コレクション                                           │
│  │   └── List、Set、Map、Queue                             │
│  ├── 例外処理                                               │
│  │   └── try-catch、カスタム例外、ベストプラクティス       │
│  ├── Stream API                                             │
│  │   └── filter、map、reduce、collect                      │
│  └── ラムダ式                                               │
│      └── 関数型インターフェース、メソッド参照               │
│                                                              │
│  上級 (並行処理とJava 21)                                   │
│  ├── 並行処理                                               │
│  │   └── Thread、ExecutorService、CompletableFuture        │
│  ├── Records                                                │
│  │   └── 不変データクラス、コンパクトコンストラクタ        │
│  ├── Sealed Classes                                         │
│  │   └── 継承制限、permits                                 │
│  └── Pattern Matching                                       │
│      └── instanceof、switch式、Record Patterns             │
│                                                              │
│  実践 (パターンと注意点)                                    │
│  ├── 実践パターン                                          │
│  │   └── 値オブジェクト、Result型、Builderパターン         │
│  └── 落とし穴と注意点                                      │
│      └── メモリリーク、スレッドセーフ、NullPointerException │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## ドキュメント一覧

### 導入

| # | ドキュメント | 説明 | 所要時間 |
|---|-------------|------|---------|
| 00 | [Java言語の特徴](java-00-introduction.md) | Javaとは、他言語との比較、バージョン進化 | 30分 |

### 基礎

| # | ドキュメント | 説明 | 所要時間 |
|---|-------------|------|---------|
| 01 | [構文基礎](java-01-syntax.md) | クラス、メソッド、変数、演算子、制御構文 | 60分 |
| 02 | [オブジェクト指向](java-02-oop.md) | 継承、インターフェース、抽象クラス、ポリモーフィズム | 60分 |

### 中級

| # | ドキュメント | 説明 | 所要時間 |
|---|-------------|------|---------|
| 03 | [ジェネリクス](java-03-generics.md) | 型パラメータ、境界、ワイルドカード、型消去 | 60分 |
| 04 | [コレクション](java-04-collections.md) | List、Set、Map、Queue、イテレーション | 60分 |
| 05 | [例外処理](java-05-exceptions.md) | try-catch、カスタム例外、ベストプラクティス | 45分 |
| 06 | [Stream API](java-06-streams.md) | filter、map、reduce、collect、並列ストリーム | 60分 |
| 07 | [ラムダ式](java-07-lambda.md) | 関数型インターフェース、メソッド参照、クロージャ | 45分 |

### 上級

| # | ドキュメント | 説明 | 所要時間 |
|---|-------------|------|---------|
| 08 | [並行処理](java-08-concurrency.md) | Thread、ExecutorService、CompletableFuture | 90分 |
| 09 | [Records](java-09-records.md) | 不変データクラス、コンパクトコンストラクタ | 30分 |
| 10 | [Sealed Classes](java-10-sealed.md) | 継承制限、permits、パターンマッチングとの組み合わせ | 30分 |
| 11 | [Pattern Matching](java-11-pattern-matching.md) | instanceof、switch式、Record Patterns | 45分 |

### 実践

| # | ドキュメント | 説明 | 所要時間 |
|---|-------------|------|---------|
| 12 | [Java 実践パターン](java-12-idp-patterns.md) | 値オブジェクト、Result型、Builder、Factory、Strategyパターン | 60分 |
| 13 | [落とし穴と注意点](java-13-pitfalls.md) | メモリリーク、スレッドセーフ、NPE、よくある間違い | 45分 |

### コラム

| # | ドキュメント | 説明 | 所要時間 |
|---|-------------|------|---------|
| 99 | [1つの言語を深く学ぶ価値](java-99-column-one-language.md) | 言語を超えて転用できる知識、深く学ぶとは何か | 15分 |

---

## 推奨学習パス

### パス1: Java初心者

```
1. 構文基礎
2. オブジェクト指向
3. 例外処理
4. コレクション
5. ジェネリクス
```

### パス2: 他言語経験者（Python、JavaScript等）

```
1. 構文基礎（差分確認）
2. オブジェクト指向（静的型付けの理解）
3. ジェネリクス ★
4. Stream API
5. ラムダ式
```

★ 動的型付け言語からの最大の違い

### パス3: idp-server開発者

```
1. オブジェクト指向
2. ジェネリクス
3. コレクション
4. Stream API
5. Records ★
6. Sealed Classes ★
7. Pattern Matching ★
8. 実践パターン
9. 落とし穴と注意点 ★★
```

★ Java 21の重要機能
★★ 本番障害を防ぐための必読

### パス4: コードレビュー担当

```
1. ジェネリクス
2. 例外処理
3. 並行処理
4. 実践パターン
5. 落とし穴と注意点 ★
```

★ レビュー時の重要チェックポイント

---

## 学べること

### idp-server開発に必要な知識

| Javaの概念 | idp-serverでの活用 |
|----------|-------------------|
| インターフェース | Handler、Service、Repository分離 |
| ジェネリクス | 型安全なリポジトリ、Result型 |
| Records | DTO、値オブジェクト |
| Sealed Classes | 列挙的な型の網羅性保証 |
| Pattern Matching | 型に応じた処理分岐 |
| Stream API | コレクション操作、データ変換 |
| Optional | null安全な戻り値 |

### よく使うパターン

| パターン | 用途 |
|---------|------|
| 値オブジェクト | ドメインの概念を型で表現 |
| Result型 | 成功/失敗を型で表現、例外より安全 |
| Builder | 複雑なオブジェクトの構築 |
| Factory | オブジェクト生成の隠蔽 |
| Strategy | アルゴリズムの差し替え |
| Null Object | nullチェックの排除 |

### よくある落とし穴

| 落とし穴 | 対策 |
|---------|------|
| メモリリーク | リソースのclose、コレクションのクリア |
| スレッドセーフ違反 | AtomicXxx、ConcurrentHashMap、synchronized |
| NullPointerException | Optional活用、事前条件チェック |
| equals/hashCode不整合 | 両方を必ずオーバーライド |

---

## 前提知識

- プログラミングの基本概念（変数、条件分岐、ループ）
- 何らかのプログラミング言語の経験（あれば望ましい）

---

## 関連ドキュメント

### JVM・ランタイム

- [JVM 学習ガイド](../20-jvm/README.md) - メモリ管理、GC、パフォーマンス

### フレームワーク

- [フレームワーク学習ガイド](../22-frameworks/README.md) - IoC、DI、設計原則

### 参考リソース

- [Java SE 21 Documentation](https://docs.oracle.com/en/java/javase/21/)
- [Java Language Specification](https://docs.oracle.com/javase/specs/jls/se21/html/)
- [Effective Java 3rd Edition](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/)
