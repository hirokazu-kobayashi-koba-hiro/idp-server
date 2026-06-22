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

import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * Serializes {@link HttpResponseResolveConfigs} as a bare array ({@code [...]}) — the canonical
 * form shared with identity-verification (issue #1500) — rather than the legacy object wrapper
 * ({@code {"configs": [...]}}).
 *
 * <p>This makes the persisted form (via {@code JsonConverter.write}) and the management API output
 * consistent: both emit the array form. Paired with {@link HttpResponseResolveConfigsDeserializer}
 * (which still accepts both forms) and registered centrally in {@code JsonConverter} so
 * configuration classes stay free of JSON-library annotations.
 */
public class HttpResponseResolveConfigsSerializer
    extends ValueSerializer<HttpResponseResolveConfigs> {

  @Override
  public void serialize(
      HttpResponseResolveConfigs value, JsonGenerator gen, SerializationContext ctxt)
      throws JacksonException {
    gen.writeStartArray();
    List<HttpResponseResolveConfig> configs = value.configs();
    if (configs != null) {
      for (HttpResponseResolveConfig config : configs) {
        ctxt.writeValue(gen, config);
      }
    }
    gen.writeEndArray();
  }
}
