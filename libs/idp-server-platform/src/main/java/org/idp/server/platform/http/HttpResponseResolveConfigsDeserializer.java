/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.platform.http;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * Deserializes {@code response_resolve_configs} from both the canonical array form ({@code [...]})
 * and the legacy object-wrapper form ({@code {"configs": [...]}}).
 *
 * <p>The array form matches identity-verification configurations and is what {@link
 * HttpRequestExecutionConfig#toMap()} now emits. The wrapper form is accepted for backward
 * compatibility with configurations persisted before issue #1500: the field was previously typed as
 * {@link HttpResponseResolveConfigs}, so any configuration embedding an {@link
 * HttpRequestExecutionConfig} (security event hooks, federation, email sender, etc.) may still hold
 * the wrapper form.
 *
 * <p>Registered centrally in {@code JsonConverter} so configuration classes stay free of
 * JSON-library annotations.
 */
public class HttpResponseResolveConfigsDeserializer
    extends ValueDeserializer<HttpResponseResolveConfigs> {

  @Override
  public HttpResponseResolveConfigs deserialize(JsonParser p, DeserializationContext ctxt)
      throws JacksonException {
    JsonNode node = ctxt.readTree(p);

    JsonNode arrayNode = null;
    if (node.isArray()) {
      arrayNode = node;
    } else if (node.isObject() && node.has("configs")) {
      arrayNode = node.get("configs");
    }

    List<HttpResponseResolveConfig> configs = new ArrayList<>();
    if (arrayNode != null && arrayNode.isArray()) {
      for (JsonNode element : arrayNode) {
        configs.add(ctxt.readTreeAsValue(element, HttpResponseResolveConfig.class));
      }
    }
    return new HttpResponseResolveConfigs(configs);
  }
}
