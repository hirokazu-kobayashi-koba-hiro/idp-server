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
import org.idp.server.platform.json.JsonReadable;

/**
 * Internal container for an ordered list of {@link HttpResponseResolveConfig}.
 *
 * <p>Configs are evaluated in order; the first matching config is used.
 *
 * <p>The JSON representation is a bare array (see {@link HttpResponseResolveConfigsSerializer} and
 * {@link HttpResponseResolveConfigsDeserializer}, registered centrally in {@code JsonConverter}).
 * The legacy object-wrapper form {@code {"configs": [...]}} is still accepted on input for backward
 * compatibility but is normalized to the array form on output. Each condition path is evaluated
 * against the resolve context built by {@link HttpResponseResolver}: {@code $.status_code}, {@code
 * $.response_headers.*} and {@code $.response_body.*}.
 *
 * <pre>{@code
 * [
 *   {
 *     "conditions": [
 *       {"path": "$.status_code", "operation": "in", "value": [200, 201]},
 *       {"path": "$.response_body.status", "operation": "eq", "value": "approved"}
 *     ],
 *     "match_mode": "ALL",
 *     "mapped_status_code": 200
 *   },
 *   {
 *     "conditions": [
 *       {"path": "$.status_code", "operation": "eq", "value": 503}
 *     ],
 *     "match_mode": "ALL",
 *     "mapped_status_code": 503
 *   }
 * ]
 * }</pre>
 */
public class HttpResponseResolveConfigs implements JsonReadable {
  private List<HttpResponseResolveConfig> configs;

  public HttpResponseResolveConfigs() {
    this.configs = new ArrayList<>();
  }

  public HttpResponseResolveConfigs(List<HttpResponseResolveConfig> configs) {
    this.configs = configs;
  }

  public List<HttpResponseResolveConfig> configs() {
    return configs;
  }

  public boolean isEmpty() {
    return configs == null || configs.isEmpty();
  }
}
