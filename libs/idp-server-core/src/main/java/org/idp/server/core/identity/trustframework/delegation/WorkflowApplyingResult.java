package org.idp.server.core.identity.trustframework.delegation;

import java.util.Map;

public class WorkflowApplyingResult {
  int statusCode;
  Map<String, Object> response;

  public WorkflowApplyingResult() {}

  public WorkflowApplyingResult(int statusCode, Map<String, Object> response) {
    this.statusCode = statusCode;
    this.response = response;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, Object> response() {
    return response;
  }
}
