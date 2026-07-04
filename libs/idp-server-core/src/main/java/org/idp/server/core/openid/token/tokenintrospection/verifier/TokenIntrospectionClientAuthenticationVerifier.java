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

package org.idp.server.core.openid.token.tokenintrospection.verifier;

import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;

/**
 * Requires token introspection to be performed by a confidential client (Issue #1707, RFC 7662
 * §2.1).
 *
 * <p>RFC 7662 §2.1 requires the introspection endpoint to require authorization to prevent token
 * scanning attacks. A public client ({@code token_endpoint_auth_method=none}) presents no
 * credential, so the shared {@code ClientAuthenticationHandler} would let it through the {@code
 * none} authenticator unchecked. This verifier rejects that case up front so only confidential
 * clients (client_secret_*, private_key_jwt, tls_client_auth, ...) can introspect; those are then
 * authenticated by {@code ClientAuthenticationHandler} as usual.
 *
 * <p>The thrown {@link ClientUnAuthorizedException} is mapped to {@code invalid_client} by {@code
 * TokenIntrospectionErrorHandler}.
 */
public class TokenIntrospectionClientAuthenticationVerifier {

  ClientAuthenticationType clientAuthenticationType;

  public TokenIntrospectionClientAuthenticationVerifier(
      ClientAuthenticationType clientAuthenticationType) {
    this.clientAuthenticationType = clientAuthenticationType;
  }

  public void verify() {
    if (clientAuthenticationType.isNone()) {
      throw new ClientUnAuthorizedException(
          "Token introspection requires client authentication; a public client"
              + " (token_endpoint_auth_method=none) is not allowed to introspect.");
    }
  }
}
