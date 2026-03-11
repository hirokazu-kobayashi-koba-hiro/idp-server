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

package org.idp.server.core.openid.oauth.clientauthenticator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.exception.ClientUnAuthorizedException;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.token.TokenRequestContext;
import org.idp.server.core.openid.token.TokenRequestParameters;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * Regression for #1598.
 *
 * <p>A client configured for {@code client_secret_jwt} but without a {@code client_secret} has no
 * HMAC key. The {@code client_assertion} can therefore never be verified, and a null secret must
 * not reach the HMAC verifier (which would surface as a {@link NullPointerException} / HTTP 500).
 * It must be rejected with {@link ClientUnAuthorizedException} (→ {@code invalid_client}, 401).
 */
class ClientSecretJwtAuthenticatorTest {

  private static final JsonConverter JSON = JsonConverter.snakeCaseInstance();
  private static final String JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

  private static TokenRequestContext contextWith(String clientConfigJson) {
    TokenRequestParameters parameters =
        new TokenRequestParameters(
            Map.of(
                "client_id", new String[] {"c"},
                "client_assertion", new String[] {"dummy.client.assertion"},
                "client_assertion_type", new String[] {JWT_BEARER}));
    ClientConfiguration clientConfiguration =
        JSON.read(clientConfigJson, ClientConfiguration.class);
    return new TokenRequestContext(
        null,
        new ClientSecretBasic(),
        null,
        null,
        null,
        null,
        parameters,
        null,
        null,
        null,
        null,
        null,
        clientConfiguration);
  }

  @Test
  void throwsInvalidClientWhenSecretIsNotConfigured() {
    ClientSecretJwtAuthenticator authenticator = new ClientSecretJwtAuthenticator();

    ClientUnAuthorizedException exception =
        assertThrows(
            ClientUnAuthorizedException.class,
            () -> authenticator.authenticate(contextWith("{\"client_id\":\"c\"}")));
    assertTrue(exception.getReason().contains("client_secret is not configured"));
  }

  @Test
  void throwsInvalidClientWhenSecretIsEmpty() {
    ClientSecretJwtAuthenticator authenticator = new ClientSecretJwtAuthenticator();

    assertThrows(
        ClientUnAuthorizedException.class,
        () ->
            authenticator.authenticate(
                contextWith("{\"client_id\":\"c\",\"client_secret\":\"\"}")));
  }
}
