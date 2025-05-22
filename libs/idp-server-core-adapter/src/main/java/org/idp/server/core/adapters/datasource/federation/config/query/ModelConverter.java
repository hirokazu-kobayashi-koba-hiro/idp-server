/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.federation.config.query;

import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.oidc.federation.FederationConfiguration;

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
