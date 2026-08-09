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

package org.idp.server.control_plane.management.oidc.authorization.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationCommandRepository;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfigurationQueryRepository;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * #1762: PUT is a full replacement, but {@link AuthorizationServerConfiguration#toMap()} leaves
 * {@code jwks} out of the management representation. A caller doing GET -> modify -> PUT therefore
 * sends a body with no {@code jwks} at all, and without the carry-over below that single save would
 * clear the signing keys of the tenant and stop every token issuance for it.
 *
 * <p>{@code updateConfiguration} does not touch the repositories, so mocks are enough.
 */
class AuthorizationServerUpdateServiceTest {

  private static final String STORED_JWKS =
      "{\"keys\":[{\"kty\":\"EC\",\"d\":\"yIWDrlhnCy3yL9xLuqZGOBFFq4PWGsCeM7Sc_lfeaQQ\",\"use\":\"sig\",\"crv\":\"P-256\",\"kid\":\"access_token\",\"x\":\"iWJINqt0ySv3kVEvlHbvNkPKY2pPSf1cG1PSx3tRfw0\",\"y\":\"rW1FdfXK5AQcv-Go6Xho0CR5AbLai7Gp9IdLTIXTSIQ\",\"alg\":\"ES256\"}]}";

  private static final String ROTATED_JWKS =
      "{\"keys\":[{\"kty\":\"EC\",\"d\":\"HrgT4zqM2BvrlwUWagyeNnZ40nZ7rTY4gYG9k99oGJg\",\"use\":\"sig\",\"crv\":\"P-256\",\"kid\":\"rotated\",\"x\":\"PM6be42POiKdNzRKGeZ1Gia8908XfmSSbS4cwPasWTo\",\"y\":\"wksaan9a4h3L8R1UMmvc9w6rPB_F07IA-VHx7n7Add4\",\"alg\":\"ES256\"}]}";

  private final AuthorizationServerUpdateService service =
      new AuthorizationServerUpdateService(
          mock(AuthorizationServerConfigurationQueryRepository.class),
          mock(AuthorizationServerConfigurationCommandRepository.class));

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private AuthorizationServerConfiguration before() {
    Map<String, Object> values = new HashMap<>();
    values.put("issuer", "https://idp.example.com/tenant-1");
    values.put("authorization_endpoint", "https://idp.example.com/tenant-1/v1/authorizations");
    values.put("token_endpoint", "https://idp.example.com/tenant-1/v1/tokens");
    values.put("jwks", STORED_JWKS);
    return jsonConverter.read(values, AuthorizationServerConfiguration.class);
  }

  /** The body a client gets back from GET: every field except the signing keys. */
  private Map<String, Object> bodyFromGetResponse(AuthorizationServerConfiguration before) {
    Map<String, Object> body = new HashMap<>(before.toMap());
    assertFalse(body.containsKey("jwks"), "the management representation must not expose jwks");
    return body;
  }

  @Test
  void keepsStoredJwksWhenTheUpdateCarriesNone() {
    AuthorizationServerConfiguration before = before();
    Map<String, Object> body = bodyFromGetResponse(before);
    body.put("token_endpoint", "https://idp.example.com/tenant-1/v1/tokens-v2");

    AuthorizationServerConfiguration after =
        service.updateConfiguration(before, new AuthorizationServerUpdateRequest(body));

    assertEquals(STORED_JWKS, after.jwks());
    assertEquals("https://idp.example.com/tenant-1/v1/tokens-v2", after.tokenEndpoint());
  }

  @Test
  void keepsStoredJwksWhenTheUpdateSendsAnExplicitNull() {
    AuthorizationServerConfiguration before = before();
    Map<String, Object> body = bodyFromGetResponse(before);
    body.put("jwks", null);

    AuthorizationServerConfiguration after =
        service.updateConfiguration(before, new AuthorizationServerUpdateRequest(body));

    assertEquals(STORED_JWKS, after.jwks());
  }

  @Test
  void keepsStoredJwksWhenTheUpdateSendsABlankValue() {
    AuthorizationServerConfiguration before = before();
    Map<String, Object> body = bodyFromGetResponse(before);
    body.put("jwks", "   ");

    AuthorizationServerConfiguration after =
        service.updateConfiguration(before, new AuthorizationServerUpdateRequest(body));

    assertEquals(STORED_JWKS, after.jwks());
  }

  @Test
  void replacesJwksWhenTheUpdateCarriesOne() {
    // Key rotation still has to work: an explicit jwks wins over the stored one.
    AuthorizationServerConfiguration before = before();
    Map<String, Object> body = bodyFromGetResponse(before);
    body.put("jwks", ROTATED_JWKS);

    AuthorizationServerConfiguration after =
        service.updateConfiguration(before, new AuthorizationServerUpdateRequest(body));

    assertEquals(ROTATED_JWKS, after.jwks());
  }
}
