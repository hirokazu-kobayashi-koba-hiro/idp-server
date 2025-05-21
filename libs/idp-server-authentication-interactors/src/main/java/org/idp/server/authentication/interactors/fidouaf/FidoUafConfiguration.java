package org.idp.server.authentication.interactors.fidouaf;

import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.authentication.interactors.webauthn.WebAuthnCredentialNotFoundException;

public class FidoUafConfiguration implements JsonReadable {
  String type;
  String deviceIdParam;
  Map<String, Map<String, Object>> details;

  public FidoUafConfiguration() {}

  public FidoUafExecutorType type() {
    return new FidoUafExecutorType(type);
  }

  public String deviceIdParam() {
    return deviceIdParam;
  }

  public Map<String, Map<String, Object>> details() {
    return details;
  }

  public Map<String, Object> getDetail(FidoUafExecutorType type) {
    if (!details.containsKey(type.name())) {
      throw new WebAuthnCredentialNotFoundException(
          "invalid configuration. key: " + type.name() + " is unregistered.");
    }
    return details.get(type.name());
  }
}
