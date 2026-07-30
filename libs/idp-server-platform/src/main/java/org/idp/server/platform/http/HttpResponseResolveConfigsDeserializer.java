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
 * Accepts {@code response_resolve_configs} in both the canonical bare-array form {@code [...]} and
 * the legacy object-wrapper form {@code {"configs": [...]}}.
 *
 * <p>Backport of the dual-format reader introduced in v0.12.0 (#1500), which changed the persisted
 * representation from the object-wrapper to a bare array. This v0.10.x line originally modeled
 * {@link HttpResponseResolveConfigs} as a plain bean and therefore fails with {@code
 * MismatchedInputException} ("from Array value") when it reads configs written by v0.12.0+. Adding
 * this deserializer lets a v0.10.x app read the new array form, so a v0.12.0+ database stays
 * readable during a rollback or a mixed-version rolling deployment. Output is unchanged (the app
 * keeps writing the object-wrapper form, which v0.12.0+ still reads).
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
