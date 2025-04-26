package org.idp.server.core.identity.trustframework.application;

import java.util.Map;
import org.idp.server.core.basic.json.JsonNodeWrapper;

public class IdentityVerificationApplicationDetails {

  JsonNodeWrapper json;

  public IdentityVerificationApplicationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public IdentityVerificationApplicationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }
}
