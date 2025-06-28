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

package org.idp.server.security.event.hooks.webhook;

import java.util.Map;
import org.idp.server.platform.http.*;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.security.event.SecurityEventType;

public class WebHookConfiguration implements JsonReadable {

  WebHookConfig base;
  Map<String, WebHookConfig> overlays;

  public WebHookConfiguration() {}

  public WebHookConfiguration(WebHookConfig base, Map<String, WebHookConfig> overlays) {
    this.base = base;
    this.overlays = overlays;
  }

  public HttpRequestUrl httpRequestUrl(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).httpRequestUrl();
    }
    return base.httpRequestUrl();
  }

  public HttpMethod httpMethod(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).httpMethod();
    }
    return base.httpMethod();
  }

  public HttpRequestStaticHeaders httpRequestHeaders(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).httpRequestHeaders();
    }
    return base.httpRequestHeaders();
  }

  public HttpRequestDynamicBodyKeys httpRequestDynamicBodyKeys(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).httpRequestDynamicBodyKeys();
    }
    return base.httpRequestDynamicBodyKeys();
  }

  public HttpRequestStaticBody httpRequestStaticBody(SecurityEventType type) {
    if (overlays.containsKey(type.value())) {
      return overlays.get(type.value()).httpRequestStaticBody();
    }
    return base.httpRequestStaticBody();
  }
}
