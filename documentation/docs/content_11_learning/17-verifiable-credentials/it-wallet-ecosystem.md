---
sidebar_position: 34
---

# IT-Wallet の生態系: クレデンシャルはどう流れるか

[ウォレットはどうやって「本物」を証明するか](./wallet-attestation.md) と [IT-Wallet を読む](./it-wallet.md) では、ウォレットの真正性と信頼基盤を見ました。この記事では、**クレデンシャルが実際にどう流れるか**を追います。

生態系が存在する目的はクレデンシャルの発行と提示です。信頼基盤はそのための土台にすぎません。

:::warning これは IT-Wallet の設計であって、唯一の答えではありません
ウォレットの生態系には複数の流派があります。信頼の根拠、クレデンシャルの形式、失効の表現は流派ごとに違います。

| 流派 | 信頼の根拠 |
|---|---|
| **IT-Wallet** | OpenID Federation 1.0 |
| EU / eIDAS 2 のベースライン | 欧州委員会の LoTE と X.509 証明書 |
| ISO mDL（18013-5 / -7） | IACA ルート証明書（X.509） |
| W3C VC + DID | DID method |

この記事が扱うのは **1 行目だけ**です。とくに「信頼基盤は OpenID Federation」は IT-Wallet の選択であり、EU ARF や HAIP が義務づけているものではありません。

EU の枠組みそのものは [EUDI Wallet の全体像](./eu-wallet-ecosystem.md)、形式ごとの違いは [VC フォーマット比較](./vc-formats.md)、識別子の考え方は [DID](./did.md) を参照してください。
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

## 登場するシステム

上の図を、実際に動くシステムの単位まで開きます。技術仕様 v1.4.7 に基づきます。

システムは 2 つの文脈に分かれます。

| 文脈 | 何をするか | 中心になるシステム |
|---|---|---|
| **Wallet の文脈** | ウォレットを持ち、管理する | Wallet Provider |
| **VC の文脈** | クレデンシャルを受け取り、使う | Credential Issuer と Relying Party |

2 つをつなぐのは、Wallet の文脈で発行される **WIA と KA** です。VC の文脈では、ウォレットはこれを見せて自分の正当性を示します。

### Wallet の文脈: ウォレットを持つ

```
                    ┌──────────┐
     PIN / 生体で ┌─│  利用者   │───────────────────────────────────┐ Web ポータルにログイン
     ロック解除    │ └──────────┘                                   │（2 要素以上）
                  ▼                                                  ▼
┌─ 利用者の端末 ───────────────────────────────┐             ┌─ Wallet Provider ──────┐
│ Wallet Instance（アプリ）                    │  ① 登録     │ ・Nonce                │
│  持っているもの:                             │───────────▶ │ ・端末の登録           │
│  ・ハードウェア鍵（登録用）                  │  ② KA 要求  │ ・KA / WIA の発行      │
│  ・クレデンシャル用の鍵                      │───────────▶ │ ・WIA の Status List   │
│  ・KA / WIA                                  │  ③ WIA 要求 │ ・利用者アカウント     │
│         │                  │                 │───────────▶ │  （Web ポータル）      │
│  鍵の生成と署名        証明を頼む            │◀─────────── └────────────────────────┘
│         ▼                  ▼                 │   KA / WIA               ┊ 認証に使うかは
│ ┌──────────────────┐ ┌─────────────────────┐ │                          ┊ Wallet Provider
│ │ Keystore         │ │ OS の証明機能       │ │                          ┊ の選択
│ │ Secure Enclave / │ │ Key Attestation API │ │                          ▼
│ │ StrongBox / TEE  │ │ Device Integrity    │ │                   ┌──────────────┐
│ └──────────────────┘ │ Service             │ │                   │ 国の IdP     │
│                      └─────────────────────┘ │                   │（SPID / CIE）│
│                       ↑ 端末メーカー製。     │                   └──────────────┘
│                         Federation の外      │
└──────────────────────────────────────────────┘

 KA と WIA は、VC の文脈でウォレットが自分の正当性を示すのに使う
```

