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

package org.idp.server.account_linking.gateway;

import java.util.Map;
import org.idp.server.account_linking.AccountLinkingSession;
import org.idp.server.account_linking.exception.ExternalIdpRequestFailedException;
import org.idp.server.core.openid.federation.sso.oidc.OidcSsoSession;
import org.idp.server.federation.sso.oidc.*;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Verifies the ID Token from the token response and reads the external account identifier from it.
 *
 * <p>The {@code sub} of a verified ID Token is what the provider itself asserts about which account
 * granted access. An attribute read back from a userinfo endpoint is not the same thing: what it
 * returns depends on the scopes the access token happens to carry, and a mapping rule pointed at
 * the wrong field would silently key the link on a value that is not stable.
 *
 * <p>Verification is the full check the login path already performs, reused as is. The linking
 * session supplies only the nonce it generated.
 */
public class ExternalIdpIdTokenGateway {

  OidcSsoExecutors oidcSsoExecutors;
  LoggerWrapper log = LoggerWrapper.getLogger(ExternalIdpIdTokenGateway.class);

  public ExternalIdpIdTokenGateway(OidcSsoExecutors oidcSsoExecutors) {
    this.oidcSsoExecutors = oidcSsoExecutors;
  }

  /**
   * @return the verified {@code sub}
   * @throws ExternalIdpRequestFailedException if the JWKS cannot be read, verification fails, or
   *     the token carries no {@code sub}. Never falls back to another source: mixing a verified
   *     identifier with an unverified one is how a link ends up on the wrong account.
   */
  public String verifiedSubject(
      OidcSsoConfiguration configuration,
      AccountLinkingSession session,
      OidcTokenResult tokenResult) {

    OidcSsoExecutor executor = oidcSsoExecutors.get(configuration.ssoProvider());

    // Only fetched when the provider publishes keys. A MAC signed id_token verifies against the
    // client secret instead, and asking for a JWKS that does not exist would fail a link that is
    // perfectly verifiable. Skipping the fetch is not skipping the check: verification below still
    // runs, and an asymmetric signature with no keys to check it against fails there.
    OidcJwksResult jwksResult = fetchJwks(executor, configuration);
    if (jwksResult.isError()) {
      log.warn(
          "Account linking could not read the external identity provider JWKS. uri={}, status={}",
          configuration.jwksUri(),
          jwksResult.statusCode());
      throw new ExternalIdpRequestFailedException(
          "Account linking could not verify the id_token issued by the external identity provider.");
    }

    // verifyIdToken reads only the nonce off the session; everything else it needs comes from the
    // configuration and the token response. Supplying a session that carries just the nonce lets
    // the login path's verification be reused without duplicating it.
    OidcSsoSession nonceHolder =
        new OidcSsoSession(null, null, null, null, null, session.nonce(), null, null, null, null);

    IdTokenVerificationResult verification =
        executor.verifyIdToken(configuration, nonceHolder, jwksResult, tokenResult);
    if (verification.isError()) {
      log.warn("Account linking id_token verification failed: {}", verification.data());
      throw new ExternalIdpRequestFailedException(
          "Account linking could not verify the id_token issued by the external identity provider.");
    }

    String subject = verification.claims().getSub();
    if (subject == null || subject.isEmpty()) {
      // sub is REQUIRED in OpenID Connect. A token without one is malformed, not a provider that
      // declines to identify the account, so it is rejected rather than treated as anonymous.
      throw new ExternalIdpRequestFailedException(
          "Account linking received an id_token without sub.");
    }

    return subject;
  }

  private OidcJwksResult fetchJwks(OidcSsoExecutor executor, OidcSsoConfiguration configuration) {
    if (!configuration.hasJwksUri()) {
      return new OidcJwksResult(200, Map.of(), "");
    }
    return executor.getJwks(new OidcJwksRequest(configuration.jwksUri()));
  }
}
