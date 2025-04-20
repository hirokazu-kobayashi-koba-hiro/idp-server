package org.idp.server.core.authentication.webauthn;

import java.util.Map;
import org.idp.server.core.basic.json.JsonReadable;

public class WebAuthnConfiguration implements JsonReadable {
  String type;
  Map<String, Map<String, Object>> details;

  public WebAuthnConfiguration() {}

  public WebAuthnExecutorType type() {
    return new WebAuthnExecutorType(type);
  }

  public Map<String, Map<String, Object>> details() {
    return details;
  }

  public Map<String, Object> getDetail(WebAuthnExecutorType key) {
    if (!details.containsKey(key.value())) {
      throw new WebAuthnCredentialNotFoundException(
          "invalid configuration. key: " + key + "is unregistered.");
    }
    return details.get(key.value());
  }
}
