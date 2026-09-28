---
sidebar_position: 31
---

# EUDI Wallet の全体像: EU の生態系は何でできているか

[ウォレット生態系の流派](./wallet-ecosystem-lineages.md) では、EU を「規制と国家制度から生まれた系譜」として 1 行で扱いました。この記事では、その中身を広げます。

EU Digital Identity Wallet（EUDI Wallet）の生態系には、ウォレットと発行者と検証者のほかに、**登録する人、証明書を出す人、リストを公開する人、認定する人**が登場します。三者モデルの外側にある、これらの役割が全体像の大半を占めます。

:::note この記事の基準
EU の枠組みは改訂が続いています。この記事は **ARF v3.0.0（2026 年 7 月）** に基づきます。
:::

## 文書の層

EUDI Wallet を定める文書は、拘束力の強い順に重なっています。

```
┌──────────────────────────────────────────────────────────┐
│ Regulation (EU) 2024/1183（eIDAS 2）         法的拘束力あり │
│   eIDAS 1（910/2014）を改正する規則                         │
├──────────────────────────────────────────────────────────┤
│ Implementing Regulations（実施規則）         法的拘束力あり │
│   PID と属性証明 / 認証 / プロトコル / RP 登録 など         │
├──────────────────────────────────────────────────────────┤
│ ARF（Architecture and Reference Framework）   参考         │
│   全体の設計と、役割ごとの要件を記述する                     │
├──────────────────────────────────────────────────────────┤
│ Technical Specifications（TS01〜TS14）        参考         │
│   Wallet Unit Attestation、RP 登録の形式 などの詳細         │
├──────────────────────────────────────────────────────────┤
│ 標準                                                      │
│   OpenID4VCI / OpenID4VP / HAIP / SD-JWT VC /              │
│   ISO/IEC 18013-5・-7 / ETSI（Trusted List など）           │
└──────────────────────────────────────────────────────────┘
```

目を引くのは **ARF に拘束力がない**ことです。

> This ARF is **informative** and intended to support implementation; it **does not replace** the legally binding [European Digital Identity Regulation] or its adopted implementing and delegated acts, which are the only mandatory requirements.
>
> — ARF §1.3

守る義務があるのは規則と実施規則だけで、ARF はそれを実装するための設計図です。「ARF にこう書いてある」と「EU がこう義務づけている」は同じではありません。

## 登場人物

ARF は生態系の役割を 18 に分けています。性質ごとにまとめると、こうなります。

```
 ┌─ 制度で支える ──────────────────────────────────────────────┐
 │  National Accreditation Body → Conformity Assessment Body     │
 │  Supervisory Body / Attestation Scheme Provider               │
 └──────────────────────────────────────────────────────────────┘
 ┌─ 信頼を配る ────────────────────────────────────────────────┐
 │  Registrar / Access Certificate Authority /                   │
 │  Provider of Registration Certificates / Trusted List・LoTE Provider │
 └──────────────────────────────────────────────────────────────┘

    発行する側                 持つ側                   使う側
 ┌──────────────┐                                ┌──────────────┐
 │ PID Provider │─┐                              │ Relying Party│
 │ QEAA Provider│ │  発行   ┌──────────────┐ 提示 │（Intermediary│
 │ PuB-EAA Prov.│ ├───────▶│ Wallet Unit  │─────▶│  を含む）     │
 │ EAA Provider │ │        │   （User）    │      └──────────────┘
 │ QESRC Prov.  │─┘        └──────▲───────┘
 └──────▲───────┘                 │
        │ 属性                     │ 提供
 ┌──────┴───────┐          ┌──────┴────────────────────┐
 │Authentic     │          │ Wallet Provider /          │
 │Source        │          │ Device Manufacturer        │
 └──────────────┘          └────────────────────────────┘
```