| システム | 役割 |
|---|---|
| **Wallet Instance** | 利用者の端末で動くアプリ。鍵は自分で持たず、Keystore に生成と署名を頼む |
| **Keystore** | 端末に組み込まれた鍵の保管領域（Android の TEE / StrongBox、iOS の Secure Enclave）。仕様 v1.4.7 は、これを高い認証水準の **WSCD** とは区別して Keystore と呼び、利用者の鍵はここに置くとしている（初期化の章には WSCD と書いた箇所も残っている） |
| **Key Attestation API / Device Integrity Service** | 端末メーカー（Apple、Google）が OS に組み込んだ仕組み。「この鍵はハードウェアにある」「このアプリは改ざんされていない」を署名付きで証明する |
| **Wallet Provider Backend** | Wallet Instance を登録し、KA と WIA を発行する。Wallet Instance の失効は、WIA の Status List で表す |
| **利用者アカウント** | 有効化のときに作られ、Wallet Instance と紐付く。利用者は Wallet Provider の **Web ポータル**にログインし、端末が無くても失効を頼める。ポータルはアプリからも外部ブラウザからも使え、ログインには 2 要素以上の認証が必須 |
| **国の IdP** | 仕様が定めるのはポータルの認証が 2 要素以上であることだけで、方式は Wallet Provider の選択。国の IdP を使うかどうかも含めて決まっていない |

国の IdP は、有効化のときにもう一度出てきます。PID を受け取るためのログインで、こちらは相手が Wallet Provider ではなく **PID Provider** です。そのため、次の VC の文脈の図に描いています。

端末メーカーの仕組みは Federation の外にあります。

> they do not need to be registered as Federation Entities through national registration systems

OS に組み込まれていて、専用のエンドポイントも持たないからです。Wallet Provider は、各メーカーが定める手順に従って検証します。

### VC の文脈: クレデンシャルを受け取り、使う

```
                                          ┌──────────┐
                                          │  利用者   │
                                          └────┬─────┘
                                               │ 有効化のときにログイン
                                               ▼
                                       ┌──────────────┐
                                       │ 国の IdP      │
                                       │（CieID）       │
                                       └──────▲───────┘
                                              │ 利用者認証
 ┌──────────────────┐  ① 発行を要求    ┌──────┴────────────┐
 │ Wallet Instance   │ ──────────────▶ │ Credential Issuer  │
 │（WIA / KA を持つ）  │   WIA を提示     │ Authorization      │
 │                   │ ◀────────────── │   Server           │
 │                   │ ② クレデンシャル  │ ・Credential        │
 │                   │  （鍵に束縛）     │ ・Nonce             │
 └────────┬──────────┘                 │ ・Notification      │
          │                            │ ・Status List       │
          │ ③ 提示                      └──────┬────────────┘
          ▼                                   │ 属性の取得
 ┌──────────────────┐                         │（PDND 経由）
 │ Relying Party     │                         ▼
 │ ・RP Instance     │                 ┌──────────────────┐
 │  （Web / アプリ）   │                 │ Authentic Source  │
 │ ・RP Backend      │                 │（PID なら ANPR）    │
 └────────┬──────────┘                 └──────────────────┘
          │
          │ ④ 失効状態を確認
          └──────▶ Issuer の Status List（誰が引いたかは Issuer に伝わらない）
```

| システム | 役割 |
|---|---|
| **Credential Issuer** | OAuth の認可サーバーとクレデンシャルの発行口を持つ。ウォレットを WIA で確かめてから発行する。失効状態を Status List として公開する |
| **国の IdP（CieID）** | 電子身分証 CIE による本人認証。PID の発行では保証レベル High（CIE L3）が必須。利用者は Issuer の認可フローの中でここにログインする |
| **Authentic Source** | 属性の正本を持つ。公的機関の場合、Issuer とのやりとりは国のデータ連携基盤 **PDND** を通す |
| **Relying Party** | 利用者が触れる RP Instance と、証明書などを管理する RP Backend に分かれる |

### 共通の信頼基盤

どちらの文脈でも、参加者の正当性は OpenID Federation で確かめます。

```
 ┌─ 信頼基盤 ─────────────────────────────────────────────┐
 │ OpenID Federation（Trust Anchor / Intermediate）            │
 │ Digital Credentials Catalog と各種レジストリ                 │
 └────────────────────────────────────────────────────────┘
      ▲              ▲                ▲
 Wallet Provider  Credential Issuer  Relying Party
```

