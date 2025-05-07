package org.idp.server.core.oidc.configuration.authentication;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.AcrValues;

public class AuthenticationPolicy implements JsonReadable {
  String id;
  int priority;
  AuthenticationPolicyCondition conditions;
  List<String> availableMethods;
  AuthenticationResultConditions successConditions;
  AuthenticationResultConditions failureConditions;
  AuthenticationResultConditions lockConditions;

  public AuthenticationPolicy() {}

  public boolean anyMatch(AuthorizationFlow authorizationFlow, AcrValues acrValues, Scopes scopes) {
    return conditions.anyMatch(authorizationFlow, acrValues, scopes);
  }

  public AuthenticationPolicyIdentifier identifier() {
    return new AuthenticationPolicyIdentifier(id);
  }

  public int priority() {
    return priority;
  }

  public AuthenticationPolicyCondition conditions() {
    return conditions;
  }

  public boolean hasPolicyConditions() {
    return conditions != null;
  }

  public List<String> availableMethods() {
    return availableMethods;
  }

  public boolean hasAvailableMethods() {
    return availableMethods != null;
  }

  public AuthenticationResultConditions successConditions() {
    return successConditions;
  }

  public boolean hasSuccessConditions() {
    return successConditions != null;
  }

  public AuthenticationResultConditions failureConditions() {
    return failureConditions;
  }

  public boolean hasFailureConditions() {
    return failureConditions != null;
  }

  public AuthenticationResultConditions lockConditions() {
    return lockConditions;
  }

  public boolean hasLockConditions() {
    return lockConditions != null;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    if (hasPolicyConditions()) map.put("conditions", conditions.toMap());
    if (hasAvailableMethods()) map.put("available_methods", availableMethods);
    if (hasSuccessConditions()) map.put("success_conditions", successConditions.toMap());
    if (hasFailureConditions()) map.put("failure_conditions", failureConditions.toMap());
    if (hasLockConditions()) map.put("lock_conditions", lockConditions.toMap());
    return map;
  }
}
