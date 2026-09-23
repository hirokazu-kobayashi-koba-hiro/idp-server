---
sidebar_position: 31
---

# ウォレットはどうやって「本物」を証明するか

クレデンシャルを発行する側から見ると、目の前のウォレットが本物かどうかは分かりません。アプリは誰でもダウンロードでき、改造版を作ることもできます。鍵が端末のセキュアハードウェアにあるのか、抜き出せるソフトウェア鍵なのかも、通信からは見えません。

この問題に対する答えが **Wallet Attestation** です。ウォレットが「私は正規のアプリで、鍵はセキュアハードウェアにあります」と主張し、それを**第三者が裏書きする**仕組みです。

この記事では、その仕組みがどう設計されているかを、EU の枠組みと各国の実装から見ていきます。

## 登場人物

```
┌──────────────────┐
│ Wallet Provider  │  ウォレットアプリの提供者。認定を受けた事業者
└────────┬─────────┘
         │ ① Wallet Attestation を発行
         ▼
┌──────────────────┐
│ Wallet Instance  │  利用者の端末にインストールされた 1 つのアプリ
└────────┬─────────┘
         │ ② Attestation を提示
         ▼
┌──────────────────┐
│ Issuer / Verifier│  クレデンシャルを発行する側、検証する側
└──────────────────┘
```

**Issuer は Wallet Provider を信頼し、Wallet Provider が個々の Instance を保証する**、という二段構えです。Issuer が全ウォレットを個別に審査するのは現実的でないので、信頼を一段挟みます。

Issuer が Wallet Provider を信頼する根拠は **Trusted List** です。認定を受けた Provider が登録され、Issuer はそのリストと照合します。

## Wallet Attestation は 2 種類ある

EU の技術仕様（TS3）は、証明を 2 つに分けています。

| | 何を証明するか | 失効の理由 |
|---|---|---|
| **WIA**（Wallet Instance Attestation） | このウォレットインスタンスが健全であること | 端末や OS の脆弱性、利用者による紛失・盗難の申告 |
| **KA**（Key Attestation） | 鍵を保管する領域（WSCD / キーストア）が健全であること | 保管領域自体の脆弱性 |

**なぜ分けるのか。** 「端末を盗まれた」と「この機種のセキュアエレメントに脆弱性が見つかった」は、起きる理由も影響範囲も違います。前者は 1 台だけ、後者はその機種すべてです。1 つの証明にまとめると、片方の理由でもう片方まで巻き込んで失効させることになります。

## 有効期間と失効

**WIA は短命です。**

> the difference between expiration time `exp` and the time the Wallet Provider verified the integrity of the Wallet Instance SHALL be less than 24 hours
>
> — EU TS3

健全性を確認してから **24 時間未満**。証明書のように何年も使うものではありません。

一方、**失効情報はもっと長く維持されます**。

> The token-level `exp` denotes when the token itself expires and SHALL NOT be interpreted as the end of the revocation maintenance period

トークンの期限と、失効情報を維持する期間は**別の概念**です。Provider は失効情報を**最低 31 日先まで**引けるように保つことが求められ、PID Provider 側は**24 時間ごとに失効を確認**します。

