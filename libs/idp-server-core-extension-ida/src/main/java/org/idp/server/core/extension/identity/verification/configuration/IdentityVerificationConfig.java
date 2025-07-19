package org.idp.server.core.extension.identity.verification.configuration;

import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class IdentityVerificationConfig implements JsonReadable {
  String type;
  Map<String, Object> details;

  public IdentityVerificationConfig() {}

  public IdentityVerificationConfig(String type, Map<String, Object> details) {
    this.type = type;
    this.details = details;
  }

  public String type() {
    return type;
  }

  public Map<String, Object> details() {
    return details;
  }
}
