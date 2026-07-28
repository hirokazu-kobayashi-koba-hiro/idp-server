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
import org.junit.jupiter.api.Test;

/**
 * #1743: create is a full-replacement deserialization (like the update path / client management),
 * so enabled is honored from the request instead of being forced to true. {@code
 * createConfiguration} does not touch the repository, so a mock is enough.
 */
class FederationConfigCreationServiceTest {

  private final FederationConfigCreationService service =
      new FederationConfigCreationService(mock(FederationConfigurationCommandRepository.class));

  @Test
  void honorsEnabledFromRequest() {
    FederationConfigRequest request =
        new FederationConfigRequest(
            Map.of(
                "id",
                "id-1",
                "type",
                "oidc",
                "sso_provider",
                "google",
                "payload",
                Map.of("provider", "standard"),
                "enabled",
                false));

    FederationConfiguration created = service.createConfiguration(request);

    assertFalse(created.isEnabled());
  }

  @Test
  void generatesIdWhenAbsent() {
    FederationConfigRequest request =
        new FederationConfigRequest(
            Map.of(
                "type",
                "oidc",
                "sso_provider",
                "google",
                "payload",
                Map.of("provider", "standard")));

    FederationConfiguration created = service.createConfiguration(request);

    assertNotNull(created.identifier().value());
    assertFalse(created.identifier().value().isEmpty());
  }

  @Test
  void usesIdFromRequestWhenPresent() {
    FederationConfigRequest request =
        new FederationConfigRequest(
            Map.of(
                "id",
                "explicit-id",
                "type",
                "oidc",
                "sso_provider",
                "google",
                "payload",
                Map.of("provider", "standard")));

    FederationConfiguration created = service.createConfiguration(request);

    assertEquals("explicit-id", created.identifier().value());
  }
}