失効の表現には [IETF Token Status List](https://datatracker.ietf.org/doc/draft-ietf-oauth-status-list/) を使い、証明の中に「失効リストのどこを見ればよいか」という参照を埋め込みます。

:::tip なぜ「期限が短い」と「失効情報は長く維持」が両立するのか
証明が 24 時間で切れるなら失効は不要に思えますが、そうではありません。「24 時間有効な証明を受け取った Issuer が、その証明に紐づくクレデンシャルを 1 年間発行する」という状況があるからです。Issuer はクレデンシャルの有効期間中ずっと、元になった証明が失効していないかを確認し続けます。
:::

## ブートストラップ問題

ここが設計上いちばん難しいところです。

**インストール直後のウォレットは、何も持っていません。** ID もパスワードも証明書もない。その状態で Wallet Provider は「このインストールは本物か」をどう判断するのか。

EU の ARF は、この点を**規定していません**。

> during Wallet Unit activation, the Wallet Provider SHALL take measures to verify the integrity of the Wallet Instance before issuing a Wallet Instance Attestation
>
> — ARF WIAM_10

「検証せよ」とは言うが、**どう検証するかは Provider の裁量**。認定ポリシーに手順を文書化することだけが求められます。

つまり、ここは各実装が自分で設計する領域です。

## 実装例: IT-Wallet の登録フロー

イタリアの IT-Wallet は、この空白を具体的な手順で埋めています。

```
 1-3   端末内の鍵を確認 / エフェメラル鍵ペアを生成 / Provider の federation 所属を確認
 4-6   Nonce Endpoint から nonce を取得（単回・短命・予測不能）
 7     client_data = SHA256({ nonce, エフェメラル公開鍵のサムプリント })
 8-10  hardware_signature = ハードウェア秘密鍵で client_data に署名
       integrity_assertion = 端末 OEM の Device Integrity Service が署名
11-12  JWT（integrity_assertion + hardware_signature + nonce + key tag）を
       エフェメラル秘密鍵で署名し、Issuance Endpoint へ
13-17  Provider が検証：nonce の鮮度 / 登録済み instance の存在 /
       client_data の再構成 / OEM の integrity assertion / 端末のセキュリティ要件
  18   最大 24 時間の attestation を発行
```

設計として読み取れるものが 3 つあります。

**① サーバー発行の nonce が起点**

`nonce` は「予測不能」かつ「単回・短時間」と定められています。これが無いと、過去に取得した証明をいつまでも使い回せます。

**② nonce を鍵に結びつけてから署名する**

`SHA256({ nonce, 鍵のサムプリント })` をハードウェア鍵で署名する形です。nonce だけに署名すると、その署名を別の鍵の登録に流用できてしまいます。**「この nonce に対して、この鍵で」**という 2 つを 1 つの署名に畳み込むのが要点です。

**③ 二重署名**

JWT 全体はエフェメラル鍵で署名し、ハードウェア鍵は `client_data` にだけ署名します。ハードウェア鍵を毎回の通信に直接使わないための分離です。

## 実装例: ドイツ国家ウォレット

ドイツはプラットフォームの仕組みをそのまま使う形です。

| | |
|---|---|
| **iOS** | App Attest（DeviceCheck 配下）。**アプリの初回セットアップ時に 1 回だけ**実行し、生成した鍵を Apple が証明、アプリのサーバーに登録 |
| **Android** | Play Integrity の **classic request 方式**（プライバシー上の理由で指定） |

どちらも「Wallet Backend が Platform Attestation Provider の鍵で検証する」という形で、IT-Wallet の `integrity_assertion` に相当します。

## プラットフォームの証明手段

ブートストラップで使われる材料は、大きく 3 つです。

| 手段 | 何を言えるか | 性質 |
|---|---|---|
| **Android Key Attestation** | この鍵はセキュアハードウェアで生成され、このアプリのために作られた | **オフラインで検証可能**。証明書チェーンを Google のルートまで辿る |
| **Play Integrity** | このアプリは Play 経由で入手された正規版で、端末も改ざんされていない | **オンライン**。Google のサービスに依存 |
| **Apple App Attest** | この鍵は Secure Enclave にあり、このアプリのものである | Apple のルートまで検証。**インストールごとに 1 回**が想定 |

**Key Attestation と Play Integrity は答えるものが違います。** 前者は「鍵がどこにあるか」、後者は「アプリと端末が正規か」。IT-Wallet が両方を要求しているのはそのためです。

:::warning 鍵の作り直しには制約がある
Apple は `attestKey()` を「インストールごとに 1 回」と想定しており、鍵はアプリを消すまで有効です。Android 12 以降は attestation 鍵が **Remote Key Provisioning (RKP)** でプールから供給されるため、端末が長期オフラインだとプールが枯れて証明を取れないことがあります。

「必要なときにいつでも新しい鍵を作れる」という前提は置けません。
:::

## ウォレットインスタンスの状態

IT-Wallet は、インスタンスの一生を 4 つの状態で表します。

```
Installed ──アクティベーション──▶ Operational ──PID 発行──▶ Valid
    ▲                                  │                      │
    └────────── 失効 ──────────────────┴──────────────────────┘
                                       │
                                   アンインストール ──▶ Uninstalled
```

| 状態 | できること |
|---|---|
| **Installed** | 何もできない。入れただけ |
| **Operational** | PID や (Q)EAA の**発行を受けられる** |
| **Valid** | 有効な PID を持つ。クレデンシャルを**提示できる** |
| **Uninstalled** | 終端 |

面白いのは **Valid が 1 つに限られる**ことです。

> users can have only one Wallet Instance in Valid state for the same Wallet Solution

新しい端末で PID を取ると、**前の端末は削除されずに Operational へ降格**します。機種変更のたびに前の端末を消してしまうと、新端末で問題が起きたときに戻れません。「1 つだけ有効」と「履歴を残す」を両立させる形です。

## ユーザーとの紐付け

IT-Wallet では、アクティベーション時に**利用者アカウントの作成が必須**です。

> A User account MUST be created with the Wallet Provider and associated with the Wallet Instance through the Wallet Cryptographic Hardware Key Tag, subject to obtaining the User's consent.

そして Provider ポータルへのアクセスには**第二要素を含む認証**が求められます。

:::note 端末のロック解除は「認証」ではありません
IT-Wallet はウォレットのロック解除に PIN または生体認証を設定させますが、これは**端末上でアプリを開くための仕組み**であって、Wallet Provider に対して利用者を認証するものではありません。2 つは別の話です。
:::

なお、利用者認証を**いつ**行うか（登録の前か、最中か）は仕様に明記されていません。ここもブートストラップと同じく、実装の裁量が残る部分です。

## プライバシー上の制約

証明がインスタンスを一意に識別できてしまうと、複数の Issuer や Verifier が結託して**同じ利用者の行動を追跡**できます。

HAIP はこれを明確に禁じています。

> Wallet Attestations MUST NOT be reused across different Issuers. They MUST NOT introduce a unique identifier specific to a single Wallet instance.

さらに `sub` クレームについて、

> The subject claim for the Wallet Attestation MUST be a value that is shared by all Wallet instances using the present type of wallet implementation

**端末ごとに違う値にしてはいけない**、と定めています。

ここは設計上の緊張点です。**Provider は個々のインスタンスを識別して失効させたい**（そのために状態を管理している）が、**Issuer や Verifier には識別させたくない**。EU の答えは、失効を「識別子」ではなく **Status List のインデックス**で表すことでした。リストのどこを見るかは分かっても、それが誰かは分かりません。

## まとめ

| 論点 | 設計の答え |
|---|---|
| Issuer はどうやってウォレットを信頼するか | Wallet Provider を Trusted List で信頼し、Provider が個々のインスタンスを保証する |
| 何を証明するか | WIA（インスタンスの健全性）と KA（鍵保管領域の健全性）を分ける |
| 証明はどれだけ有効か | 24 時間未満。ただし失効情報は最低 31 日維持する |
| インストール直後をどう信頼するか | **仕様は規定しない**。実装はサーバー発行 nonce + プラットフォーム証明で埋めている |
| nonce をどう縛るか | nonce と鍵のサムプリントを一緒にハッシュし、ハードウェア鍵で署名する |
| 機種変更はどう扱うか | 前のインスタンスを削除せず降格させる |
| 追跡をどう防ぐか | インスタンス固有の識別子を証明に入れず、失効は Status List で表す |

## 参考

- [EUDI ARF - Wallet Unit Attestation](https://eudi.dev/latest/discussion-topics/c-rr-wallet-unit-attestations/)
- [EUDI TS3 - Wallet Unit Attestation](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/main/docs/technical-specifications/ts3-wallet-unit-attestation.md)
- [IT-Wallet Technical Specifications - Wallet Instance Lifecycle](https://italia.github.io/eid-wallet-it-docs/versione-corrente/en/wallet-instance-lifecycle.html)
- [OpenID4VC High Assurance Interoperability Profile](https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0.html)
- [German National EUDI Wallet - Establishing app integrity](https://bmi.usercontent.opencode.de/eudi-wallet/wallet-development-documentation-public/latest/Guidelines/appAttestation/)
- [Android Key Attestation](https://source.android.com/docs/security/features/keystore/attestation)
- [Apple App Attest](https://developer.apple.com/documentation/devicecheck/validating-apps-that-connect-to-your-server)
