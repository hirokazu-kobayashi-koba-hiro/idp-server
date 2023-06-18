package org.idp.server.handler.configuration.datasource.database.server;

import java.util.Map;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.configuration.ServerConfiguration;

class ModelConverter {
  static JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  static ServerConfiguration convert(Map<String, String> stringMap) {
    return jsonParser.read(stringMap.get("payload"), ServerConfiguration.class);
  }
}
