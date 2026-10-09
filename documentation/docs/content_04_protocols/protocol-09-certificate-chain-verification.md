# 証明書チェーンの検証

## 概要

呼び出し側が提示した X.509 証明書チェーンを、判断材料として使える状態まで検証する手順です。

考え方は [証明書チェーンをどこまで信じるか](../content_03_concepts/06-security-extensions/concept-05-certificate-chain-trust.md) を参照。ここでは実際の検査順序と、プラットフォームごとの差を書きます。

実装は `X509CertificateChain`（`idp-server-platform`）。

---

## 2つの入口

信頼点をどう渡すかで 2 つあります。

| メソッド | チェーンの内容 | 信頼点の渡し方 | 使う側 |
|---|---|---|---|
| `verify(List<String> trustedRootSha256)` | リーフ … ルート（**ルートを含む**） | ルート DER の SHA-256（base64url） | Android Key Attestation |
| `verifyToRoot(List<X509Certificate> trustAnchors)` | リーフ …（トラストアンカーを**含んでも含まなくてもよい**） | トラストアンカーの証明書（ルートでも途中の CA でもよい） | Apple App Attest、ABCA の `x5c` |

ダイジェストで渡す側は、**同じ鍵で再発行されたルートを黙って受け入れない**ための形です。証明書そのものではなくバイト列を固定します。

`verifyToRoot` は RFC 5280 のパス検証です。トラストアンカーは「名前と公開鍵」（RFC 5280 6.1.1 (d)）で、証明書の形で渡しても使うのは subject と公開鍵だけです。そのため途中の CA をトラストアンカーにでき、同じ鍵で更新した CA 証明書に差し替えても通ります。パスはトラストアンカーが発行した証明書から始まり、トラストアンカー自身はパスに入りません（RFC 5280 6.1）。

---

## 検査順序（`verify`）

```
入力: [ leaf, i1, i2, ..., root ]          ← 呼び出し側が提示

 1. パース
      枚数が上限（10 枚）を超えていれば、デコードせずに拒否
      base64 DER をすべて X509Certificate に

 2. 有効期限
      すべての証明書が checkValidity() を通ること

 3. 発行者の資格           ← ここが chain of trust の本体
      各リンク i について、発行者 = certificates[i+1] が
        BasicConstraints cA=TRUE
        pathLenConstraint >= i        （自分より下にある CA の数）
        KeyUsage keyCertSign          （拡張がある場合）

 4. リンク署名
      certificates[i] が certificates[i+1] の公開鍵で検証できること

 5. 終端
      末尾が自己署名 かつ そのダイジェストが信頼リストにある

─────────────────────────────────
 6. 以降、呼び出し側が leaf() の中身を読む
```

**3 と 4 は別の検査です。** 4 だけでは「署名が繋がっている」しか言えません。末端の証明書の鍵でも別の証明書に署名できるため、3 が無いと偽のリーフをチェーンの先頭に継ぎ足せます。

### `verifyToRoot` の検査順序

パスの組み立てと検証は、Java 標準の PKIX 実装（`CertPathBuilder`）に任せます。提示されたチェーンはパスそのものではなく、パスを組み立てる**材料**として渡します。

```
入力: [ leaf, ... ]                         ← 呼び出し側が提示（順序は leaf が先頭）

 1. パース（verify と同じ。枚数の上限もここ）

 2. リーフの検査
      CA でない（BasicConstraints cA=TRUE でない）
      自己署名でない
      ← トラストアンカーだけのチェーンで、CA 自身の鍵を署名鍵として通さないため

 3. パスの組み立てと検証（PKIX）
      リーフからトラストアンカーが発行した証明書までの道を、提示された証明書から探す
      各証明書について RFC 5280 6.1 の検査
        署名、有効期限、issuer と上の subject の一致、
        BasicConstraints / pathLenConstraint / KeyUsage keyCertSign
      トラストアンカーより上の証明書は読まない（有効期限も見ない）
      失効確認はしない

 4. トラストアンカーの検査（RFC 5280 の要求ではなく、設定の誤りを通さないため）
      有効期限内
      CA であり、pathLenConstraint >= 組み立てたパスの CA の数

─────────────────────────────────
 5. 以降、呼び出し側が leaf() の中身を読む
```

### 枚数の上限

チェーンは送り手が認証される前に届くため、枚数は送り手が決められます。証明書 1 枚ごとにデコードと、パスの組み立てでは発行者の候補ごとの署名検証がかかるため、`parse` の時点で 10 枚を超えるチェーンをデコードせずに拒否します。`verify`（Android）と `verifyToRoot`（Apple / ABCA）の両方に効きます。実際のチェーンは数枚です（Apple App Attest は 2 枚、Android Key Attestation は 5 枚程度まで）。

### `pathLenConstraint` の数え方

