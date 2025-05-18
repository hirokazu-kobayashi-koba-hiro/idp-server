package org.idp.server.control_plane.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthorizationDetailRequestBuilder {
  Map<String, Object> detail;

  public AuthorizationDetailRequestBuilder() {
    this.detail = new HashMap<>();
  }

  public AuthorizationDetailRequestBuilder addType(String type) {
    detail.put("type", type);
    return this;
  }

  public AuthorizationDetailRequestBuilder addActions(List<String> actions) {
    detail.put("actions", actions);
    return this;
  }

  public AuthorizationDetailRequestBuilder addLocations(List<String> locations) {
    detail.put("locations", locations);
    return this;
  }

  public AuthorizationDetailRequestBuilder add(String key, Object value) {
    this.detail.put(key, value);
    return this;
  }

  public Map<String, Object> build() {
    return detail;
  }
}
