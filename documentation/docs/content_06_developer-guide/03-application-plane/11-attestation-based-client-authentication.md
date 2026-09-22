# Attestation-Based Client Authentication 実装ガイド

## このドキュメントの目的

`attest_jwt_client_auth`（[draft-ietf-oauth-attestation-based-client-auth-10](https://datatracker.ietf.org/doc/draft-ietf-oauth-attestation-based-client-auth/)）が `idp-server` の中でどう組み立てられているかを、**コードを触る人向け**に説明します。

プロトコルとしての挙動・設定値・エラーは [プロトコル仕様](../../content_04_protocols/protocol-08-attestation-based-client-authentication.md) を参照してください。ここでは「どのクラスが何を担当し、どこを触れば拡張できるか」を扱います。

### 前提知識

- [10. クライアント認証実装ガイド](./10-client-authentication.md) - `ClientAuthenticator` の SPI と組み立て
- [プロトコル仕様](../../content_04_protocols/protocol-08-attestation-based-client-authentication.md) - 2つの JWT・信頼モデル・エラー

---

## 全体像

他の認証方式と違い、**2つの独立したフェーズ**を持ちます。

```
 ① 登録フェーズ（インストール時に一度）        ② 認証フェーズ（リクエストごと）
 ─────────────────────────────────────       ─────────────────────────────
 POST /{tenant}/v1/client-instances           OAuth-Client-Attestation
        ↓                                     OAuth-Client-Attestation-PoP
 PlatformAttestationVerifier                         ↓
   （端末・アプリの証明を検証）                 AttestJwtClientAuthAuthenticator
        ↓                                            ↓
 client_instance に鍵を登録                    ClientAttestationJwtVerifier
                                                     + ClientAttestationPopJwtVerifier
```

①は `registered_instance_key`（自己署名）モードのときだけ通る経路です。`attester_jwks` モードでは Client Attester が発行するため、①は不要になります。

---

## モジュール構成

| モジュール | 責務 |
|---|---|
| `idp-server-core` | 型・SPI・登録フローのユースケース。プロトコルの語彙を持つ |
| `idp-server-core-extension-attestation` | 検証の実装。JWT 2種とプラットフォーム証明 |
| `idp-server-platform` | 外部ライブラリのラッパー（JOSE / X.509 / ASN.1） |

**外部ライブラリは platform でラップする**のがこのプロジェクトの方針です。BouncyCastle を使うのは `platform.asn1` と `platform.x509` だけで、拡張モジュールはそれらの型だけを見ます。

---

## 認証フェーズの実装

### クラスの並び

| クラス | 担当 |
|---|---|
| `AttestJwtClientAuthAuthenticator` | `ClientAuthenticator` SPI の実装。2つの Verifier を順に呼ぶ |
| `ClientAttestationJwtVerifier` | Attestation JWT（draft-10 §7.1） |
| `ClientAttestationPopJwtVerifier` | PoP JWT（§7.2）。Challenge の検証も持つ |
| `ClientAttestationKeyResolvers` | `trust_source` で鍵の解決方法を切り替えるレジストリ |
| `StaticJwksClientAttestationKeyResolver` | `attester_jwks`。設定の JWKS で検証 |
| `X5cClientAttestationKeyResolver` | `x5c`。JOSE ヘッダのチェーンをピン留めしたルートまで検証し、リーフの鍵を返す |
| `RegisteredInstanceKeyResolver` | `registered_instance_key`。`kid` を instance_id として鍵を引く |

### 鍵解決の切り替え

`ClientAttestationKeyResolvers` が `ClientAttestationTrustSource` をキーにしたレジストリになっています。信頼モデルを増やすときはここに実装を足します。

```java
// ClientAttestationKeyResolvers
resolvers.put(ClientAttestationTrustSource.registered_instance_key, new RegisteredInstanceKeyResolver(...));
resolvers.put(ClientAttestationTrustSource.attester_jwks, new StaticJwksClientAttestationKeyResolver());
resolvers.put(ClientAttestationTrustSource.x5c, new X5cClientAttestationKeyResolver());
```

リゾルバは JWKS 文字列を返す契約です。`x5c` のように単一の鍵に辿り着く実装は `JwkParser.parseFromCertificate` で 1 鍵の JWKS にして返します。

:::warning attester_jwks では JWKS に kid か alg が要ります
`JsonWebSignatureVerifierFactory` は `kid` が無いと `alg` で鍵を引きます（`JsonWebKeys.findByAlgorithm`）。`client_attestation_attester_jwks` の鍵に **`kid` も `alg` も無いと、署名検証に到達する前に 401 になります**。ログからは「鍵が見つからない」以上のことが読めないので、切り分けにくい形です。

`x5c` ではこの分岐がありません。`X5cClientAttestationKeyResolver` が証明書から鍵を取り出すとき、提示された `alg` をその鍵に付けてから返します。鍵の材料は検証済み証明書のもので固定なので、ヘッダが偽った `alg` は署名検証で落ちます。
:::

:::warning registered_instance_key では kid が鍵の索引になります
`RegisteredInstanceKeyResolver` は JOSE ヘッダの `kid` を `ClientInstanceIdentifier` として扱います。`kid` が無いと鍵を解決できず、署名検証に到達する前に失敗します。クライアント実装のつまずきどころなので、401 の切り分け時はまずここを見てください。
:::

### DI の組み立て

`ClientAuthenticationHandler` は**起動時に一度だけ**組み立てて注入します（`IdpServerApplication`）。`ClientAuthenticator` の SPI は no-arg の ServiceLoader なので、依存を持つ実装は `ClientAuthenticatorFactory` 経由で `ApplicationComponentContainer` から受け取ります。

---

## 登録フェーズの実装

### PlatformAttestationVerifier SPI

プラットフォーム証明の検証は `PlatformAttestationVerifier` に切られています。

```java
public interface PlatformAttestationVerifier {
  String platform();                                  // platform_evidence.platform の値
  void verify(PlatformAttestationVerificationRequest request);
}
```

`PlatformAttestationVerificationRequest` は `tenant` / `clientConfiguration` / `challenge` / `instanceKey` / `evidence` を持つ record です。

:::danger 実装は3つの束縛をすべて確立すること
登録エンドポイントは無認証なので、**この検証がリクエストの認証そのもの**です。

1. **チャレンジ** — 証拠がこの登録のために作られたこと
2. **インスタンス鍵** — 証拠が登録しようとしている鍵を対象にしていること
3. **アプリ同一性** — 証明されたアプリがこのクライアントのアプリであること

加えて証明書チェーンはピン留めしたルートまで検証すること。検証できないものを通す実装は `attest_jwt_client_auth` 全体を無意味にします。
:::

:::danger 束縛が揃っても、鍵そのものの性質は別に確かめること
3つの束縛は「この証拠がこの登録のものか」を決めるだけで、**その鍵を登録してよいか**は決めません。次の 2 つは束縛がすべて成立していても成立しないことがあります。

| 確かめること | 通してしまうもの |
|---|---|
| 鍵がセキュアハードウェアで**生成**されたこと | 外部で生成して取り込んだ鍵。セキュアハードウェア内にあっても、生成元に複製が存在する。「鍵は端末から出ない」という ABCA の前提が崩れる |
| 鍵が**署名に使える**こと | PoP を署名できない鍵。登録は通り、最初の利用時に署名検証で落ちる |

**どちらのフィールドをどちらのリストから読むかも意味の一部です。** Android の `AuthorizationList` は 2 つあり、`softwareEnforced` は Android プラットフォームが、`hardwareEnforced` は KeyMint がセキュアハードウェア内で書きます。鍵自身の性質（`origin` / `purpose`）はハードウェア側から読まなければ、プラットフォームが知り得ないことをプラットフォームの申告で信じることになります。`attestationApplicationId` は逆で、どのアプリが要求したかを知っているのはプラットフォームです。
:::

### 登録の既定は「全拒否」

`PlatformAttestationVerifierPluginLoader` は既定で何も登録しません。verifier が1つも無ければ未知 platform として例外になり、**登録はすべて拒否**されます。無認証エンドポイントに対する安全側の既定です。

開発用の `RequestHashBindingVerifier` は環境変数 `IDP_SERVER_CLIENT_INSTANCE_DEVELOPMENT_VERIFIER` を明示したときだけ読み込まれ、有効時は WARN ログを出します。**アプリとデバイスについて何も検証しない**ので本番では使えません。

---

## Android Key Attestation の実装

現状で唯一の本番向け verifier です。

### クラス構成

| クラス | 担当 |
|---|---|
| `AndroidKeyAttestationVerifier` | SPI 実装。3つの束縛・鍵の性質・ハードウェア裏付けを判定 |
| `AndroidCertificateChain` | どのルートを信頼するかの判断 |
| `AndroidKeyAttestationExtension` | KeyDescription のスキーマ解釈 |
| `AndroidAttestationApplicationId` | package name / 署名証明書ダイジェスト |
| `AndroidKeyAttestationSecurityLevel` | `software` / `trusted_environment` / `strong_box` |
| `AndroidKeyOrigin` | `generated` / `imported` / `securely_imported` ほか（`origin`） |
| `AndroidKeyPurpose` | `sign` / `verify` / `encrypt` ほか（`purpose`） |
| `AndroidKeyAttestationConfiguration` | クライアント設定の読み取り |
| `platform.x509.X509CertificateChain` | チェーンのパース・有効期限・署名連鎖・ルート照合 |
| `platform.asn1.Asn1Node` | DER の読み取り（BouncyCastle を隠す） |

### 検証の流れ

![Android Key Attestation の検証](../../content_04_protocols/img/android-key-attestation-verification.svg)

### ASN.1 スキーマ（規範は AOSP）

```
KeyDescription ::= SEQUENCE {
    attestationVersion        INTEGER,
    attestationSecurityLevel  SecurityLevel,     -- ENUMERATED
    keyMintVersion            INTEGER,
    keyMintSecurityLevel      SecurityLevel,     -- ENUMERATED
    attestationChallenge      OCTET_STRING,
    uniqueId                  OCTET_STRING,
    softwareEnforced          AuthorizationList,
    hardwareEnforced          AuthorizationList,
}

SecurityLevel ::= ENUMERATED { Software (0), TrustedEnvironment (1), StrongBox (2) }
```

`AuthorizationList` から読むフィールドと、**どちらのリストから読むか**は次のとおりです。

| フィールド | タグ | 読むリスト | 判定 |
|---|---|---|---|
| `purpose` | `[1] EXPLICIT SET OF INTEGER` | `hardwareEnforced` | `sign`(2) を含むこと |
| `origin` | `[702] EXPLICIT INTEGER` | `hardwareEnforced` | `KM_ORIGIN_GENERATED`(0) であること |
| `attestationApplicationId` | `[709] EXPLICIT OCTET_STRING` | `softwareEnforced`（無ければ `hardwareEnforced`） | package / 署名ダイジェストの照合 |

`attestationApplicationId` だけプラットフォームが埋めるフィールドなので `softwareEnforced` 側です。逆に `origin` / `purpose` は鍵自身の性質なので、`hardwareEnforced` にあるときだけセキュアハードウェアの申告として扱います。

`SecurityLevel` は 2 か所にあり、**どちらも**設定した `min_security_level` 以上であることを要求します。

| フィールド | 主語 |
|---|---|
| `attestationSecurityLevel` | 証明書（attestation）がどこで作られたか |
| `keyMintSecurityLevel` | 鍵を保持する KeyMint がどこで動いているか |

前者だけを見ると、TEE で署名された証明書を持つソフトウェア鍵を通します。そのとき `hardwareEnforced` の中身も同じだけ信頼できないので、`origin` / `purpose` の判定ごと意味を失います。

これらは OPTIONAL なフィールドなので、`AndroidKeyAttestationExtension` は不在をエラーにせず `undefined` / 空として返し、**受け入れ可否は verifier が決めます**。「スキーマに従っていない」と「端末が報告しなかった」を混ぜないためです。verifier は報告されなかった `origin` を拒否します（フィールドが判定の材料そのもので、不在は何の証拠でもないため）。

:::warning SecurityLevel は ENUMERATED であって INTEGER ではありません
DER 上のタグが異なるため、INTEGER として読む実装は**実機のチェーンで落ちます**。テストのフィクスチャを INTEGER で組むと、テストだけ通って実機で壊れる形になります。同じ取り違えは WebAuthn のテストベクタでも報告されています。
:::

### 新しいプラットフォームを足すには

1. `PlatformAttestationVerifier` を実装する（3つの束縛 + チェーン検証）
2. `META-INF/services/org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerifier` に追加する
3. クライアント設定 `client_instance_platform_config.<platform>` に必要な項目を足す
4. 外部ライブラリが要るなら **platform 側にラッパーを作る**

iOS App Attest を足す場合、束縛②の作り方が Android と異なります。Android は「鍵そのもの」の証明書チェーンなのでリーフの公開鍵と比較すれば済みますが、App Attest の attested key は App Attest 専用鍵で、登録したい鍵とは別物です。`clientDataHash` に登録鍵を含める形で間接的に縛る必要があります。

---

## テスト

| テスト | 対象 |
|---|---|
| `AttestJwtClientAuthAuthenticatorTest` | 認証フェーズ |
| `RegisteredInstanceKeyModeTest` | 自己署名モードの追加検証 |
| `AndroidKeyAttestationVerifierTest` | 登録フェーズの3つの束縛・チェーン・レベル・鍵の性質 |
| `e2e/src/tests/spec/oauth_attestation_based_client_auth.test.js` | draft-10 の章立てに沿った準拠台帳 |

`AndroidAttestationFixture` が BouncyCastle でチェーンを合成するので、実機なしで検証ロジックを動かせます。`hardwareEnforced` も実機が埋めるとおりに埋めます。**verifier がそこから読む値を、空のリストを持つフィックスチャでは動かせない**ためです（ルート証明書に CA 制約を持たせているのと同じ理由）。

:::tip 一番効くテスト
`rejectsAChainThatDoesNotLeadToTheConfiguredRoot` — 攻撃者が自分のルートで作った、**内部的には完璧なチェーン**を拒否できることを固定しています。これが通らないと他の検証がすべて飾りになります。
:::

---

## 一次ドキュメント

| 用途 | 参照先 |
|---|---|
| ABCA 仕様 | [draft-ietf-oauth-attestation-based-client-auth](https://datatracker.ietf.org/doc/draft-ietf-oauth-attestation-based-client-auth/) |
| Android 証明の ASN.1 スキーマ（規範）| [Key and ID attestation — AOSP](https://source.android.com/docs/security/features/keystore/attestation) |
| Android 証明の検証手順・ルート証明書 | [Verify hardware-backed key pairs with key attestation](https://developer.android.com/privacy-and-security/security-key-attestation) |
| ルート証明書の取得 | `https://android.googleapis.com/attestation/root` |
| 参照実装（Java）| [platform/external/android-key-attestation](https://android.googlesource.com/platform/external/android-key-attestation/+/HEAD/README.md) |
| 参照実装（Kotlin）| [android/keyattestation](https://github.com/android/keyattestation) |

---

## 現時点の制約

| 項目 | 状態 |
|---|---|
| Google ルート証明書の同梱 | 未実施。`trusted_root_certificates` を設定しない限り拒否されます |
| 証明書の失効確認 | 未実装 |
| iOS App Attest | 未実装 |
| PoP のリプレイ検出 | `jti` は存在確認のみ。使用済みの記録は持ちません |

---

## 次のステップ

- [10. クライアント認証実装ガイド](./10-client-authentication.md) - 他の7方式
- [プロトコル仕様](../../content_04_protocols/protocol-08-attestation-based-client-authentication.md) - 設定値とエラー
