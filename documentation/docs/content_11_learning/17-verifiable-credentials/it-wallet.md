---
sidebar_position: 33
---

# IT-Wallet を読む: 国家規模のウォレット生態系はどう設計されるか

[EUDI Wallet の全体像](./eu-wallet-ecosystem.md) で見たとおり、EU の枠組みは「何を満たすべきか」を定めますが、「どう作るか」の多くを各国に残しています。[ウォレットはどうやって「本物」を証明するか](./wallet-attestation.md) で見たとおり、インストール直後の信頼をどう確立するかさえ規定がありません。

では実際に作るとどうなるのか。イタリアの **IT-Wallet** は、その空白を具体的に埋めた数少ない公開仕様です。この記事では、まず制度としての全体像を押さえ、そのうえで「国家規模のウォレット生態系を設計するとどんな問題を解くことになるか」を読み取ります。

:::note この記事の読み方
前半は制度と現状、後半は信頼基盤の**設計判断とその理由**です。後半の問題は、ウォレットに限らず「多数の事業者が参加する認証基盤」を作るときに繰り返し現れます。

現状の数字や進捗は **2026 年 9 月時点**のものです。技術仕様は v1.4.7（2026-09-22）を参照しています。
:::

## 全体像

### 制度としての位置づけ

IT-Wallet はイタリアの法令（Decree-Law No.19, 2024-03-02 が導入したデジタル行政法典 第 64-quater 条）に根拠を持つ**国家制度**です。技術仕様は義務的な Guidelines を補完するもので、規制の枠組みの一部として位置づけられます。

```
 ┌─ EU ───────────────────────────────────────────────┐
 │ Regulation (EU) 2024/1183（eIDAS 2）                  │
 │ EUDI Wallet の枠組み。加盟国に提供を義務づける          │
 └──────────────────────┬─────────────────────────────┘
                        │ 段階的に寄せる
 ┌─ イタリア ────────────▼─────────────────────────────┐
 │ デジタル行政法典 第 64-quater 条                      │
 │   （Decree-Law No.19, 2024 で導入）                   │
 │        │                                             │
 │        ▼                                             │
 │ Guidelines（義務的）                                  │
 │        │ 補完する                                     │
 │        ▼                                             │
 │ 技術仕様（eid-wallet-it-docs）                         │
 └─────────────────────────────────────────────────────┘
```

> a system allowing **natural or legal persons** to access public and private services through the secure presentation of Digital Credential

**法人も対象**に含まれます。個人向けの身分証明だけでなく、企業が資格や権限を示す用途まで視野に入っています。

EU との関係は「段階的に寄せる」という立場です。

> progressive and controlled adoption / gradual alignment with European specifications

先に作って、EU 仕様の確定に合わせて調整していく。仕様が固まるのを待たずに動く、という判断です。

### 誰が何を担うか

```
 ┌─ 制度 ─────────────────────────────────────────────────────────┐
 │ デジタル変革局: 推進と調整    AgID: Guidelines の提案、民間の認定規則   │
 │ ACN / Garante: 規則づくりに参加                                     │
 └────────────────────────────────────────────────────────────────┘

  Authentic Source       Credential Issuer      Wallet               Relying Party
 ┌────────────┐  属性  ┌────────────┐  発行  ┌──────────────┐ 提示 ┌────────────┐
 │ MIT        │───────▶│ IPZS       │───────▶│ IO（PagoPA）  │─────▶│ 対面での   │
 │ MEF        │        │ 公的な      │        │ 民間（試験中） │      │ 確認       │
 │ INPS など   │        │ 電子証明    │        └──────────────┘      └────────────┘
 └────────────┘        └────────────┘

 ┌─ 信頼基盤 ─────────────────────────────────────────────────────┐
 │ OpenID Federation。中央インフラは IPZS が運営する                     │
 └────────────────────────────────────────────────────────────────┘
```

| 組織 | 担うこと |
|---|---|
| **デジタル変革局**（Dipartimento per la trasformazione digitale） | 制度全体の推進と調整。技術仕様を公開する |
| **AgID**（イタリア・デジタル庁） | Guidelines の提案。民間事業者の参加と認定の規則を定める |
| **IPZS**（国立印刷造幣局） | 中央インフラの運営と、公的な電子証明の発行 |
| **PagoPA** | 行政アプリ **IO** を運営し、**公的ウォレット**を提供する |
| **ACN**（国家サイバーセキュリティ庁）、**Garante**（データ保護当局） | 実施のための規則づくりに加わる |

技術仕様は Trust Anchor や Registration Body という**役割**を定めますが、どの組織がそれを担うかは書いていません。役割と主体を分けて書くのは、[ARF](./eu-wallet-ecosystem.md) と同じ流儀です。

### ウォレットは公的と民間の 2 本立て

