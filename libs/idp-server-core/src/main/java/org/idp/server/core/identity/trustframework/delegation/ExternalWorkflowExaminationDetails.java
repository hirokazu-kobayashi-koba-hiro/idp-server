package org.idp.server.core.identity.trustframework.delegation;

import java.util.Map;
import org.idp.server.core.basic.json.JsonNodeWrapper;

public class ExternalWorkflowExaminationDetails {

  JsonNodeWrapper json;

  public ExternalWorkflowExaminationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public ExternalWorkflowExaminationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }
}
