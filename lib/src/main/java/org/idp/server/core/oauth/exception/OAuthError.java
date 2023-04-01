package org.idp.server.core.oauth.exception;

import java.util.HashMap;
import java.util.Map;

public interface OAuthError {

  String error();

  String errorDescription();

  default Map<String, String> errors() {
    HashMap<String, String> map = new HashMap<>();
    map.put("error", error());
    map.put("error_description", errorDescription());
    return map;
  }
}
