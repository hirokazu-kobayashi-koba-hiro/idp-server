package org.idp.server.core.oidc.configuration.mfa;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.json.JsonReadable;

public class MfaPolicyCondition implements JsonReadable {
  List<String> acrValues;
  List<String> scopes;

  public MfaPolicyCondition() {}

  public MfaPolicyCondition(List<String> acrValues, List<String> scopes) {
    this.acrValues = acrValues;
    this.scopes = scopes;
  }

  public List<String> acrValues() {
    return acrValues;
  }

  public List<String> scopes() {
    return scopes;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("acr_values", acrValues);
    map.put("scopes", scopes);
    return map;
  }
}
