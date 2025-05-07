package org.idp.server.core.oidc.configuration.mfa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class MfaPolicy implements JsonReadable {
  String id;
  MfaPolicyCondition policyConditions;
  List<String> availableMethods;
  MfaResultConditions successConditions;
  MfaResultConditions failureConditions;
  MfaResultConditions lockConditions;

  public MfaPolicy() {}

  public MfaPolicyIdentifier identifier() {
    return new MfaPolicyIdentifier(id);
  }

  public MfaPolicyCondition policyConditions() {
    return policyConditions;
  }

  public boolean hasPolicyConditions() {
    return policyConditions != null;
  }

  public List<String> availableMethods() {
    return availableMethods;
  }

  public boolean hasAvailableMethods() {
    return availableMethods != null;
  }

  public MfaResultConditions successConditions() {
    return successConditions;
  }

  public boolean hasSuccessConditions() {
    return successConditions != null;
  }

  public MfaResultConditions failureConditions() {
    return failureConditions;
  }

  public boolean hasFailureConditions() {
    return failureConditions != null;
  }

  public MfaResultConditions lockConditions() {
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
    if (hasPolicyConditions()) map.put("policy_conditions", policyConditions.toMap());
    if (hasAvailableMethods()) map.put("available_methods", availableMethods);
    if (hasSuccessConditions()) map.put("success_conditions", successConditions.toMap());
    if (hasFailureConditions()) map.put("failure_conditions", failureConditions.toMap());
    if (hasLockConditions()) map.put("lock_conditions", lockConditions.toMap());
    return map;
  }
}
