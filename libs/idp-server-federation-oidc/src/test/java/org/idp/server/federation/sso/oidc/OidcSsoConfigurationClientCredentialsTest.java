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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.exception.InvalidConfigurationException;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.oauth.client.OutboundClientAuthenticationType;
import org.idp.server.platform.oauth.client.OutboundClientCredentials;
import org.junit.jupiter.api.Test;

class OidcSsoConfigurationClientCredentialsTest {

  static OidcSsoConfiguration configuration(Map<String, Object> extra) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("issuer", "https://idp.example.com");
    payload.put("token_endpoint", "https://idp.example.com/token");
    payload.put("client_id", "client-1");
    payload.put("client_secret", "secret");
    payload.putAll(extra);
    return JsonConverter.snakeCaseInstance().read(payload, OidcSsoConfiguration.class);
  }

  @Test
  void sendsTheSecretInTheFormWhenNoTypeIsNamed() {
    assertEquals(
        OutboundClientAuthenticationType.client_secret_post,
        configuration(Map.of()).clientCredentials().type());
  }

  @Test
  void addressesAnAssertionToItsOwnIssuerWhenAskedTo() {
    OutboundClientCredentials credentials =
        configuration(
                Map.of(
                    "client_authentication_type",
                    "client_secret_jwt",
                    "client_authentication",
                    Map.of("assertion_audience", "issuer")))
            .clientCredentials();

    assertEquals(OutboundClientAuthenticationType.client_secret_jwt, credentials.type());
    assertEquals("https://idp.example.com", credentials.assertionAudience());
  }

  @Test
  void addressesAnAssertionToTheTokenEndpointByDefault() {
    assertEquals(
        "https://idp.example.com/token",
        configuration(Map.of("client_authentication_type", "private_key_jwt"))
            .clientCredentials()
            .assertionAudience());
  }

  @Test
  void refusesAClientAuthenticationTypeItDoesNotKnow() {
    OidcSsoConfiguration configuration =
        configuration(Map.of("client_authentication_type", "private_key_jwk"));

    assertThrows(InvalidConfigurationException.class, configuration::clientCredentials);
  }
}
