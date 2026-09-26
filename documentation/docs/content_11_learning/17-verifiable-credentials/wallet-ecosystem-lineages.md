---
sidebar_position: 30
---

# ウォレット生態系の流派

「デジタルクレデンシャル」と一言で言っても、その裏にある設計思想はひとつではありません。運転免許証をデジタル化する系譜と、分散型識別子から出発した系譜と、EU の規制から生まれた系譜では、**何を信頼の起点に置くか**が根本的に違います。

この記事では、主要な流派を並べて、どこが同じでどこが違うのかを整理します。

:::note なぜ流派を意識する必要があるか
「ウォレットに対応する」と決めたとき、どの流派を指しているかで作るものが変わります。信頼の確立方法も、失効の表現も、選択的開示の実現方法も違うからです。**一方の常識がもう一方では成立しません。**
:::

## 3 つの系譜

```
┌────────────────────────────────────────────────────────────┐
│ ① ISO mDL 系      運転免許証のデジタル化から                 │
│    ISO/IEC 18013-5 / -7                                     │
│    信頼の起点: IACA ルート証明書（X.509）                     │
├────────────────────────────────────────────────────────────┤
│ ② W3C VC + DID 系  自己主権型アイデンティティ（SSI）から      │
│    W3C VC Data Model / DID                                  │
│    信頼の起点: DID method（台帳・Web・鍵など）                │
├────────────────────────────────────────────────────────────┤
│ ③ EU / eIDAS 2 系  規制と国家制度から                        │
│    EUDI ARF / HAIP / OpenID4VCI・VP                         │
│    信頼の起点: 欧州委員会の LoTE と X.509 証明書             │
└────────────────────────────────────────────────────────────┘
```

出自が違うので、**同じ問題に別の答えを出しています**。

## 信頼の起点

ここが最も大きな違いです。「この発行者は本物か」をどう確かめるか。

### ① ISO mDL: 証明書階層

**IACA**（Issuing Authority Certificate Authority）のルート証明書を起点に、X.509 の証明書チェーンで辿ります。

検証者はあらかじめ信頼する IACA ルートを持っておき、提示されたクレデンシャルの署名者がそこまで繋がるかを確認します。**PKI そのもの**です。

免許証という文脈を考えると自然な選択です。発行主体は各国・各州の当局で、数が限られ、変化も遅い。証明書配布で十分に回ります。

### ② W3C VC + DID: 識別子の解決

発行者は **DID**（Decentralized Identifier）で表され、DID を解決すると公開鍵が得られます。

解決方法は **DID method** によって違います。ブロックチェーンに書く方式、Web サーバーに置く方式（`did:web`）、鍵そのものから導出する方式（`did:key`）など。

**「誰が信頼を保証するか」を方式ごとに差し替えられる**のが設計思想です。中央の登録機関に依存しない、という出発点がここに現れています。

詳しくは [DID](./did.md) を参照してください。

### ③ EU / eIDAS 2: 一覧と証明書

発行者と Wallet Provider は、欧州委員会が公開する一覧（**LoTE**: List of Trusted Entities）に載ります。適格な属性証明（QEAA）の発行者だけは、eIDAS 1 から続く各国の **Trusted List** です。

Relying Party は一覧に載りません。数が多すぎるからです。代わりに登録を受け、**X.509 のアクセス証明書**と、何を要求してよいかを示す**登録証明書**を持ちます。

OpenID Federation は ARF の本文に登場しません。[IT-Wallet](./it-wallet.md) は国内の信頼基盤に Federation を採りましたが、これは EU の枠組みの上に積んだ国の判断です。

Federation が持つ特徴は、鍵の正当性だけでなく **Metadata Policy**（何を要求してよいか）まで上位から降ろせる点です。EU の枠組みでは、同じことを登録証明書が担います。

詳しくは [EUDI Wallet の全体像](./eu-wallet-ecosystem.md) を参照してください。

## クレデンシャルの形式

| 流派 | 主な形式 | 署名 |
|---|---|---|
| ISO mDL | **mdoc**（CBOR ベース） | COSE |
| W3C VC | **JSON-LD** / JWT | JWS、BBS+ など |
| EU | **SD-JWT VC** と **mdoc** の両方 | JWS / COSE |

EU が 2 つ採用しているのが目を引きます。ARF は **mdoc と SD-JWT VC をウォレットの必須**とし、W3C VCDM 2.0 は任意としています。

免許証（mdoc）との相互運用を捨てられないので両対応になった、という経緯が読み取れます。対面の提示は mdoc だけ、リモートの提示は SD-JWT VC が主、という分担です。

形式ごとの詳細は [VC フォーマット比較](./vc-formats.md) を参照してください。

## 選択的開示

「必要な項目だけ出す」をどう実現するかも、流派で違います。

| 流派 | 仕組み |
|---|---|
| **SD-JWT VC** | ハッシュのダイジェストを並べ、出す項目の **disclosure** だけを添える |
| **mdoc** | 項目ごとに署名済みの構造を持ち、選んだものだけ送る |
| **W3C VC + BBS+** | **ゼロ知識証明**。署名そのものから部分開示を導出する |
| **AnonCreds** | ZKP。属性の値を見せずに「条件を満たす」ことだけ証明できる |

上 2 つは「**出すものを選ぶ**」、下 2 つは「**見せずに証明する**」です。

