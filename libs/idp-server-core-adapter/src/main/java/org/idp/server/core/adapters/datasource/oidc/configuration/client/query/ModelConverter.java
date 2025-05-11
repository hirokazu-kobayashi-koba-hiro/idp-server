package org.idp.server.core.adapters.datasource.oidc.configuration.client.query;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

class ModelConverter {
  static JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  static ClientConfiguration convert(Map<String, String> stringMap) {
    return jsonConverter.read(stringMap.get("payload"), ClientConfiguration.class);
  }
}
