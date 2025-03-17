package org.idp.server.core.adapters.datasource.configuration.database.client;

import java.util.Map;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.configuration.ClientConfiguration;

class ModelConverter {
  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static ClientConfiguration convert(Map<String, String> stringMap) {
    return jsonConverter.read(stringMap.get("payload"), ClientConfiguration.class);
  }
}
