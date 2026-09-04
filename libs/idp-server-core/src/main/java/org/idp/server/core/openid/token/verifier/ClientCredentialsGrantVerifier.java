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

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;

public class ClientCredentialsGrantVerifier {

  Scopes scopes;
  Map<String, List<String>> scopeResourceMapping;
  ClientAuthenticationType clientAuthenticationType;

  public ClientCredentialsGrantVerifier(
      Scopes scopes,
      Map<String, List<String>> scopeResourceMapping,
      ClientAuthenticationType clientAuthenticationType) {
    this.scopes = scopes;
    this.scopeResourceMapping = scopeResourceMapping;
    this.clientAuthenticationType = clientAuthenticationType;
  }

  public void verify() {
    throwExceptionIfPublicClient();
    throwExceptionIfInvalidScope();
    new ScopeResourceGrantVerifier(scopes, scopeResourceMapping).verify();
  }

  /**
   * Requires the client credentials grant to be used by a confidential client (Issue #1820, RFC
   * 6749 Section 4.4).
   *
   * <p>RFC 6749 Section 4.4: "The client credentials grant type MUST only be used by confidential
   * clients." A public client ({@code token_endpoint_auth_method=none}) presents no credential, so
   * {@code PublicClientAuthenticator} lets it through unchecked and the grant would otherwise issue
   * an access token to an unauthenticated caller.
   *
   * <p>This is a general OAuth 2.0 guard, independent of the FAPI profiles: the {@code isNone()}
   * checks in the FAPI verifiers only apply when a FAPI profile is selected.
   *
   * <p>The thrown {@link ClientUnAuthorizedException} is mapped to {@code invalid_client} by {@code
   * TokenRequestErrorHandler}. It is checked before the scope verification so that an
   * unauthenticated caller receives no feedback about scope validity.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.4">RFC 6749 Section 4.4</a>
   */
  void throwExceptionIfPublicClient() {
    if (clientAuthenticationType.isNone()) {
      throw new ClientUnAuthorizedException(
          "The client credentials grant requires a confidential client; a public client"
              + " (token_endpoint_auth_method=none) is not allowed to use grant_type="
              + "client_credentials.");
    }
  }

  void throwExceptionIfInvalidScope() {
    if (!scopes.exists()) {
      throw new TokenBadRequestException(
          "invalid_scope", "token request does not contains valid scope");
    }
  }
}
