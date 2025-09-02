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

package org.idp.server.federation.sso.oidc;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.federation.FederationType;
import org.idp.server.core.openid.federation.sso.SsoProvider;
import org.idp.server.core.openid.federation.sso.oidc.OidcSsoSession;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JoseHandler;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface OidcSsoExecutor {

  SsoProvider type();

  default OidcSsoSession createOidcSession(
      Tenant tenant,
      AuthorizationRequestIdentifier authorizationRequestIdentifier,
      OidcSsoConfiguration oidcSsoConfiguration,
      FederationType federationType,
      SsoProvider ssoProvider) {
    OidcSsoSessionCreator authorizationRequestCreator =
        new OidcSsoSessionCreator(
            oidcSsoConfiguration,
            tenant,
            authorizationRequestIdentifier,
            federationType,
            ssoProvider);
    return authorizationRequestCreator.create();
  }

  OidcTokenResult requestToken(OidcTokenRequest oidcTokenRequest);

  OidcJwksResult getJwks(OidcJwksRequest oidcJwksRequest);

  default IdTokenVerificationResult verifyIdToken(
      OidcSsoConfiguration configuration,
      OidcSsoSession ssoSession,
      OidcJwksResult jwksResponse,
      OidcTokenResult tokenResponse) {
    try {

      JoseHandler joseHandler = new JoseHandler();

      // 3.1.3.7.  ID Token Validation
      // Clients MUST validate the ID Token in the Token Response in the following manner:

      // 1. If the ID Token is encrypted, decrypt it using the keys and algorithms that the Client
      // specified during Registration that the OP was to use to encrypt the ID Token. If encryption
      // was negotiated with the OP at Registration time and the ID Token is not encrypted, the RP
      // SHOULD reject it.
      JoseContext joseContext =
          joseHandler.handle(
              tokenResponse.idToken(),
              jwksResponse.body(),
              configuration.privateKeys(),
              configuration.clientSecret());

      // 2. The Issuer Identifier for the OpenID Provider (which is typically obtained during
      // Discovery) MUST exactly match the value of the iss (issuer) Claim.
      JsonWebTokenClaims claims = joseContext.claims();
      if (!configuration.issuer().equals(claims.getIss())) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", "server_error");
        data.put(
            "error_description",
            String.format(
                "issuer does not match the issuer config: %s id_token: %s.",
                configuration.issuer(), claims.getIss()));

        return new IdTokenVerificationResult(false, data);
      }

      // 3. The Client MUST validate that the aud (audience) Claim contains its client_id value
      // registered at the Issuer identified by the iss (issuer) Claim as an audience. The aud
      // (audience) Claim MAY contain an array with more than one element. The ID Token MUST be
      // rejected if the ID Token does not list the Client as a valid audience, or if it contains
      // additional audiences not trusted by the Client.
      if (claims.hasAud() && !claims.getAud().contains(configuration.clientId())) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", "server_error");
        data.put(
            "error_description",
            String.format(
                "aud does not contain the client_id config: %s id_token: %s.",
                configuration.clientId(), String.join(" ", claims.getAud())));

        return new IdTokenVerificationResult(false, data);
      }

      // Unverify 4. If the implementation is using extensions (which are beyond the scope of this
      // specification) that result in the azp (authorized party) Claim being present, it SHOULD
      // validate the azp value as specified by those extensions.

      // Unverify 5. This validation MAY include that when an azp (authorized party) Claim is
      // present, the Client SHOULD verify that its client_id is the Claim Value.

      // 6. If the ID Token is received via direct communication between the Client and the Token
      // Endpoint (which it is in this flow), the TLS server validation MAY be used to validate the
      // issuer in place of checking the token signature. The Client MUST validate the signature of
      // all other ID Tokens according to JWS [JWS] using the algorithm specified in the JWT alg
      // Header Parameter. The Client MUST use the keys provided by the Issuer.
      // 8. If the JWT alg Header Parameter uses a MAC based algorithm such as HS256, HS384, or
      // HS512, the octets of the UTF-8 [RFC3629] representation of the client_secret corresponding
      // to the client_id contained in the aud (audience) Claim are used as the key to validate the
      // signature. For MAC based algorithms, the behavior is unspecified if the aud is
      // multi-valued. The current time MUST be before the time represented by the exp Claim.
      joseContext.verifySignature();

      // Unverify 7. The alg value SHOULD be the default of RS256 or the algorithm sent by the
      // Client in the id_token_signed_response_alg parameter during Registration.

      // 9. The current time MUST be before the time represented by the exp Claim.
      if (!claims.hasExp()) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", "server_error");
        data.put("error_description", "id_token does not contain exp.");
        return new IdTokenVerificationResult(false, data);
      }

      Date date = new Date(SystemDateTime.epochMilliSecond());
      if (claims.getExp().before(date)) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", "server_error");
        data.put("error_description", "id_token is expired.");
        return new IdTokenVerificationResult(false, data);
      }

      // Unverify 10. The iat Claim can be used to reject tokens that were issued too far away from
      // the current time, limiting the amount of time that nonces need to be stored to prevent
      // attacks. The acceptable range is Client specific.

      // 11. If a nonce value was sent in the Authentication Request, a nonce Claim MUST be present
      // and its value checked to verify that it is the same value as the one that was sent in the
      // Authentication Request. The Client SHOULD check the nonce value for replay attacks. The
      // precise method for detecting replay attacks is Client specific.
      if (ssoSession.hasNonce() && !ssoSession.nonce().equals(claims.getValue("nonce"))) {
        Map<String, Object> data = new HashMap<>();
        data.put("error", "server_error");
        data.put(
            "error_description",
            String.format(
                "nonce is unmatched request: %s, id_token: %s.",
                ssoSession.nonce(), claims.getValue("nonce")));
        return new IdTokenVerificationResult(false, data);
      }

      // Unverify 12. If the acr Claim was requested, the Client SHOULD check that the asserted
      // Claim Value is appropriate. The meaning and processing of acr Claim Values is out of scope
      // for this specification.
      // Unverify 13. If the auth_time Claim was requested, either through a specific request for
      // this Claim or by using the max_age parameter, the Client SHOULD check the auth_time Claim
      // value and request re-authentication if it determines too much time has elapsed since the
      // last End-User authentication.

      return new IdTokenVerificationResult(true, Map.of());
    } catch (JoseInvalidException e) {

      Map<String, Object> data = new HashMap<>();
      data.put("error", "server_error");
      data.put("error_description", "failed to parse id_token: " + e.getMessage());
      return new IdTokenVerificationResult(false, data);
    }
  }

  UserinfoExecutionResult requestUserInfo(OidcUserinfoRequest oidcUserinfoRequest);
}