Wallet Instance と端末メーカーの仕組みは、ここに参加しません。

### 利用者とはどこで結びつくか

ウォレットは個人に結びつきますが、結びつきは 3 か所に分かれていて、それぞれ手段が違います。

| どこで | 何と何が | 手段 |
|---|---|---|
| **Wallet Provider** | Wallet Instance と利用者アカウント | 有効化のときにハードウェア鍵のタグと紐付ける。**管理と失効のため** |
| **国の IdP** | PID と実在の本人 | CieID（CIE L3）で認証する。**「誰か」が決まるのはここ** |
| **端末** | 各クレデンシャルと端末の鍵 | 鍵への束縛。提示には鍵の所持証明が要るので、**盗んでも使えない** |

そのうえで、結びつきが**外に漏れない**ように作られています。

- WIA には本人を特定する情報を入れない。Issuer や RP が「誰か」を知るのは、利用者が提示した PID からだけ
- PIN や生体でのロック解除は、アプリを開くことと操作の承認に使う。Wallet Provider のポータルへの 2 要素認証とは別に定められている
- 同じ Wallet Solution で Valid になれるのは、1 人につき 1 つの Wallet Instance だけ。新しい端末で PID を受け取ると、前の端末の PID は失効する

端末メーカーの仕組みが Federation の外にあるのが目を引きます。

> they do not need to be registered as Federation Entities through national registration systems

OS に組み込まれていて、専用のエンドポイントも持たないからです。Wallet Provider は、各メーカーが定める手順に従って検証します。

## 事前準備: ウォレットを有効化する

利用者から見ると、有効化は「アプリを入れて、**国の IdP でログインし**、身元のクレデンシャルを受け取る」ことです。仕様の機能要件は、順番をこう定めています。

```
 1. アプリをダウンロードする
 2. ロック解除の PIN（または生体認証）を設定する
 3. 規約と各ポリシーを読んで同意する
 4. 認証方法を選ぶ
 5. 国の IdP で認証する
 6. 受け取る PID（または IT-Wallet ID）の内容を確認する
 7. ロック解除の方法で承認する
 8. 有効化が完了する
```

公的ウォレットの IO について、デジタル変革局は「身元は常に CIE か SPID の認証で確認される」と説明しています。

裏側では、4 つの段階が順に動きます。①〜③は端末の証明で、利用者が誰かはまだ関係しません。

```
 Wallet Instance        端末メーカーの仕組み           Wallet Provider Backend
      │                                                     │
 ① 初期化（インストール直後に 1 回）
      │ ─────────────────── nonce を要求 ─────────────────▶ │
      │ ◀────────────────── nonce ──────────────────────── │
      │ ハードウェア鍵を生成                                   │
      │ ── 鍵を証明して ──▶ Key Attestation API                 │
      │ ◀── key_attestation（メーカーの署名）                    │
      │ ────── key_attestation + hardware_key_tag ────────▶ │
      │                                          鍵と端末を検証し、
      │                                          Wallet Instance を登録
      │                                                     │
 ② Key Attestation の取得（クレデンシャル用の鍵を証明してもらう）
      │ クレデンシャル用の鍵を生成                               │
      │ ── nonce と鍵に束縛して ──▶ Device Integrity Service    │
      │                          （Android は Key Attestation API も）
      │ ◀── integrity_assertion / key_attestation             │
      │ ───────────── KA の発行を要求 ────────────────────▶ │
      │ ◀──────────── KA（1 か月以上有効）───────────────── │
      │                                                     │
 ③ Wallet Instance Attestation の取得（必要になるたびに）
      │ 使い捨ての鍵を生成                                     │
      │ ── nonce と鍵に束縛して ──▶ Device Integrity Service    │
      │ ◀── integrity_assertion                               │
      │ ───────────── WIA の発行を要求 ───────────────────▶ │
      │ ◀──────────── WIA（24 時間未満）──────────────────── │
```

④で初めて利用者が登場します。国の IdP にログインする相手は、Wallet Provider ではなく **PID Provider** です。

