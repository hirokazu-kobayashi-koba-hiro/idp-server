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

package org.idp.server.core.openid.oauth.configuration.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.idp.server.platform.json.JsonConverter;
import org.junit.jupiter.api.Test;

/**
 * #1833: client custom properties must reach the authentication device through {@link
 * ClientAttributes}.
 *
 * <p>{@link ClientAttributes} is serialized into {@code oauth_token.client_payload}, the
 * authorization code grant, the authorization request and the authentication transaction, so adding
 * a field has to stay compatible with rows written before it existed — and with an older
 * application reading rows written after it.
 */
class ClientAttributesTest {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Test
  void toMap_exposesCustomProperties() {
    ClientAttributes clientAttributes =
        new ClientAttributes(
            "client-x",
            null,
            "My App",
            null,
            null,
            null,
            null,
            null,
            Map.of("brand_color", "#0075ca", "support_phone", "0120-000-000"));

    Map<String, Object> map = clientAttributes.toMap();

    assertEquals("client-x", map.get("client_id"));
    @SuppressWarnings("unchecked")
    Map<String, Object> customProperties = (Map<String, Object>) map.get("custom_properties");
    assertEquals("#0075ca", customProperties.get("brand_color"));
    assertEquals("0120-000-000", customProperties.get("support_phone"));
  }

  @Test
  void toMap_omitsCustomPropertiesWhenEmpty() {
    ClientAttributes clientAttributes =
        new ClientAttributes("client-x", null, "My App", null, null, null, null, null, null);

    assertFalse(clientAttributes.hasCustomProperties());
    assertFalse(clientAttributes.toMap().containsKey("custom_properties"));
  }

  @Test
  void read_legacyJsonWithoutCustomProperties() {
    String legacyJson =
        """
        {"client_id":"client-x","client_name":"My App","logo_uri":"https://example.com/logo.png"}
        """;

    ClientAttributes clientAttributes = jsonConverter.read(legacyJson, ClientAttributes.class);

    assertEquals("client-x", clientAttributes.identifier().value());
    assertNotNull(clientAttributes.customProperties());
    assertFalse(clientAttributes.hasCustomProperties());
    assertFalse(clientAttributes.toMap().containsKey("custom_properties"));
  }

  @Test
  void read_explicitNullCustomProperties() {
    String jsonWithNull = """
        {"client_id":"client-x","custom_properties":null}
        """;

    ClientAttributes clientAttributes = jsonConverter.read(jsonWithNull, ClientAttributes.class);

    assertNotNull(clientAttributes.customProperties());
    assertFalse(clientAttributes.hasCustomProperties());
    assertFalse(clientAttributes.toMap().containsKey("custom_properties"));
  }

  @Test
  void read_roundTripsCustomProperties() {
    ClientAttributes original =
        new ClientAttributes(
            "client-x", null, "My App", null, null, null, null, null, Map.of("tier", "gold"));

    ClientAttributes restored =
        jsonConverter.read(jsonConverter.write(original), ClientAttributes.class);

    assertEquals("gold", restored.customProperties().get("tier"));
  }

  /**
   * An application deployed before this field existed still has to read rows written after it
   * (rolling deploy). That reduces to whether an unknown property is tolerated.
   */
  @Test
  void read_toleratesUnknownProperty() {
    String forwardJson =
        """
        {"client_id":"client-x","custom_properties":{"tier":"gold"},"not_yet_known":"value"}
        """;

    ClientAttributes clientAttributes = jsonConverter.read(forwardJson, ClientAttributes.class);

    assertEquals("client-x", clientAttributes.identifier().value());
    assertEquals("gold", clientAttributes.customProperties().get("tier"));
  }
}
