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

package org.idp.server.core.openid.federation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * #1742: {@link FederationConfiguration#toMap()} must expose sso_provider so the GET API returns
 * it.
 */
class FederationConfigurationTest {

  @Test
  void toMapExposesSsoProvider() {
    FederationConfiguration configuration =
        new FederationConfiguration("id-1", "oidc", "google", Map.of("provider", "standard"));

    Map<String, Object> map = configuration.toMap();

    assertEquals("google", map.get("sso_provider"));
    // sanity: the historically-present keys are still there.
    assertEquals("id-1", map.get("id"));
    assertEquals("oidc", map.get("type"));
    assertTrue(map.containsKey("payload"));
    assertTrue(map.containsKey("enabled"));
  }
}
