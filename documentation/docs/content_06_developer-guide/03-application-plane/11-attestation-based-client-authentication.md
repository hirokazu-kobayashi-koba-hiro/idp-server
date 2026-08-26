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
| `RegisteredInstanceKeyResolver` | `registered_instance_key`。`kid` を instance_id として鍵を引く |

### 鍵解決の切り替え

`ClientAttestationKeyResolvers` が `ClientAttestationTrustSource` をキーにしたレジストリになっています。信頼モデルを増やすときはここに実装を足します。

```java
// ClientAttestationKeyResolvers
resolvers.put(ClientAttestationTrustSource.registered_instance_key, new RegisteredInstanceKeyResolver(...));
resolvers.put(ClientAttestationTrustSource.attester_jwks, new StaticJwksClientAttestationKeyResolver());
```

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

### 登録の既定は「全拒否」

`PlatformAttestationVerifierPluginLoader` は既定で何も登録しません。verifier が1つも無ければ未知 platform として例外になり、**登録はすべて拒否**されます。無認証エンドポイントに対する安全側の既定です。

開発用の `RequestHashBindingVerifier` は環境変数 `IDP_SERVER_CLIENT_INSTANCE_DEVELOPMENT_VERIFIER` を明示したときだけ読み込まれ、有効時は WARN ログを出します。**アプリとデバイスについて何も検証しない**ので本番では使えません。

---

## Android Key Attestation の実装

現状で唯一の本番向け verifier です。

### クラス構成

| クラス | 担当 |
|---|---|
| `AndroidKeyAttestationVerifier` | SPI 実装。3つの束縛とハードウェア裏付けを判定 |
| `AndroidCertificateChain` | どのルートを信頼するかの判断 |
| `AndroidKeyAttestationExtension` | KeyDescription のスキーマ解釈 |
| `AndroidAttestationApplicationId` | package name / 署名証明書ダイジェスト |
| `AndroidKeyAttestationSecurityLevel` | `software` / `trusted_environment` / `strong_box` |
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

`attestationApplicationId` は `AuthorizationList` の `[709] EXPLICIT OCTET_STRING OPTIONAL`。プラットフォームが埋めるフィールドなので `softwareEnforced` 側に入ります。

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
| `AndroidKeyAttestationVerifierTest` | 登録フェーズの3つの束縛・チェーン・レベル |
| `e2e/src/tests/spec/oauth_attestation_based_client_auth.test.js` | draft-10 の章立てに沿った準拠台帳 |

`AndroidAttestationFixture` が BouncyCastle でチェーンを合成するので、実機なしで検証ロジックを動かせます。

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
