package org.idp.server.handler.configuration.datasource.database.client;

import java.util.Map;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.configuration.ClientConfiguration;

class ModelConverter {
  static JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  static ClientConfiguration convert(Map<String, String> stringMap) {
    return jsonParser.read(stringMap.get("payload"), ClientConfiguration.class);
  }
}
