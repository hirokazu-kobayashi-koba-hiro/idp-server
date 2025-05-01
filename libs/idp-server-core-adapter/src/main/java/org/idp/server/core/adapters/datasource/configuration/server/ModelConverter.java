package org.idp.server.core.adapters.datasource.configuration.server;

import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.configuration.ServerConfiguration;

class ModelConverter {
  static JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  static ServerConfiguration convert(Map<String, String> stringMap) {
    return jsonConverter.read(stringMap.get("payload"), ServerConfiguration.class);
  }
}
