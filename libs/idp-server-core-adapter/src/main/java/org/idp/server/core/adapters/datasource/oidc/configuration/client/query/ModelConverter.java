/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
