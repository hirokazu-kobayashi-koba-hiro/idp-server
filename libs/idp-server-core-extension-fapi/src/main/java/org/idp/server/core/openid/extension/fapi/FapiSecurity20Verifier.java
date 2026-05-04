/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.extension.fapi;

import java.util.List;
import java.util.Set;
import org.idp.server.core.openid.oauth.AuthorizationProfile;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAssertionJwt;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.RedirectUri;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestVerifier;
import org.idp.server.core.openid.oauth.verifier.base.OAuthRequestBaseVerifier;
import org.idp.server.core.openid.oauth.verifier.base.OidcRequestBaseVerifier;
import org.idp.server.core.openid.oauth.verifier.rule.Rule;
import org.idp.server.core.openid.oauth.verifier.rule.RuleBasedVerifier;
import org.idp.server.core.openid.oauth.verifier.rule.RuleId;

/**
 * FAPI 2.0 Security Profile Final - Authorization Server requirements (Section 5.3.2).
 *
 * <p>FAPI 2.0 SP は FAPI 1.0 Advanced からの大きな簡素化と厳格化を含む:
 *
 * <ul>
 *   <li>{@code response_type=code} のみ (Hybrid Flow 禁止)
 *   <li>PAR (RFC 9126) 必須 — 直接 Authorization Endpoint リクエストは拒否
 *   <li>PKCE S256 必須
 *   <li>Sender-Constrained Token: mTLS または DPoP 必須
 *   <li>Public Client 禁止
 *   <li>Confidential Client 認証は mTLS または private_key_jwt のみ
 *   <li>{@code iss} parameter (RFC 9207) を Authorization Response に含める (サーバ全体で対応済み)
 *   <li>Authorization Code Binding ({@code dpop_jkt}, RFC 9449 §10) — DPoP 使用時に必須
 * </ul>
 *
 * <h3>実装方針</h3>
 *
 * <p>各検証ルールを {@link Rule} 実装として独立化。 1 ルール = 1 nested static class とすることで:
 *
 * <ul>
 *   <li>各 rule のユニットテストが verifier 全体のセットアップなしで書ける
 *   <li>新仕様追加 = rule クラス追加 + {@link #FAPI20_RULES} に 1 行追加
 *   <li>プロファイル間で rule 再利用可能 (例: 将来の派生 SP)
 * </ul>
 *
 * <h3>意図的に強制していない要件</h3>
 *
 * <p><b>{@code nonce} は必須にしていない。</b> FAPI 1.0 Advanced とは異なり、FAPI 2.0 SP は client から の {@code
 * nonce} 送信を必須化していない。
 *
 * <p><b>{@code state} も必須にしていない。</b> FAPI 2.0 では PKCE が CSRF 対策を担うため、{@code state} は CSRF
 * 用途では用いられない。
 *
 * @see <a href="https://openid.net/specs/fapi-security-profile-2_0.html">FAPI 2.0 Security Profile
 *     Final</a>
 */
