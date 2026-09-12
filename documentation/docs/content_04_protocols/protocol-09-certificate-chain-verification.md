# 証明書チェーンの検証

## 概要

呼び出し側が提示した X.509 証明書チェーンを、判断材料として使える状態まで検証する手順です。

考え方は [証明書チェーンをどこまで信じるか](../content_03_concepts/06-security-extensions/concept-05-certificate-chain-trust.md) を参照。ここでは実際の検査順序と、プラットフォームごとの差を書きます。

実装は `X509CertificateChain`（`idp-server-platform`）。

---

## 2つの入口

信頼点をどう渡すかで 2 つあります。チェーンにルートが含まれるかどうかで決まります。

| メソッド | チェーンの内容 | 信頼点の渡し方 | 使う側 |
|---|---|---|---|
| `verify(List<String> trustedRootSha256)` | リーフ … ルート（**ルートを含む**） | ルート DER の SHA-256（base64url） | Android Key Attestation |
| `verifyToRoot(List<X509Certificate> trustedRoots)` | リーフ … 中間（**ルートを含まない**） | ルート証明書そのもの | Apple App Attest |

ダイジェストで渡す側は、**同じ鍵で再発行されたルートを黙って受け入れない**ための形です。証明書そのものではなくバイト列を固定します。

ルートを含まない側は、チェーン内に固定対象が無いため、終端の検査が「ダイジェスト一致」ではなく「**信頼するルートの鍵での署名検証**」になります。

---

## 検査順序

```
入力: [ leaf, i1, i2, ..., (root) ]        ← 呼び出し側が提示

 1. パース
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
      verify()        : 末尾が自己署名 かつ そのダイジェストが信頼リストにある
      verifyToRoot()  : 末尾が信頼ルートの鍵で検証でき、そのルート自身も CA である

─────────────────────────────────
 6. 以降、呼び出し側が leaf() の中身を読む
```

**3 と 4 は別の検査です。** 4 だけでは「署名が繋がっている」しか言えません。末端の証明書の鍵でも別の証明書に署名できるため、3 が無いと偽のリーフをチェーンの先頭に継ぎ足せます。

### `pathLenConstraint` の数え方

`pathLenConstraint` は「その証明書より**下**にある CA 証明書の数」の上限です。リーフは CA ではないので数に入りません。

```
[ leaf , I , root ]
   0      1    2      ← index

  I が leaf を発行     → I より下の CA = 0 個  → I は pathLen >= 0 が必要
  root が I を発行     → root より下の CA = 1 個（I） → root は pathLen >= 1 が必要
```

index `i` の証明書を発行する者は index `i+1` にいて、その下にある CA はちょうど `i` 個です。

---

## プラットフォーム別

### Android Key Attestation

```
x5c = [ leaf , 中間 , ... , Google ルート ]
```

- チェーンにルートを含むので `verify(ダイジェスト)` を使う
- 既定の信頼点は同梱の Google ルート。`trusted_root_certificates` で上書きできるが、**上書きすると WARN が出る**（実質そのルートの持ち主を信頼することになるため）
- リーフから読む: attestation 拡張（OID `1.3.6.1.4.1.11129.2.1.17`）、チャレンジ、`package_names`、`signature_digests`、セキュリティレベル、インスタンス鍵

### Apple App Attest

```
x5c = [ credCert , Apple 中間 ]      ← ルートは含まれない
```

- ルートは検証側が持っているので `verifyToRoot(ルート証明書)` を使う
- リーフから読む: nonce 拡張（OID `1.2.840.113635.100.8.2`）、公開鍵

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

---

## 関連ドキュメント

- [証明書チェーンをどこまで信じるか](../content_03_concepts/06-security-extensions/concept-05-certificate-chain-trust.md) — 考え方
- [Attestation-Based Client Authentication](./protocol-08-attestation-based-client-authentication.md) — チェーン検証を使う側
