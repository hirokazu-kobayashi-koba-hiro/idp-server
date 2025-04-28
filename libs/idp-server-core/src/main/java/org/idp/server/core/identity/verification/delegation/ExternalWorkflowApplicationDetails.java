package org.idp.server.core.identity.verification.delegation;

import java.util.Map;
import org.idp.server.core.basic.json.JsonNodeWrapper;

public class ExternalWorkflowApplicationDetails {

  JsonNodeWrapper json;

  public ExternalWorkflowApplicationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public ExternalWorkflowApplicationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public boolean exists() {
    return json != null && json.exists();
  }
}