| グループ | 役割 | 担うこと |
|---|---|---|
| **持つ側** | User | Wallet Unit を管理し、PID や属性証明を受け取り、提示する |
| | Wallet Provider | 認証（certification）を受けた Wallet Solution を利用者に提供する。加盟国か、加盟国が委任・承認した組織 |
| | Device Manufacturer | 端末、OS、セキュアエレメントなどの土台を提供する |
| **発行する側** | PID Provider | PID（本人を特定するデータ）を発行する |
| | QEAA / PuB-EAA / EAA Provider | 属性証明を発行する。違いは次の節で扱う |
| | QESRC Provider | 適格電子署名（QES）をリモートで作成する環境を提供する |
| | Authentic Source | 特定の属性について、正本となるデータを持つ |
| **使う側** | Relying Party | Wallet Unit に属性を要求し、受け取る。Intermediary（仲介者）も RP とみなされる |
| **信頼を配る** | Registrar | 発行者と RP を登録する。加盟国ごとに置かれる |
| | Access Certificate Authority | 登録された主体にアクセス証明書を出す |
| | Provider of Registration Certificates | 登録内容を示す登録証明書を出す |
| | Trusted List / LoTE Provider | 信頼できる主体の一覧を公開する |
| **制度で支える** | National Accreditation Body | 適合性評価機関を認定する |
| | Conformity Assessment Body | Wallet Solution を認証し、トラストサービス事業者を監査する |
| | Supervisory Body | 各主体が適切に動いているかを監督する |
| | Attestation Scheme Provider | 属性証明の種類ごとに Rulebook（中身と形式の決まり）を定める |

> a single entity may combine multiple of the roles depicted in the figure
>
> — ARF §3.1

**役割は主体ではありません。** 一つの組織が PID Provider と Wallet Provider を兼ねることもできます。各国の実装を読むときは、「この組織はどの役割を担っているか」を分けて見ると整理しやすくなります。

### 「ウォレット」の粒度

「ウォレット」という言葉は、ARF では 3 つの粒度に分かれています。

| 用語 | 指すもの |
|---|---|
| **Wallet Solution** | 製品としてのウォレット。ソフトウェア、ハードウェア、サービスの組み合わせで、認証の対象になる単位 |
| **Wallet Unit** | 利用者 1 人に提供される構成。Wallet Instance と、鍵を守る領域（WSCA / WSCD）から成る |
| **Wallet Instance** | 端末に入ったアプリ。Web アプリの形もありうる |

鍵を守る領域（**WSCD**）は、端末内のセキュアエレメントに限りません。リモートの HSM、スマートカード、SIM / eSIM、OS の機能も選択肢として挙げられています。

[ウォレットはどうやって「本物」を証明するか](./wallet-attestation.md) で見た WIA は Wallet Instance の健全性を、KA は WSCD の健全性を証明するものです。

## クレデンシャルは 4 つに分かれる

規則は、ウォレットに入る証明を 4 つに分類しています。

| 分類 | 発行者 | 信頼の確認先 |
|---|---|---|
| **PID** | PID Provider（EU 法または国内法に基づく） | PID Provider の LoTE |
| **QEAA**（適格な属性証明） | 適格トラストサービス事業者（QTSP） | 各国の Trusted List |
| **PuB-EAA** | 正本データを持つ公的機関、またはその委託先 | PuB-EAA Provider の LoTE |
| **EAA**（非適格） | 任意のトラストサービス事業者 | ARF の範囲外。Rulebook などに委ねる |

分類の違いは技術ではなく**法律上の扱い**です。

> Please note that the differences between them are purely legal. For example, a diploma may be a QEAA or a non-qualified EAA, depending on whether it is issued by a qualified trust service provider (QTSP) or by an unqualified one.
>
> — ARF §5.2.1

同じ卒業証明書でも、誰が発行するかで分類が変わります。形式はどれも共通で、**mdoc と SD-JWT VC はウォレットの対応が必須**、W3C VCDM 2.0 は任意です。

また、**保証レベル（LoA）という概念は PID にだけ**適用されます。PID は本人確認の手段そのものなので「どれだけ確実に本人か」が問われますが、属性証明はそうではありません。

## 信頼の張り方

ここが全体像の核心です。誰が誰を、何で確かめるのか。

