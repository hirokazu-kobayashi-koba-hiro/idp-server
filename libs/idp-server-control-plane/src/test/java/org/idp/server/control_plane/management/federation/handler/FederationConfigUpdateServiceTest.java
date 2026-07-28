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

package org.idp.server.control_plane.management.federation.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.idp.server.control_plane.management.federation.io.FederationConfigRequest;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.core.openid.federation.repository.FederationConfigurationCommandRepository;
import org.idp.server.core.openid.federation.repository.FederationConfigurationQueryRepository;
import org.junit.jupiter.api.Test;

/**
 * #1742: PUT is a full replacement. {@code updateConfiguration} deserializes the whole request body
 * (like the other management update services) so that every field — including the sso_provider
 * lookup key now exposed by {@code toMap()} — is round-trippable via GET -> modify -> PUT. The id
 * is always taken from the path identifier, never the body. {@code updateConfiguration} does not
 * touch the repositories, so mocks are enough.
 */
class FederationConfigUpdateServiceTest {

  private final FederationConfigUpdateService service =
      new FederationConfigUpdateService(
          mock(FederationConfigurationQueryRepository.class),
          mock(FederationConfigurationCommandRepository.class));

  private final FederationConfiguration before =
      new FederationConfiguration("id-1", "oidc", "google", Map.of("provider", "standard"));

  @Test
  void deserializesFullConfigFromRequest() {
    FederationConfigRequest request =
        new FederationConfigRequest(
            Map.of(
                "type",
                "oidc",
                "sso_provider",
                "azure_ad",
                "payload",
                Map.of("provider", "standard", "client_id", "abc")));

    FederationConfiguration after = service.updateConfiguration(before, request);

    assertEquals("azure_ad", after.ssoProvider().name());
    assertEquals("oidc", after.typeName());
    assertEquals("abc", after.payload().get("client_id"));
  }

  @Test
  void idAlwaysComesFromPathIdentifierNotBody() {
    FederationConfigRequest request =
        new FederationConfigRequest(
            Map.of(
                "id",
                "malicious-id-from-body",
                "type",
                "oidc",
                "sso_provider",
                "google",
                "payload",
                Map.of("provider", "standard")));

    FederationConfiguration after = service.updateConfiguration(before, request);

    assertEquals("id-1", after.identifier().value());
  }
}
