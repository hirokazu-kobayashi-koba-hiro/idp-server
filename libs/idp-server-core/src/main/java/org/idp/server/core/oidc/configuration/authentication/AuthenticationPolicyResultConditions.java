package org.idp.server.core.oidc.configuration.authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class AuthenticationPolicyResultConditions implements JsonReadable {

  List<AuthenticationPolicyCondition> anyOf;
  List<AuthenticationPolicyCondition> allOf;

  public AuthenticationPolicyResultConditions() {}

  public AuthenticationPolicyResultConditions(
      List<AuthenticationPolicyCondition> anyOf, List<AuthenticationPolicyCondition> allOf) {
    this.anyOf = anyOf;
    this.allOf = allOf;
  }

  public List<AuthenticationPolicyCondition> anyOf() {
    return anyOf;
  }

  public List<AuthenticationPolicyCondition> allOf() {
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
