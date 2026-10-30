---
paths:
  - "libs/**/src/main/**/*Handler.java"
  - "libs/idp-server-use-cases/src/main/**/*EntryService.java"
---

# Handler / EntryService のルール

## 段取りだけを書く
Handler / EntryService はアプリケーション層。取得 → 確認 → 委譲 → 応答 の段取りだけを書き、業務の決まりは持たない。

## private method は設計の見直しどころ
Handler / EntryService に private method を足すときは、先に置き場所を考える。

| 中身 | 置き場所 |
|------|---------|
| ドメインの決まり（どちらを優先するか、期限内か など） | 値オブジェクト / エンティティ。データを持つ側に聞く（Tell, Don't Ask） |
| 外部とのやりとり、保存する形への組み立て | Service（Handler-Service-Repository の Service） |
| 確認 | Verifier（void + throw） |
| 段取りだけ（例外を応答に変える、イベントの付加情報を付ける） | Handler に残してよい |

主フローを読みやすくするために、例外の処理などを private method に抜き出すのは段取りに入る。抜き出した中身が上の 3 つのどれかに当たるなら、そちらに置く。

Handler の状態（リポジトリなどのフィールド）を使わない static method は、とくに置き場所を疑う。引数のデータだけで決まるなら、Handler にある理由が無い。データを持つ側か値オブジェクトに移す（Move Method）。

Handler に残すと決めた private method は、なぜ段取りだけと言えるのかを説明できるようにしておく。

## テストしたくなったら外に出す
private method をユニットテストしたくなったら、まだ名前の付いていないクラスが隠れている合図。private のままテストを書くのでなく、値オブジェクトや Service に出してテストする。テストから呼ぶために private を外す（可視性を緩める）のも同じ合図。
