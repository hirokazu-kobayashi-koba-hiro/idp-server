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
package org.idp.server.core.openid.oauth.configuration.vci;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CredentialIssuanceConfigurationTest {

  private final JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  private final Map<String, Object> stored =
      Map.of(
          "signing_key_id",
          "credential-signing-key",
          "credentials",
          Map.of(
              "identity_credential",
              Map.of(
                  "expires_in",
                  86400L,
                  "claims",
                  List.of(
                      Map.of(
                          "name",
                          "given_name",
                          "from",
                          "$.given_name",
                          "selectively_disclosable",
                          true),
                      Map.of(
                          "name",
                          "country",
                          "from",
                          "$.address.country",
                          "selectively_disclosable",
                          false)))));

  @Test
  @DisplayName("保存した形のまま往復する（管理 API の GET → PUT で失われない）")
  void storedFormRoundTrips() {
    CredentialIssuanceConfiguration configuration =
        jsonConverter.read(stored, CredentialIssuanceConfiguration.class);

    assertEquals(stored, jsonConverter.read(configuration.toMap(), Map.class));
  }

  @Test
  @DisplayName("selectively_disclosable は省略すると true（保有者が開示を選べるのが既定）")
  void selectiveDisclosureIsTheDefault() {
    CredentialIssuanceConfiguration configuration =
        jsonConverter.read(
            Map.of(
                "signing_key_id",
                "k",
                "credentials",
                Map.of("c", Map.of("claims", List.of(Map.of("name", "email", "from", "$.email"))))),
            CredentialIssuanceConfiguration.class);

    CredentialIssuanceDefinition definition = configuration.definition("c");
    assertTrue(definition.claims().get(0).selectivelyDisclosable());
    assertEquals(31536000L, definition.expiresIn());
    assertFalse(configuration.definition("unknown").exists());
  }
}
