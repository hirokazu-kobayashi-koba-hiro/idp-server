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

package org.idp.server.core.adapters.datasource.federation.config.query;

import java.util.Map;
import org.idp.server.core.oidc.federation.FederationConfiguration;
import org.idp.server.platform.json.JsonNodeWrapper;

class ModelConverter {

  static FederationConfiguration convert(Map<String, String> result) {
    String id = result.get("id");
    String type = result.get("type");
    String ssoProvider = result.get("sso_provider");
    String payloadJson = result.get("payload");
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(payloadJson);
    Map<String, Object> payload = jsonNodeWrapper.toMap();

    return new FederationConfiguration(id, type, ssoProvider, payload);
  }
}