```
                    IT-Wallet の仕組み（共通の信頼基盤・共通の技術仕様）
                                    │
              ┌─────────────────────┴─────────────────────┐
              ▼                                           ▼
  ┌──────────────────────┐                  ┌──────────────────────┐
  │ 公的ウォレット          │                  │ 民間ウォレット          │
  │ IO アプリ（PagoPA）     │                  │ AgID の認定を受けた事業者 │
  │ 2024-12 から全利用者へ   │                  │ 2026-05 から試験環境     │
  └──────────────────────┘                  └──────────────────────┘
```

| | 状況 |
|---|---|
| **公的ウォレット** | 行政アプリ IO に組み込まれている。2024 年 12 月に全利用者へ開放 |
| **民間ウォレット** | 制度上は認められている。2026 年 5 月から、実在しないデータを使う試験環境で検証中 |

民間ウォレットの一般提供は、国内の規則が出揃うのを待って始まります。

### 何が載っているか

IO のウォレットで使えるのは、現時点で 3 つです。

| クレデンシャル | 元データを持つ主体（Authentic Source） |
|---|---|
| 運転免許証 | インフラ交通省（MIT） |
| 健康保険証 / 欧州健康保険カード | 経済財政省（MEF） |
| 欧州障害者カード | 国立社会保障機構（INPS） |

デジタル変革局は、IO で書類を追加するとき「身元は常に CIE（電子身分証）か SPID の認証で確認される」と説明しています。紙やカードの置き換えは**任意**で、使える場面も**対面での確認**に限られています。

```
 利用者                IO アプリ                    確認する人
   │  CIE / SPID で      │                             │
   │  身元を確認 ───────▶ │                             │
   │                     │ 免許証などを追加              │
   │                     │ （元データは各省庁）           │
   │                     │                             │
   │ ────── 対面で提示 ──────────────────────────────▶ │
   │                     │                    その場で確認
```

規模はすでに大きく、2026 年 2 月時点で 1,010 万件の有効化と 1,730 万件の書類が登録され、7 月には有効化が 1,200 万件に達しました。

```
 2024-10 ─┬─ 5 万人から段階的に開放
          │
 2024-12 ─┼─ 全利用者へ開放
          │
 2025-06 ─┼─ オフラインでも使えるように
          │
 2026-02 ─┼─ 有効化 1,010 万件 / 書類 1,730 万件
          │
 2026-05 ─┼─ 民間ウォレットの試験環境が開く
          │
 2026-07 ─┴─ 有効化 1,200 万件
```

選挙人証などの追加が予告されています。技術仕様は PID を定義していますが、IO に載っている 3 つはどれも PID ではありません。

### EU の役割との対応

| ARF の役割 | IT-Wallet |
|---|---|
| Wallet Provider | PagoPA（公的）、民間事業者（試験中） |
| PID Provider / Attestation Provider | Credential Issuer として一つにまとめられている |
| Authentic Source | 省庁などの正本データ保有者。ARF と同じく発行者と別の役割 |
| Relying Party | 同じ |
| Registrar、Access CA、LoTE | **OpenID Federation**（Trust Anchor / Intermediate）で担う |

いちばん大きな違いは最終行です。EU の枠組みが X.509 の証明書と一覧で組む部分を、IT-Wallet は国内では **OpenID Federation** で組んでいます。

ただし EU の手続きから抜けるわけではありません。Wallet Provider は Wallet Solution の適合性評価を受け、EU の一覧へ通知される流れにも乗ります。**国内は Federation、EU とは一覧**、という二重構造です。

```
 ┌─ EU の枠組み ─────────────────────────────────────┐
 │ 欧州委員会の一覧（LoTE）/ Wallet Solution の適合性評価 │
 └────────────────────────▲──────────────────────────┘
                          │ Wallet Provider を通知
 ┌─ イタリア国内: OpenID Federation ──┴──────────────────┐
 │                  Trust Anchor                        │
 │                       │                              │
 │           Intermediate（Registration Body）            │
 │                       │                              │
 │   Wallet Provider / Credential Issuer / Relying Party │
 └───────────────────────┬──────────────────────────────┘
                         │ Wallet Provider が attestation を発行
                  Wallet Instance（Federation の外）
```

IT-Wallet 固有のものとしては、ほかに **IT-Wallet ID**（国内の RP 専用の身元証明で、PID ではない）や、属性やスキーマを管理する**レジストリ群**があります。

## 原則が 2 つ、設計を貫いている

### ① 発行時点で既に認証済み

> Digital Credentials refer to characteristics, qualities or properties, **already authenticated at source**

クレデンシャルは「提示のたびに真偽を問い合わせるもの」ではなく、**発行元で既に真正性が確立された属性**を運ぶ器である、という整理です。