| 確かめる側 | 確かめられる側 | 何で | 何を起点に |
|---|---|---|---|
| 発行者 | Wallet Unit | WIA / KA | Wallet Provider の LoTE |
| Wallet Unit | 発行者 | アクセス証明書 + 登録証明書 | Access CA / 登録証明書発行者の LoTE |
| Wallet Unit | Relying Party | アクセス証明書 + 登録証明書 | 同上 |
| Relying Party | PID / 属性証明 | 発行者の署名（X.509） | 各分類の LoTE / Trusted List |

### Trusted List と LoTE

名前が似ていますが、別のものです。

| | Trusted List | LoTE（List of Trusted Entities） |
|---|---|---|
| 載るもの | QEAA Provider だけ | Wallet Provider、PID Provider、PuB-EAA Provider、Access CA、登録証明書の発行者 |
| 公開者 | 各加盟国 | 欧州委員会 |
| 準拠する標準 | ETSI TS 119 612 | ETSI TS 119 602 |

QEAA Provider は eIDAS の意味でのトラストサービス事業者なので、eIDAS 1 から続く Trusted List に載ります。それ以外はトラストサービス事業者ではないので、別の一覧（LoTE）に載ります。

LoTE に載るには、**加盟国が欧州委員会に通知**します。発行者の場合は、その前に自国の Registrar で登録を済ませておく必要があります。登録と通知は別の手続きです。

### Relying Party にはリストが無い

発行者はリストに載りますが、RP は載りません。

> There is no Trusted List or LoTE for Relying Parties. The expected number of Relying Parties in the Union would make this infeasible. Instead, a Relying Party receives one or more access certificate(s) from an Access Certificate Authority
>
> — ARF §3.5

RP は自国の Registrar に登録し、2 種類の証明書を受け取ります。

| | アクセス証明書 | 登録証明書 |
|---|---|---|
| 形式 | X.509 | JWT |
| 単位 | RP のシステム（RP Instance）ごと | 利用目的（intended use）ごと |
| 答える問い | この RP は本物か | この RP はこの属性を要求してよいか |

2 つの証明書は、同じ RP 識別子とサービス識別子を持つことで結びついています。

ウォレットは、RP が**登録した範囲を超える属性を要求したら利用者に警告**します。要求そのものは技術的に送れますが、登録内容と照合されるわけです。

:::tip 数が違えば仕組みを変える
EU は 3 種類の参加者を、3 つの別々の仕組みで扱っています。

| 参加者 | 規模 | 仕組み |
|---|---|---|
| 発行者、Wallet Provider | 限られる | 欧州委員会が一覧（LoTE）を公開する |
| Relying Party | 多い | 一覧を作らず、証明書を持たせる |
| Wallet Unit | 利用者の数だけ | Wallet Provider が attestation を発行する |

[IT-Wallet](./it-wallet.md) が Wallet Instance を OpenID Federation に入れないのと、同じ理由です。
:::

### OpenID Federation の位置づけ

ARF の本文には OpenID Federation が登場しません。PID、QEAA、PuB-EAA の署名は X.509 の PKI で検証する、というのが ARF の前提です。

> Interoperability is achieved by using a PKI following X.509 certificate standards (RFC 5280, RFC 3647) for signing PIDs, QEAAs, and PuB-EAAs. Non-qualified EAAs may adopt alternative trust models and verification mechanisms.
>
> — ARF §6.1

属性のカタログを定める TS11 は、OpenID Federation を**非適格な EAA に限って**認めています。イタリアの IT-Wallet は国内の信頼基盤に OpenID Federation を採っていますが、それは EU の枠組みの**上に**積んだ国の判断です。

## 提示の 4 つの形

| フロー | 場面 | 使うもの |
|---|---|---|
| **Proximity Supervised** | 対面。係員が立ち会う | ISO/IEC 18013-5（NFC / BLE など） |
| **Proximity Unsupervised** | 対面。機械が相手 | 同上 |
| **Remote Same-Device** | 同じ端末のブラウザやアプリから | OpenID4VP または ISO/IEC 18013-7 |
| **Remote Cross-Device** | PC の画面を端末のウォレットで | 同上 |

