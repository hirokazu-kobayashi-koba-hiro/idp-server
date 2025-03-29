package org.idp.server.core.oauth.grant.consent;

import java.util.HashMap;
import java.util.Map;

public class ConsentClaims {

  Map<String, Object> values;

  public ConsentClaims() {
    this.values = new HashMap<>();
  }

  public ConsentClaims(Map<String, Object> values) {
    this.values = values;
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }

  public Map<String, Object> toMap() {
    return values;
  }

  public ConsentClaims merge(ConsentClaims other) {
    Map<String, Object> merged = new HashMap<>(this.values);
    merged.putAll(other.toMap());
    return new ConsentClaims(merged);
  }
}
