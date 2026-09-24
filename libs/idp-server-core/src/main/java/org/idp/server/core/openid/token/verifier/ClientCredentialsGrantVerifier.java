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
import org.idp.server.core.openid.clientinstance.ClientInstanceRegistrationPolicy;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;

public class ClientCredentialsGrantVerifier {

  Scopes scopes;
  Map<String, List<String>> scopeResourceMapping;
  ClientAuthenticationType clientAuthenticationType;
  ClientInstanceRegistrationPolicy clientInstanceRegistrationPolicy;

  public ClientCredentialsGrantVerifier(
      Scopes scopes,
      Map<String, List<String>> scopeResourceMapping,
      ClientAuthenticationType clientAuthenticationType) {
    this(
        scopes,
        scopeResourceMapping,
        clientAuthenticationType,
        ClientInstanceRegistrationPolicy.undefined);
  }

  public ClientCredentialsGrantVerifier(
      Scopes scopes,
      Map<String, List<String>> scopeResourceMapping,
      ClientAuthenticationType clientAuthenticationType,
      ClientInstanceRegistrationPolicy clientInstanceRegistrationPolicy) {
    this.scopes = scopes;
    this.scopeResourceMapping = scopeResourceMapping;
    this.clientAuthenticationType = clientAuthenticationType;
    this.clientInstanceRegistrationPolicy =
        clientInstanceRegistrationPolicy != null
            ? clientInstanceRegistrationPolicy
            : ClientInstanceRegistrationPolicy.undefined;
  }

  public void verify() {
    throwExceptionIfPublicClient();
    throwExceptionIfUserBoundClient();
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

  /**
   * A client whose instances are bound to users does not obtain tokens without one (Issue #1521).
   *
   * <p>With {@code client_instance_registration_policy = user_bound} the client is an application
   * installed by users, each install registered by a user's login. Every token it obtains otherwise
   * carries a user: the code, CIBA and refresh paths check that the user is still active, and that
   * it is the instance's user. A client credentials token carries none, so a deleted or disabled
   * user's device would keep obtaining tokens.
   *
   * <p>Decided by the client, as grant types are, so that every instance of the client behaves the
   * same. A device or a server that needs the grant is a client of its own.
   *
   * <p>Not required by Attestation-Based Client Authentication, which leaves grant types open. It
   * follows how attested clients are deployed: the IT-Wallet token endpoint allows only {@code
   * authorization_code} and {@code refresh_token}, and the EUDI PID issuer disables the grant for
   * its attested client and serves it to a separate backend client.
   */
  void throwExceptionIfUserBoundClient() {
    if (clientInstanceRegistrationPolicy.isUserBound()) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "a client whose instances are bound to users (client_instance_registration_policy=user_bound)"
              + " cannot use grant_type=client_credentials");
    }
  }

  void throwExceptionIfInvalidScope() {
    if (!scopes.exists()) {
      throw new TokenBadRequestException(
          "invalid_scope", "token request does not contains valid scope");
    }
  }
}
