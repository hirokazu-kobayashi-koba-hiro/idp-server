package org.idp.server.handler.configuration.datasource.database.server;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.configuration.ServerConfiguration;

class ModelConverter {
  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static ServerConfiguration convert(Map<String, String> stringMap) {
    return jsonConverter.read(stringMap.get("payload"), ServerConfiguration.class);
  }
}
