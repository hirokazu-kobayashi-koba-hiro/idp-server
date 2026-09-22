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

package org.idp.server.core.openid.token.verifier;

import org.idp.server.core.openid.clientinstance.ClientInstanceThumbprint;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientAuthenticationPublicKey;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.token.AccessToken;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;

/**
 * The refresh comes from the Client Instance the token was issued to.
 *
 * <p>draft-ietf-oauth-attestation-based-client-auth Section 10.3: a refresh token issued in
 * response to a request using the client attestation mechanism MUST be bound to the Client Instance
 * and its key, and refreshing MUST use the same key that was in the {@code cnf} claim.
 *
 * <h2>Why client authentication is not enough here</h2>
 *
 * <p>RFC 9449 Section 5 leaves refresh tokens of confidential clients unbound, reasoning that
 * client authentication already constrains the sender. That reasoning does not carry over: several
 * instances of one application share a {@code client_id}, so authenticating as the client says
 * nothing about which instance is asking. Without this check, a refresh token belonging to one
 * user's device is redeemable by any other install of the same app — including the attacker's own.
 *
 * <p>The comparison is against the key that verified the Client Attestation on this request, so a
 * caller cannot choose it: {@link ClientCredentials} carries what the authenticator accepted.
 */
public class RefreshTokenClientInstanceBindingVerifier {

  AccessToken originalAccessToken;
  ClientCredentials clientCredentials;

  public RefreshTokenClientInstanceBindingVerifier(
      AccessToken originalAccessToken, ClientCredentials clientCredentials) {
    this.originalAccessToken = originalAccessToken;
    this.clientCredentials = clientCredentials;
  }

  public void verify() {
    if (!originalAccessToken.hasClientInstanceBinding()) {
      return;
    }

    // No guard for "authenticated some other way": a bound token belongs to a client whose
    // token_endpoint_auth_method is attest_jwt_client_auth, so there is no other way in. A check
    // no test can reach reads as protection that is not there.
    if (!originalAccessToken.matchClientInstanceThumbprint(presentedThumbprint())) {
      throw new TokenBadRequestException(
          "invalid_grant",
          "the refresh token was issued to a different client instance of this client");
    }
  }

  private ClientInstanceThumbprint presentedThumbprint() {
    ClientAuthenticationPublicKey instanceKey = clientCredentials.clientAuthenticationPublicKey();
    if (!instanceKey.exists()) {
      throw new TokenBadRequestException(
          "invalid_grant", "the client attestation carried no client instance key");
    }

    try {
      return new ClientInstanceThumbprint(instanceKey.thumbprintSha256());
    } catch (JsonWebKeyInvalidException e) {
      throw new TokenBadRequestException(
          "invalid_grant", "failed to read the client instance key: " + e.getMessage());
    }
  }
}