```
 Wallet Instance              PID Provider                  国の IdP
      │                            │                            │
 ④ 身元クレデンシャルの取得
      │ ── WIA を添えて認可を要求 ──▶ │                            │
      │                     WIA でウォレットを確認                  │
      │                            │ ─────── 利用者認証 ──────▶ │ CieID
      │                            │ ◀──────── 結果 ────────── │（CIE L3）
      │                            │ ── 属性の取得 ──▶ ANPR       │
      │ ◀──── PID（鍵に束縛）────── │                            │
```

| 段階 | 得るもの | 何を証明するか | 状態 |
|---|---|---|---|
| ① 初期化 | Wallet Provider への登録 | このアプリと、そのハードウェア鍵が本物であること | |
| ② KA | Key Attestation | クレデンシャルを束縛する鍵が、安全な領域で守られていること | |
| ③ WIA | Wallet Instance Attestation | この Wallet Instance が今も健全で、失効していないこと | Operational |
| ④ PID | 身元のクレデンシャル | 国の IdP で認証された本人であること | **Valid** |

PID Provider はウォレットを WIA で確かめるので、①〜③が先に済んでいる必要があります。PID の場合、利用者認証は **CieID の保証レベル High（CIE L3）が必須**です。④の詳しい流れは、次の「発行」で追います。

②と③では、毎回 ①で登録したハードウェア鍵でも署名します。Wallet Provider はその署名を、登録済みの公開鍵で確かめます。**「登録済みの同じ端末からの要求か」**を毎回確認しているわけです。

これとは別に、①と同じ段階で **Wallet Provider にも利用者アカウント**が作られ、ハードウェア鍵のタグと紐付けられます。紛失したときに、利用者が Wallet Provider に失効を頼めるようにするためです。Wallet Provider の Web ポータルへのログインには 2 要素以上の認証が必須ですが、その方式は Wallet Provider に委ねられています。

nonce と鍵を一緒に署名する理由と、WIA と KA を分ける理由は [ウォレットはどうやって「本物」を証明するか](./wallet-attestation.md) を参照してください。

## 発行: OpenID4VCI の Authorization Code Flow

IT-Wallet は発行に [OpenID4VCI](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html) の Authorization Code Flow を使います。PID（基礎的な身元クレデンシャル）も (Q)EAA（資格や属性）も、**同じ型**で流れます。

| フェーズ | 何が起きるか |
|---|---|
| **1-3. 発見と信頼確立** | ウォレットが Issuer を発見し、正当性を検証する。**同時に Issuer 側もウォレットの正当性を検証する** |
| **4. 利用者認証** | Issuer が利用者を認証する |
| **5. データ取得** | Issuer が **Authentic Source** から属性を取得する |
| **6. 発行** | 「requesting Wallet Instance が保持する鍵材料に束縛された」クレデンシャルを発行する |

### システムで見ると（PID の場合）

```
 Wallet Instance            Credential Issuer               外部
      │                   （Authorization Server）
      │ ① Federation で Issuer を確認し、メタデータを取得
      │                          │
      │ ② PAR ──────────────────▶│  WIA と、その所持証明を添える
      │ ◀────────── request_uri  │  → Wallet Provider の鍵で WIA を検証
      │                          │
      │ ③ 認可リクエスト ─────────▶│ ── 利用者認証 ──▶ CieID（CIE L3）
      │ ◀──────────── code       │
      │                          │
      │ ④ トークン要求 ──────────▶│  DPoP + Client Attestation ヘッダ
      │ ◀──── Access Token       │  （ここでも WIA を提示する）
      │                          │
      │ ⑤ nonce を取得 ──────────▶│
      │ ⑥ クレデンシャル要求 ─────▶│ ── 属性の取得 ──▶ ANPR（PDND 経由）
      │   （鍵の所持証明つき）       │
      │ ◀──── PID（鍵に束縛）      │  Status List にインデックスを割り当てる
      │                          │
      │ ⑦ 受け取りを通知 ─────────▶│
```

Issuer がウォレットを確かめる手段は、**OAuth のクライアント認証**です。WIA を `OAuth-Client-Attestation` ヘッダで送り、Wallet Instance の鍵で作った所持証明を添えます。ウォレットは Issuer に事前登録されたクライアントではありませんが、Wallet Provider の署名がそれを補います。

