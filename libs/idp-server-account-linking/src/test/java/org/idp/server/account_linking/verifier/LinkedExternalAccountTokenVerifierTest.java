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

package org.idp.server.account_linking.verifier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.account_linking.exception.LinkedExternalAccountTokenException;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

class LinkedExternalAccountTokenVerifierTest {

  LinkedExternalAccountTokenVerifier verifier = new LinkedExternalAccountTokenVerifier();

  static ClientConfiguration client(String tokenEndpointAuthMethod) {
    return JsonConverter.snakeCaseInstance()
        .read(
            Map.of("client_id", "rp-client", "token_endpoint_auth_method", tokenEndpointAuthMethod),
            ClientConfiguration.class);
  }

  @Test
  void refusesAPublicClient() {
    LinkedExternalAccountTokenException exception =
        assertThrows(
            LinkedExternalAccountTokenException.class,
            () -> verifier.verifyConfidentialClient(client("none")));
    assertEquals("invalid_client", exception.error());
  }

  @Test
  void acceptsAConfidentialClient() {
    assertDoesNotThrow(() -> verifier.verifyConfidentialClient(client("client_secret_basic")));
    assertDoesNotThrow(() -> verifier.verifyConfidentialClient(client("private_key_jwt")));
  }
}