`pathLenConstraint` は「その証明書より**下**にある CA 証明書の数」の上限です。リーフは CA ではないので数に入りません。

```
[ leaf , I , root ]
   0      1    2      ← index

  I が leaf を発行     → I より下の CA = 0 個  → I は pathLen >= 0 が必要
  root が I を発行     → root より下の CA = 1 個（I） → root は pathLen >= 1 が必要
```

index `i` の証明書を発行する者は index `i+1` にいて、その下にある CA はちょうど `i` 個です。

`verifyToRoot` では、トラストアンカーの下の CA の数を**組み立てたパス**から数えます。提示されたチェーンにトラストアンカーやその上のルートが入っていても、それはパスに入らないので数えません。

---

## プラットフォーム別

### Android Key Attestation

```
x5c = [ leaf , 中間 , ... , Google ルート ]
```

- チェーンにルートを含むので `verify(ダイジェスト)` を使う
- 既定の信頼点は同梱の Google ルート。`override_root_certificates` で**置き換え**られる（足すのではない）が、**置き換えると WARN が出る**（実質そのルートの持ち主を信頼することになるため）。テスト用の設定
- リーフから読む: attestation 拡張（OID `1.3.6.1.4.1.11129.2.1.17`）、チャレンジ、`package_names`、`signature_digests`、セキュリティレベル、インスタンス鍵

### Apple App Attest

```
x5c = [ credCert , Apple 中間 ]      ← ルートは含まれない
```

- ルートは検証側が持っているので `verifyToRoot(ルート証明書)` を使う
- リーフから読む: nonce 拡張（OID `1.2.840.113635.100.8.2`）、公開鍵

### ABCA の `x5c`

```
x5c = [ Attester の証明書 , 発行 CA , ... ]   ← どこまで含むかは Attester 次第
```

- `client_attestation_trusted_root_certificates` の証明書をトラストアンカーにして `verifyToRoot` を使う。ルートでも、Attester の証明書を発行する途中の CA でもよい
- リーフから読む: 公開鍵（Client Attestation JWT の署名検証に使う）

---

## なぜ順序が固定なのか

リーフの拡張領域は、**呼び出し側が最も自由に書ける場所**です。チャレンジもアプリ識別子もセキュリティレベルも、攻撃者が望む値を入れられます。

信頼点まで遡れることを確認する前にそこを読むと、まだ信頼していないデータで分岐することになります。だから `leaf()` を読むのは検証を通した後だけです。

この順序は API の形にも表れていて、`verify` / `verifyToRoot` は例外を投げるだけで値を返しません。中身を取り出すのは呼び出し側が別途 `leaf()` を呼んだときです。

---

## テスト

攻撃の形そのものを `X509CertificateChainIssuerConstraintTest` で固定しています。

| ケース | 期待 |
|---|---|
| 発行者がすべて CA の正規チェーン | 通る |
| **末端証明書の鍵で署名した偽リーフを継ぎ足す** | `is not a CA` で拒否 |
| `pathLenConstraint` を超える段数 | `pathLenConstraint` で拒否 |
| `cA=TRUE` だが `keyCertSign` が無い発行者 | `keyCertSign` で拒否 |
| `verifyToRoot` に対する同じ偽リーフ | 拒否 |
| `verifyToRoot` の正規チェーン | 通る |

2 番目が本体です。全リンクの署名検証・ルートのダイジェスト一致・有効期限がすべて通るチェーンで、**発行者の資格だけが違反している**状態を作っています。

トラストアンカーの扱いは `X509CertificateChainTrustAnchorTest` で固定しています。

| ケース | 期待 |
|---|---|
| 途中の CA をトラストアンカーにして、チェーンが [リーフ] / [リーフ, その CA] / [リーフ, その CA, ルート] | どれも通る |
| 同じルートの下の別の CA が発行したリーフ | 拒否（ルートをトラストアンカーにすると通る） |
| チェーンがトラストアンカーだけ / 自己署名のリーフ | 拒否 |
| 同じ subject と鍵で更新したトラストアンカー | 通る |
| トラストアンカーより上に期限切れの証明書がある | 通る |
| 期限切れのトラストアンカー | 拒否 |
| ルート（pathLenConstraint=1）をトラストアンカーにして [リーフ, 中間, ルート] | 通る（ルート自身は数えない） |
| トラストアンカーの pathLenConstraint を超えるパス | 拒否 |
| 枚数の上限超え（11 枚）/ 上限ちょうど（10 枚） | パースの時点で拒否 / パースできる |

---

## 関連ドキュメント

- [証明書チェーンをどこまで信じるか](../content_03_concepts/06-security-extensions/concept-05-certificate-chain-trust.md) — 考え方
- [Attestation-Based Client Authentication](./protocol-08-attestation-based-client-authentication.md) — チェーン検証を使う側
