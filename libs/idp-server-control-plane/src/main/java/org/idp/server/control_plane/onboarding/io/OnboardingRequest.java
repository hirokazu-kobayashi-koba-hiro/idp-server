package org.idp.server.control_plane.onboarding.io;

import java.util.Map;

public class OnboardingRequest {

  Map<String, Object> values;

  public OnboardingRequest(Map<String, Object> values) {
    this.values = values;
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public Object get(String key) {
    return values.get(key);
  }
}
