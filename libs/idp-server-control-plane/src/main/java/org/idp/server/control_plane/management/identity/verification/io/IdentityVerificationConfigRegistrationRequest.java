package org.idp.server.control_plane.management.identity.verification.io;

import java.util.Map;

public class IdentityVerificationConfigRegistrationRequest {

  Map<String, Object> values;

  public IdentityVerificationConfigRegistrationRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
