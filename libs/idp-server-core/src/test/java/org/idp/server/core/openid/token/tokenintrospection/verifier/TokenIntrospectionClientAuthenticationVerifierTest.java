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

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Issue #1707 (RFC 7662 §2.1): token introspection must be limited to confidential clients. */
class TokenIntrospectionClientAuthenticationVerifierTest {

  @Test
  void rejectsPublicClient() {
    // token_endpoint_auth_method=none presents no credential, so it must not be able to introspect.
    TokenIntrospectionClientAuthenticationVerifier verifier =
        new TokenIntrospectionClientAuthenticationVerifier(ClientAuthenticationType.none);
    assertThrows(ClientUnAuthorizedException.class, verifier::verify);
  }

  @ParameterizedTest
  @EnumSource(
      value = ClientAuthenticationType.class,
      names = {"none"},
      mode = EnumSource.Mode.EXCLUDE)
  void allowsConfidentialClients(ClientAuthenticationType confidentialType) {
    // Every non-none method (client_secret_*, private_key_jwt, tls_client_auth, ...) is a
    // confidential client and passes; it is then authenticated by ClientAuthenticationHandler.
    TokenIntrospectionClientAuthenticationVerifier verifier =
        new TokenIntrospectionClientAuthenticationVerifier(confidentialType);
    assertDoesNotThrow(verifier::verify);
  }
}