後者の方が強いプライバシー保護を与えますが、実装と検証のコストが高く、標準化も遅れました。EU が SD-JWT VC と mdoc を先に選んだのは、この現実的な差が大きいと考えられます。

## 提示のときの束縛

提示が横取りされないよう、リクエストと応答を結びつける必要があります。ここも方法が違います。

**ISO mDL** は `SessionTranscript` という構造を使います。

> ISO mDL/mdoc validation requires **SessionTranscript** and OID4VP hand-over structure for integrity and replay protection, with **DeviceResponse bound to the Verifier's request** through this mechanism

もともと NFC や BLE での対面提示を想定した仕組みで、セッション全体を文字起こしして署名対象に含めます。**OpenID4VP と組み合わせるときは hand-over 構造で橋渡し**します。

**SD-JWT VC** は KB-JWT（Key Binding JWT）に `aud` と `nonce` を入れる形で、JWT の世界の作法です。

:::warning 同じ OpenID4VP でも中身が違う
プロトコルとして OpenID4VP を使っていても、**mdoc を運ぶか SD-JWT VC を運ぶかで検証のコードは別物**になります。「OpenID4VP に対応した」だけでは相互運用の保証になりません。
:::

## 失効の表現

| 流派 | 仕組み |
|---|---|
| EU / SD-JWT VC | **Token Status List**。証明の中にリストの位置を埋め込む |
| ISO mDL | 証明書の失効（CRL / OCSP）と、短い有効期間 |
| AnonCreds | **Revocation Registry**。ZKP で「失効していない」ことを証明する |
| OpenID Federation | **Subordinate Statement を公開しない**ことで失効を表す |

AnonCreds の方式だけ性質が違います。他は「失効リストを引く」形ですが、AnonCreds は**引いたこと自体を隠せます**。プライバシー保護が徹底している反面、レジストリの運用が重くなります。

## 比較表

| | ISO mDL | W3C VC + DID | EU / eIDAS 2 |
|---|---|---|---|
| **出自** | 運転免許証 | 自己主権型アイデンティティ | 規制・国家制度 |
| **信頼の起点** | IACA ルート証明書 | DID method | LoTE / Trusted List + X.509 証明書 |
| **形式** | mdoc（CBOR） | JSON-LD / JWT | SD-JWT VC + mdoc |
| **選択的開示** | 項目ごとの署名 | BBS+（ZKP）など | disclosure（SD-JWT）/ 項目署名（mdoc） |
| **提示の束縛** | SessionTranscript | 方式による | KB-JWT / SessionTranscript |
| **対面提示** | **本命**（NFC / BLE） | 想定は薄い | Proximity Flow として取り込み |
| **発行主体の数** | 限定的（当局） | 原理的に無制限 | 認定事業者 |

## どこで交わるか

流派は無関係に並立しているのではなく、**EU が結節点**になっています。

```
ISO mDL ──┐
          ├──▶ EU / eIDAS 2（両形式を採用、OpenID4VCI/VP で運ぶ）
W3C VC ───┘        │
                   └──▶ 各国実装（IT-Wallet、ドイツ国家ウォレット など）
```

EU は mdoc を取り込むことで免許証の世界と繋がり、SD-JWT VC を採ることで JWT の世界と繋がりました。W3C VCDM は任意の形式として残っています。

そして各国が ARF の上に自国の判断を積みます。**イタリアが OpenID Federation を選んだのはその一例**で、EU の枠組みそのものではありません。

:::tip 「EU 対応」の粒度に注意
EU の枠組みに沿うと言っても、ARF のレベル、HAIP のレベル、各国実装のレベルで要求が違います。[IT-Wallet](./it-wallet.md) と [その生態系](./it-wallet-ecosystem.md) で扱う仕組みの一部は、イタリア固有です。
:::

## まとめ

**何を信頼の起点に置くかが、流派を分ける最大の軸**です。

| | 信頼を保証するもの | 向いている状況 |
|---|---|---|
| ISO mDL | 証明書階層 | 発行主体が限られ、変化が遅い |
| W3C VC + DID | 識別子の解決方式 | 中央の登録機関を置きたくない |
| EU | 一覧（LoTE）と証明書 | 規制と認定が前提にある |

そして選んだ起点が、形式・失効・開示方法まで連鎖して決まっていきます。**どこか一箇所だけ他の流派から借りてくる、ということは難しい**構造になっています。

## 参考

- [ISO/IEC 18013-5](https://www.iso.org/standard/69084.html)
- [W3C Verifiable Credentials Data Model](https://www.w3.org/TR/vc-data-model-2.0/)
- [EUDI ARF](https://eudi.dev/latest/)
- [OpenID for Verifiable Credentials（OIDF ホワイトペーパー）](https://openid.net/wordpress-content/uploads/2022/06/OIDF-Whitepaper_OpenID-for-Verifiable-Credentials-V2_2022-06-23.pdf)
- [Verifiable Credential Formats in the EUDI Wallet](https://docs.igrant.io/concepts/eudi-wallet-verifiable-credential-formats/)
- [Where can the W3C VCs meet the ISO 18013-5 mDL?](https://medium.com/@identitywoman-in-business/where-can-the-w3c-vcs-meet-the-iso-18013-5-mdl-b2d450bb19f8)
