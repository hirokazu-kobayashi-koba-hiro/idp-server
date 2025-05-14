package org.idp.server.core.adapters.datasource.federation.config.query;

import java.util.Map;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.federation.FederationConfiguration;

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
