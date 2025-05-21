package org.idp.server.security.event.hooks.webhook;

import java.util.Map;
import org.idp.server.basic.http.*;
import org.idp.server.basic.json.JsonReadable;
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

  public HttpRequestHeaders httpRequestHeaders(SecurityEventType type) {
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
