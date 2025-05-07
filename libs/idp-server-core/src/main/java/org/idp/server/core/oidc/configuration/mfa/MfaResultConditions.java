package org.idp.server.core.oidc.configuration.mfa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class MfaResultConditions implements JsonReadable {

  List<MfaCondition> anyOf;
  List<MfaCondition> allOf;

  public MfaResultConditions() {}

  public MfaResultConditions(List<MfaCondition> anyOf, List<MfaCondition> allOf) {
    this.anyOf = anyOf;
    this.allOf = allOf;
  }

  public List<MfaCondition> anyOf() {
    return anyOf;
  }

  public List<MfaCondition> allOf() {
    return allOf;
  }

  public boolean hasAnyOf() {
    return anyOf != null && !anyOf.isEmpty();
  }

  public boolean hasAllOf() {
    return allOf != null && !allOf.isEmpty();
  }

  public boolean exists() {
    return hasAnyOf() || hasAllOf();
  }

  public Map<String, Object> toMap() {
    HashMap<String, Object> map = new HashMap<>();
    if (hasAnyOf()) map.put("any_of", anyOf);
    if (hasAllOf()) map.put("all_of", allOf);
    return map;
  }
}
