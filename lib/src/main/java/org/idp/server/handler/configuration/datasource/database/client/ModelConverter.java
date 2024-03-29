package org.idp.server.handler.configuration.datasource.database.client;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.configuration.ClientConfiguration;

class ModelConverter {
  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static ClientConfiguration convert(Map<String, String> stringMap) {
    return jsonConverter.read(stringMap.get("payload"), ClientConfiguration.class);
  }
}
