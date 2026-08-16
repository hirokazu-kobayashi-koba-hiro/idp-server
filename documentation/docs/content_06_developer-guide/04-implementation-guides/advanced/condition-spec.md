# condition（条件式）

`condition` は「この処理を実行するか」を設定で表すための共通の仕組みです。JSONPath で値を取り出し、演算子で判定します。

同じ形式が複数の場所で使われます。

| 使う場所 | 効果 |
|---------|------|
| `execution.http_requests[].condition` | そのHTTPリクエストを送るかどうか（[外部トークン認証](../../05-configuration/authn/external-token.md) / [フェデレーション](../../05-configuration/federation.md)）|
| `*_mapping_rules[].condition` | そのマッピングルールを適用するかどうか |
| 身元確認の `pre_hook` 各種 | そのパラメータ解決・検証を行うかどうか |

## 基本形

```json
{
  "operation": "eq",
  "path": "$.response_body.status",
  "value": "approved"
}
```

| フィールド | 説明 |
|-----------|------|
| `operation` | 演算子（必須）|
| `path` | 判定対象を取り出す JSONPath（`allOf` / `anyOf` 以外では必須）|
| `value` | 比較値（`exists` / `missing` では不要）|

## 演算子

| operation | 判定 | 備考 |
|-----------|------|------|
| `eq` | 等しい | |
| `ne` | 等しくない | |
| `gt` / `gte` / `lt` / `lte` | 数値比較 | BigDecimal で比較するため `18` と `"18.0"` は等しい。**null や数値化できない値は例外扱い**（下記）|
| `in` | `value` の配列に含まれる | `value` が配列でない場合は常に false |
| `nin` | `value` の配列に含まれない | 同上 |
| `exists` | 値が存在する（null でない）| `value` 不要 |
| `missing` | 値が存在しない（null）| `value` 不要 |
| `contains` | 包含する | |
| `regex` | 正規表現にマッチする | |

未知の `operation` は false と判定されます。

## 複合条件

`allOf`（AND）/ `anyOf`（OR）で入れ子にできます。`path` は不要で、`value` にネストした条件の配列を置きます。

```json
{
  "operation": "allOf",
  "value": [
    { "operation": "exists", "path": "$.response_body.score" },
    { "operation": "gte", "path": "$.response_body.score", "value": 80 }
  ]
}
```

- ネストの中にさらに `allOf` / `anyOf` を書けます
- 深さの上限は 10（`idp.condition.maxDepth` で変更可）。超えると warn を出して false
- 空の `allOf` は true、空の `anyOf` は false

`not` に相当する演算子はありません。否定は `ne` / `nin` / `missing` で表現してください。

## 判定できないときは false になります

**評価に失敗した条件は、warn ログを出して false と判定されます。** 例外にはなりません。

そのため「実行しない」側に倒れます。無条件に実行されるより安全ですが、**設定ミスが静かなスキップとして現れる**ことになります。

主な失敗パターン:

| パターン | 結果 |
|---------|------|
| `path` が解決しない（キーが存在しない・添字が範囲外）| 値は null。`eq` なら false、`missing` なら true |
| `gte` 等の数値比較で対象が null または非数値 | 例外 → **false** |
| `in` / `nin` の `value` が配列でない | false |

数値比較を使うときは、値の存在を先に確かめると意図しないスキップを避けられます。

```json
{
  "operation": "allOf",
  "value": [
    { "operation": "exists", "path": "$.response_body.score" },
    { "operation": "gte", "path": "$.response_body.score", "value": 80 }
  ]
}
```

:::warning 解決しないパスはエラーになりません
`$.responseBody.status` のような綴り違いを書いても、設定の登録も GET も成功します。実行時に値が null になるだけで、どこにも報告されません。パスを書いたら実際に動かして確かめてください。
:::

## `path` に何を書けるか

参照できるキーは**使う場所ごとに異なります**。条件を書く前に、その場所のドキュメントでコンテキストを確認してください。

| 使う場所 | 参照先 |
|---------|-------|
| 認証の `http_requests` | [外部トークン認証の設定](../../05-configuration/authn/external-token.md) |
| フェデレーションの `http_requests` | [フェデレーションの設定](../../05-configuration/federation.md) |
| マッピングルール | [マッピングと JSONPath](mapping-jsonpath.md) |
