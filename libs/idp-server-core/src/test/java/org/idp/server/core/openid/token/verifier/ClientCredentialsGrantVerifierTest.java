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

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Issue #1820 (RFC 6749 Section 4.4): the client credentials grant is confidential-client only. */
class ClientCredentialsGrantVerifierTest {

  private ClientCredentialsGrantVerifier verifier(ClientAuthenticationType type) {
    return new ClientCredentialsGrantVerifier(new Scopes(Set.of("account")), Map.of(), type);
  }

  @Test
  void rejectsPublicClient() {
    // token_endpoint_auth_method=none presents no credential, so the grant must not issue a token.
    assertThrows(
        ClientUnAuthorizedException.class, verifier(ClientAuthenticationType.none)::verify);
  }

  @ParameterizedTest
  @EnumSource(
      value = ClientAuthenticationType.class,
      names = {"none"},
      mode = EnumSource.Mode.EXCLUDE)
  void allowsConfidentialClients(ClientAuthenticationType confidentialType) {
    // Every non-none method is a confidential client and passes; it is then authenticated by
    // ClientAuthenticationHandler as usual.
    assertDoesNotThrow(verifier(confidentialType)::verify);
  }

  @Test
  void rejectsPublicClientBeforeScopeVerification() {
    // An unauthenticated caller must not learn whether the requested scope was valid.
    ClientCredentialsGrantVerifier verifier =
        new ClientCredentialsGrantVerifier(
            new Scopes(Set.of()), Map.of(), ClientAuthenticationType.none);
    assertThrows(ClientUnAuthorizedException.class, verifier::verify);
  }
}