public class FapiSecurity20Verifier extends RuleBasedVerifier<Fapi20Context>
    implements AuthorizationRequestVerifier {

  /** FAPI 2.0 §5.4: signing algorithms permitted on client assertions. */
  static final Set<String> ALLOWED_CLIENT_ASSERTION_ALGORITHMS =
      Set.of("PS256", "PS384", "PS512", "ES256", "ES384", "ES512", "EdDSA");

  /**
   * FAPI 2.0 で適用される rule の順序つきリスト。
   *
   * <p>fail-fast で評価されるため、より広範な前提 (PAR / response_type 等) を先に置き、 client_assertion 等の細かいチェックを後に置く。
   */
  static final List<Rule<Fapi20Context>> FAPI20_RULES =
      List.of(
          new NotPushedRequestRule(),
          new ResponseTypeCodeRule(),
          new S256CodeChallengeRule(),
          new HttpsRedirectUriRule(),
          new ConfidentialClientAuthRule(),
          new PublicClientNotAllowedRule(),
          new SenderConstrainedAccessTokenRule(),
          new ClientAssertionSigningAlgorithmRule(),
          new ClientAssertionAudIsArrayRule(),
          new ClientAssertionAudIsIssuerRule());

  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();
  OidcRequestBaseVerifier oidcRequestBaseVerifier = new OidcRequestBaseVerifier();

  @Override
  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_2_0;
  }

  @Override
  protected List<Rule<Fapi20Context>> rules() {
    return FAPI20_RULES;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }
    verifyRules(new Fapi20Context(context, null));
  }

  /**
   * Profile-aware verification at the PAR endpoint where the parsed client_assertion JWT is
   * available via {@link ClientCredentials}. Runs the same checks as {@link
   * #verify(OAuthRequestContext)} and additionally enforces FAPI 2.0 §5.4 / §5.3.2.1-2.8 strict
   * rules on the client assertion.
   */
  @Override
  public void verify(OAuthRequestContext context, ClientCredentials clientCredentials) {
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }
    verifyRules(new Fapi20Context(context, clientCredentials));
  }

  // =========================================================================
  // FAPI 2.0 §5.3.2.2.3: PAR usage required
  // =========================================================================

  /**
   * "Authorization Servers shall require Pushed Authorization Requests for all authorization
   * requests."
   *
   * <p>PAR エンドポイント自身では check 対象外 (=既に push されているため)。
   */
  static final class NotPushedRequestRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.par.required");
    }

    @Override
    public boolean appliesTo(Fapi20Context ctx) {
      return !ctx.request().isAtPushedEndpoint();
    }

    @Override
    public void verify(Fapi20Context ctx) {
      if (!ctx.request().isPushedRequest()) {
        throw new OAuthRedirectableBadRequestException(
            "invalid_request",
            "When FAPI 2.0 Security Profile, authorization requests MUST use Pushed Authorization Requests (PAR, RFC 9126). Direct authorization requests are not allowed.",
            ctx.request());
      }
    }
  }

  // =========================================================================
  // FAPI 2.0 §5.3.2.1: response_type=code only (Hybrid prohibited)
  // =========================================================================

  static final class ResponseTypeCodeRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.response-type.code-only");
    }

    @Override
    public void verify(Fapi20Context ctx) {
      if (!ctx.request().responseType().isCode()) {
        throw new OAuthRedirectableBadRequestException(
            "invalid_request",
            "When FAPI 2.0 Security Profile, only response_type=code is allowed (Hybrid flows prohibited).",
            ctx.request());
      }
    }
  }

  // =========================================================================
  // FAPI 2.0 §5.3.2.1.7: PKCE S256 required
  // =========================================================================

  static final class S256CodeChallengeRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.pkce.s256");
    }

    @Override
    public void verify(Fapi20Context ctx) {
      AuthorizationRequest authorizationRequest = ctx.request().authorizationRequest();
      if (!authorizationRequest.hasCodeChallenge()
          || !authorizationRequest.hasCodeChallengeMethod()) {
        throw new OAuthRedirectableBadRequestException(
            "invalid_request",
            "When FAPI 2.0 Security Profile, PKCE (RFC 7636) is required.",
            ctx.request());
      }
      if (!authorizationRequest.codeChallengeMethod().isS256()) {
        throw new OAuthRedirectableBadRequestException(
            "invalid_request",
            "When FAPI 2.0 Security Profile, code_challenge_method MUST be S256.",
            ctx.request());
      }
    }
  }

  // =========================================================================
  // FAPI 2.0 §5.3.2.2-2.8: redirect URIs MUST use https
  // =========================================================================

  static final class HttpsRedirectUriRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.redirect-uri.https");
    }

    @Override
    public void verify(Fapi20Context ctx) {
      RedirectUri redirectUri = ctx.request().redirectUri();
      if (!redirectUri.isHttps()) {
        throw new OAuthBadRequestException(
            "invalid_request",
            String.format(
                "When FAPI 2.0 Security Profile, redirect URIs MUST use the https scheme (%s)",
                redirectUri.value()),
            ctx.request().tenant());
      }
    }
  }

  // =========================================================================
  // FAPI 2.0 §5.3.3.4: client auth restricted to mTLS or private_key_jwt
  // =========================================================================

  static final class ConfidentialClientAuthRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.client-auth.no-client-secret");
    }

    @Override
    public void verify(Fapi20Context ctx) {
      ClientAuthenticationType type = ctx.request().clientAuthenticationType();
      if (type.isClientSecretBasic() || type.isClientSecretPost() || type.isClientSecretJwt()) {
        throw new OAuthRedirectableBadRequestException(
            "unauthorized_client",
            "When FAPI 2.0 Security Profile, client authentication MUST be one of mTLS or private_key_jwt. client_secret_* schemes are not allowed.",
            ctx.request());
      }
    }
  }

  // =========================================================================
  // FAPI 2.0 §5.3.3.1: public clients prohibited
  // =========================================================================

  static final class PublicClientNotAllowedRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.client-auth.no-public-client");
    }

    @Override
    public void verify(Fapi20Context ctx) {
      if (ctx.request().clientAuthenticationType().isNone()) {
        throw new OAuthRedirectableBadRequestException(
            "unauthorized_client",
            "When FAPI 2.0 Security Profile, public clients are not allowed.",
            ctx.request());
      }
    }
  }

  // =========================================================================
  // FAPI 2.0 §5.3.2.1: sender-constrained access tokens (mTLS or DPoP)
  // =========================================================================

  /**
   * 本実装ではサーバ設定として mTLS バインドが有効、または DPoP の署名アルゴリズムがサポートされていれば sender-constrained 化が可能とみなす。
   * 実際のバインドの有無は Token Endpoint で検証される ({@link AuthorizationCodeGrantFapi20Verifier})。
   */
  static final class SenderConstrainedAccessTokenRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.sender-constrained.capable");
    }

    @Override
    public void verify(Fapi20Context ctx) {
      AuthorizationServerConfiguration serverConfiguration = ctx.request().serverConfiguration();
      ClientConfiguration clientConfiguration = ctx.request().clientConfiguration();
      boolean mtlsCapable =
          serverConfiguration.isTlsClientCertificateBoundAccessTokens()
              && clientConfiguration.isTlsClientCertificateBoundAccessTokens();
      boolean dpopCapable = !serverConfiguration.dpopSigningAlgValuesSupported().isEmpty();
      if (!mtlsCapable && !dpopCapable) {
        throw new OAuthRedirectableBadRequestException(
            "invalid_request",
            "When FAPI 2.0 Security Profile, sender-constrained access tokens are required (mTLS or DPoP must be enabled).",
            ctx.request());
      }
    }
  }

  // =========================================================================
  // FAPI 2.0 §5.4: client_assertion signing algorithm restriction + key size
  // =========================================================================

  /** PAR endpoint で client_assertion が解釈済みの場合のみ適用 (private_key_jwt 限定)。 */
  static final class ClientAssertionSigningAlgorithmRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.client-assertion.signing-algorithm");
    }

    @Override
    public boolean appliesTo(Fapi20Context ctx) {
      return ctx.hasCredentials() && ctx.request().clientAuthenticationType().isPrivateKeyJwt();
    }

    @Override
    public void verify(Fapi20Context ctx) {
      ClientAssertionJwt clientAssertionJwt = ctx.credentials().clientAssertionJwt();
      String algorithm = clientAssertionJwt.algorithm();

      if (!ALLOWED_CLIENT_ASSERTION_ALGORITHMS.contains(algorithm)) {
        throw new OAuthBadRequestException(
            "invalid_client",
            String.format(
                "When FAPI 2.0 Security Profile (§5.4), client assertion signing algorithm must be one of %s. Current algorithm: %s",
                ALLOWED_CLIENT_ASSERTION_ALGORITHMS, algorithm),
            ctx.request().tenant());
      }

      ClientAuthenticationPublicKey publicKey = ctx.credentials().clientAuthenticationPublicKey();
      int keySize = publicKey.size();

      if (algorithm.startsWith("PS") && keySize < 2048) {
        throw new OAuthBadRequestException(
            "invalid_client",
            String.format(
                "When FAPI 2.0 Security Profile, RSA key size must be 2048 bits or larger. Current key size: %d bits",
                keySize),
            ctx.request().tenant());
      }
      if (algorithm.startsWith("ES") && keySize < 224) {
        throw new OAuthBadRequestException(
            "invalid_client",
            String.format(
                "When FAPI 2.0 Security Profile, elliptic curve key size must be 224 bits or larger. Current key size: %d bits",
                keySize),
            ctx.request().tenant());
      }
      // EdDSA: Ed25519 は 256bit / Ed448 は 456bit が標準だが、defense-in-depth で最小を 256bit と
      // して明示チェック。RFC 8037 で定義された Ed25519 / Ed448 はいずれもこの基準を満たす。
      if ("EdDSA".equals(algorithm) && keySize < 256) {
        throw new OAuthBadRequestException(
            "invalid_client",
            String.format(
                "When FAPI 2.0 Security Profile, EdDSA key size must be 256 bits or larger. Current key size: %d bits",
                keySize),
            ctx.request().tenant());
      }
    }
  }

  // =========================================================================
  // FAPI 2.0 §5.3.2.1-2.8: client_assertion aud must be string (not array)
  // =========================================================================

  static final class ClientAssertionAudIsArrayRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.client-assertion.aud-not-array");
    }

    @Override
    public boolean appliesTo(Fapi20Context ctx) {
      return ctx.hasCredentials() && ctx.request().clientAuthenticationType().isPrivateKeyJwt();
    }

    @Override
    public void verify(Fapi20Context ctx) {
      if (ctx.credentials().clientAssertionJwt().isAudArray()) {
        throw new OAuthBadRequestException(
            "invalid_client",
            "When FAPI 2.0 Security Profile (§5.3.2.1-2.8), client assertion aud claim must be a string, not an array.",
            ctx.request().tenant());
      }
    }
  }

  // =========================================================================
  // FAPI 2.0 §5.3.2.1-2.8: client_assertion aud MUST be the AS issuer
  // =========================================================================

  static final class ClientAssertionAudIsIssuerRule implements Rule<Fapi20Context> {
    @Override
    public RuleId id() {
      return new RuleId("fapi2.client-assertion.aud-is-issuer");
    }

    @Override
    public boolean appliesTo(Fapi20Context ctx) {
      return ctx.hasCredentials() && ctx.request().clientAuthenticationType().isPrivateKeyJwt();
    }

    @Override
    public void verify(Fapi20Context ctx) {
      Object rawAud = ctx.credentials().clientAssertionJwt().getFromRawPayload("aud");
      if (!(rawAud instanceof String)) {
        return; // already rejected by ClientAssertionAudIsArrayRule (or missing)
      }
      String issuer = ctx.request().serverConfiguration().tokenIssuer().value();
      if (!issuer.equals(rawAud)) {
        throw new OAuthBadRequestException(
            "invalid_client",
            String.format(
                "When FAPI 2.0 Security Profile (§5.3.2.1-2.8), client assertion aud must be the AS issuer identifier (%s). Received: %s",
                issuer, rawAud),
            ctx.request().tenant());
      }
    }
  }
}