(Q)EAA の場合は、③の利用者認証が変わります。Issuer は CieID の代わりに、**ウォレットに入っている PID の提示を OpenID4VP で求めます**（Issuer の方針によっては IT-Wallet ID でもよい）。ウォレットの中の身元クレデンシャルが、次のクレデンシャルを受け取る鍵になっているわけです。

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

### システムで見ると（Cross Device の場合）

```
 PC のブラウザ            Relying Party                 Wallet Instance
      │                                                    （スマホ）
      │ ① ログイン ───────────▶│                                │
      │ ◀── QR コード付きの画面   │                                │
      │   （状態を監視する JS）    │                                │
      │                         │ ◀──── ② QR を読み取る ──────── │
      │                         │                                │ Federation で
      │                         │                                │ RP を確認
      │                         │ ◀── ③ Request Object を取得 ── │
      │                         │ ── 署名済み Request Object ──▶ │
      │                         │                                │ ④ 利用者が
      │                         │                                │   同意・選択
      │                         │ ◀── ⑤ 暗号化した応答 ────────── │
      │                         │   （direct_post.jwt）           │
      │                         │                                │
      │                    ⑥ 検証: Issuer の信頼 / 所持証明 /      │
      │                       Status List で失効状態               │
      │                         │                                │
      │ ── ⑦ 状態を問い合わせ ──▶│                                │
      │ ◀── 完了 → 元の画面へ     │                                │
```

ウォレットの応答はブラウザを通らず、RP に直接届きます。ブラウザは RP の状態確認エンドポイントを見て、完了を知ります。Same Device の場合は QR の代わりにリダイレクトやリンクでウォレットを起動し、残りは同じです。

### RP を個別に登録しない

面白いのは、Issuer やウォレットが **RP を個別に登録しなくてよい**ことです。RP は Registration Body に一度登録されれば、あとは Federation の中で身元を示せます。

> The specification requires no explicit pre-registration for federation-based parties — trust derives from **federation membership**

`client_id` が `openid_federation` プレフィックスなら、Trust Chain の中の Entity Configuration の `sub` と一致することを検証します。つまり**「誰か」は Federation が保証する**ので、Issuer や Wallet が個別に RP を登録する必要がありません。

さらに、

> The Wallet validates the Relying Party's eligibility through **policies obtained via trust chains**, ensuring they're **authorized to request specific credentials**

**「その RP がそのクレデンシャルを要求してよいか」まで Trust Chain から降りてきます。** [IT-Wallet の記事](./it-wallet.md) で見た Metadata Policy が、ここで効きます。身元が本物でも、要求してよい範囲は別に決まる。

EU の枠組みでも、ウォレットは RP を個別に登録しません。違うのは運び方で、EU は身元を**アクセス証明書（X.509）**、要求してよい範囲を**登録証明書**で運びます。IT-Wallet は両方を **Trust Chain** で運びます。

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

## 失効と更新はどう伝わるか

発行して終わりではありません。属性が変わったり、ウォレットが失効したりすると、クレデンシャルの状態も変わります。

```
 Authentic Source ── 属性の変更・無効化 ──▶ PDND Signal Hub
                                              │
                                              ▼
 Wallet Provider ── Wallet Instance の失効 ──▶ Credential Issuer
                                              │
                                   Status List を更新（失効・一時停止）
                                              │
                          ┌───────────────────┴──────────────────┐
                          ▼                                      ▼
                   Wallet Instance                         Relying Party
                   状態を確かめ、必要なら                     提示を受けたときに
                   再発行を受ける                            失効状態を確かめる
```

クレデンシャルを失効させる理由には、次のようなものがあります。

| 理由 | 起点 |
|---|---|
| 属性が変わった、無効になった | Authentic Source（Signal Hub で通知） |
| ウォレットが失効した | Wallet Provider |
| 利用者が求めた | 利用者（ウォレットか、Issuer の Web サービスから） |
| 発行時の本人認証に使った ID が盗まれた（PID など） | Identity Provider |
| 違法行為が確認された | 司法機関、監督機関 |
| 鍵が危殆化した | Credential Issuer |

どの経路でも、最後は **Issuer の Status List** に集まります。RP はそれを引くだけで、どの理由で失効したかも、誰が引いたかも Issuer に伝わりません。

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
