package org.idp.server.core.adapters.datasource.oidc.configuration.server;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;

class ModelConverter {
  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static AuthorizationServerConfiguration convert(Map<String, String> stringMap) {
    return jsonConverter.read(stringMap.get("payload"), AuthorizationServerConfiguration.class);
  }
}
