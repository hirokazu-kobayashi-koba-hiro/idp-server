# Mapping JSONPath

> **関連ドキュメント**
> - [Mapping Functions](./mapping-functions.md) - 値の変換・生成関数
> - [HTTP Request Executor](../integration/http-request-executor.md) - HTTP通信でのマッピング利用

## 概要

mapping rule の `from`、関数の動的 args（`$.` で始まる値）、condition の `path` は、いずれも [Jayway JSONPath](https://github.com/json-path/JsonPath)（json-path 2.9.0）で評価される。評価は `JsonPathWrapper` に集約されている。

このページは、設定で使える JSONPath の表現力——特にフィルタ述語 `[?(...)]` のクロス要素比較——と、エラーにならず**静かに空になる**罠パターンをまとめる。ここに記載の挙動はすべて `JsonPathWrapperTest`（`idp-server-platform`）の回帰テストでピン留めされており、ライブラリのバージョンアップで挙動が変わればテストが落ちる。

## 基本パターン

| パターン | 例 | 結果 |
|---------|-----|------|
| ネストアクセス | `$.request_body.email` | スカラー値 |
| オブジェクトまるごと | `$.request_body` | オブジェクト |
| 配列インデックス | `$.execution_http_requests[0].response_body.id` | 要素の値 |
| ハイフン入りキー | `$.password-authentication.success_count` | ドット記法のまま参照可 |
| ワイルドカード射影 | `$.list[*].properties.holder_name` | 全要素のフィールド値リスト |
| フィルタ | `$.list[?(@.primary == true)]` | 条件一致要素のリスト |
| 複合条件 | `$.list[?(@.primary == false && @.type == 'x')]` | AND/OR 可 |
| フィルタ後の射影 | `$.list[?(@.primary == true)].properties.holder_name` | 一致要素のフィールド値**リスト** |

フィルタの結果は常に**リスト**になる（1件一致でも）。フィルタ後の射影もリストになる点が、後述のクロス要素比較の演算子選択に効いてくる。

## クロス要素比較（述語内ルート参照・ネスト述語）

述語の中で `$.`（ルート参照）が使え、その中にさらに述語をネストできる。これにより「リストのうち、基準要素と同一属性を持つ要素だけ残す」というクロス要素比較が **`from` の JSONPath だけで**書ける。

実例: 外部 API から取得したリストのうち、基準要素（`primary == true`）と同じ `holder_name` を持つ要素だけを残す。

```
$.execution_http_requests[0].response_body.list[?(@.properties.holder_name in $.execution_http_requests[0].response_body.list[?(@.primary==true)].properties.holder_name)]
```

### 使える書き方

| パターン | 例（述語部分） | 説明 |
|---------|--------------|------|
| ルート参照 + インデックス | `[?(@.x == $.list[0].x)]` | 基準要素が位置で特定できる場合 |
| `in` + ネスト述語 | `[?(@.x in $.list[?(@.primary==true)].x)]` | 基準要素を条件で特定。ネスト述語の解決結果は**リスト**なので `in` で照合する |
| `contains`（リストを左辺に） | `[?($.list[?(@.primary==true)].x contains @.x)]` | `in` と同じ結果。リスト解決結果を左辺に置く |
| 否定 `!(in)` | `[?(!(@.x in $.list[?(@.primary==true)].x))]` | 基準に一致**しない**要素の抽出 |
| 否定 `nin` | `[?(@.x nin $.list[?(@.primary==true)].x)]` | json-path 2.9.0 では `in` の完全な否定として動作し、`!(in)` と同じ結果になる |

### 罠: `==` は静かに空になる

ネスト述語の解決結果は**リスト**のため、`==` で比較すると一致せず、**エラーにならず空リスト**が返る。

```
NG: [?(@.x == $.list[?(@.primary==true)].x)]  → 常に []（エラーは出ない）
OK: [?(@.x in $.list[?(@.primary==true)].x)]
```

設定を書いた直後に結果が空になる場合、まずこのパターンを疑うこと。

## フェイルクローズ挙動

パス不一致・該当なしはエラーにならず、空またはnullに解決される（fail-closed）。

| ケース | 結果 |
|--------|------|
| フィルタに一致する要素がない | 空リスト |
| フィルタが参照するプロパティがどの要素にもない | 空リスト |
| 存在しない definite path（`$.a.b`） | `null`（`PathNotFoundException` は `JsonPathWrapper` が吸収） |
| 配列の範囲外インデックス | `null` |

マッピングでは `from` が `null` に解決されたルールは値なしとして扱われる。「設定ミス＝静かに値が消える」ため、新しい式を設定に入れる際は実データで結果を確認すること。

## 注意: 実装依存の挙動

述語内ルート参照・ネスト述語・`in`/`contains`/`nin` の挙動は、JSONPath の正式な仕様（RFC 9535 は Jayway 実装と互換でない部分がある）ではなく **Jayway 実装の挙動**である。設定がこの表現力に依存する場合、ライブラリ更新時の挙動変化は `JsonPathWrapperTest` の回帰テストで検知する。
