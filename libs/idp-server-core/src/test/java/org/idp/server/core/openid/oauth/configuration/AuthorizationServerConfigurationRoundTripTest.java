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

package org.idp.server.core.openid.oauth.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * #1762: the management update is a full replacement — it deserializes the whole request body, like
 * the other management update services. Round-trippability therefore has to come from {@link
 * AuthorizationServerConfiguration#toMap()} exposing every field, not from the update service
 * merging individual ones back in.
 *
 * <p>{@code jwks} used to be left out, so a caller doing GET -&gt; modify -&gt; PUT sent a body
 * with no signing keys and cleared them for the tenant in a single save, stopping all token
 * issuance for it.
 */
class AuthorizationServerConfigurationRoundTripTest {

  private static final String JWKS =
      "{\"keys\":[{\"kty\":\"EC\",\"d\":\"yIWDrlhnCy3yL9xLuqZGOBFFq4PWGsCeM7Sc_lfeaQQ\",\"use\":\"sig\",\"crv\":\"P-256\",\"kid\":\"access_token\",\"x\":\"iWJINqt0ySv3kVEvlHbvNkPKY2pPSf1cG1PSx3tRfw0\",\"y\":\"rW1FdfXK5AQcv-Go6Xho0CR5AbLai7Gp9IdLTIXTSIQ\",\"alg\":\"ES256\"}]}";

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private AuthorizationServerConfiguration configuration(Map<String, Object> overrides) {
    Map<String, Object> values = new HashMap<>();
    values.put("issuer", "https://idp.example.com/tenant-1");
    values.put("authorization_endpoint", "https://idp.example.com/tenant-1/v1/authorizations");
    values.put("token_endpoint", "https://idp.example.com/tenant-1/v1/tokens");
    values.put("jwks_uri", "https://idp.example.com/tenant-1/v1/jwks");
    values.putAll(overrides);
    return jsonConverter.read(values, AuthorizationServerConfiguration.class);
  }

  @Test
  void managementRepresentationCarriesTheSigningKeys() {
    AuthorizationServerConfiguration configuration = configuration(Map.of("jwks", JWKS));

    assertEquals(JWKS, configuration.toMap().get("jwks"));
  }

  @Test
  void signingKeysSurviveAGetModifyPutRoundTrip() {
    AuthorizationServerConfiguration before = configuration(Map.of("jwks", JWKS));

    // what a caller reads, edits and writes back
    Map<String, Object> body = new HashMap<>(before.toMap());
    body.put("token_endpoint", "https://idp.example.com/tenant-1/v1/tokens-v2");

    AuthorizationServerConfiguration after =
        jsonConverter.read(body, AuthorizationServerConfiguration.class);

    assertEquals(JWKS, after.jwks());
    assertEquals("https://idp.example.com/tenant-1/v1/tokens-v2", after.tokenEndpoint());
  }

  @Test
  void omitsTheKeyEntirelyWhenNoSigningKeysAreConfigured() {
    AuthorizationServerConfiguration configuration = configuration(Map.of());

    assertFalse(configuration.toMap().containsKey("jwks"));
    assertFalse(configuration.hasJwks());
  }

  @Test
  void treatsABlankValueAsNoSigningKeys() {
    AuthorizationServerConfiguration configuration = configuration(Map.of("jwks", "   "));

    assertFalse(configuration.hasJwks());
    assertFalse(configuration.toMap().containsKey("jwks"));
  }
}