この原則があるから、**検証がオフラインで成立します**。Verifier は Issuer に問い合わせる必要がなく、署名と失効状態だけを見ればよい。

### ② 利用は Issuer に見えない

> usage occurs **without issuer awareness**, with no usage information released to third parties as the relationship is **exclusive between the User and the Relying Party**

**Issuer は利用を追跡できません。** 関係は User と Relying Party の間で閉じます。

```
  ┌──────────┐   発行    ┌──────────┐   提示    ┌───────────────┐
  │  Issuer  │─────────▶│   User   │─────────▶│ Relying Party │
  └──────────┘          └──────────┘          └───────────────┘
        │                                             │
        └───────────── ✕ 利用の情報は流れない ─────────┘
```

これは技術的制約ではなく、**制度としての原則**です。「クレデンシャルを発行した機関が、それが使われた先を知れてしまう」構造を最初から禁じています。紙の身分証を店で見せても発行元には伝わらない、という性質をデジタルでも保つ、ということです。

:::tip この原則が技術に効いてくる
オフライン検証も、リンカビリティ対策も、失効を Status List で表すことも、すべてこの原則から降りてきます。**「Issuer に見せない」を守ろうとすると、設計の選択肢が絞られる**。
:::

## 信頼基盤: OpenID Federation

IT-Wallet の信頼モデルは **OpenID Federation 1.0** です。

> a RESTful API for distributing metadata, metadata policies, trust marks, cryptographic public keys and X.509 certificates, and the revocation status of the participants

X.509 の PKI に似ていますが、配るものが**鍵だけでなくメタデータとポリシーまで**含む点が違います。

### 3 層構造

```
        ┌─────────────────┐
        │  Trust Anchor   │  生態系全体を設定する。PKI のルート CA に相当
        └────────┬────────┘
                 │ Subordinate Statement を発行
        ┌────────▼────────┐
        │  Intermediate   │  登録機関。下位に信頼を委譲しつつ監督する
        └────────┬────────┘
                 │
        ┌────────▼────────┐
        │  Leaf Entities  │  Credential Issuer / Relying Party /
        │                 │  Wallet Provider / QTSP
        └─────────────────┘  下位に証明書を出せない
```

**Wallet Instance はこの階層に入りません。**

> Wallet Instances, as personal devices, are deemed reliable through a **verifiable attestation issued and signed by a trusted third party**

個人の端末は Federation のエンティティとして登録せず、**Wallet Provider が発行する attestation で信頼を得ます**。これが前の記事で見た Wallet Attestation です。

理由は規模です。事業者は数百のオーダーですが、端末は数千万。**同じ仕組みで扱うと破綻します。**

### Entity Configuration と Trust Chain

各参加者は `.well-known/openid-federation` に **Entity Configuration** を公開します。自己署名された文書で、中身はこうです。

| | |
|---|---|
| **JWKS** | 署名検証用の公開鍵 |
| **メタデータ** | 組織情報、エンドポイント |
| **Authority Hints** | 「私の上位はここ」という参照 |
| **Trust Marks** | 準拠を示す検証可能な証明（任意） |

自己署名なので、それだけでは何の保証もありません。**上位の Subordinate Statement と繋いで初めて意味を持ちます。**

> **Trust Chain**: a sequence of verified statements that validates a participant's compliance with the Federation

```
Leaf の Entity Configuration（自己署名）
   ↑ Authority Hints
Intermediate の Subordinate Statement（Leaf の鍵を保証）
   ↑
Trust Anchor の Subordinate Statement（Intermediate の鍵を保証）
   ↑
Trust Anchor の Entity Configuration（自己署名。ここが信頼の起点）
```

### Metadata Policy: 上位が下位を縛る

Subordinate Statement には **Metadata Policy** を入れられます。下位が主張するメタデータに**制約をかける**仕組みです。

X.509 との違いがここに出ます。証明書は「この鍵はこの主体のもの」しか言えませんが、Federation では**「この Issuer が発行してよいのはこの種類のクレデンシャルだけ」**といった制約を上位から課せます。

### Trust Marks: 準拠の表明

> Trust Marks: verifiable attestations proving **adherence to agreed-upon security, privacy, and operational standards**

「認定を受けている」ことを機械可読に示すものです。Trust Chain が「この鍵は本物か」に答えるのに対し、Trust Mark は**「この事業者は基準を満たしているか」**に答えます。

## 信頼の評価は 4 段階

```
1. Federation Entity Discovery   ディレクトリに問い合わせて有効性を確認
2. Trust Chain Verification      Trust Anchor までの暗号的な連鎖を検証
3. Trust Marks Assessment        準拠の signal を確認
4. Policy Evaluation             そのクレデンシャル種別・スコープの権限があるか
```

**2 と 4 が別**なのがポイントです。「本物である」ことと「それをしてよい」ことは違います。

