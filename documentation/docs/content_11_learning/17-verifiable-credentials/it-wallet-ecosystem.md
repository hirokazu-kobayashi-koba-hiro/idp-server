---
sidebar_position: 33
---

# IT-Wallet の生態系: クレデンシャルはどう流れるか

[ウォレットはどうやって「本物」を証明するか](./wallet-attestation.md) と [IT-Wallet を読む](./it-wallet.md) では、ウォレットの真正性と信頼基盤を見ました。この記事では、**クレデンシャルが実際にどう流れるか**を追います。

生態系が存在する目的はクレデンシャルの発行と提示です。信頼基盤はそのための土台にすぎません。

:::warning これは IT-Wallet の設計であって、唯一の答えではありません
ウォレットの生態系には複数の流派があります。信頼の根拠、クレデンシャルの形式、失効の表現は流派ごとに違います。

| 流派 | 信頼の根拠 |
|---|---|
| **IT-Wallet** | OpenID Federation 1.0 |
| EU / eIDAS 2 のベースライン | Trusted List（ETSI） |
| ISO mDL（18013-5 / -7） | IACA ルート証明書（X.509） |
| W3C VC + DID | DID method |

この記事が扱うのは **1 行目だけ**です。とくに「信頼基盤は OpenID Federation」は IT-Wallet の選択であり、EU ARF や HAIP が義務づけているものではありません。

形式ごとの違いは [VC フォーマット比較](./vc-formats.md)、識別子の考え方は [DID](./did.md) を参照してください。
:::

## 全体の流れ

```
┌──────────────────┐
│ Authentic Source │  権威データを持つ主体（例: 住民基本台帳）
└────────┬─────────┘
         │ ⑤ 属性を取得
         ▼
┌──────────────────┐     ①〜⑥ 発行（OpenID4VCI）
│ Credential Issuer│ ───────────────────────┐
└──────────────────┘                        │
                                            ▼
                                  ┌──────────────────┐
                                  │ Wallet Instance  │
                                  └────────┬─────────┘
                                           │ 提示（OpenID4VP）
                                           ▼
                                  ┌──────────────────┐
                                  │  Relying Party   │
                                  └──────────────────┘

         Issuer と Relying Party の間に線が無いのが要点
```

**Issuer は提示を知りません。** 発行したあと、それがどこで使われたかは伝わらない。これが前の記事で見た制度原則「利用は Issuer に見えない」の形です。

## 発行: OpenID4VCI の Authorization Code Flow

IT-Wallet は発行に [OpenID4VCI](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html) の Authorization Code Flow を使います。PID（基礎的な身元クレデンシャル）も (Q)EAA（資格や属性）も、**同じ型**で流れます。

| フェーズ | 何が起きるか |
|---|---|
| **1-3. 発見と信頼確立** | ウォレットが Issuer を発見し、正当性を検証する。**同時に Issuer 側もウォレットの正当性を検証する** |
| **4. 利用者認証** | Issuer が利用者を認証する |
| **5. データ取得** | Issuer が **Authentic Source** から属性を取得する |
| **6. 発行** | 「requesting Wallet Instance が保持する鍵材料に束縛された」クレデンシャルを発行する |

### 信頼は双方向

フェーズ 1-3 で目を引くのは、**検証が両方向**だということです。

```
ウォレット ──「この Issuer は Trust Chain で辿れるか」──▶ Issuer
ウォレット ◀──「このウォレットは正当か（Wallet Provider に確認）」── Issuer
```

ウォレットが Issuer を確かめるのは当然として、**Issuer もウォレットを確かめます**。その手段が Wallet Attestation です。前の記事で見た仕組みが、ここで使われます。

片方向だと、偽の Issuer が属性を抜き取るか、偽のウォレットがクレデンシャルを受け取るか、どちらかが成立してしまいます。

### Authentic Source が分離されている意味

フェーズ 5 で Issuer は**自分のデータベースを見るのではなく、Authentic Source に問い合わせます**。PID なら国の住民登録（ANPR）です。

これが [IT-Wallet の記事](./it-wallet.md) で見た「Authentic Source が Issuer と別に定義されている」理由です。**データの権威と、クレデンシャルとして発行する権限は別**。Issuer はデータを持たず、持っている主体から取ってきて署名する役です。

### 鍵への束縛

発行されるクレデンシャルは「ウォレットが保持する鍵材料に束縛」されます。クレデンシャルの中に鍵（または鍵のサムプリント）が入り、**提示するときにその鍵で所持を証明**します。

これが無いと、クレデンシャルを盗んだ人がそのまま提示できてしまいます。束縛があると、盗んだだけでは使えません。

## 提示: OpenID4VP

提示には [OpenID4VP](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html) を使います。2 つの形があります。

| | 使う場面 | 起点 |
|---|---|---|
| **Same Device** | 同じ端末のブラウザとウォレット | HTTP リダイレクト / リンク |
| **Cross Device** | PC の画面をスマホのウォレットで | **QR コード** |