リモートの 2 つは、ウォレットの呼び出しに**カスタム URI スキーム**か **W3C Digital Credentials API**（ブラウザ経由）を使います。

SD-JWT VC は対面の提示には使えません。対面では mdoc だけです。

流れの具体は、[IT-Wallet の生態系](./it-wallet-ecosystem.md) で追います。

## 利用者に約束されていること

規則は、技術要件と並んで、利用者が**できること**も定めています。

| | 内容 |
|---|---|
| **任意** | 市民がウォレットを使うことは義務ではない |
| **取引ログ** | ダッシュボードで、どの RP に何を出したかを見られる |
| **削除要求** | ログから RP に、GDPR 第 17 条に基づく削除を要求できる |
| **通報** | 疑わしい RP を、各国のデータ保護当局に通報できる |
| **登録範囲の確認** | RP が登録した範囲の属性を要求しているかを知らされる |
| **仮名** | 身元の提示が法的に要らない場面では、仮名で認証できる |
| **適格電子署名** | 個人は無料で QES を作成できる |

取引ログから削除要求と通報に直接つながるのが特徴です。**プライバシーの権利を、ウォレットの機能として実装させている**わけです。

## 時間軸

| 時期 | 出来事 |
|---|---|
| 2024-05 | Regulation (EU) 2024/1183 が発効 |
| 2024-12 | 最初の実施規則群（PID と属性証明、認証、プロトコルなど）が発効 |
| 2025 | 第 2 弾の実施規則（RP 登録など） |
| 2025-12 | OpenID4VC HAIP 1.0 が Final |
| 2026-07 | ARF v3.0.0。実施規則の改正に追随 |
| **2026 年末** | **加盟国は EUDI Wallet を少なくとも 1 つ提供する** |

加盟国の期限は「実施規則の発効から 24 か月以内」と定められていて、欧州委員会は「2026 年末まで」と説明しています。

その先には、**受け入れる側の義務**があります。強い利用者認証が法的・契約的に求められる民間の RP（銀行、通信、交通、医療など）と、超大規模オンラインプラットフォームは、利用者が望めばウォレットを受け入れる必要があります。

実証は **Large Scale Pilots** で進められてきました。第 1 弾の 4 つ（POTENTIAL、EWC、DC4EU、NOBID）は終了し、第 2 弾の 2 つ（APTITUDE、WE BUILD）が進行中です。

## まとめ

| 問い | EU の答え |
|---|---|
| 何が義務か | 規則と実施規則。ARF は参考 |
| 何を発行するか | PID と 3 種類の属性証明。違いは法律上の扱い |
| 発行者をどう信頼するか | 欧州委員会の LoTE（QEAA は各国の Trusted List） |
| RP をどう信頼するか | 一覧を作らず、アクセス証明書と登録証明書を持たせる |
| ウォレットをどう信頼するか | Wallet Provider が WIA / KA を発行する |
| 利用者は何ができるか | 取引ログ、削除要求、通報、仮名、無料の QES |

**参加者の数で仕組みを変える**のが、この信頼モデルの芯です。各国はこの枠組みの上に、自国の実装を積みます。その一例が次の [IT-Wallet](./it-wallet.md) です。

## 参考

- [Regulation (EU) 2024/1183](https://eur-lex.europa.eu/eli/reg/2024/1183/oj)
- [EUDI ARF](https://eudi.dev/latest/)（[GitHub](https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework)）
- [ARF - Roles within the EUDI Wallet ecosystem](https://eudi.dev/latest/main/03-roles-within-the-eudi-wallet-ecosystem/)
- [ARF - Trust model](https://eudi.dev/latest/main/06-trust-model/)
- [EUDI Technical Specifications](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications)
- [European Digital Identity Regulation（欧州委員会）](https://digital-strategy.ec.europa.eu/en/policies/eudi-regulation)
- [Large Scale Pilot Projects](https://ec.europa.eu/digital-building-blocks/sites/spaces/EUDIGITALIDENTITYWALLET/pages/694487808/What+are+the+Large+Scale+Pilot+Projects)
- [OpenID4VC High Assurance Interoperability Profile 1.0](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