そして重要な性質があります。

> Trust Chains can be **verified offline**, using one of the Trust Anchor's public keys

**Trust Anchor の公開鍵さえ持っていれば、オフラインで検証できます。** 提示のたびに Federation へ問い合わせる必要がありません。

## 失効を「公開しないこと」で表す

Federation の失効表現は独特です。

> if the Trust Anchor or its Intermediate **doesn't publish a valid Subordinate Statement**, the subject MUST be intended as **not valid or revoked**

**失効リストを配るのではなく、「有効な Statement を出し続けること」が有効性の表明**になっています。出すのをやめれば失効。

これが成立するのは、**Trust Chain が短命（典型的には 24 時間）**だからです。

| | |
|---|---|
| 長命な証明書 + 失効リスト | 失効の伝播にリストの配布が要る。取得失敗時の扱いが難しい |
| **短命な Statement + 出さない** | 再取得のたびに最新状態になる。**失効の伝播が自動** |

前の記事で見た Wallet Attestation の 24 時間も同じ思想です。**短命にすることで失効の問題を構造的に小さくする**、という一貫した判断が生態系全体に通っています。

:::warning 短命は「再取得できること」が前提
この設計は、Statement を定期的に取りに行ける前提で成立します。完全オフラインが長期間続く端末では、いずれ検証できなくなります。オフライン検証ができるのは**手元の Trust Chain が有効な間だけ**で、無期限ではありません。
:::

## オンボーディング

> all participants are Federation Entities that **MUST be registered by a Registration Body**, except for Wallet Instances

Registration Body（Trust Anchor または Intermediate）が、参加者を Federation のルールに照らして評価し、通れば Subordinate Statement を発行します。

技術仕様は、参加の手続きを段階に分けています。

```
 ① 管理上の登録       法的な立場と規制への適合を確認する
          │
          ▼
 ② 技術的な登録       参加者の種類ごとに手続きが分かれる
          │
          ▼
 ③ レジストリへの統合   属性・Authentic Source・Federation のレジストリ、
          │            クレデンシャルのカタログに載る
          ▼
 ④ Wallet Instance    Wallet Provider を通して間接的に登録される
```

**「誰をどうやって信頼するか」が仕様の章になっている**ことに意味があります。技術的に鍵を検証できることと、その鍵の持ち主を信頼してよいことは別問題で、後者は運用と制度の話です。IT-Wallet はそこを技術仕様と同じ文書に置いています。

## 設計から読み取れること

| 問題 | IT-Wallet の答え | 効いている理由 |
|---|---|---|
| 事業者をどう信頼するか | OpenID Federation の階層と Trust Chain | 数百規模なら階層で扱える |
| 端末をどう信頼するか | Federation に入れず、Provider の attestation で | 数千万を同じ仕組みでは扱えない |
| 「本物」と「権限」をどう分けるか | Trust Chain と Policy Evaluation を別段階に | 鍵の正当性と行為の認可は別 |
| 失効をどう伝えるか | 短命 Statement を出し続ける / 止める | 失効リストの配布問題が消える |
| オフラインでどう検証するか | Trust Anchor の公開鍵だけで連鎖を辿る | 提示のたびの問い合わせが不要 |
| 発行元に利用を見せない | 制度の原則として先に置く | 技術の選択肢がそこから絞られる |

**共通しているのは「規模が違うものは違う仕組みで扱う」**という判断です。事業者と端末、鍵の検証と権限の評価、長命なものと短命なもの。一つの仕組みで統一しようとせず、性質ごとに分けています。

## 参考

- [IT-Wallet Technical Specifications](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/index.html)
- [Introduction](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/introduction.html)
- [Trust Infrastructure](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/trust.html)
- [Wallet Instance Lifecycle](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/wallet-instance-lifecycle.html)
- [Onboarding System](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/onboarding-high-level.html)
- [OpenID Federation 1.0](https://openid.net/specs/openid-federation-1_0.html)
- [italia/eid-wallet-it-docs](https://github.com/italia/eid-wallet-it-docs)
- [Sistema IT-Wallet（デジタル変革局）](https://innovazione.gov.it/progetti/sistema-it-wallet/)
- [IT-Wallet（AgID）](https://www.agid.gov.it/en/it-wallet)
- [Il sistema IT-Wallet apre ai test con le aziende private（2026-07-23）](https://innovazione.gov.it/notizie/comunicati-stampa/il-sistema-it-wallet-apre-ai-test-con-le-aziende-private/)
- [IT-Wallet supera quota 10 milioni di attivazioni（2026-02-17）](https://innovazione.gov.it/notizie/articoli/it-wallet-supera-quota-10-milioni-di-attivazioni-su-app-io-17-3-milioni-di-documenti-gia-caricati/)