### 流れ

```
1. RP が URL（Same Device）または QR（Cross Device）で Authorization Request を渡す
2. ウォレットが署名済み Request Object を取得（参照渡しが推奨）
3. ウォレットが Request Object の署名と RP の身元を検証
4. 利用者に同意画面：何を求められているか、誰が求めているか
5. 利用者が出す項目を選ぶ（選択的開示）
6. ウォレットが暗号化した Authorization Response を RP の response_uri へ
7. RP が検証して成功を返す
```

### RP は事前登録しない（Federation を使う場合）

面白いのは **RP の事前登録が要らない**ことです。ただしこれは **IT-Wallet が OpenID Federation を採っているから**成立する性質で、X.509 ベースの生態系では事前の証明書配布が必要になります。

> The specification requires no explicit pre-registration for federation-based parties — trust derives from **federation membership**

`client_id` が `openid_federation` プレフィックスなら、Trust Chain の中の Entity Configuration の `sub` と一致することを検証します。つまり**「誰か」は Federation が保証する**ので、Issuer や Wallet が個別に RP を登録する必要がありません。

さらに、

> The Wallet validates the Relying Party's eligibility through **policies obtained via trust chains**, ensuring they're **authorized to request specific credentials**

**「その RP がそのクレデンシャルを要求してよいか」まで Trust Chain から降りてきます。** [IT-Wallet の記事](./it-wallet.md) で見た Metadata Policy が、ここで効きます。身元が本物でも、要求してよい範囲は別に決まる。

:::tip ウォレットが認可の判断をする
一般的な OAuth では認可サーバーが「このクライアントは何を要求してよいか」を判断します。ここでは**ウォレット（＝利用者の端末）がその判断をします**。Trust Chain とポリシーを手元で検証できるからこそ成立する形です。
:::

### 選択的開示

利用者は「出す項目を選び、外す」ことができます。実現方法は形式によって違います。

| 形式 | 仕組み |
|---|---|
| **SD-JWT VC** | **KB-JWT**（Key Binding JWT）に `sd_hash` を入れる。Issuer 署名済み JWT と**選んだ disclosure** に対して計算した値で、「どれを開示したか」を証明する |
| **mdoc** | credential device authentication を通じて開示する |

`sd_hash` があるので、**開示した組み合わせごと**に所持証明が成立します。あとから disclosure を足したり抜いたりすると値が合わなくなります。

詳しくは [SD-JWT](./sd-jwt.md) と [mdoc](./mdoc.md) を参照してください。

## 「Issuer に見えない」はどう成立しているか

> the Issuer does **not** learn about individual presentations. The architecture separates Issuer from Verifier, with **no communication between them** regarding specific presentation events

成立させているのは、次の組み合わせです。

| | |
|---|---|
| **オフライン検証** | RP は Issuer に問い合わせず、署名と Trust Chain だけで検証する |
| **発行時点で認証済み** | 属性は発行時に真正性が確立されている。提示時に確認し直す必要がない |
| **失効は Status List** | 「このクレデンシャルは有効か」をリストのインデックスで引く。誰が引いたかは Issuer に分からない |
| **インスタンス固有識別子を持たない** | HAIP の MUST NOT。Issuer と RP が結託しても紐づけられない |

**どれか一つでも欠けると崩れます。** 提示のたびに Issuer へ問い合わせる設計なら、その時点で利用が筒抜けです。

## 全体を貫く構造

ここまでの 3 記事を通して見ると、同じ考え方が繰り返し現れます。

| 場面 | 分けているもの |
|---|---|
| Authentic Source / Issuer | **データの権威** と **発行の権限** |
| Trust Chain / Policy Evaluation | **本物であること** と **してよいこと** |
| WIA / KA | **端末の健全性** と **鍵保管領域の健全性** |
| Issuer / Verifier | **発行** と **検証**（間に通信を作らない） |
| 端末ロック解除 / Provider への認証 | **端末を開くこと** と **利用者を名乗ること** |
| Federation の事業者 / Wallet Instance | **数百規模** と **数千万規模** |

**一つの仕組みで統一せず、性質ごとに分ける。** 分けたうえで、それぞれに合った有効期間と失効の表現を与える。これが IT-Wallet の設計方針です。

他の流派が同じ分け方をしているとは限りません。たとえば ISO mDL は Federation を使わず X.509 の証明書階層で信頼を表し、W3C VC + DID は識別子の解決自体を分散させます。**何を分けて何を統一するかが、流派の違いそのもの**です。

## 参考

- [IT-Wallet - Credential Issuance High-Level Flow](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/credential-issuance-high-level.html)
- [IT-Wallet - Remote Flow](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/remote-flow.html)
- [OpenID for Verifiable Credential Issuance](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [OpenID for Verifiable Presentations](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [OpenID4VC High Assurance Interoperability Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
